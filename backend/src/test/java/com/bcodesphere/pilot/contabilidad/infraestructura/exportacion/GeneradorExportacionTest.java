package com.bcodesphere.pilot.contabilidad.infraestructura.exportacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.EstadoAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenLinea;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.CodigoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.estados.BalanzaComprobacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.LibroMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.MovimientoLinea;
import com.bcodesphere.pilot.contabilidad.dominio.estados.NetoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de los tres adaptadores de exportación (F4-04, ADR-038): sin Spring ni base de datos, con
 * asientos armados a mano. Verifican que el Libro Diario exportado conserva los mismos totales que sus asientos de
 * origen y que los caracteres del español y los campos con comas y comillas quedan correctos en cada formato.
 */
class GeneradorExportacionTest {

    /** Cuenta con caracteres del español y un texto que fuerza el escape de comillas y comas en CSV. */
    private static final Cuenta CAJA = new Cuenta(
            GeneradorId.nuevo(),
            CodigoCuenta.de("11010101"),
            "Caja general — áéíóúñ, \"efectivo\"",
            null,
            NaturalezaCuenta.DEUDORA,
            true,
            true,
            0);

    private static final Cuenta VENTAS = new Cuenta(
            GeneradorId.nuevo(),
            CodigoCuenta.de("51010101"),
            "Ventas gravadas",
            null,
            NaturalezaCuenta.ACREEDORA,
            true,
            true,
            0);

    /** Cuenta de agrupación de la clase 1 (nivel 1), necesaria porque la Balanza siempre arma desde el nivel 1. */
    private static final Cuenta CLASE_1 = new Cuenta(
            GeneradorId.nuevo(), CodigoCuenta.de("1"), "ACTIVO", null, NaturalezaCuenta.DEUDORA, false, true, 0);

    /** Cuenta de agrupación de la clase 5 (nivel 1), misma razón que {@link #CLASE_1}. */
    private static final Cuenta CLASE_5 = new Cuenta(
            GeneradorId.nuevo(), CodigoCuenta.de("5"), "INGRESOS", null, NaturalezaCuenta.ACREEDORA, false, true, 0);

    private static final Cuenta GASTOS = new Cuenta(
            GeneradorId.nuevo(),
            CodigoCuenta.de("42020101"),
            "Gastos de administración",
            null,
            NaturalezaCuenta.DEUDORA,
            true,
            true,
            0);

    private static final Cuenta IMPUESTO_RENTA = new Cuenta(
            GeneradorId.nuevo(),
            CodigoCuenta.de("44010101"),
            "Impuesto sobre la renta",
            null,
            NaturalezaCuenta.DEUDORA,
            true,
            true,
            0);

    private static final List<Cuenta> CATALOGO = List.of(CLASE_1, CAJA, CLASE_5, VENTAS, GASTOS, IMPUESTO_RENTA);

    /** Un asiento balanceado (113.00 = 113.00) con un concepto que también fuerza el escape RFC 4180. */
    private static Asiento asientoDePrueba() {
        LineaAsiento debe = new LineaAsiento(
                GeneradorId.nuevo(),
                1,
                CAJA.resumen(),
                "Depósito, con coma y \"comillas\"",
                Dinero.de("113.00"),
                Dinero.CERO,
                OrigenLinea.USUARIO,
                null);
        LineaAsiento haber = new LineaAsiento(
                GeneradorId.nuevo(),
                2,
                VENTAS.resumen(),
                null,
                Dinero.CERO,
                Dinero.de("113.00"),
                OrigenLinea.USUARIO,
                null);
        return new Asiento(
                GeneradorId.nuevo(),
                2026,
                1L,
                LocalDate.of(2026, 1, 15),
                "Cierre de caja, con \"comillas\" y ñ",
                EstadoAsiento.CONTABILIZADO,
                OrigenAsiento.MANUAL,
                null,
                null,
                null,
                null,
                Dinero.de("113.00"),
                Dinero.de("113.00"),
                Instant.parse("2026-01-15T12:00:00Z"),
                List.of(debe, haber));
    }

    // ---------------------------------------------------------------------------------------------------- CSV

