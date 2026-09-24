package com.bcodesphere.pilot.aceptacion.f0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Aceptación F0, criterio 2 del plan: "Una tabla de ejemplo con RLS: una prueba de integración demuestra que la
 * empresa A no lee ni escribe datos de la empresa B" (docs/plan-de-trabajo.md, F0). La tabla de ejemplo es
 * {@code idempotencia}. La aplicación se conecta como {@code pilot_app} (CLAUDE.md 4.5) y se ataca con SQL directo por
 * {@link JdbcClient}, es decir, sin pasar por ningún repositorio que pudiera filtrar por su cuenta.
 */
class AislamientoRlsIT extends BasePlataformaIT {

    /** Empresa activa en la sesión. UUID propio de esta clase para no chocar con otras pruebas. */
    private static final UUID EMPRESA_A = UUID.fromString("00000000-0000-7000-8000-0000000f0a01");

    /** Empresa ajena a la sesión. */
    private static final UUID EMPRESA_B = UUID.fromString("00000000-0000-7000-8000-0000000f0b01");

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager gestor;

    /** Siembra una fila por empresa como dueño (superusuario en pruebas: se salta RLS) y limpia las de corridas previas. */
    @BeforeEach
    void sembrar() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            // 1. Estado limpio: las pruebas son independientes entre sí
            try (PreparedStatement ps =
                    c.prepareStatement("DELETE FROM idempotencia WHERE empresa_id IN (?::uuid, ?::uuid)")) {
                ps.setString(1, EMPRESA_A.toString());
                ps.setString(2, EMPRESA_B.toString());
                ps.executeUpdate();
            }
            // 2. Una clave por empresa, con el mismo formato que guarda la aplicación
            for (UUID empresa : List.of(EMPRESA_A, EMPRESA_B)) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO idempotencia (empresa_id, clave, hash_solicitud, estado_http, respuesta)"
                                + " VALUES (?::uuid, ?, repeat('0', 64), 201, '{}'::jsonb)")) {
                    ps.setString(1, empresa.toString());
                    ps.setString(2, "clave-de-" + (empresa.equals(EMPRESA_A) ? "A" : "B"));
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Ejecuta el trabajo en una transacción de la aplicación con la empresa A activa. */
    private <T> T comoEmpresaA(Supplier<T> trabajo) {
        return ContextoEmpresa.ejecutarCon(
                new EmpresaId(EMPRESA_A), "u", () -> new TransactionTemplate(gestor).execute(s -> trabajo.get()));
    }

    /** Cuenta filas de una empresa con el dueño (salta RLS) para comprobar el estado real de la tabla. */
    private static int filasReales(UUID empresa) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection();
                PreparedStatement ps =
                        c.prepareStatement("SELECT count(*) FROM idempotencia WHERE empresa_id = ?::uuid")) {
            ps.setString(1, empresa.toString());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Criterio F0 (RLS, lectura): en contexto A, SELECT sin filtro solo devuelve filas de A. */
    @Test
    void laEmpresaANoLeeFilasDeLaEmpresaBConSqlDirecto() {
        // 1. Consulta sin WHERE de empresa: RLS es lo único que puede filtrar
        List<UUID> visibles =
                comoEmpresaA(() -> jdbc.sql("SELECT empresa_id FROM idempotencia WHERE clave LIKE 'clave-de-%'")
                        .query(UUID.class)
                        .list());

        // 2. Solo aparece la de A
        assertThat(visibles).containsExactly(EMPRESA_A);
    }

    /** Criterio F0 (RLS, lectura dirigida): pedir explícitamente las filas de B desde A devuelve cero filas. */
    @Test
    void pedirExplicitamenteFilasDeBDesdeANoDevuelveNada() {
        int filas = comoEmpresaA(() -> jdbc.sql("SELECT count(*) FROM idempotencia WHERE empresa_id = :b")
                .param("b", EMPRESA_B)
                .query(Integer.class)
                .single());

        assertThat(filas).isZero();
    }

    /** Criterio F0 (RLS, escritura): en contexto A, insertar una fila con empresa B es rechazado por WITH CHECK. */
    @Test
    void laEmpresaANoPuedeInsertarFilasDeLaEmpresaB() throws SQLException {
        assertThatThrownBy(() -> comoEmpresaA(() -> jdbc.sql(
                                "INSERT INTO idempotencia (empresa_id, clave, hash_solicitud, estado_http, respuesta)"
                                        + " VALUES (:e, 'intruso', repeat('0', 64), 201, '{}'::jsonb)")
                        .param("e", EMPRESA_B)
                        .update()))
                .isInstanceOf(DataAccessException.class);

        // La tabla de B sigue con su única fila original
        assertThat(filasReales(EMPRESA_B)).isEqualTo(1);
    }

    /** Criterio F0 (RLS, escritura): en contexto A, DELETE de filas de B no afecta ninguna y B conserva su fila. */
    @Test
    void laEmpresaANoPuedeBorrarFilasDeLaEmpresaB() throws SQLException {
        int borradas = comoEmpresaA(() -> jdbc.sql("DELETE FROM idempotencia WHERE empresa_id = :b")
                .param("b", EMPRESA_B)
                .update());

        assertThat(borradas).isZero();
        assertThat(filasReales(EMPRESA_B)).isEqualTo(1);
    }

    /** Criterio F0 (RLS, control positivo): A sí puede escribir y leer lo suyo, así que el aislamiento no es un bloqueo total. */
    @Test
    void laEmpresaASiPuedeEscribirYLeerSusPropiasFilas() {
        int filas = comoEmpresaA(() -> {
            jdbc.sql("INSERT INTO idempotencia (empresa_id, clave, hash_solicitud, estado_http, respuesta)"
                            + " VALUES (:e, 'propia', repeat('0', 64), 201, '{}'::jsonb)")
                    .param("e", EMPRESA_A)
                    .update();
            return jdbc.sql("SELECT count(*) FROM idempotencia WHERE clave = 'propia'")
                    .query(Integer.class)
                    .single();
        });

        assertThat(filas).isEqualTo(1);
    }

    /** Criterio F0 (RLS falla cerrada): sin empresa fijada en la sesión, la consulta falla en vez de mostrar filas. */
    @Test
    void sinEmpresaEnLaSesionLaConsultaFalla() {
        // Transacción sin ContextoEmpresa: el gestor exige empresa al abrirla, así que ni siquiera llega a consultar
        assertThatThrownBy(() -> new TransactionTemplate(gestor)
                        .execute(s -> jdbc.sql("SELECT count(*) FROM idempotencia")
                                .query(Integer.class)
                                .single()))
                .isInstanceOf(RuntimeException.class);
    }
}
