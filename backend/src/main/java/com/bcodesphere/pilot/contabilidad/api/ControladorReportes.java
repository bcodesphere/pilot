package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.api.contrato.BalanzaComprobacion;
import com.bcodesphere.pilot.compartido.api.contrato.DiagnosticoMayorizacion;
import com.bcodesphere.pilot.compartido.api.contrato.EstadoResultados;
import com.bcodesphere.pilot.compartido.api.contrato.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.compartido.api.contrato.LibroMayor;
import com.bcodesphere.pilot.compartido.api.contrato.ReportesContablesApi;
import com.bcodesphere.pilot.compartido.api.contrato.ResumenIva;
import com.bcodesphere.pilot.contabilidad.aplicacion.ConsultarReportes;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de los reportes contables ({@code /contabilidad/mayor}, {@code /balanza}, {@code /estados/*},
 * {@code /reportes/iva} y {@code /diagnostico/mayorizacion}; CLAUDE.md 10.4, 10.5 y 13, ADR-038). No lleva lógica
 * de negocio: valida la entrada de forma (el contrato ya aplica los límites de {@code nivel}, {@code anio} y
 * {@code mes} con Bean Validation), llama al caso de uso de lectura y mapea la respuesta a los DTO del contrato.
 */
@RestController
class ControladorReportes implements ReportesContablesApi {

    private final ConsultarReportes consultar;

    /**
     * Crea el controlador.
     *
     * @param consultar casos de uso de lectura de los reportes
     */
    ControladorReportes(ConsultarReportes consultar) {
        this.consultar = consultar;
    }

    @Override
    public ResponseEntity<LibroMayor> obtenerLibroMayor(
            UUID xEmpresaId, UUID cuentaId, LocalDate desde, LocalDate hasta, String xRequestId) {
        return ResponseEntity.ok(MapeadorReportes.aDto(consultar.obtenerLibroMayor(cuentaId, desde, hasta)));
    }

    @Override
    public ResponseEntity<BalanzaComprobacion> obtenerBalanza(
            UUID xEmpresaId, LocalDate desde, LocalDate hasta, String xRequestId, Integer nivel) {
        return ResponseEntity.ok(MapeadorReportes.aDto(consultar.obtenerBalanza(desde, hasta, nivel)));
    }

    @Override
    public ResponseEntity<EstadoResultados> obtenerEstadoResultados(
            UUID xEmpresaId, LocalDate desde, LocalDate hasta, String xRequestId, Integer nivel, Boolean incluirCeros) {
        return ResponseEntity.ok(
                MapeadorReportes.aDto(consultar.obtenerEstadoResultados(desde, hasta, nivel, incluirCeros)));
    }

    @Override
    public ResponseEntity<EstadoSituacionFinanciera> obtenerEstadoSituacionFinanciera(
            UUID xEmpresaId, LocalDate fechaCorte, String xRequestId, Integer nivel, Boolean incluirCeros) {
        return ResponseEntity.ok(
                MapeadorReportes.aDto(consultar.obtenerEstadoSituacionFinanciera(fechaCorte, nivel, incluirCeros)));
    }

    @Override
    public ResponseEntity<ResumenIva> obtenerResumenIva(UUID xEmpresaId, Integer anio, Integer mes, String xRequestId) {
        return ResponseEntity.ok(MapeadorReportes.aDto(consultar.obtenerResumenIva(anio, mes)));
    }

    @Override
    public ResponseEntity<DiagnosticoMayorizacion> diagnosticarMayorizacion(UUID xEmpresaId, String xRequestId) {
        return ResponseEntity.ok(MapeadorReportes.aDto(consultar.diagnosticarMayorizacion()));
    }
}
