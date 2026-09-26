package com.bcodesphere.pilot.aceptacion.f2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ConsultaMovimientosCuenta;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Aceptación de F2, criterio 3: «no se puede desactivar una cuenta con saldo, ni cambiar el código de una cuenta con
 * movimientos». <strong>Cobertura parcial:</strong> en F2 no existen asientos, así que el puerto
 * {@link ConsultaMovimientosCuenta} se sustituye por un doble (ADR-035, decisión 5) y se prueba la regla, no la
 * lectura de movimientos reales. <strong>La prueba con movimientos y saldos reales queda para F3</strong>, cuando el
 * adaptador lea {@code asiento_linea} y {@code saldo_cuenta_mensual}. Fuente de los códigos: CLAUDE.md 10.2.
 */
class SaldoYMovimientosConDobleF2IT extends BaseAceptacionF2IT {

    /** Doble del puerto: cada prueba decide si hay movimientos y saldo. */
    @MockitoBean
    private ConsultaMovimientosCuenta movimientos;

    /** Hoja de 8 dígitos de la plantilla que no usan la configuración ni las reglas, con un hermano libre para el cambio de código. */
    private static final String HOJA = "11010102";

    /** Cambio de código válido para {@link #HOJA}: misma longitud, mismo padre {@code 110101} y sin uso previo. */
    private static final String HOJA_CODIGO_NUEVO = "11010199";

    /** Configura el doble: con o sin movimientos y con el saldo dado (escala 2, como un {@code NUMERIC(19,2)}). */
    private void doble(boolean conMovimientos, String saldo) {
        when(movimientos.tieneMovimientos(any(UUID.class))).thenReturn(conMovimientos);
        when(movimientos.saldo(any(UUID.class))).thenReturn(new BigDecimal(saldo));
    }

    private String estadoDe(UUID empresa, String codigo, String columna) {
        return duenio.sql("SELECT " + columna + "::text FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?")
                .params(empresa, codigo)
                .query(String.class)
                .single();
    }

    /** Con saldo distinto de cero, desactivar una cuenta de detalle sin uso da 422 {@code CON-012} y no cambia nada. */
    @Test
    void desactivarUnaCuentaConSaldoSeRechazaConCon012() throws Exception {
        Sesion s = sesionConContabilidad();
        doble(false, "150.00");
        UUID id = cuentaId(s.empresa(), HOJA);

        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"activa\":false}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-012"));

        assertThat(estadoDe(s.empresa(), HOJA, "activa")).isEqualTo("true");
    }

    /** Con movimientos, cambiar el código a uno válido (misma longitud y padre, sin hijas) da 422 {@code CON-011}. */
    @Test
    void cambiarElCodigoDeUnaCuentaConMovimientosSeRechazaConCon011() throws Exception {
        Sesion s = sesionConContabilidad();
        doble(true, "0.00");
        UUID id = cuentaId(s.empresa(), HOJA);

        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"codigo\":\"" + HOJA_CODIGO_NUEVO + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-011"));

        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?", s.empresa(), HOJA))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?",
                        s.empresa(),
                        HOJA_CODIGO_NUEVO))
                .isZero();
    }

    /** Con movimientos en una hoja, crearle una hija da 422 {@code CON-011} y no se crea la hija. */
    @Test
    void crearUnaHijaBajoUnaHojaConMovimientosSeRechazaConCon011() throws Exception {
        Sesion s = sesionConContabilidad();
        doble(false, "0.00");
        String hijaCodigo = hijaDeHoja(s);
        // Ahora sí: la hoja recién creada «tiene movimientos»
        doble(true, "0.00");

        post(s, "/contabilidad/cuentas", "{\"codigo\":\"" + hijaCodigo + "\",\"nombre\":\"Hija de prueba\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-011"));

        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?",
                        s.empresa(),
                        hijaCodigo))
                .isZero();
    }

    /**
     * Control positivo: con el doble respondiendo «sin movimientos» y saldo 0.00, las tres operaciones se aceptan.
     * Demuestra que los rechazos anteriores se deben al puerto y no a otra regla.
     */
    @Test
    void sinMovimientosNiSaldoLasTresOperacionesSeAceptan() throws Exception {
        Sesion s = sesionConContabilidad();
        doble(false, "0.00");

        // 1. Desactivar la hoja sin uso
        UUID id = cuentaId(s.empresa(), HOJA);
        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"activa\":false}").andExpect(status().isOk());
        assertThat(estadoDe(s.empresa(), HOJA, "activa")).isEqualTo("false");

        // 2. Cambiar el código de otra hoja de la misma cuenta (versión 0 aún: no se ha editado)
        UUID otra = cuentaId(s.empresa(), "11010103");
        patch(s, "/contabilidad/cuentas/" + otra, "\"0\"", "{\"codigo\":\"" + HOJA_CODIGO_NUEVO + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(HOJA_CODIGO_NUEVO));

        // 3. Crear una hija bajo una hoja sin uso
        String hijaCodigo = hijaDeHoja(s);
        post(s, "/contabilidad/cuentas", "{\"codigo\":\"" + hijaCodigo + "\",\"nombre\":\"Hija de prueba\"}")
                .andExpect(status().isCreated());
    }

    /**
     * Crea (con el doble sin movimientos) una hoja de 6 dígitos nueva bajo una cuenta de 4 dígitos de la plantilla y
     * devuelve el código de la hija de 8 dígitos que se le intentará crear. En el catálogo base todas las hojas son de
     * 8 dígitos y no admiten hijas, así que la hoja de prueba se crea aquí: es sin uso y su código no choca con la
     * plantilla (se elige un sufijo {@code 99} ausente).
     */
    private String hijaDeHoja(Sesion s) throws Exception {
        String cuenta = duenio.sql("SELECT p.codigo FROM plantilla_cuenta p WHERE p.nivel = 3 AND EXISTS"
                        + " (SELECT 1 FROM plantilla_cuenta h WHERE h.nivel = 4 AND starts_with(h.codigo, p.codigo))"
                        + " AND NOT EXISTS (SELECT 1 FROM plantilla_cuenta h WHERE h.codigo = p.codigo || '99')"
                        + " ORDER BY p.codigo LIMIT 1")
                .query(String.class)
                .single();
        String hoja = cuenta + "99";
        post(s, "/contabilidad/cuentas", "{\"codigo\":\"" + hoja + "\",\"nombre\":\"Hoja intermedia de prueba\"}")
                .andExpect(status().isCreated());
        return hoja + "01";
    }
}
