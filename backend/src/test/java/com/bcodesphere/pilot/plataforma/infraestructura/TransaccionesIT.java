package com.bcodesphere.pilot.plataforma.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ContextoEmpresaAusenteException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pruebas del gestor de transacciones con contexto de empresa, conectado como {@code pilot_app}.
 * El pool tiene UNA sola conexión para poder comprobar que el contexto no sobrevive a la transacción en la misma
 * conexión física (CLAUDE.md 4.5, ADR-002).
 */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class TransaccionesIT extends BasePlataformaIT {

    @Autowired
    private PlatformTransactionManager gestor;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DataSource dataSource;

    /** Ejecuta una transacción con el contexto indicado y devuelve el valor calculado en ella. */
    private <T> T enTransaccion(EmpresaId empresa, String usuario, Supplier<T> trabajo) {
        return ContextoEmpresa.ejecutarCon(
                empresa, usuario, () -> new TransactionTemplate(gestor).execute(s -> trabajo.get()));
    }

    /** Caso: el gestor registrado es el que fija el contexto de empresa. */
    @Test
    void elGestorPrincipalEsElTenantAware() {
        assertThat(gestor).isInstanceOf(TenantAwareTransactionManager.class);
    }

    /** Caso: dentro de la transacción app.empresa_id y app.usuario_id valen los del contexto. */
    @Test
    void laTransaccionFijaEmpresaYUsuarioEnPostgres() {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());

        String[] valores = enTransaccion(empresa, "usuario-prueba", () -> new String[] {
            jdbc.sql("SELECT current_setting('app.empresa_id')")
                    .query(String.class)
                    .single(),
            jdbc.sql("SELECT current_setting('app.usuario_id')")
                    .query(String.class)
                    .single()
        });

        assertThat(valores).containsExactly(empresa.toString(), "usuario-prueba");
    }

    /** Caso: el contexto no persiste fuera de la transacción; sin contexto la siguiente consulta FALLA (cerrada). */
    @Test
    void elContextoNoPersisteFueraDeLaTransaccion() throws SQLException {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());
        enTransaccion(
                empresa, "u", () -> jdbc.sql("SELECT 1").query(Integer.class).single());

        // Pool de una conexión: esta es la MISMA conexión física que usó la transacción anterior
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            assertThatThrownBy(() -> {
                        try (ResultSet rs = s.executeQuery("SELECT count(*) FROM idempotencia")) {
                            rs.next();
                        }
                    })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uuid");
        }
    }

    /** Caso: sin contexto de empresa abrir una transacción falla con un error claro y no filtra la conexión. */
    @Test
    void sinContextoNoSePuedeAbrirUnaTransaccion() {
        assertThatThrownBy(() -> new TransactionTemplate(gestor).execute(s -> 1))
                .isInstanceOf(ContextoEmpresaAusenteException.class)
                .hasMessageContaining("empresa activa");

        // Con el pool de una conexión, si la anterior se hubiera filtrado esto se bloquearía y fallaría
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());
        assertThat(enTransaccion(empresa, "u", () -> 1)).isEqualTo(1);
    }

    /** Caso: empresa y usuario están en el MDC durante la transacción y desaparecen al terminar. */
    @Test
    void elMdcSeLimpiaAlTerminarLaTransaccion() {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());

        String[] dentro = enTransaccion(
                empresa, "u-mdc", () -> new String[] {MDC.get(ClavesMdc.EMPRESA_ID), MDC.get(ClavesMdc.USUARIO_ID)});

        assertThat(dentro).containsExactly(empresa.toString(), "u-mdc");
        assertThat(MDC.get(ClavesMdc.EMPRESA_ID)).isNull();
        assertThat(MDC.get(ClavesMdc.USUARIO_ID)).isNull();
    }

    /** Caso: el MDC también se limpia si la transacción termina con error (rollback). */
    @Test
    void elMdcSeLimpiaTrasUnRollback() {
        EmpresaId empresa = new EmpresaId(GeneradorId.nuevo());

        assertThatThrownBy(() -> enTransaccion(empresa, "u", () -> {
                    throw new IllegalStateException("falla");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(ClavesMdc.EMPRESA_ID)).isNull();
        assertThat(MDC.get(ClavesMdc.USUARIO_ID)).isNull();
    }
}
