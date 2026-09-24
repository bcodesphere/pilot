package com.bcodesphere.pilot.plataforma.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de las migraciones base (F0-04): idempotencia, auditoría y aislamiento por Row-Level Security.
 * Flyway corre con su API sobre el DataSource del dueño, sin contexto de Spring. Fuente: CLAUDE.md 4.5, 9.2, ADR-002.
 */
class MigracionesIT {

    /** Empresa A de las pruebas (la "activa" en la sesión). */
    private static final UUID EMPRESA_A = UUID.fromString("00000000-0000-7000-8000-00000000000a");

    /** Empresa B de las pruebas (ajena a la sesión). */
    private static final UUID EMPRESA_B = UUID.fromString("00000000-0000-7000-8000-00000000000b");

    /** Resultado de migrar la base vacía, evaluado por la primera prueba. */
    private static MigrateResult resultado;

    /** Migra la base vacía una sola vez y siembra una fila de cada empresa como dueño (que se salta RLS por ser superusuario en pruebas). */
    @BeforeAll
    static void migrar() throws SQLException {
        // 1. Flyway con el dueño y las migraciones reales del classpath
        resultado = Flyway.configure()
                .dataSource(PostgresContenedor.dataSourceDuenio())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // 2. Siembra: una clave por empresa, para comprobar visibilidad cruzada
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection();
                Statement s = c.createStatement()) {
            s.execute(insertIdempotencia(EMPRESA_A, "clave-a"));
            s.execute(insertIdempotencia(EMPRESA_B, "clave-b"));
        }
    }

    /** Sentencia de siembra de una fila de idempotencia. */
    private static String insertIdempotencia(UUID empresa, String clave) {
        return "INSERT INTO idempotencia (empresa_id, clave, hash_solicitud, estado_http, respuesta) VALUES ('"
                + empresa + "', '" + clave + "', repeat('a', 64), 201, '{}'::jsonb)";
    }

    /** Abre una conexión de pilot_app dentro de una transacción con app.empresa_id fijado (equivale a set_config local). */
    private static Connection appConEmpresa(UUID empresa) throws SQLException {
        Connection c = PostgresContenedor.dataSourceApp().getConnection();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.empresa_id', ?, true)")) {
            ps.setString(1, empresa.toString());
            ps.execute();
        }
        return c;
    }

    /** Ejecuta una sentencia con pilot_app y la empresa indicada; devuelve la primera columna del primer registro o null. */
    private static String primeraColumna(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    /** Caso: Flyway migra una base vacía sin errores y aplica las tres migraciones. */
    @Test
    void flywayMigraUnaBaseVaciaSinErrores() {
        assertThat(resultado.success).isTrue();
        assertThat(Flyway.configure()
                        .dataSource(PostgresContenedor.dataSourceDuenio())
                        .load()
                        .info()
                        .applied())
                .hasSize(3);
    }

    /** Caso: con empresa A en sesión, insertar una fila de la empresa B viola la política (WITH CHECK). */
    @Test
    void noPuedeInsertarFilaDeOtraEmpresa() throws SQLException {
        try (Connection c = appConEmpresa(EMPRESA_A);
                Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute(insertIdempotencia(EMPRESA_B, "intruso")))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    /** Caso: con empresa A en sesión, pilot_app ve su fila y no la de B. */
    @Test
    void soloVeFilasDeLaEmpresaActiva() throws SQLException {
        try (Connection c = appConEmpresa(EMPRESA_A)) {
            assertThat(primeraColumna(c, "SELECT count(*) FROM idempotencia")).isEqualTo("1");
            assertThat(primeraColumna(c, "SELECT clave FROM idempotencia")).isEqualTo("clave-a");
        }
    }

    /** Caso: conexión nueva sin app.empresa_id (parámetro inexistente): la consulta falla, no devuelve cero filas. */
    @Test
    void sinEmpresaEnConexionNuevaLaConsultaFalla() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            assertThatThrownBy(() -> primeraColumna(c, "SELECT count(*) FROM idempotencia"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("app.empresa_id");
        }
    }

    /** Caso: en la misma conexión, tras una transacción que fijó el parámetro con set_config(..., true), queda como cadena vacía y el cast a uuid falla. */
    @Test
    void sinEmpresaTrasTransaccionPreviaLaConsultaFalla() throws SQLException {
        try (Connection c = appConEmpresa(EMPRESA_A)) {
            // 1. Cierra la transacción: el valor local se descarta pero el parámetro queda definido como ''
            c.commit();
            // 2. Nueva transacción sin fijar la empresa
            assertThatThrownBy(() -> primeraColumna(c, "SELECT count(*) FROM idempotencia"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uuid");
        }
    }

    /** Caso: auditoría es insert-only; con la empresa A se puede insertar y leer pero no actualizar ni borrar. */
    @Test
    void auditoriaEsSoloInsercion() throws SQLException {
        try (Connection c = appConEmpresa(EMPRESA_A);
                Statement s = c.createStatement()) {
            s.execute("INSERT INTO auditoria (id, empresa_id, entidad, entidad_id, accion, usuario_id)" + " VALUES ('"
                    + UUID.randomUUID() + "', '" + EMPRESA_A + "', 'prueba', '1', 'CREAR', 'sistema')");
            assertThat(primeraColumna(c, "SELECT count(*) FROM auditoria")).isEqualTo("1");
        }
        assertDenegado("UPDATE auditoria SET accion = 'X'");
        assertDenegado("DELETE FROM auditoria");
    }

    /** Caso: las particiones no tienen permisos para pilot_app; solo se llega a ellas por la tabla padre. */
    @Test
    void noPuedeAccederDirectoAUnaParticion() throws SQLException {
        assertDenegado("SELECT * FROM auditoria_2026_09");
        assertDenegado("SELECT * FROM auditoria_default");
        assertDenegado("INSERT INTO auditoria_2026_09 (id, empresa_id, entidad, entidad_id, accion, usuario_id)"
                + " VALUES ('" + UUID.randomUUID() + "', '" + EMPRESA_A + "', 'prueba', '1', 'CREAR', 'sistema')");
    }

    /** Caso: RLS habilitado y forzado en idempotencia y auditoría (catálogo pg_class). */
    @Test
    void rlsEstaHabilitadoYForzadoEnAmbasTablas() throws SQLException {
        DataSource duenio = PostgresContenedor.dataSourceDuenio();
        try (Connection c = duenio.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class"
                        + " WHERE relname IN ('idempotencia', 'auditoria')")) {
            int filas = 0;
            while (rs.next()) {
                filas++;
                assertThat(rs.getBoolean("relrowsecurity"))
                        .as(rs.getString("relname"))
                        .isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity"))
                        .as(rs.getString("relname"))
                        .isTrue();
            }
            assertThat(filas).isEqualTo(2);
        }
    }

    /** Verifica que pilot_app (con empresa A fijada) recibe "permission denied" al ejecutar la sentencia. */
    private static void assertDenegado(String sql) throws SQLException {
        try (Connection c = appConEmpresa(EMPRESA_A);
                Statement s = c.createStatement()) {
            assertThatThrownBy(() -> s.execute(sql))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }
}
