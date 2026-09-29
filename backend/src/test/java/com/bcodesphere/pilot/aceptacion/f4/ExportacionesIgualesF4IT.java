package com.bcodesphere.pilot.aceptacion.f4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Criterio 6 de F4 (plan de trabajo): "PDF, XLSX y CSV tienen los mismos totales que la pantalla", para los seis
 * reportes exportables (Libro Diario, Libro Mayor, Balanza, Estado de Situación Financiera, Estado de Resultados y
 * resumen de IVA) y los tres formatos, sobre el conjunto dorado de {@link ConjuntoDoradoF4IT}. A diferencia de
 * {@code contabilidad.api.ExportacionesIT} (que usa los filtros por defecto), aquí se piden filtros distintos del
 * valor por defecto — {@code nivel=3} e {@code incluirCeros=true} donde el reporte los acepta — para probar que la
 * exportación respeta los filtros de la petición y no solo los del caso por defecto; los totales no deben cambiar
 * (ADR-038 §6: salen siempre del detalle, sin importar el nivel ni si se incluyen los ceros).
 *
 * <p>Cada total se compara por su etiqueta exacta (la columna o fila que le corresponde en cada formato), tal como
 * hace {@code ExportacionesIT}, no por si el número aparece en cualquier parte del archivo.
 */
class ExportacionesIgualesF4IT extends BaseAceptacionF4IT {

    private static final String BALANZA_FILTROS = "desde=2026-03-01&hasta=2026-03-31&nivel=3";
    private static final String ESTADOS_FILTROS = "nivel=3&incluirCeros=true";

    // ------------------------------------------------------------------------------------------------- balanza

    @Test
    void laBalanzaExportadaCoincideConElJsonEnLosTresFormatos() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        ResultActions json = get(s, "/contabilidad/balanza?" + BALANZA_FILTROS).andExpect(status().isOk());
        String totalDebe = leer(json, "$.totalDebe");
        String totalHaber = leer(json, "$.totalHaber");
        String totalDeudores = leer(json, "$.totalSaldosDeudores");
        String totalAcreedores = leer(json, "$.totalSaldosAcreedores");
        assertThat(totalDebe).as("nivel=3 no cambia el total").isEqualTo("2878.98");
        assertThat(totalDeudores).isEqualTo("4045.00");

