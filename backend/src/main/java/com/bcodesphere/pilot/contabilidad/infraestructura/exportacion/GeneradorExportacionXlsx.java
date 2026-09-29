package com.bcodesphere.pilot.contabilidad.infraestructura.exportacion;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.aplicacion.GeneradorExportacion;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.estados.BalanzaComprobacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DesgloseIva;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.FilaBalanza;
import com.bcodesphere.pilot.contabilidad.dominio.estados.FilaEstado;
import com.bcodesphere.pilot.contabilidad.dominio.estados.LibroMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.MovimientoMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva;
import com.bcodesphere.pilot.contabilidad.dominio.estados.RubroEstado;
import com.bcodesphere.pilot.contabilidad.dominio.exportacion.FormatoArchivo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Exportación de reportes a XLSX con Apache POI SXSSF (escritura en streaming, ADR-038 "Implementación de las
 * exportaciones"): una hoja con encabezados en negrita, montos como celdas numéricas con formato {@code #,##0.00}
 * (el {@link Dinero} solo se convierte a {@code double} al escribir la celda, nunca para sumar ni comparar,
 * CLAUDE.md 1.2.2), fechas como celdas de fecha y sin fórmulas. No calcula nada: cada método recorre el mismo
 * objeto de dominio que ya entregó el reporte JSON.
 */
@Component
public class GeneradorExportacionXlsx implements GeneradorExportacion {

    /** Zona horaria de negocio para convertir fechas contables a celdas de fecha (CLAUDE.md, encabezado). */
    private static final ZoneId ZONA_EL_SALVADOR = ZoneId.of("America/El_Salvador");

    /** Filas que SXSSF conserva en memoria antes de volcarlas a disco (streaming, ADR-038). */
    private static final int VENTANA_FILAS = 100;

    @Override
    public FormatoArchivo formato() {
        return FormatoArchivo.XLSX;
    }

    @Override
    public String extension() {
        return "xlsx";
    }

    @Override
    public String tipoContenido() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    @Override
    public byte[] libroDiario(List<Asiento> asientos, LocalDate desde, LocalDate hasta) {
        return generar("Libro Diario", h -> {
            h.encabezado(
                    "Año",
                    "Número",
                    "Fecha",
                    "Concepto",
                    "Estado",
                    "Origen",
                    "N° Línea",
                    "Código Cuenta",
                    "Cuenta",
                    "Descripción",
                    "Debe",
                    "Haber");
            Dinero totalDebe = Dinero.CERO;
            Dinero totalHaber = Dinero.CERO;
            for (Asiento a : asientos) {
                for (LineaAsiento l : a.lineas()) {
                    h.fila(
                            a.anio(),
                            a.numero(),
                            a.fecha(),
                            a.concepto(),
                            a.estado(),
                            a.origenTipo(),
                            l.numeroLinea(),
                            l.cuenta().codigo(),
                            l.cuenta().nombre(),
                            l.descripcion(),
                            l.debe(),
                            l.haber());
                }
                totalDebe = totalDebe.sumar(a.totalDebe());
                totalHaber = totalHaber.sumar(a.totalHaber());
            }
            h.fila("TOTALES", null, null, null, null, null, null, null, null, null, totalDebe, totalHaber);
        });
    }

    @Override
    public byte[] libroMayor(LibroMayor m) {
        return generar("Libro Mayor", h -> {
            h.fila("Cuenta", m.cuenta().codigo().valor(), m.cuenta().nombre());
            h.fila("Naturaleza", m.naturaleza().name());
            h.fila("Período", m.desde(), m.hasta());
            h.fila(
                    "Saldo inicial",
                    m.saldoInicial().monto(),
                    m.saldoInicial().lado().name());
            h.fila();
            h.encabezado(
                    "Fecha",
                    "Año",
                    "Número",
                    "Concepto",
                    "Descripción",
                    "Código Cuenta",
                    "Cuenta",
                    "Debe",
                    "Haber",
                    "Saldo",
                    "Lado");
            for (MovimientoMayor mov : m.movimientos()) {
                var l = mov.linea();
                h.fila(
                        l.fecha(),
                        l.anio(),
                        l.numero(),
                        l.concepto(),
                        l.descripcion(),
                        l.cuenta().codigo().valor(),
                        l.cuenta().nombre(),
                        l.debe(),
                        l.haber(),
                        mov.saldo().monto(),
                        mov.saldo().lado().name());
            }
            h.fila("Totales", null, null, null, null, null, null, m.totalDebe(), m.totalHaber(), null, null);
            h.fila(
                    "Saldo final",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    m.saldoFinal().monto(),
                    m.saldoFinal().lado().name());
        });
    }

    @Override
    public byte[] balanza(BalanzaComprobacion b) {
        return generar("Balanza", h -> {
            h.fila("Período", b.desde(), b.hasta());
            h.fila("Nivel", b.nivel());
            h.fila();
            h.encabezado("Código", "Nombre", "Nivel", "Saldo Inicial", "Lado", "Debe", "Haber", "Saldo Final", "Lado");
            for (FilaBalanza f : b.filas()) {
                h.fila(
                        f.cuenta().codigo().valor(),
                        f.cuenta().nombre(),
                        f.nivel(),
                        f.saldoInicial().monto(),
                        f.saldoInicial().lado().name(),
                        f.debe(),
                        f.haber(),
                        f.saldoFinal().monto(),
                        f.saldoFinal().lado().name());
            }
            h.fila("Totales", null, null, null, null, b.totalDebe(), b.totalHaber(), null, null);
            h.fila("Total saldos deudores", b.totalSaldosDeudores());
            h.fila("Total saldos acreedores", b.totalSaldosAcreedores());
            h.fila("Cuadra", b.cuadra());
        });
    }

    @Override
    public byte[] estadoResultados(EstadoResultados e) {
        return generar("Estado de Resultados", h -> {
            h.fila("Estado de Resultados", e.desde(), e.hasta());
            rubro(h, e.ingresos());
            rubro(h, e.costosGastos());
            h.fila("Utilidad antes de impuesto", e.utilidadAntesImpuesto());
            rubro(h, e.impuestoSobreRenta());
            h.fila("Utilidad del ejercicio", e.utilidadEjercicio());
            h.fila(e.leyenda());
        });
    }

    @Override
    public byte[] estadoSituacionFinanciera(EstadoSituacionFinanciera e) {
        return generar("Estado de Situación Financiera", h -> {
            h.fila("Estado de Situación Financiera", "Fecha de corte", e.fechaCorte());
            rubro(h, e.activo());
            rubro(h, e.pasivo());
            rubro(h, e.patrimonio());
            h.fila("Resultados de ejercicios anteriores", e.resultadosEjerciciosAnteriores());
            h.fila("Utilidad del ejercicio", e.utilidadEjercicio());
            h.fila("Total Pasivo + Patrimonio", e.totalPasivoPatrimonio());
            h.fila("Comprobación (cuadra)", e.comprobacion().cuadra());
            h.fila("Diferencia", e.comprobacion().diferencia());
            h.fila(e.leyenda());
        });
    }

    @Override
    public byte[] resumenIva(ResumenIva r) {
        return generar("Resumen de IVA", h -> {
            h.fila("Resumen de IVA", r.anio(), String.format("%02d", r.mes()));
            h.fila(
                    "IVA débito fiscal",
                    "Cuenta",
                    r.cuentaIvaDebito().codigo(),
                    r.cuentaIvaDebito().nombre());
            desglose(h, r.ivaDebito());
            h.fila(
                    "IVA crédito fiscal",
                    "Cuenta",
                    r.cuentaIvaCredito().codigo(),
                    r.cuentaIvaCredito().nombre());
            desglose(h, r.ivaCredito());
            h.fila("Diferencia estimada", r.diferenciaEstimada());
            h.fila(r.nota());
        });
    }

    /** Rubro de un estado: sus filas de nivel 2 al pedido y su total (ADR-038 §6). */
    private static void rubro(Hoja h, RubroEstado rubro) {
        h.fila(rubro.nombre());
        h.encabezado("Código", "Nombre", "Nivel", "Monto");
        for (FilaEstado f : rubro.filas()) {
            h.fila(f.cuenta().codigo().valor(), f.cuenta().nombre(), f.nivel(), f.monto());
        }
        h.fila("Total " + rubro.nombre(), null, null, rubro.total());
    }

    /** Desglose de IVA por origen de asiento (ADR-038 §8). */
    private static void desglose(Hoja h, DesgloseIva d) {
        h.encabezado("Manual", "N8N", "Reversión", "Total");
        h.fila(d.manual(), d.n8n(), d.reversion(), d.total());
    }

    /** Crea el libro en streaming, delega el contenido y lo serializa; libera siempre los archivos temporales. */
    private static byte[] generar(String nombreHoja, java.util.function.Consumer<Hoja> contenido) {
        SXSSFWorkbook libro = new SXSSFWorkbook(VENTANA_FILAS);
        try {
            SXSSFSheet hoja = libro.createSheet(nombreHoja);
            contenido.accept(new Hoja(libro, hoja));
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            // SXSSF respalda las filas en disco mientras se escriben: dispose() borra siempre los temporales
            // (ADR-038), incluso si el bloque anterior lanzó una excepción.
            libro.dispose();
        }
    }

    /**
     * Envoltorio de una hoja SXSSF con los estilos ya preparados: encabezados en negrita y montos con formato
     * {@code #,##0.00}. Cada llamada a {@link #fila} o {@link #encabezado} escribe la siguiente fila y avanza el
     * índice; el tipo de cada valor decide el tipo de celda (texto, numérica, fecha o booleana).
     */
    private static final class Hoja {

        private final SXSSFSheet hoja;
        private final CellStyle estiloEncabezado;
        private final CellStyle estiloMonto;
        private final CellStyle estiloFecha;
        private int fila;

        Hoja(SXSSFWorkbook libro, SXSSFSheet hoja) {
            this.hoja = hoja;
            DataFormat formato = libro.createDataFormat();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            this.estiloEncabezado = libro.createCellStyle();
            this.estiloEncabezado.setFont(negrita);
            this.estiloMonto = libro.createCellStyle();
            this.estiloMonto.setDataFormat(formato.getFormat("#,##0.00"));
            this.estiloFecha = libro.createCellStyle();
            this.estiloFecha.setDataFormat(formato.getFormat("yyyy-mm-dd"));
            for (int c = 0; c < 15; c++) {
                hoja.setColumnWidth(c, 22 * 256);
            }
        }

        /** Fila de encabezado, en negrita. */
        void encabezado(Object... valores) {
            Row r = hoja.createRow(fila++);
            for (int i = 0; i < valores.length; i++) {
                Cell celda = r.createCell(i);
                celda.setCellValue(String.valueOf(valores[i]));
                celda.setCellStyle(estiloEncabezado);
            }
        }

        /** Fila de datos: cada valor nulo deja la celda vacía; el tipo del valor decide el tipo de celda. */
        void fila(Object... valores) {
            Row r = hoja.createRow(fila++);
            for (int i = 0; i < valores.length; i++) {
                escribir(r.createCell(i), valores[i]);
            }
        }

        /** Escribe el valor en la celda según su tipo; nunca usa {@code double} para comparar, solo para mostrar. */
        private void escribir(Cell celda, Object valor) {
            if (valor == null) {
                return;
            }
            if (valor instanceof Dinero monto) {
                celda.setCellValue(monto.valor().doubleValue());
                celda.setCellStyle(estiloMonto);
            } else if (valor instanceof Integer || valor instanceof Long || valor instanceof Short) {
                celda.setCellValue(((Number) valor).doubleValue());
            } else if (valor instanceof Boolean b) {
                celda.setCellValue(b);
            } else if (valor instanceof LocalDate fecha) {
                celda.setCellValue(
                        Date.from(fecha.atStartOfDay(ZONA_EL_SALVADOR).toInstant()));
                celda.setCellStyle(estiloFecha);
            } else {
                celda.setCellValue(String.valueOf(valor));
            }
        }
    }
}
