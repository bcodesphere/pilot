package com.bcodesphere.pilot.plataforma.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Pruebas del adaptador de auditoría: empresa, usuario, traceId y valores JSON quedan en {@code auditoria}. */
class AuditoriaIT extends BasePlataformaIT {

    @Autowired
    private RegistroAuditoria auditoria;

    @Autowired
    private PlatformTransactionManager gestor;

    @Autowired
    private JdbcClient jdbc;

    /** Caso: la fila queda con empresa, usuario, traceId del MDC y los valores nuevo y anterior como JSON. */
    @Test
    void laFilaQuedaConEmpresaUsuarioYTraceId() throws SQLException {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());
        MDC.put(ClavesMdc.TRACE_ID, "traza-auditoria-1");
        try {
            ContextoEmpresa.ejecutarCon(
                    empresa,
                    "usuario-aud",
                    () -> new TransactionTemplate(gestor).executeWithoutResult(s -> {
                        auditoria.registrar(
                                "cuenta_contable",
                                "c-1",
                                "ACTUALIZAR",
                                Map.of("nombre", "Caja"),
                                Map.of("nombre", "Caja general"));
                        auditoria.registrar("cuenta_contable", "c-2", "CREAR", null, Map.of("nombre", "Bancos"));
                    }));
        } finally {
            MDC.remove(ClavesMdc.TRACE_ID);
        }

        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection();
                PreparedStatement ps = c.prepareStatement("SELECT entidad_id, accion, usuario_id, trace_id,"
                        + " valor_anterior::text, valor_nuevo->>'nombre' FROM auditoria"
                        + " WHERE empresa_id = ?::uuid ORDER BY entidad_id")) {
            ps.setString(1, empresa.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("entidad_id")).isEqualTo("c-1");
                assertThat(rs.getString("accion")).isEqualTo("ACTUALIZAR");
                assertThat(rs.getString("usuario_id")).isEqualTo("usuario-aud");
                assertThat(rs.getString("trace_id")).isEqualTo("traza-auditoria-1");
                assertThat(rs.getString(5)).contains("Caja");
                assertThat(rs.getString(6)).isEqualTo("Caja general");
                // Segunda fila: en una creación el valor anterior es NULL de SQL
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("entidad_id")).isEqualTo("c-2");
                assertThat(rs.getString(5)).isNull();
                assertThat(rs.next()).isFalse();
            }
        }
    }

    /** Caso: la auditoría de la empresa A no es visible desde una transacción de la empresa B (RLS). */
    @Test
    void laAuditoriaDeUnaEmpresaNoSeVeDesdeOtra() {
        EmpresaId a = new EmpresaId(GeneradorId.nuevo());
        EmpresaId b = new EmpresaId(GeneradorId.nuevo());
        ContextoEmpresa.ejecutarCon(
                a,
                "u",
                () -> new TransactionTemplate(gestor)
                        .executeWithoutResult(s -> auditoria.registrar("asiento", "1", "CREAR", null, Map.of("n", 1))));

        Integer vistasPorB = ContextoEmpresa.ejecutarCon(
                b,
                "u",
                () -> new TransactionTemplate(gestor)
                        .execute(s -> jdbc.sql("SELECT count(*) FROM auditoria")
                                .query(Integer.class)
                                .single()));
        Integer vistasPorA = ContextoEmpresa.ejecutarCon(
                a,
                "u",
                () -> new TransactionTemplate(gestor)
                        .execute(s -> jdbc.sql("SELECT count(*) FROM auditoria")
                                .query(Integer.class)
                                .single()));

        assertThat(vistasPorB).isZero();
        assertThat(vistasPorA).isEqualTo(1);
    }

    /** Caso: fuera de una transacción el registro falla (MANDATORY): la auditoría debe ir con la mutación. */
    @Test
    void sinTransaccionActivaFalla() {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());

        assertThatThrownBy(() -> ContextoEmpresa.ejecutarCon(
                        empresa, "u", () -> auditoria.registrar("asiento", "1", "CREAR", null, Map.of("n", 1))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