        byte[] csv = exportar(
                s, "/contabilidad/balanza/exportacion?formato=csv&" + BALANZA_FILTROS, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        verificarCsv(textoCsv, "Totales", 5, totalDebe);
        verificarCsv(textoCsv, "Totales", 6, totalHaber);
        verificarCsv(textoCsv, "Total saldos deudores", 1, totalDeudores);
        verificarCsv(textoCsv, "Total saldos acreedores", 1, totalAcreedores);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/balanza/exportacion?formato=xlsx&" + BALANZA_FILTROS,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verificarXlsx(xlsx, "Totales", 5, totalDebe);
        verificarXlsx(xlsx, "Totales", 6, totalHaber);
        verificarXlsx(xlsx, "Total saldos deudores", 1, totalDeudores);
        verificarXlsx(xlsx, "Total saldos acreedores", 1, totalAcreedores);

        byte[] pdf = exportar(s, "/contabilidad/balanza/exportacion?formato=pdf&" + BALANZA_FILTROS, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Total saldos deudores", totalDeudores);
        verificarPdf(textoPdf, "Total saldos acreedores", totalAcreedores);
        assertThat(textoPdf).contains("Cuadra: Sí").doesNotContain("true");
    }

    // ---------------------------------------------------------------------------------------- estado de resultados

    @Test
    void elEstadoDeResultadosExportadoCoincideConElJsonEnLosTresFormatos() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        String rango = "desde=2026-01-01&hasta=2026-03-31&" + ESTADOS_FILTROS;
        ResultActions json = get(s, "/contabilidad/estados/resultados?" + rango).andExpect(status().isOk());
        String ingresos = leer(json, "$.ingresos.total");
        String costos = leer(json, "$.costosGastos.total");
        String utilidadAntes = leer(json, "$.utilidadAntesImpuesto");
        String impuesto = leer(json, "$.impuestoSobreRenta.total");
        String utilidadEjercicio = leer(json, "$.utilidadEjercicio");
        assertThat(ingresos).isEqualTo("1804.42");
        assertThat(utilidadEjercicio).isEqualTo("899.42");

        byte[] csv = exportar(
                s, "/contabilidad/estados/resultados/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        verificarCsv(textoCsv, "Total INGRESOS", 3, ingresos);
        verificarCsv(textoCsv, "Total COSTOS Y GASTOS", 3, costos);
        verificarCsv(textoCsv, "Utilidad antes de impuesto", 1, utilidadAntes);
        verificarCsv(textoCsv, "Total IMPUESTO SOBRE LA RENTA", 3, impuesto);
        verificarCsv(textoCsv, "Utilidad del ejercicio", 1, utilidadEjercicio);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/estados/resultados/exportacion?formato=xlsx&" + rango,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verificarXlsx(xlsx, "Total INGRESOS", 3, ingresos);
        verificarXlsx(xlsx, "Total COSTOS Y GASTOS", 3, costos);
        verificarXlsx(xlsx, "Utilidad antes de impuesto", 1, utilidadAntes);
        verificarXlsx(xlsx, "Total IMPUESTO SOBRE LA RENTA", 3, impuesto);
        verificarXlsx(xlsx, "Utilidad del ejercicio", 1, utilidadEjercicio);

        byte[] pdf =
                exportar(s, "/contabilidad/estados/resultados/exportacion?formato=pdf&" + rango, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Total ingresos", ingresos);
        verificarPdf(textoPdf, "Total costos y gastos", costos);
        verificarPdf(textoPdf, "Utilidad antes de impuesto", utilidadAntes);
        verificarPdf(textoPdf, "Total impuesto sobre la renta", impuesto);
        verificarPdf(textoPdf, "Utilidad (pérdida) del ejercicio", utilidadEjercicio);
        assertThat(textoPdf).contains(com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados.LEYENDA);
    }

    // ------------------------------------------------------------------------------ estado de situación financiera

    @Test
    void elEstadoDeSituacionFinancieraExportadoCoincideConElJsonEnLosTresFormatos() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        String filtro = "fechaCorte=2026-03-31&" + ESTADOS_FILTROS;
        ResultActions json =
                get(s, "/contabilidad/estados/situacion-financiera?" + filtro).andExpect(status().isOk());
        String activo = leer(json, "$.activo.total");
        String pasivo = leer(json, "$.pasivo.total");
        String patrimonio = leer(json, "$.patrimonio.total");
        String resultadosAnteriores = leer(json, "$.resultadosEjerciciosAnteriores");
        String utilidadEjercicio = leer(json, "$.utilidadEjercicio");
        String totalPasivoPatrimonio = leer(json, "$.totalPasivoPatrimonio");
        String diferencia = leer(json, "$.comprobacion.diferencia");
        assertThat(activo).isEqualTo("3030.00");
        assertThat(diferencia).isEqualTo("0.00");

        byte[] csv = exportar(
                s,
                "/contabilidad/estados/situacion-financiera/exportacion?formato=csv&" + filtro,
                "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        verificarCsv(textoCsv, "Total ACTIVO", 3, activo);
        verificarCsv(textoCsv, "Total PASIVO", 3, pasivo);
        verificarCsv(textoCsv, "Total PATRIMONIO", 3, patrimonio);
        verificarCsv(textoCsv, "Resultados de ejercicios anteriores", 1, resultadosAnteriores);
        verificarCsv(textoCsv, "Utilidad del ejercicio", 1, utilidadEjercicio);
        verificarCsv(textoCsv, "Total Pasivo + Patrimonio", 1, totalPasivoPatrimonio);
        verificarCsv(textoCsv, "Diferencia", 1, diferencia);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/estados/situacion-financiera/exportacion?formato=xlsx&" + filtro,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verificarXlsx(xlsx, "Total ACTIVO", 3, activo);
        verificarXlsx(xlsx, "Total PASIVO", 3, pasivo);
        verificarXlsx(xlsx, "Total PATRIMONIO", 3, patrimonio);
        verificarXlsx(xlsx, "Resultados de ejercicios anteriores", 1, resultadosAnteriores);
        verificarXlsx(xlsx, "Utilidad del ejercicio", 1, utilidadEjercicio);
        verificarXlsx(xlsx, "Total Pasivo + Patrimonio", 1, totalPasivoPatrimonio);
        verificarXlsx(xlsx, "Diferencia", 1, diferencia);

        byte[] pdf = exportar(
                s, "/contabilidad/estados/situacion-financiera/exportacion?formato=pdf&" + filtro, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Total activo", activo);
        verificarPdf(textoPdf, "Total pasivo", pasivo);
        verificarPdf(textoPdf, "Total patrimonio", patrimonio);
        verificarPdf(textoPdf, "Resultados de ejercicios anteriores", resultadosAnteriores);
        verificarPdf(textoPdf, "Total Pasivo + Patrimonio", totalPasivoPatrimonio);
        verificarPdf(textoPdf, "Diferencia", diferencia);
        assertThat(textoPdf).contains(": Sí — Diferencia:").doesNotContain("true");
    }

    // -------------------------------------------------------------------------------------------------- resumen iva

    @Test
    void elResumenDeIvaExportadoCoincideConElJsonEnLosTresFormatos() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        String periodo = "anio=2026&mes=3";
        ResultActions json = get(s, "/contabilidad/reportes/iva?" + periodo).andExpect(status().isOk());
        String ivaDebito = leer(json, "$.ivaDebito.total");
        String ivaCredito = leer(json, "$.ivaCredito.total");
        String diferencia = leer(json, "$.diferenciaEstimada");
        assertThat(ivaDebito).isEqualTo("234.58");
        assertThat(ivaCredito).isEqualTo("39.00");
        assertThat(diferencia).isEqualTo("195.58");

        byte[] csv =
                exportar(s, "/contabilidad/reportes/iva/exportacion?formato=csv&" + periodo, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(totalDesgloseCsv(textoCsv, "IVA débito fiscal")).isEqualTo(ivaDebito);
        assertThat(totalDesgloseCsv(textoCsv, "IVA crédito fiscal")).isEqualTo(ivaCredito);
        verificarCsv(textoCsv, "Diferencia estimada", 1, diferencia);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/reportes/iva/exportacion?formato=xlsx&" + periodo,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(celdaTrasEtiqueta(xlsx, "IVA débito fiscal", 2, 3)).isEqualTo(Double.parseDouble(ivaDebito));
        assertThat(celdaTrasEtiqueta(xlsx, "IVA crédito fiscal", 2, 3)).isEqualTo(Double.parseDouble(ivaCredito));
        verificarXlsx(xlsx, "Diferencia estimada", 1, diferencia);

        byte[] pdf = exportar(s, "/contabilidad/reportes/iva/exportacion?formato=pdf&" + periodo, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Diferencia estimada", diferencia);
        assertThat(textoPdf).contains(com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva.NOTA);
    }

    // ------------------------------------------------------------------------------------------------- libro mayor

    @Test
    void elLibroMayorExportadoCoincideConElJsonEnLosTresFormatos() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        UUID gastosAdmin = cuentaId(s.empresa(), "42020101");
        String rango = "cuentaId=" + gastosAdmin + "&desde=2026-03-01&hasta=2026-03-31";
        ResultActions json = get(s, "/contabilidad/mayor?" + rango).andExpect(status().isOk());
        String saldoInicial = leer(json, "$.saldoInicial.monto");
        String totalDebe = leer(json, "$.totalDebe");
        String totalHaber = leer(json, "$.totalHaber");
        String saldoFinal = leer(json, "$.saldoFinal.monto");
        assertThat(saldoInicial).isEqualTo("150.00");
        assertThat(saldoFinal).isEqualTo("370.00");

        byte[] csv = exportar(s, "/contabilidad/mayor/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        verificarCsv(textoCsv, "Saldo inicial", 1, saldoInicial);
        verificarCsv(textoCsv, "Totales", 7, totalDebe);
        verificarCsv(textoCsv, "Totales", 8, totalHaber);
        verificarCsv(textoCsv, "Saldo final", 9, saldoFinal);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/mayor/exportacion?formato=xlsx&" + rango,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verificarXlsx(xlsx, "Saldo inicial", 1, saldoInicial);
        verificarXlsx(xlsx, "Totales", 7, totalDebe);
        verificarXlsx(xlsx, "Totales", 8, totalHaber);
        verificarXlsx(xlsx, "Saldo final", 9, saldoFinal);

        byte[] pdf = exportar(s, "/contabilidad/mayor/exportacion?formato=pdf&" + rango, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Saldo inicial", saldoInicial);
        verificarPdf(textoPdf, "TOTALES", totalDebe);
        verificarPdf(textoPdf, "Saldo final", saldoFinal);
    }

    // ------------------------------------------------------------------------------------------------ libro diario

    /** Criterio 6 para el Libro Diario: no tiene {@code nivel}; se exporta con el rango de marzo del conjunto. */
    @Test
    void elLibroDiarioExportadoCoincideConElJsonEnLosTresFormatos() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        String rango = "desde=2026-03-01&hasta=2026-03-31";
        ResultActions json = get(s, "/contabilidad/asientos?" + rango).andExpect(status().isOk());
        String cuerpo = json.andReturn().getResponse().getContentAsString();
        java.util.List<java.util.Map<String, Object>> elementos =
                com.jayway.jsonpath.JsonPath.read(cuerpo, "$.elementos");
        java.math.BigDecimal totalDebeBd = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalHaberBd = java.math.BigDecimal.ZERO;
        for (var a : elementos) {
            totalDebeBd = totalDebeBd.add(new java.math.BigDecimal((String) a.get("totalDebe")));
            totalHaberBd = totalHaberBd.add(new java.math.BigDecimal((String) a.get("totalHaber")));
        }
        String totalDebe = totalDebeBd.setScale(2).toPlainString();
        String totalHaber = totalHaberBd.setScale(2).toPlainString();
        assertThat(totalDebe).isEqualTo("2878.98");

        byte[] csv = exportar(s, "/contabilidad/asientos/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(textoCsv).contains("TOTALES,,,,,,,,,," + totalDebe + "," + totalHaber);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/asientos/exportacion?formato=xlsx&" + rango,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(celdaNumericaDeFila(xlsx, "TOTALES", 10)).isEqualTo(Double.parseDouble(totalDebe));
        assertThat(celdaNumericaDeFila(xlsx, "TOTALES", 11)).isEqualTo(Double.parseDouble(totalHaber));

        byte[] pdf = exportar(s, "/contabilidad/asientos/exportacion?formato=pdf&" + rango, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "TOTALES", totalDebe);
    }

    // ----------------------------------------------------------------------------------------------- content-type

    /** Cada formato responde su tipo de contenido exacto y un nombre de archivo en minúsculas y sin espacios. */
    @Test
    void cadaFormatoRespondeSuTipoDeContenidoYSuNombreDeArchivo() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        String rango = "desde=2026-03-01&hasta=2026-03-31";

        get(s, "/contabilidad/balanza/exportacion?formato=csv&" + rango)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"balanza_2026-03-01_2026-03-31.csv\""));

        get(s, "/contabilidad/balanza/exportacion?formato=xlsx&" + rango)
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"balanza_2026-03-01_2026-03-31.xlsx\""));

        get(s, "/contabilidad/balanza/exportacion?formato=pdf&" + rango)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"balanza_2026-03-01_2026-03-31.pdf\""));
    }
}
