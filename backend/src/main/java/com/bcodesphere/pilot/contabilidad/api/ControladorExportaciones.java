package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.api.contrato.EstadoAsiento;
import com.bcodesphere.pilot.compartido.api.contrato.ExportacionesContablesApi;
import com.bcodesphere.pilot.compartido.api.contrato.FormatoExportacion;
import com.bcodesphere.pilot.compartido.api.contrato.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.aplicacion.ArchivoExportado;
import com.bcodesphere.pilot.contabilidad.aplicacion.ExportarReportes;
import com.bcodesphere.pilot.contabilidad.aplicacion.FiltroAsientos;
import com.bcodesphere.pilot.contabilidad.dominio.exportacion.FormatoArchivo;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de las seis exportaciones de reportes contables a PDF, XLSX y CSV ({@code …/exportacion}, CLAUDE.md
 * 10.5 y 13, ADR-038). No lleva lógica de negocio: convierte el DTO de entrada a la solicitud del dominio (igual
 * que {@link ControladorAsientos} y {@link ControladorReportes}), llama a {@link ExportarReportes} y responde el
 * archivo con {@code Content-Disposition: attachment} y el tipo de contenido exacto del formato pedido.
 */
@RestController
class ControladorExportaciones implements ExportacionesContablesApi {

    private final ExportarReportes exportar;

    /**
     * Crea el controlador.
     *
     * @param exportar casos de uso de exportación de reportes
     */
    ControladorExportaciones(ExportarReportes exportar) {
        this.exportar = exportar;
    }

    @Override
    public ResponseEntity<Resource> exportarLibroDiario(
            UUID xEmpresaId,
            FormatoExportacion formato,
            LocalDate desde,
            LocalDate hasta,
            String xRequestId,
            Integer anio,
            Long numero,
            OrigenAsiento origen,
            EstadoAsiento estado,
            UUID cuentaId) {
        FiltroAsientos filtro = new FiltroAsientos(
                desde,
                hasta,
                anio,
                numero,
                origen == null
                        ? null
                        : com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento.valueOf(origen.getValue()),
                estado == null
                        ? null
                        : com.bcodesphere.pilot.contabilidad.dominio.asiento.EstadoAsiento.valueOf(estado.getValue()),
                cuentaId);
        return responder(exportar.libroDiario(filtro, aDominio(formato)));
    }

    @Override
    public ResponseEntity<Resource> exportarLibroMayor(
            UUID xEmpresaId,
            FormatoExportacion formato,
            UUID cuentaId,
            LocalDate desde,
            LocalDate hasta,
            String xRequestId) {
        return responder(exportar.libroMayor(cuentaId, desde, hasta, aDominio(formato)));
    }

    @Override
    public ResponseEntity<Resource> exportarBalanza(
            UUID xEmpresaId,
            FormatoExportacion formato,
            LocalDate desde,
            LocalDate hasta,
            String xRequestId,
            Integer nivel) {
        return responder(exportar.balanza(desde, hasta, nivel, aDominio(formato)));
    }

    @Override
    public ResponseEntity<Resource> exportarEstadoResultados(
            UUID xEmpresaId,
            FormatoExportacion formato,
            LocalDate desde,
            LocalDate hasta,
            String xRequestId,
            Integer nivel,
            Boolean incluirCeros) {
        return responder(exportar.estadoResultados(desde, hasta, nivel, incluirCeros, aDominio(formato)));
    }

    @Override
    public ResponseEntity<Resource> exportarEstadoSituacionFinanciera(
            UUID xEmpresaId,
            FormatoExportacion formato,
            LocalDate fechaCorte,
            String xRequestId,
            Integer nivel,
            Boolean incluirCeros) {
        return responder(exportar.estadoSituacionFinanciera(fechaCorte, nivel, incluirCeros, aDominio(formato)));
    }

    @Override
    public ResponseEntity<Resource> exportarResumenIva(
            UUID xEmpresaId, FormatoExportacion formato, Integer anio, Integer mes, String xRequestId) {
        return responder(exportar.resumenIva(anio, mes, aDominio(formato)));
    }

    /** El enum del contrato y el del dominio comparten valores; se convierte por nombre (CLAUDE.md 8.3). */
    private static FormatoArchivo aDominio(FormatoExportacion formato) {
        return FormatoArchivo.valueOf(formato.name());
    }

    /**
     * Arma la respuesta binaria: el archivo como recurso, con su tipo de contenido y su nombre para descargar. El
     * nombre siempre es ASCII (minúsculas, dígitos, guiones y puntos, CLAUDE.md 12.5), así que no hace falta el
     * parámetro {@code filename*} de RFC 5987.
     */
    private static ResponseEntity<Resource> responder(ArchivoExportado archivo) {
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(archivo.nombreArchivo())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(new ByteArrayResource(archivo.contenido()));
    }
}
