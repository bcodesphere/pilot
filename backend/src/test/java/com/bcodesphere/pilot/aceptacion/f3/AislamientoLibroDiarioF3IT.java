package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Criterio F3 "Aislamiento" (CLAUDE.md §15: "un usuario o API key de la empresa A nunca lee ni escribe datos de la
 * empresa B, por API ni por SQL con pilot_app"), para las tres tablas nuevas de F3. La parte de API ya la prueba por
 * completo {@code LibroDiarioIT.obtenerDevuelveElAsientoYOtraEmpresaNoLoVeNiLoRevierte} (una empresa no ve el asiento
 * de otra ni por id ni en el listado, y no lo revierte: 404 PLT-017). Esta clase cubre lo que esa prueba no toca:
 * como {@code pilot_app} con {@code app.empresa_id} de la empresa B fijado a mano (sin pasar por ningún repositorio),
 * un {@code SELECT} sin filtro sobre {@code asiento}, {@code asiento_linea} y {@code saldo_cuenta_mensual} no
 * devuelve ninguna fila de la empresa A (RLS forzado, CLAUDE.md §4.5).
 */
class AislamientoLibroDiarioF3IT extends BaseAceptacionF3IT {

    /** Registra un asiento simple por la API para la sesión dada. */
    private void sembrarAsiento(Sesion s) throws Exception {
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Asiento de A\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"10.00\",\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"10.00\",\"llevaIva\":false}]}";
        postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                .andExpect(status().isCreated());
    }

    /** Cuenta, como {@code pilot_app} con {@code app.empresa_id} fijado a mano, las filas visibles de una tabla. */
    private int visiblesComoPilotApp(UUID empresaActiva, String tabla) throws SQLException {
        try (Connection con = PostgresContenedor.dataSourceApp().getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement fijar = con.prepareStatement("SELECT set_config('app.empresa_id', ?, true)")) {
                fijar.setString(1, empresaActiva.toString());
                fijar.execute();
            }
            try (PreparedStatement consulta = con.prepareStatement("SELECT count(*) FROM " + tabla);
                    ResultSet rs = consulta.executeQuery()) {
                rs.next();
                int total = rs.getInt(1);
                con.rollback();
                return total;
            }
        }
    }

    /** Ninguna de las tres tablas nuevas de F3 muestra filas de la empresa A cuando la sesión de PostgreSQL está fijada en B. */
    @Test
    void pilotAppConEmpresaBNoVeAsientosLineasNiSaldosDeLaEmpresaA() throws Exception {
        Sesion a = sesionConContabilidad();
        Sesion b = sesionConContabilidad();
        sembrarAsiento(a);

        // Control: B, con sus propios datos, sí ve sus propias filas (el aislamiento no es un bloqueo total)
        sembrarAsiento(b);

        assertThat(visiblesComoPilotApp(b.empresa(), "asiento")).isEqualTo(1);
        assertThat(visiblesComoPilotApp(b.empresa(), "asiento_linea")).isEqualTo(2);
        assertThat(visiblesComoPilotApp(b.empresa(), "saldo_cuenta_mensual")).isEqualTo(2);

        // Como dueño (sin RLS): A sí tiene sus propias filas, así que la ausencia bajo B es RLS y no falta de datos
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", a.empresa()))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE empresa_id = ?", a.empresa()))
                .isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ?", a.empresa()))
                .isEqualTo(2);
    }
}
