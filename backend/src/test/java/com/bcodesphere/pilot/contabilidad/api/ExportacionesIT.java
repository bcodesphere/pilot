package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas de integración de las seis exportaciones de reportes contables a PDF, XLSX y CSV (F4-04) contra PostgreSQL
 * real como {@code pilot_app}. Fuente: CLAUDE.md 10.5 y 13, ADR-038 ("Implementación de las exportaciones") y los
 * criterios de aceptación de F4-04. Reutiliza los asientos sembrados con {@link LibroDiarioIT#asiento} y
 * {@link LibroDiarioIT#linea}.
 *
 * <p>Corrección 1 (revisión del arquitecto): los totales de {@link #sembrarUnAsiento()} (un solo asiento
 * balanceado) coinciden entre sí (p. ej. {@code totalDebe} y {@code totalSaldosDeudores} de la Balanza son ambos
 * 500.00), así que una mutación que confunda dos totales en la plantilla no se nota. Las pruebas de "totales
 * iguales" usan en cambio {@link #sembrarEscenarioDistinguible()}, con seis asientos en cuentas de las cinco clases
 * (activo, pasivo, patrimonio, ingreso, gasto e impuesto sobre la renta) elegidos para que todos los totales de cada
 * reporte sean distintos entre sí, y comparan cada total por su etiqueta exacta (la columna o fila que le
 * corresponde), no solo por si el número aparece en algún lugar del archivo.
 */
class ExportacionesIT extends BaseContabilidadIT {

    /** Cuenta de Caja del catálogo base (CLAUDE.md 12.5). */
    private static final String CAJA = "11010101";

    /** Cuenta de Ventas gravadas del catálogo base. */
    private static final String VENTAS_GRAVADAS = "51010101";

    /** Cuenta de Gastos de administración del catálogo base (clase 4, fuera del grupo 44). */
    private static final String GASTOS_ADMIN = "42020101";

    /** Cuenta del grupo 44 (Impuesto sobre la renta). */
    private static final String IMPUESTO_RENTA = "44010101";

    /** Cuenta de Proveedores del catálogo base (clase 2, Pasivo). */
    private static final String PROVEEDORES = "21010101";

    /** Cuenta de Capital social del catálogo base (clase 3, Patrimonio). */
    private static final String CAPITAL_SOCIAL = "31010101";

    /** Registra un asiento con la clave dada y devuelve su id. */
    private String registrar(Sesion s, String clave, String cuerpo) throws Exception {
        return leer(postConClave(s, "/contabilidad/asientos", clave, cuerpo).andExpect(status().isCreated()), "$.id");
    }

    /** Empresa con Contabilidad instalada y un único asiento Caja 500.00 / Ventas 500.00 el 2026-03-10. */
    private Sesion sembrarUnAsiento() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-03-10",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "500.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "500.00", false)));
        return s;
    }

    /**
     * Seis asientos en cuentas de las cinco clases, para que los totales de cada reporte sean distintos entre sí
     * (corrección 1 — F4-04):
     *
     * <pre>
     * 2025-06-01  Caja 200.00 D / Ventas gravadas 200.00 H                  (utilidad del año anterior)
     * 2026-02-15  Caja 1130.00 D / Ventas gravadas 1130.00 H (IVA)          → Ventas 1000.00 + IVA débito 130.00
     * 2026-02-20  Gastos admin 226.00 D (IVA) / Caja 226.00 H               → Gastos 200.00 + IVA crédito 26.00
     * 2026-05-10  Caja 300.00 D / Proveedores 300.00 H                     (pasivo)
     * 2026-06-10  Caja 500.00 D / Capital social 500.00 H                  (patrimonio)
     * 2026-09-10  Impuesto sobre la renta 50.00 D / Caja 50.00 H
     * </pre>
     *
     * Con esto: Activo 1880.00, Pasivo 430.00, Patrimonio 500.00, resultados anteriores 200.00, ingresos 1000.00,
     * costos y gastos 200.00, utilidad antes de impuesto 800.00, impuesto 50.00, utilidad del ejercicio 750.00,
     * Balanza (2026) Σ Debe/Haber 2206.00 y Σ saldos deudores/acreedores 2130.00, IVA débito 130.00, IVA crédito
     * 26.00, diferencia estimada 104.00: todos distintos entre sí dentro de cada reporte.
     */
    private Sesion sembrarEscenarioDistinguible() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k0",
                LibroDiarioIT.asiento(
                        "2025-06-01",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "200.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "200.00", false)));
        registrar(
                s,
                "k1",
                LibroDiarioIT.asiento(
                        "2026-02-15",
                        "CON_IVA",
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "1130.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "1130.00", true)));
        registrar(
                s,
                "k2",
                LibroDiarioIT.asiento(
                        "2026-02-20",
                        "CON_IVA",
                        LibroDiarioIT.linea(cuentaId(s.empresa(), GASTOS_ADMIN), "226.00", "0", true),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "0", "226.00", false)));
        registrar(
                s,
                "k3",
                LibroDiarioIT.asiento(
                        "2026-05-10",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "300.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), PROVEEDORES), "0", "300.00", false)));
        registrar(
                s,
                "k4",
                LibroDiarioIT.asiento(
                        "2026-06-10",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "500.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAPITAL_SOCIAL), "0", "500.00", false)));
        registrar(
                s,
                "k5",
                LibroDiarioIT.asiento(
                        "2026-09-10",
                        null,
                        LibroDiarioIT.linea(cuentaId(s.empresa(), IMPUESTO_RENTA), "50.00", "0", false),
                        LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "0", "50.00", false)));
        return s;
    }

    // ------------------------------------------------------------------------------------------- totales iguales

    /**
     * Corrección 1 — F4-04: los cuatro totales de la Balanza (Σ Debe, Σ Haber, Σ saldos deudores, Σ saldos
     * acreedores) son distintos entre sí en el escenario sembrado, y cada uno coincide —identificado por su
     * etiqueta, no por casualidad— entre el JSON y los tres formatos exportados.
     */
    @Test
    void losTotalesDeLaBalanzaCoincidenPorEtiquetaEnJsonYEnLosTresFormatos() throws Exception {
        Sesion s = sembrarEscenarioDistinguible();
        String rango = "desde=2026-01-01&hasta=2026-12-31";
        ResultActions json = get(s, "/contabilidad/balanza?" + rango);
        String totalDebe = leer(json, "$.totalDebe");
        String totalHaber = leer(json, "$.totalHaber");
        String totalDeudores = leer(json, "$.totalSaldosDeudores");
        String totalAcreedores = leer(json, "$.totalSaldosAcreedores");
        assertThat(totalDebe).isEqualTo("2206.00");
        assertThat(totalHaber).isEqualTo("2206.00");
        assertThat(totalDeudores).isEqualTo("2130.00");
        assertThat(totalAcreedores).isEqualTo("2130.00");
        // El hallazgo del revisor: con un solo asiento Σ Debe y Σ saldos deudores coinciden (500.00 los dos) y una
        // mutación que confunda una fila con la otra no se nota; aquí deben ser distintos.
        assertThat(totalDebe).isNotEqualTo(totalDeudores);

        byte[] csv = exportar(s, "/contabilidad/balanza/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, StandardCharsets.UTF_8);
        verificarCsv(textoCsv, "Totales", 5, totalDebe);
        verificarCsv(textoCsv, "Totales", 6, totalHaber);
        verificarCsv(textoCsv, "Total saldos deudores", 1, totalDeudores);
        verificarCsv(textoCsv, "Total saldos acreedores", 1, totalAcreedores);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/balanza/exportacion?formato=xlsx&" + rango,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verificarXlsx(xlsx, "Totales", 5, totalDebe);
        verificarXlsx(xlsx, "Totales", 6, totalHaber);
        verificarXlsx(xlsx, "Total saldos deudores", 1, totalDeudores);
        verificarXlsx(xlsx, "Total saldos acreedores", 1, totalAcreedores);

        byte[] pdf = exportar(s, "/contabilidad/balanza/exportacion?formato=pdf&" + rango, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Total saldos deudores", totalDeudores);
        verificarPdf(textoPdf, "Total saldos acreedores", totalAcreedores);
    }

    /**
     * Corrección 1 — F4-04: los cinco totales del Estado de Resultados (ingresos, costos y gastos, utilidad antes
     * de impuesto, impuesto sobre la renta, utilidad del ejercicio) son distintos entre sí y cada uno coincide,
     * identificado por su etiqueta, entre el JSON y los tres formatos.
     */
    @Test
    void losTotalesDelEstadoDeResultadosCoincidenPorEtiquetaEnJsonYEnLosTresFormatos() throws Exception {
        Sesion s = sembrarEscenarioDistinguible();
        String rango = "desde=2026-01-01&hasta=2026-12-31";
        ResultActions json = get(s, "/contabilidad/estados/resultados?" + rango);
        String ingresos = leer(json, "$.ingresos.total");
        String costos = leer(json, "$.costosGastos.total");
        String utilidadAntes = leer(json, "$.utilidadAntesImpuesto");
        String impuesto = leer(json, "$.impuestoSobreRenta.total");
        String utilidadEjercicio = leer(json, "$.utilidadEjercicio");
        assertThat(ingresos).isEqualTo("1000.00");
        assertThat(costos).isEqualTo("200.00");
        assertThat(utilidadAntes).isEqualTo("800.00");
        assertThat(impuesto).isEqualTo("50.00");
        assertThat(utilidadEjercicio).isEqualTo("750.00");
        assertThat(Set.of(ingresos, costos, utilidadAntes, impuesto, utilidadEjercicio))
                .as("los cinco totales del Estado de Resultados deben ser distintos entre sí")
                .hasSize(5);

        byte[] csv = exportar(
                s, "/contabilidad/estados/resultados/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, StandardCharsets.UTF_8);
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

    /**
     * Corrección 1 — F4-04: Activo, Pasivo, Patrimonio, resultados anteriores, utilidad del ejercicio y la
     * comprobación del Estado de Situación Financiera son distintos entre sí (salvo Activo y el total Pasivo +
     * Patrimonio, iguales por la ecuación contable misma) y cada uno coincide, identificado por su etiqueta, entre
     * el JSON y los tres formatos.
     */
    @Test
    void losTotalesDelEstadoDeSituacionFinancieraCoincidenPorEtiquetaEnJsonYEnLosTresFormatos() throws Exception {
        Sesion s = sembrarEscenarioDistinguible();
        String fecha = "fechaCorte=2026-12-31";
        ResultActions json = get(s, "/contabilidad/estados/situacion-financiera?" + fecha);
        String activo = leer(json, "$.activo.total");
        String pasivo = leer(json, "$.pasivo.total");
        String patrimonio = leer(json, "$.patrimonio.total");
        String resultadosAnteriores = leer(json, "$.resultadosEjerciciosAnteriores");
        String utilidadEjercicio = leer(json, "$.utilidadEjercicio");
        String totalPasivoPatrimonio = leer(json, "$.totalPasivoPatrimonio");
        String diferencia = leer(json, "$.comprobacion.diferencia");
        assertThat(activo).isEqualTo("1880.00");
        assertThat(pasivo).isEqualTo("430.00");
        assertThat(patrimonio).isEqualTo("500.00");
        assertThat(resultadosAnteriores).isEqualTo("200.00");
        assertThat(utilidadEjercicio).isEqualTo("750.00");
        assertThat(totalPasivoPatrimonio).isEqualTo(activo); // ecuación contable: Activo = Pasivo + Patrimonio + ...
        assertThat(diferencia).isEqualTo("0.00");
        assertThat(Set.of(activo, pasivo, patrimonio, resultadosAnteriores, utilidadEjercicio, diferencia))
                .as("activo, pasivo, patrimonio, resultados anteriores, utilidad del ejercicio y la diferencia deben"
                        + " ser distintos entre sí")
                .hasSize(6);
        assertThat((Boolean) leer(json, "$.comprobacion.cuadra")).isTrue();

        byte[] csv = exportar(
                s,
                "/contabilidad/estados/situacion-financiera/exportacion?formato=csv&" + fecha,
                "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, StandardCharsets.UTF_8);
        verificarCsv(textoCsv, "Total ACTIVO", 3, activo);
        verificarCsv(textoCsv, "Total PASIVO", 3, pasivo);
        verificarCsv(textoCsv, "Total PATRIMONIO", 3, patrimonio);
        verificarCsv(textoCsv, "Resultados de ejercicios anteriores", 1, resultadosAnteriores);
        verificarCsv(textoCsv, "Utilidad del ejercicio", 1, utilidadEjercicio);
        verificarCsv(textoCsv, "Total Pasivo + Patrimonio", 1, totalPasivoPatrimonio);
        verificarCsv(textoCsv, "Diferencia", 1, diferencia);
        assertThat(textoCsv).contains(com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados.LEYENDA);

        byte[] xlsx = exportar(
                s,
                "/contabilidad/estados/situacion-financiera/exportacion?formato=xlsx&" + fecha,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        verificarXlsx(xlsx, "Total ACTIVO", 3, activo);
        verificarXlsx(xlsx, "Total PASIVO", 3, pasivo);
        verificarXlsx(xlsx, "Total PATRIMONIO", 3, patrimonio);
        verificarXlsx(xlsx, "Resultados de ejercicios anteriores", 1, resultadosAnteriores);
        verificarXlsx(xlsx, "Utilidad del ejercicio", 1, utilidadEjercicio);
        verificarXlsx(xlsx, "Total Pasivo + Patrimonio", 1, totalPasivoPatrimonio);
        verificarXlsx(xlsx, "Diferencia", 1, diferencia);

        byte[] pdf = exportar(
                s, "/contabilidad/estados/situacion-financiera/exportacion?formato=pdf&" + fecha, "application/pdf");
        String textoPdf = extraerTexto(pdf);
        verificarPdf(textoPdf, "Total activo", activo);
        verificarPdf(textoPdf, "Total pasivo", pasivo);
        verificarPdf(textoPdf, "Total patrimonio", patrimonio);
        verificarPdf(textoPdf, "Resultados de ejercicios anteriores", resultadosAnteriores);
        verificarPdf(textoPdf, "Total Pasivo + Patrimonio", totalPasivoPatrimonio);
        verificarPdf(textoPdf, "Diferencia", diferencia);
    }

    /**
     * Corrección 1 — F4-04: IVA débito total, IVA crédito total y la diferencia estimada del resumen de IVA son
     * distintos entre sí y cada uno coincide, identificado por su etiqueta, entre el JSON y los tres formatos.
     */
    @Test
    void losTotalesDelResumenDeIvaCoincidenPorEtiquetaEnJsonYEnLosTresFormatos() throws Exception {
        Sesion s = sembrarEscenarioDistinguible();
        String periodo = "anio=2026&mes=2";
        ResultActions json = get(s, "/contabilidad/reportes/iva?" + periodo);
        String ivaDebito = leer(json, "$.ivaDebito.total");
        String ivaCredito = leer(json, "$.ivaCredito.total");
        String diferencia = leer(json, "$.diferenciaEstimada");
        assertThat(ivaDebito).isEqualTo("130.00");
        assertThat(ivaCredito).isEqualTo("26.00");
        assertThat(diferencia).isEqualTo("104.00");
        assertThat(Set.of(ivaDebito, ivaCredito, diferencia)).hasSize(3);

        byte[] csv =
                exportar(s, "/contabilidad/reportes/iva/exportacion?formato=csv&" + periodo, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, StandardCharsets.UTF_8);
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

    /**
     * Corrección 1 — F4-04: saldo inicial, Σ Debe, Σ Haber y saldo final del Libro Mayor de Caja son distintos
     * entre sí y cada uno coincide, identificado por su etiqueta, entre el JSON y los tres formatos.
     */
    @Test
    void losTotalesDelLibroMayorCoincidenPorEtiquetaEnJsonYEnLosTresFormatos() throws Exception {
        Sesion s = sembrarEscenarioDistinguible();
        UUID caja = cuentaId(s.empresa(), CAJA);
        String rango = "cuentaId=" + caja + "&desde=2026-01-01&hasta=2026-12-31";
        ResultActions json = get(s, "/contabilidad/mayor?" + rango);
        String saldoInicial = leer(json, "$.saldoInicial.monto");
        String totalDebe = leer(json, "$.totalDebe");
        String totalHaber = leer(json, "$.totalHaber");
        String saldoFinal = leer(json, "$.saldoFinal.monto");
        assertThat(saldoInicial).isEqualTo("200.00");
        assertThat(totalDebe).isEqualTo("1930.00");
        assertThat(totalHaber).isEqualTo("276.00");
        assertThat(saldoFinal).isEqualTo("1854.00");
        assertThat(Set.of(saldoInicial, totalDebe, totalHaber, saldoFinal)).hasSize(4);

        byte[] csv = exportar(s, "/contabilidad/mayor/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String textoCsv = new String(csv, StandardCharsets.UTF_8);
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

    // -------------------------------------------------------------------------------------- libro diario paginado

    /** Criterio F4-04: el Libro Diario exportado trae todos los asientos, aunque superen el tamaño de página (50). */
    @Test
    void elLibroDiarioExportadoTraeTodosLosAsientosAunqueSuperenLaPagina() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), CAJA);
        UUID ventas = cuentaId(s.empresa(), VENTAS_GRAVADAS);
        int cantidad = 55;
        for (int i = 0; i < cantidad; i++) {
            registrar(
                    s,
                    "k" + i,
                    LibroDiarioIT.asiento(
                            "2026-05-01",
                            null,
                            LibroDiarioIT.linea(caja, "1.00", "0", false),
                            LibroDiarioIT.linea(ventas, "0", "1.00", false)));
        }

        byte[] csv = exportar(
                s,
                "/contabilidad/asientos/exportacion?formato=csv&desde=2026-05-01&hasta=2026-05-31",
                "text/csv;charset=UTF-8");
        String texto = new String(csv, StandardCharsets.UTF_8);
        long lineasDeAsiento = Arrays.stream(texto.split("\r\n"))
                .filter(l -> l.startsWith("2026,"))
                .count();
        // Cada asiento tiene 2 líneas (Caja y Ventas): 55 asientos * 2 líneas = 110 filas de datos
        assertThat(lineasDeAsiento).isEqualTo(cantidad * 2L);
        assertThat(texto).contains("TOTALES,,,,,,,,,,55.00,55.00");
    }

    // ------------------------------------------------------------------------------------------------- errores

    /** ADR-038 §10: rango invertido → 422 PLT-002; cuenta de otra empresa → 404 PLT-017; auditor puede exportar. */
    @Test
    void rangoInvertidoYCuentaAjenaSonErroresDeValidacionYElAuditorPuedeExportar() throws Exception {
        Sesion a = sembrarUnAsiento();
        Sesion b = sesionConContabilidad();
        UUID cuentaDeB = cuentaId(b.empresa(), CAJA);

        get(a, "/contabilidad/balanza/exportacion?formato=csv&desde=2026-12-31&hasta=2026-01-01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.codigo").value("PLT-002"));

        get(
                        a,
                        "/contabilidad/mayor/exportacion?formato=csv&cuentaId=" + cuentaDeB
                                + "&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));

        Sesion auditor = sembrarMiembro(a, "auditor");
        get(auditor, "/contabilidad/balanza/exportacion?formato=csv&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------------- caracteres y content-type

    /** Un concepto con comas, comillas y ñ queda correcto en el CSV (RFC 4180) y en el texto extraído del PDF. */
    @Test
    void unConceptoConComasComillasYEnieQuedaCorrectoEnCsvYPdf() throws Exception {
        Sesion s = sesionConContabilidad();
        registrar(
                s,
                "k1",
                "{\"fecha\":\"2026-06-01\",\"concepto\":\"Depósito, con \\\"comillas\\\" y ñ\",\"lineas\":["
                        + LibroDiarioIT.linea(cuentaId(s.empresa(), CAJA), "10.00", "0", false) + ","
                        + LibroDiarioIT.linea(cuentaId(s.empresa(), VENTAS_GRAVADAS), "0", "10.00", false) + "]}");

        String rango = "desde=2026-06-01&hasta=2026-06-30";
        byte[] csv = exportar(s, "/contabilidad/asientos/exportacion?formato=csv&" + rango, "text/csv;charset=UTF-8");
        String texto = new String(csv, StandardCharsets.UTF_8);
        assertThat(texto).contains("\"Depósito, con \"\"comillas\"\" y ñ\"");

        byte[] pdf = exportar(s, "/contabilidad/asientos/exportacion?formato=pdf&" + rango, "application/pdf");
        assertThat(extraerTexto(pdf)).contains("Depósito, con \"comillas\" y ñ");
    }

    /** Cada formato responde su tipo de contenido exacto y un nombre de archivo en minúsculas y sin espacios. */
    @Test
    void cadaFormatoRespondeSuTipoDeContenidoYSuNombreDeArchivo() throws Exception {
        Sesion s = sembrarUnAsiento();
        String rango = "desde=2026-01-01&hasta=2026-12-31";

        get(s, "/contabilidad/balanza/exportacion?formato=csv&" + rango)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"balanza_2026-01-01_2026-12-31.csv\""));

        get(s, "/contabilidad/balanza/exportacion?formato=xlsx&" + rango)
                .andExpect(status().isOk())
                .andExpect(header().string(
                                "Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"balanza_2026-01-01_2026-12-31.xlsx\""));

        get(s, "/contabilidad/balanza/exportacion?formato=pdf&" + rango)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string(
                                "Content-Disposition", "attachment; filename=\"balanza_2026-01-01_2026-12-31.pdf\""));
    }

    // ----------------------------------------------------------------------------------------------- utilidades

    /** Compara una columna de una fila del CSV, identificada por su etiqueta, contra el valor esperado. */
    private static void verificarCsv(String textoCsv, String etiqueta, int columna, String esperado) {
        assertThat(filaCsv(textoCsv, etiqueta)[columna]).isEqualTo(esperado);
    }

    /** Compara una celda numérica del XLSX, identificada por la etiqueta de su fila, contra el valor esperado. */
    private static void verificarXlsx(byte[] xlsx, String etiqueta, int columna, String esperado) throws IOException {
        assertThat(celdaNumericaDeFila(xlsx, etiqueta, columna)).isEqualTo(Double.parseDouble(esperado));
    }

    /** Confirma que el valor esperado aparece cerca de su etiqueta en el texto extraído del PDF. */
    private static void verificarPdf(String textoPdf, String etiqueta, String esperado) {
        assertThat(etiquetaCercaDelValor(textoPdf, etiqueta, esperado)).isTrue();
    }

    /** Ejecuta la exportación, confirma el estado y el tipo de contenido, y devuelve los bytes del archivo. */
    private byte[] exportar(Sesion s, String ruta, String tipoContenidoEsperado) throws Exception {
        ResultActions r = get(s, ruta)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", tipoContenidoEsperado));
        return r.andReturn().getResponse().getContentAsByteArray();
    }

    /** Extrae el texto de un PDF con PDFBox, para verificar su contenido sin depender del layout exacto. */
    private static String extraerTexto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    /** Busca la fila cuya primera celda sea el texto dado y devuelve el valor numérico de la columna pedida. */
    private static double celdaNumericaDeFila(byte[] xlsx, String textoPrimeraCelda, int columna) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            for (Row fila : hoja) {
                Cell primera = fila.getCell(0);
                if (primera != null
                        && primera.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && textoPrimeraCelda.equals(primera.getStringCellValue())) {
                    return fila.getCell(columna).getNumericCellValue();
                }
            }
            throw new AssertionError("No se encontró la fila '" + textoPrimeraCelda + "' en la hoja exportada");
        }
    }

    /**
     * Busca la fila cuya primera celda sea la etiqueta dada (p. ej. "IVA débito fiscal") y devuelve el valor
     * numérico de la columna pedida, {@code filasDespues} filas más abajo (para leer la fila de valores del
     * desglose de IVA, dos filas después de su encabezado de sección).
     */
    private static double celdaTrasEtiqueta(byte[] xlsx, String etiqueta, int filasDespues, int columna)
            throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            for (Row fila : hoja) {
                Cell primera = fila.getCell(0);
                if (primera != null
                        && primera.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                        && etiqueta.equals(primera.getStringCellValue())) {
                    Row destino = hoja.getRow(fila.getRowNum() + filasDespues);
                    return destino.getCell(columna).getNumericCellValue();
                }
            }
            throw new AssertionError("No se encontró la etiqueta '" + etiqueta + "' en la hoja exportada");
        }
    }

    /** Divide el CSV en líneas (RFC 4180 con CRLF) y devuelve los campos de la que empieza con la etiqueta dada. */
    private static String[] filaCsv(String textoCsv, String etiqueta) {
        for (String linea : textoCsv.split("\r\n")) {
            String[] campos = linea.split(",", -1);
            if (campos.length > 0 && etiqueta.equals(campos[0])) {
                return campos;
            }
        }
        throw new AssertionError("No se encontró la fila '" + etiqueta + "' en el CSV exportado");
    }

    /**
     * Lee el total (última columna) de la fila de valores del desglose de IVA de una sección, dos líneas después
     * de su encabezado (la línea del encabezado de sección, luego "Manual,N8N,Reversión,Total", luego los valores).
     */
    private static String totalDesgloseCsv(String textoCsv, String etiquetaSeccion) {
        String[] lineas = textoCsv.split("\r\n");
        for (int i = 0; i < lineas.length; i++) {
            if (lineas[i].startsWith(etiquetaSeccion + ",")) {
                return lineas[i + 2].split(",", -1)[3];
            }
        }
        throw new AssertionError("No se encontró la sección '" + etiquetaSeccion + "' en el CSV exportado");
    }

    /**
     * Indica si {@code valor} aparece justo después de {@code etiqueta} en el texto extraído del PDF (a lo sumo 15
     * caracteres de separación: el espacio de una celda de tabla, o ": " en un párrafo). Las plantillas escriben
     * cada frase con un único {@code th:text} (nunca varios {@code <span>} hermanos en el mismo párrafo, que
     * OpenHTMLtoPDF pinta fuera de orden en el flujo de texto aunque el layout visual sea correcto), así que la
     * etiqueta y su valor siempre quedan contiguos, salvo un texto aclaratorio entre paréntesis en la propia celda
     * de la etiqueta (p. ej. "Diferencia estimada (débito − crédito)", 20 caracteres hasta el monto). La ventana es
     * corta a propósito: no alcanza a cruzar hacia el valor de una etiqueta vecina (p. ej. "Total saldos
     * acreedores" está a 37 caracteres de "Total saldos deudores" en la Balanza), así que una mutación que confunda
     * dos totales no se deja pasar (corrección 1 — F4-04).
     */
    private static boolean etiquetaCercaDelValor(String texto, String etiqueta, String valor) {
        String patron = Pattern.quote(etiqueta) + "(?s).{0,25}?" + Pattern.quote(valor);
        return Pattern.compile(patron).matcher(texto).find();
    }
}
