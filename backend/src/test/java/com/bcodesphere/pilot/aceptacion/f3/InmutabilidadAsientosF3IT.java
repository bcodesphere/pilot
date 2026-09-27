package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Criterio F3 "UPDATE o DELETE" (docs/plan-de-trabajo.md F3, ADR-019, CLAUDE.md §9.3): "un UPDATE o DELETE sobre
 * asiento_linea con pilot_app falla por permisos". Se ataca con una conexión propia a {@code pilot_app} (sin pasar
 * por ningún repositorio ni por el gestor de transacciones de Spring), fijando {@code app.empresa_id} a mano dentro
 * de una transacción JDBC — la migración V13 otorga a {@code pilot_app} solo {@code SELECT, INSERT} sobre
 * {@code asiento_linea} y solo {@code SELECT, INSERT} más {@code UPDATE (estado, asiento_reversion_id, version)}
 * sobre {@code asiento}, sin ningún {@code DELETE}, así que cualquier otra operación debe fallar con el SQLState
 * {@code 42501} (insufficient_privilege), no con un error de aplicación.
 */
class InmutabilidadAsientosF3IT extends BaseAceptacionF3IT {

    private static final String SQL_STATE_SIN_PERMISO = "42501";

    /** Registra un asiento simple por la API y devuelve su id y el id de su primera línea. */
    private UUID[] sembrarAsientoYPrimeraLinea(Sesion s) throws Exception {
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Asiento para inmutabilidad\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"10.00\",\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"10.00\",\"llevaIva\":false}]}";
        String idAsiento = leer(
                postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                        .andExpect(status().isCreated()),
                "$.id");
        String idLinea =
                leer(get(s, "/contabilidad/asientos/" + idAsiento).andExpect(status().isOk()), "$.lineas[0].id");
        return new UUID[] {UUID.fromString(idAsiento), UUID.fromString(idLinea)};
    }

    /** Abre una transacción JDBC como {@code pilot_app} con {@code app.empresa_id} fijado, ejecuta el trabajo y hace rollback siempre. */
    private void comoPilotApp(UUID empresa, TrabajoSql trabajo) throws SQLException {
        try (Connection con = PostgresContenedor.dataSourceApp().getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement("SELECT set_config('app.empresa_id', ?, true)")) {
                ps.setString(1, empresa.toString());
                ps.execute();
            }
            try {
                trabajo.ejecutar(con);
            } finally {
                con.rollback();
            }
        }
    }

    @FunctionalInterface
    private interface TrabajoSql {
        void ejecutar(Connection con) throws SQLException;
    }

    /** UPDATE de asiento_linea (cualquier columna) falla por permisos: pilot_app solo tiene SELECT e INSERT. */
    @Test
    void unUpdateSobreAsientoLineaFallaPorPermisos() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID idLinea = sembrarAsientoYPrimeraLinea(s)[1];

        assertThatThrownBy(() -> comoPilotApp(s.empresa(), con -> {
                    try (PreparedStatement ps =
                            con.prepareStatement("UPDATE asiento_linea SET debe = 999.00 WHERE id = ?")) {
                        ps.setObject(1, idLinea);
                        ps.executeUpdate();
                    }
                }))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(SQL_STATE_SIN_PERMISO));

        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE id = ? AND debe = 999.00", idLinea))
                .isZero();
    }

    /** DELETE de asiento_linea falla por permisos: no hay GRANT DELETE en absoluto. */
    @Test
    void unDeleteSobreAsientoLineaFallaPorPermisos() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID idLinea = sembrarAsientoYPrimeraLinea(s)[1];

        assertThatThrownBy(() -> comoPilotApp(s.empresa(), con -> {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM asiento_linea WHERE id = ?")) {
                        ps.setObject(1, idLinea);
                        ps.executeUpdate();
                    }
                }))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(SQL_STATE_SIN_PERMISO));

        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE id = ?", idLinea))
                .isEqualTo(1);
    }

    /** DELETE de asiento falla por permisos: tampoco hay GRANT DELETE sobre la cabecera. */
    @Test
    void unDeleteSobreAsientoFallaPorPermisos() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID idAsiento = sembrarAsientoYPrimeraLinea(s)[0];

        assertThatThrownBy(() -> comoPilotApp(s.empresa(), con -> {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM asiento WHERE id = ?")) {
                        ps.setObject(1, idAsiento);
                        ps.executeUpdate();
                    }
                }))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(SQL_STATE_SIN_PERMISO));

        assertThat(contar("SELECT count(*) FROM asiento WHERE id = ?", idAsiento))
                .isEqualTo(1);
    }

    /** UPDATE de asiento fuera de (estado, asiento_reversion_id, version) falla por permiso de columna, aunque esas tres estén permitidas. */
    @Test
    void unUpdateDeAsientoFueraDeLasColumnasPermitidasFallaPorPermisos() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID idAsiento = sembrarAsientoYPrimeraLinea(s)[0];

        assertThatThrownBy(() -> comoPilotApp(s.empresa(), con -> {
                    try (PreparedStatement ps =
                            con.prepareStatement("UPDATE asiento SET concepto = 'manipulado' WHERE id = ?")) {
                        ps.setObject(1, idAsiento);
                        ps.executeUpdate();
                    }
                }))
                .isInstanceOfSatisfying(
                        SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo(SQL_STATE_SIN_PERMISO));

        assertThat(contar("SELECT count(*) FROM asiento WHERE id = ? AND concepto = 'manipulado'", idAsiento))
                .isZero();
    }
}
