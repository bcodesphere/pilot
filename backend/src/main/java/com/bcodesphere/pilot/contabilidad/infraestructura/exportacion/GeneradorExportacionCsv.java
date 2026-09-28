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
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Exportación de reportes a CSV, sin dependencias (RFC 4180, ADR-038 "Implementación de las exportaciones"): UTF-8
 * con BOM, separador coma, comillas y comas escapadas, punto decimal y montos sin separador de miles. No calcula
 * nada: cada método recorre el mismo objeto de dominio que ya entregó el reporte JSON.
 */
@Component
public class GeneradorExportacionCsv implements GeneradorExportacion {

    /** Fin de línea de RFC 4180. */
    private static final String CRLF = "\r\n";

    @Override
    public FormatoArchivo formato() {
        return FormatoArchivo.CSV;
    }

    @Override
    public String extension() {
        return "csv";
    }

    @Override
    public String tipoContenido() {
        return "text/csv;charset=UTF-8";
    }

    @Override
    public byte[] libroDiario(List<Asiento> asientos, LocalDate desde, LocalDate hasta) {
        return generar(w -> {
            fila(
                    w,
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
                    fila(
                            w,
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
            fila(w, "TOTALES", "", "", "", "", "", "", "", "", "", totalDebe, totalHaber);
        });
    }

    @Override
    public byte[] libroMayor(LibroMayor m) {
        return generar(w -> {
            fila(w, "Cuenta", m.cuenta().codigo().valor(), m.cuenta().nombre());
            fila(w, "Naturaleza", m.naturaleza());
            fila(w, "Período", m.desde(), m.hasta());
            fila(w, "Saldo inicial", m.saldoInicial().monto(), m.saldoInicial().lado());
            fila(w);
            fila(
                    w,
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
                fila(
                        w,
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
                        mov.saldo().lado());
            }
            fila(w, "Totales", "", "", "", "", "", "", m.totalDebe(), m.totalHaber(), "", "");
            fila(
                    w,
                    "Saldo final",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    m.saldoFinal().monto(),
                    m.saldoFinal().lado());
        });
    }

    @Override
    public byte[] balanza(BalanzaComprobacion b) {
        return generar(w -> {
            fila(w, "Período", b.desde(), b.hasta());
            fila(w, "Nivel", b.nivel());
            fila(w);
            fila(w, "Código", "Nombre", "Nivel", "Saldo Inicial", "Lado", "Debe", "Haber", "Saldo Final", "Lado");
            for (FilaBalanza f : b.filas()) {
                fila(
                        w,
                        f.cuenta().codigo().valor(),
                        f.cuenta().nombre(),
                        f.nivel(),
                        f.saldoInicial().monto(),
                        f.saldoInicial().lado(),
                        f.debe(),
                        f.haber(),
                        f.saldoFinal().monto(),
                        f.saldoFinal().lado());
            }
            fila(w, "Totales", "", "", "", "", b.totalDebe(), b.totalHaber(), "", "");
            fila(w, "Total saldos deudores", b.totalSaldosDeudores());
            fila(w, "Total saldos acreedores", b.totalSaldosAcreedores());
            fila(w, "Cuadra", b.cuadra());
        });
    }

    @Override
    public byte[] estadoResultados(EstadoResultados e) {
        return generar(w -> {
            fila(w, "Estado de Resultados", e.desde(), e.hasta());
            rubro(w, e.ingresos());
            rubro(w, e.costosGastos());
            fila(w, "Utilidad antes de impuesto", e.utilidadAntesImpuesto());
            rubro(w, e.impuestoSobreRenta());
            fila(w, "Utilidad del ejercicio", e.utilidadEjercicio());
            fila(w, e.leyenda());
        });
    }

    @Override
    public byte[] estadoSituacionFinanciera(EstadoSituacionFinanciera e) {
        return generar(w -> {
            fila(w, "Estado de Situación Financiera", "Fecha de corte", e.fechaCorte());
            rubro(w, e.activo());
            rubro(w, e.pasivo());
            rubro(w, e.patrimonio());
            fila(w, "Resultados de ejercicios anteriores", e.resultadosEjerciciosAnteriores());
            fila(w, "Utilidad del ejercicio", e.utilidadEjercicio());
            fila(w, "Total Pasivo + Patrimonio", e.totalPasivoPatrimonio());
            fila(w, "Comprobación (cuadra)", e.comprobacion().cuadra());
            fila(w, "Diferencia", e.comprobacion().diferencia());
            fila(w, e.leyenda());
        });
    }

    @Override
    public byte[] resumenIva(ResumenIva r) {
        return generar(w -> {
            fila(w, "Resumen de IVA", r.anio(), String.format(Locale.ROOT, "%02d", r.mes()));
            fila(
                    w,
                    "IVA débito fiscal",
                    "Cuenta",
                    r.cuentaIvaDebito().codigo(),
                    r.cuentaIvaDebito().nombre());
            desglose(w, r.ivaDebito());
            fila(
                    w,
                    "IVA crédito fiscal",
                    "Cuenta",
                    r.cuentaIvaCredito().codigo(),
                    r.cuentaIvaCredito().nombre());
            desglose(w, r.ivaCredito());
            fila(w, "Diferencia estimada", r.diferenciaEstimada());
            fila(w, r.nota());
        });
    }

    /** Rubro de un estado: sus filas de nivel 2 al pedido y su total (ADR-038 §6). */
    private static void rubro(Writer w, RubroEstado rubro) throws IOException {
        fila(w, rubro.nombre());
        fila(w, "Código", "Nombre", "Nivel", "Monto");
        for (FilaEstado f : rubro.filas()) {
            fila(w, f.cuenta().codigo().valor(), f.cuenta().nombre(), f.nivel(), f.monto());
        }
        fila(w, "Total " + rubro.nombre(), "", "", rubro.total());
    }

    /** Desglose de IVA por origen de asiento (ADR-038 §8). */
    private static void desglose(Writer w, DesgloseIva d) throws IOException {
        fila(w, "Manual", "N8N", "Reversión", "Total");
        fila(w, d.manual(), d.n8n(), d.reversion(), d.total());
    }

    /**
     * Genera el CSV completo con BOM UTF-8, atrapando cualquier fallo de escritura como {@link UncheckedIOException}.
     * El {@link BufferedWriter} evita miles de escrituras diminutas al convertir a bytes campo por campo (10 000
     * asientos del Libro Diario tienen 20 000 líneas, ADR-038: rendimiento de la exportación).
     */
    private static byte[] generar(EscritorFilas escritor) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (Writer w = new BufferedWriter(new OutputStreamWriter(salida, StandardCharsets.UTF_8), 65536)) {
            w.write('﻿');
            escritor.escribir(w);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    /** Escribe una fila RFC 4180: separada por comas, cada campo escapado si lo necesita. */
    private static void fila(Writer w, Object... campos) throws IOException {
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                w.write(",");
            }
            w.write(escapar(campos[i]));
        }
        w.write(CRLF);
    }

    /** Escapa un campo (comillas dobladas) solo si contiene coma, comilla o salto de línea (RFC 4180). */
    private static String escapar(Object valor) {
        String texto = valor == null ? "" : String.valueOf(valor);
        boolean necesitaComillas = texto.indexOf(',') >= 0
                || texto.indexOf('"') >= 0
                || texto.indexOf('\n') >= 0
                || texto.indexOf('\r') >= 0;
        return necesitaComillas ? "\"" + texto.replace("\"", "\"\"") + "\"" : texto;
    }

    /** Escribe el cuerpo del CSV, ya con el BOM colocado. */
    @FunctionalInterface
    private interface EscritorFilas {
        void escribir(Writer w) throws IOException;
    }

    /** Sobrecarga sin campos: fila en blanco (separador visual entre bloques). */
    private static void fila(Writer w) throws IOException {
        w.write(CRLF);
    }
}
