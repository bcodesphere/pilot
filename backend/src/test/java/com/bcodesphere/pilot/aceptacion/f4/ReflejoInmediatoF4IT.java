package com.bcodesphere.pilot.aceptacion.f4;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterio 5 de F4 (plan de trabajo): "los reportes reflejan un asiento inmediatamente después de guardarlo". Sin
 * caché de reportes en 1.0 (CLAUDE.md §10.5), cada lectura recalcula desde {@code asiento_linea} y
 * {@code saldo_cuenta_mensual}, así que la petición siguiente a un 201 (registro o reversión) ya debe reflejarlo.
 * Ejercita los cinco reportes (no solo la Balanza, que ya cubre {@code contabilidad.api.ReportesIT}): Balanza,
 * Estado de Resultados, Estado de Situación Financiera, Libro Mayor y resumen de IVA, antes de registrar, justo
 * después de registrar y justo después de revertir el mismo día (donde el efecto neto vuelve a cero).
 */
class ReflejoInmediatoF4IT extends BaseAceptacionF4IT {

    private static final String CAJA = "11010101";
    private static final String VENTAS_GRAVADAS = "51010101";
    private static final String FECHA = "2026-06-15";
    private static final String RANGO = "desde=2026-06-01&hasta=2026-06-15";
    private static final String CORTE = "fechaCorte=2026-06-15";
    private static final String PERIODO_IVA = "anio=2026&mes=6";

    @org.junit.jupiter.api.Test
    void losCincoReportesReflejanElAsientoYSuReversionEnLaPeticionSiguiente() throws Exception {
        Sesion s = sesionConContabilidad();
        var caja = cuentaId(s.empresa(), CAJA);

        // 1. Antes de registrar nada: los cinco reportes están en cero
        verificarReportes(s, caja, "0.00", "0.00", "0.00", "0.00", 0, "0.00", "0.00");

        // 2. Justo después del 201 de POST /contabilidad/asientos (Caja 113.00 D / Ventas gravadas 113.00 H, con
        //    IVA: base 100.00 + IVA débito 13.00), sin ninguna espera, ya se refleja en los cinco reportes
        String id = registrar(
                s,
                "k1",
                asiento(
                        FECHA,
                        "Venta con IVA para probar el reflejo inmediato",
                        "CON_IVA",
                        linea(caja, "113.00", "0", false),
                        linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "113.00", true)));
        verificarReportes(s, caja, "113.00", "113.00", "113.00", "100.00", 1, "113.00", "13.00");

        // 3. Justo después del 201 de la reversión (mismo día): el efecto neto vuelve a cero en los cinco reportes,
        //    sin esperar ni volver a consultar dos veces
        revertir(s, id, "k1-rev", FECHA);
        verificarReportes(s, caja, "0.00", "0.00", "0.00", "0.00", 2, "0.00", "0.00");
    }

    /**
     * Verifica en una sola pasada los cinco reportes: Balanza (saldos deudor/acreedor), Estado de Resultados
     * (ingresos), Estado de Situación Financiera (activo y utilidad), Libro Mayor de Caja (cantidad de movimientos
     * y saldo final) y resumen de IVA (débito total).
     */
    private void verificarReportes(
            Sesion s,
            java.util.UUID caja,
            String totalSaldosDeudores,
            String totalSaldosAcreedores,
            String activoTotal,
            String ingresosTotal,
            int cantidadMovimientosMayor,
            String saldoFinalMayor,
            String ivaDebitoTotal)
            throws Exception {
        get(s, "/contabilidad/balanza?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSaldosDeudores").value(totalSaldosDeudores))
                .andExpect(jsonPath("$.totalSaldosAcreedores").value(totalSaldosAcreedores));

        get(s, "/contabilidad/estados/resultados?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresos.total").value(ingresosTotal));

        get(s, "/contabilidad/estados/situacion-financiera?" + CORTE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo.total").value(activoTotal));

        get(s, "/contabilidad/mayor?cuentaId=" + caja + "&" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientos.length()").value(cantidadMovimientosMayor))
                .andExpect(jsonPath("$.saldoFinal.monto").value(saldoFinalMayor));

        get(s, "/contabilidad/reportes/iva?" + PERIODO_IVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ivaDebito.total").value(ivaDebitoTotal));
    }
}
