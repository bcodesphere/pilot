package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.estados.BalanzaComprobacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.LibroMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva;
import com.bcodesphere.pilot.contabilidad.dominio.exportacion.FormatoArchivo;
import java.time.LocalDate;
import java.util.List;

/**
 * Puerto de salida de la generación de exportaciones (arquitectura hexagonal, CLAUDE.md 4.3): un adaptador en
 * {@code infraestructura} por formato (PDF, XLSX, CSV, ADR-038 "Implementación de las exportaciones"). Cada método
 * recibe exactamente el mismo objeto de dominio que ya calculó el reporte JSON correspondiente ({@code dominio.estados}
 * vía {@link ConsultarReportes} y {@link ConsultarAsientos}): las exportaciones nunca calculan nada nuevo, solo dan
 * formato a los mismos totales (CLAUDE.md 10.5). Cualquier falla de escritura se relanza como
 * {@link java.io.UncheckedIOException}, para que el manejador global la traduzca a un 500 sin exponer detalles.
 */
public interface GeneradorExportacion {

    /**
     * Formato que produce este adaptador.
     *
     * @return el formato (identifica al bean entre los tres adaptadores)
     */
    FormatoArchivo formato();

    /**
     * Extensión del archivo generado, en minúsculas y sin punto (CLAUDE.md 12.5).
     *
     * @return por ejemplo {@code "pdf"}, {@code "xlsx"} o {@code "csv"}
     */
    String extension();

    /**
     * Tipo MIME exacto del formato, para el header {@code Content-Type} de la respuesta.
     *
     * @return el tipo de contenido
     */
    String tipoContenido();

    /**
     * Genera la exportación del Libro Diario (CLAUDE.md 10.5): todos los asientos del rango, con sus líneas.
     *
     * @param asientos asientos del rango, ya con sus líneas, en orden ascendente de (año, número)
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @return el archivo generado
     */
    byte[] libroDiario(List<Asiento> asientos, LocalDate desde, LocalDate hasta);

    /**
     * Genera la exportación del Libro Mayor de una cuenta (CLAUDE.md 10.5, ADR-038 §5).
     *
     * @param mayor Libro Mayor ya calculado
     * @return el archivo generado
     */
    byte[] libroMayor(LibroMayor mayor);

    /**
     * Genera la exportación de la Balanza de Comprobación (CLAUDE.md 10.5, ADR-038 §6).
     *
     * @param balanza balanza ya calculada
     * @return el archivo generado
     */
    byte[] balanza(BalanzaComprobacion balanza);

    /**
     * Genera la exportación del Estado de Resultados (CLAUDE.md 10.4, ADR-037), con su leyenda de estado de gestión.
     *
     * @param estado estado ya calculado
     * @return el archivo generado
     */
    byte[] estadoResultados(EstadoResultados estado);

    /**
     * Genera la exportación del Estado de Situación Financiera (CLAUDE.md 10.4, ADR-016, ADR-037), con su
     * comprobación y su leyenda de estado de gestión.
     *
     * @param estado estado ya calculado
     * @return el archivo generado
     */
    byte[] estadoSituacionFinanciera(EstadoSituacionFinanciera estado);

    /**
     * Genera la exportación del resumen de IVA del mes (CLAUDE.md 10.5, ADR-038 §8).
     *
     * @param resumen resumen ya calculado
     * @return el archivo generado
     */
    byte[] resumenIva(ResumenIva resumen);
}
