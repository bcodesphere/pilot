package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Criterio F3 "Reversión" (docs/plan-de-trabajo.md F3): "deja los saldos como estaban". {@code LibroDiarioIT} ya
 * prueba que la reversión intercambia los lados de las líneas de IVA
 * ({@code revertirSinCuerpoUsaHoyYConservaLosEnlacesDeIva}) y que los saldos de las cuentas capturadas por el usuario
 * vuelven a cero ({@code revertirIntercambiaLadosRestauraLosSaldosYPublicaElEvento}, con un asiento sin IVA); ninguna
 * de las dos comprueba el saldo de la cuenta de IVA tras revertir un asiento que sí llevó IVA. Esta clase cierra ese
 * hueco para los dos modos de precio (CON_IVA y SIN_IVA, CLAUDE.md §11.2).
 */
class ReversionRestauraSaldosDeIvaF3IT extends BaseAceptacionF3IT {

    private static final String FECHA_ORIGINAL = "2026-01-15";
    private static final String FECHA_REVERSION = "2026-02-10";

    /** Saldo (Debe − Haber) acumulado de una cuenta en {@code saldo_cuenta_mensual}. */
    private BigDecimal saldo(Sesion s, UUID cuenta) {
        return duenio.sql("SELECT COALESCE(SUM(total_debe - total_haber), 0) FROM saldo_cuenta_mensual"
                        + " WHERE empresa_id = ? AND cuenta_id = ?")
                .params(s.empresa(), cuenta)
                .query(BigDecimal.class)
                .single();
    }

    private String registrar(Sesion s, String cuerpo) throws Exception {
        return leer(
                postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                        .andExpect(status().isCreated()),
                "$.id");
    }

    /** Revertir un asiento CON_IVA deja el saldo de la cuenta de IVA débito otra vez en cero. */
    @Test
    void revertirUnAsientoConIvaDejaElSaldoDeIvaDebitoEnCero() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        String codigoIvaDebito = leer(get(s, "/contabilidad/configuracion"), "$.cuentaIvaDebito.codigo");
        UUID ivaDebito = cuentaId(s.empresa(), codigoIvaDebito);

        String original = registrar(
                s,
                "{\"fecha\":\"" + FECHA_ORIGINAL
                        + "\",\"concepto\":\"Venta con IVA\",\"modoPrecio\":\"CON_IVA\",\"lineas\":["
                        + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"113.00\",\"haber\":\"0\",\"llevaIva\":false},"
                        + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"113.00\",\"llevaIva\":true}]}");
        assertThat(saldo(s, ivaDebito)).isEqualByComparingTo("-13.00");

        postConClave(
                        s,
                        "/contabilidad/asientos/" + original + "/reversion",
                        "r1",
                        "{\"fecha\":\"" + FECHA_REVERSION + "\"}")
                .andExpect(status().isCreated());

        assertThat(saldo(s, caja)).isEqualByComparingTo("0.00");
        assertThat(saldo(s, ventas)).isEqualByComparingTo("0.00");
        assertThat(saldo(s, ivaDebito)).as("saldo de IVA débito tras revertir").isEqualByComparingTo("0.00");
    }

    /** Revertir un asiento SIN_IVA deja el saldo de la cuenta de IVA crédito otra vez en cero. */
    @Test
    void revertirUnAsientoSinIvaDejaElSaldoDeIvaCreditoEnCero() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID compras = cuentaId(
                s.empresa(),
                duenio.sql("SELECT codigo FROM cuenta_contable WHERE empresa_id = ? AND acepta_movimientos"
                                + " AND codigo LIKE '4%' ORDER BY codigo LIMIT 1")
                        .param(s.empresa())
                        .query(String.class)
                        .single());
        UUID caja = cuentaId(s.empresa(), "11010101");
        String codigoIvaCredito = leer(get(s, "/contabilidad/configuracion"), "$.cuentaIvaCredito.codigo");
        UUID ivaCredito = cuentaId(s.empresa(), codigoIvaCredito);

        String original = registrar(
                s,
                "{\"fecha\":\"" + FECHA_ORIGINAL
                        + "\",\"concepto\":\"Compra con IVA\",\"modoPrecio\":\"SIN_IVA\",\"lineas\":["
                        + "{\"cuentaId\":\"" + compras + "\",\"debe\":\"100.00\",\"haber\":\"0\",\"llevaIva\":true},"
                        + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"0\",\"haber\":\"113.00\",\"llevaIva\":false}]}");
        assertThat(saldo(s, ivaCredito)).isEqualByComparingTo("13.00");

        postConClave(
                        s,
                        "/contabilidad/asientos/" + original + "/reversion",
                        "r1",
                        "{\"fecha\":\"" + FECHA_REVERSION + "\"}")
                .andExpect(status().isCreated());

        assertThat(saldo(s, compras)).isEqualByComparingTo("0.00");
        assertThat(saldo(s, caja)).isEqualByComparingTo("0.00");
        assertThat(saldo(s, ivaCredito))
                .as("saldo de IVA crédito tras revertir")
                .isEqualByComparingTo("0.00");
    }
}
