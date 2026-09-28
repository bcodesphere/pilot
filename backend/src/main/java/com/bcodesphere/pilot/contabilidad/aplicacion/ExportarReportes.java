package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.exportacion.FormatoArchivo;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de exportación de los reportes contables a PDF, XLSX y CSV (ADR-038 "Implementación de las
 * exportaciones", F4-04). No calcula nada: reutiliza los mismos casos de uso de lectura que los reportes JSON
 * ({@link ConsultarReportes}, {@link ConsultarAsientos}), que ya aplican el rol mínimo {@code auditor} con
 * {@code @PreAuthorize}, y delega el formato de salida al {@link GeneradorExportacion} del formato pedido.
 */
@Service
public class ExportarReportes {

    private final ConsultarReportes reportes;
    private final ConsultarAsientos asientos;
    private final Map<FormatoArchivo, GeneradorExportacion> generadores;

    /**
     * Crea el caso de uso.
     *
     * @param reportes casos de uso de lectura de los reportes (Mayor, Balanza, estados, IVA)
     * @param asientos casos de uso de lectura del Libro Diario
     * @param generadores un adaptador por formato (PDF, XLSX, CSV); se indexan por su {@link GeneradorExportacion#formato()}
     */
    public ExportarReportes(
            ConsultarReportes reportes, ConsultarAsientos asientos, List<GeneradorExportacion> generadores) {
        this.reportes = reportes;
        this.asientos = asientos;
        this.generadores = generadores.stream().collect(Collectors.toMap(GeneradorExportacion::formato, g -> g));
    }

    /**
     * Exporta el Libro Diario del rango filtrado (ADR-038 §1).
     *
     * @param filtro filtros del Libro Diario; {@code desde} y {@code hasta} siempre informados
     * @param formato formato pedido
     * @return el archivo generado
     */
    public ArchivoExportado libroDiario(FiltroAsientos filtro, FormatoArchivo formato) {
        GeneradorExportacion gen = generador(formato);
        byte[] contenido = gen.libroDiario(asientos.listarCompleto(filtro), filtro.desde(), filtro.hasta());
        return archivo(contenido, "libro-diario_" + filtro.desde() + "_" + filtro.hasta(), gen);
    }

    /**
     * Exporta el Libro Mayor de una cuenta (ADR-038 §5).
     *
     * @param cuentaId cuenta consultada
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @param formato formato pedido
     * @return el archivo generado
     */
    public ArchivoExportado libroMayor(UUID cuentaId, LocalDate desde, LocalDate hasta, FormatoArchivo formato) {
        GeneradorExportacion gen = generador(formato);
        byte[] contenido = gen.libroMayor(reportes.obtenerLibroMayor(cuentaId, desde, hasta));
        return archivo(contenido, "libro-mayor_" + desde + "_" + hasta, gen);
    }

    /**
     * Exporta la Balanza de Comprobación (ADR-038 §6).
     *
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @param nivel nivel de la jerarquía pedido
     * @param formato formato pedido
     * @return el archivo generado
     */
    public ArchivoExportado balanza(LocalDate desde, LocalDate hasta, int nivel, FormatoArchivo formato) {
        GeneradorExportacion gen = generador(formato);
        byte[] contenido = gen.balanza(reportes.obtenerBalanza(desde, hasta, nivel));
        return archivo(contenido, "balanza_" + desde + "_" + hasta, gen);
    }

    /**
     * Exporta el Estado de Resultados (ADR-037).
     *
     * @param desde inicio del período, inclusive
     * @param hasta fin del período, inclusive
     * @param nivel nivel de la jerarquía pedido
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @param formato formato pedido
     * @return el archivo generado
     */
    public ArchivoExportado estadoResultados(
            LocalDate desde, LocalDate hasta, int nivel, boolean incluirCeros, FormatoArchivo formato) {
        GeneradorExportacion gen = generador(formato);
        byte[] contenido = gen.estadoResultados(reportes.obtenerEstadoResultados(desde, hasta, nivel, incluirCeros));
        return archivo(contenido, "estado-resultados_" + desde + "_" + hasta, gen);
    }

    /**
     * Exporta el Estado de Situación Financiera (ADR-016, ADR-037).
     *
     * @param fechaCorte fecha de corte
     * @param nivel nivel de la jerarquía pedido
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @param formato formato pedido
     * @return el archivo generado
     */
    public ArchivoExportado estadoSituacionFinanciera(
            LocalDate fechaCorte, int nivel, boolean incluirCeros, FormatoArchivo formato) {
        GeneradorExportacion gen = generador(formato);
        byte[] contenido = gen.estadoSituacionFinanciera(
                reportes.obtenerEstadoSituacionFinanciera(fechaCorte, nivel, incluirCeros));
        return archivo(contenido, "estado-situacion-financiera_" + fechaCorte, gen);
    }

    /**
     * Exporta el resumen de IVA del mes (ADR-038 §8).
     *
     * @param anio año del período
     * @param mes mes del período (1 a 12)
     * @param formato formato pedido
     * @return el archivo generado
     */
    public ArchivoExportado resumenIva(int anio, int mes, FormatoArchivo formato) {
        GeneradorExportacion gen = generador(formato);
        byte[] contenido = gen.resumenIva(reportes.obtenerResumenIva(anio, mes));
        return archivo(contenido, "resumen-iva_" + anio + "-" + String.format("%02d", mes), gen);
    }

    /** El generador del formato pedido; el contrato ya limita {@code formato} a los tres valores soportados. */
    private GeneradorExportacion generador(FormatoArchivo formato) {
        return generadores.get(formato);
    }

    /** Arma el resultado con el nombre de archivo en minúsculas y sin espacios (CLAUDE.md 12.5). */
    private static ArchivoExportado archivo(byte[] contenido, String base, GeneradorExportacion gen) {
        String nombre = (base + "." + gen.extension())
                .toLowerCase(java.util.Locale.ROOT)
                .replace(' ', '-');
        return new ArchivoExportado(contenido, nombre, gen.tipoContenido());
    }
}
