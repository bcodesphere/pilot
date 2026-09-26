package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.AplicacionCatalogo;
import com.bcodesphere.pilot.compartido.api.contrato.AplicacionesApi;
import com.bcodesphere.pilot.plataforma.aplicacion.GestionarAplicaciones;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador del catálogo de apps: {@code GET /aplicaciones} y {@code POST /aplicaciones/{codigo}/instalacion}
 * (interfaz generada desde el contrato, ADR-003). No lleva lógica: llama al caso de uso y mapea la respuesta; el rol
 * mínimo lo exige el caso de uso (CLAUDE.md 8.3, ADR-030).
 */
@RestController
class ControladorAplicaciones implements AplicacionesApi {

    private final GestionarAplicaciones aplicaciones;

    /**
     * Crea el controlador.
     *
     * @param aplicaciones caso de uso del catálogo y la instalación
     */
    ControladorAplicaciones(GestionarAplicaciones aplicaciones) {
        this.aplicaciones = aplicaciones;
    }

    @Override
    public ResponseEntity<List<AplicacionCatalogo>> listarAplicaciones(UUID xEmpresaId, String xRequestId) {
        return ResponseEntity.ok(
                aplicaciones.listar().stream().map(MapeadorAplicaciones::aDto).toList());
    }

    @Override
    public ResponseEntity<AplicacionCatalogo> instalarAplicacion(UUID xEmpresaId, String codigo, String xRequestId) {
        GestionarAplicaciones.Resultado resultado = aplicaciones.instalar(codigo);
        // 201 si esta llamada la instaló; 200 si ya estaba instalada (contrato de la operación)
        HttpStatus estado = resultado.instaladaAhora() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(MapeadorAplicaciones.aDto(resultado.aplicacion()));
    }
}