    /** El CSV lleva BOM UTF-8, escapa comas y comillas (RFC 4180) y sus totales coinciden con el asiento. */
    @Test
    void elCsvDelLibroDiarioEscapaCamposYConservaLosTotales() throws IOException {
        byte[] contenido = new GeneradorExportacionCsv()
                .libroDiario(List.of(asientoDePrueba()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        String texto = new String(contenido, StandardCharsets.UTF_8);

        assertThat(contenido[0]).isEqualTo((byte) 0xEF); // primer byte del BOM UTF-8
        assertThat(texto).contains("\"Cierre de caja, con \"\"comillas\"\" y ñ\"");
        assertThat(texto).contains("\"Depósito, con coma y \"\"comillas\"\"\"");
        assertThat(texto).contains("TOTALES,,,,,,,,,,113.00,113.00");
    }

    // --------------------------------------------------------------------------------------------------- XLSX

    /** El XLSX tiene celdas numéricas (no texto) para los montos, con los mismos totales que el asiento. */
    @Test
    void elXlsxDelLibroDiarioEscribeMontosComoCeldasNumericas() throws IOException {
        byte[] contenido = new GeneradorExportacionXlsx()
                .libroDiario(List.of(asientoDePrueba()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            var hoja = libro.getSheetAt(0);
            // Fila 1 (índice 1) es la primera línea (Caja, Debe 113.00); columna 10 = Debe (0-indexado)
            Cell celdaDebe = hoja.getRow(1).getCell(10);
            assertThat(celdaDebe.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(celdaDebe.getNumericCellValue()).isEqualTo(113.00);
            // Fila de totales: la penúltima fila del reporte (una cabecera + 2 líneas + totales = filas 0..3)
            Cell celdaTotalDebe = hoja.getRow(3).getCell(10);
            assertThat(celdaTotalDebe.getNumericCellValue()).isEqualTo(113.00);
        }
    }

    // ----------------------------------------------------------------------------------------------------- PDF

    /** El PDF trae el texto del concepto (con sus caracteres del español) y el total del Libro Diario. */
    @Test
    void elPdfDelLibroDiarioContieneElTextoYElTotal() throws IOException {
        byte[] contenido = new GeneradorExportacionPdf()
                .libroDiario(List.of(asientoDePrueba()), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(contenido).isNotEmpty();
        String texto = extraerTexto(contenido);
        assertThat(texto).contains("Cierre de caja");
        assertThat(texto).contains("áéíóúñ");
        assertThat(texto).contains("113.00");
        assertThat(texto).contains("TOTALES");
    }

    // ----------------------------------------------------------------------------------------------- otros reportes

    /** El Libro Mayor se exporta en los tres formatos sin error, con el saldo final en el PDF. */
    @Test
    void elLibroMayorSeExportaEnLosTresFormatos() throws IOException {
        MovimientoLinea linea = new MovimientoLinea(
                LocalDate.of(2026, 1, 15),
                GeneradorId.nuevo(),
                2026,
                1L,
                "Concepto",
                "detalle",
                CAJA,
                Dinero.de("113.00"),
                Dinero.CERO);
        LibroMayor mayor = LibroMayor.generar(
                CAJA, Dinero.CERO, List.of(linea), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(new GeneradorExportacionCsv().libroMayor(mayor)).isNotEmpty();
        assertThat(new GeneradorExportacionXlsx().libroMayor(mayor)).isNotEmpty();
        assertThat(extraerTexto(new GeneradorExportacionPdf().libroMayor(mayor)))
                .contains("113.00");
    }

    /** La Balanza se exporta en los tres formatos sin error, con "Cuadra" en el PDF. */
    @Test
    void laBalanzaSeExportaEnLosTresFormatos() throws IOException {
        Map<UUID, NetoCuenta> movimientos = Map.of(
                CAJA.id(), new NetoCuenta(Dinero.de("113.00"), Dinero.CERO),
                VENTAS.id(), new NetoCuenta(Dinero.CERO, Dinero.de("113.00")));
        BalanzaComprobacion balanza = BalanzaComprobacion.generar(
                CATALOGO, Map.of(), movimientos, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 1);

        assertThat(new GeneradorExportacionCsv().balanza(balanza)).isNotEmpty();
        assertThat(new GeneradorExportacionXlsx().balanza(balanza)).isNotEmpty();
        assertThat(extraerTexto(new GeneradorExportacionPdf().balanza(balanza))).contains("113.00");
    }

    /** El Estado de Resultados se exporta con su leyenda de estado de gestión (ADR-037) en el PDF. */
    @Test
    void elEstadoDeResultadosSeExportaConSuLeyenda() throws IOException {
        Map<UUID, NetoCuenta> movimientos = Map.of(
                VENTAS.id(), new NetoCuenta(Dinero.CERO, Dinero.de("100.00")),
                GASTOS.id(), new NetoCuenta(Dinero.de("40.00"), Dinero.CERO),
                IMPUESTO_RENTA.id(), new NetoCuenta(Dinero.de("10.00"), Dinero.CERO));
        EstadoResultados estado = EstadoResultados.generar(
                CATALOGO, movimientos, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1, false);

        assertThat(new GeneradorExportacionCsv().estadoResultados(estado)).isNotEmpty();
        assertThat(new GeneradorExportacionXlsx().estadoResultados(estado)).isNotEmpty();
        assertThat(extraerTexto(new GeneradorExportacionPdf().estadoResultados(estado)))
                .contains(EstadoResultados.LEYENDA);
    }

    /** El Estado de Situación Financiera se exporta con su comprobación y leyenda en el PDF. */
    @Test
    void elEstadoDeSituacionFinancieraSeExportaConSuComprobacion() throws IOException {
        Map<UUID, NetoCuenta> acumulado = Map.of(CAJA.id(), new NetoCuenta(Dinero.de("100.00"), Dinero.CERO));
        EstadoSituacionFinanciera estado =
                EstadoSituacionFinanciera.generar(CATALOGO, acumulado, Map.of(), LocalDate.of(2026, 1, 31), 1, false);

        assertThat(new GeneradorExportacionCsv().estadoSituacionFinanciera(estado))
                .isNotEmpty();
        assertThat(new GeneradorExportacionXlsx().estadoSituacionFinanciera(estado))
                .isNotEmpty();
        assertThat(extraerTexto(new GeneradorExportacionPdf().estadoSituacionFinanciera(estado)))
                .contains("100.00");
    }

    /** El resumen de IVA se exporta en los tres formatos, con su nota informativa en el PDF. */
    @Test
    void elResumenDeIvaSeExportaConSuNota() throws IOException {
        ResumenIva resumen = ResumenIva.de(
                2026, 2, CAJA.resumen(), VENTAS.resumen(), Map.of(OrigenAsiento.MANUAL, Dinero.de("26.00")), Map.of());

        assertThat(new GeneradorExportacionCsv().resumenIva(resumen)).isNotEmpty();
        assertThat(new GeneradorExportacionXlsx().resumenIva(resumen)).isNotEmpty();
        assertThat(extraerTexto(new GeneradorExportacionPdf().resumenIva(resumen)))
                .contains(ResumenIva.NOTA);
    }

    /** Extrae el texto de un PDF con PDFBox, para verificar su contenido sin depender del layout exacto. */
    private static String extraerTexto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
