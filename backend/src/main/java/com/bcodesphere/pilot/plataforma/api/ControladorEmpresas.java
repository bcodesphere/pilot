package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.ActualizacionEmpresa;
import com.bcodesphere.pilot.compartido.api.contrato.Empresa;
import com.bcodesphere.pilot.compartido.api.contrato.EmpresasApi;
import com.bcodesphere.pilot.plataforma.aplicacion.GestionarEspacioTrabajo;
import com.bcodesphere.pilot.plataforma.dominio.EspacioTrabajo;
import com.bcodesphere.pilot.plataforma.dominio.VersionEtag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de {@code GET} y {@code PATCH /empresas/{empresaId}}: el nombre del espacio de trabajo (ADR-032).
 * No lleva lógica: llama al caso de uso y mapea; la versión viaja como {@code ETag} en ambas respuestas.
 */
@RestController
class ControladorEmpresas implements EmpresasApi {

    private final GestionarEspacioTrabajo espacio;

    /**
     * Crea el controlador.
     *
     * @param espacio caso de uso del espacio de trabajo
     */
    ControladorEmpresas(GestionarEspacioTrabajo espacio) {
        this.espacio = espacio;
    }

    @Override
    public ResponseEntity<Empresa> obtenerEmpresa(UUID xEmpresaId, UUID empresaId, String xRequestId) {
        return respuesta(espacio.obtener(empresaId));
    }

    @Override
    public ResponseEntity<Empresa> actualizarEmpresa(
            UUID xEmpresaId,
            UUID empresaId,
            String ifMatch,
            ActualizacionEmpresa actualizacionEmpresa,
            String xRequestId) {
        return respuesta(espacio.renombrar(empresaId, ifMatch, actualizacionEmpresa.getNombre()));
    }

    /** Arma la respuesta 200 con el DTO y el ETag de la versión. */
    private static ResponseEntity<Empresa> respuesta(EspacioTrabajo e) {
        return ResponseEntity.ok().eTag(VersionEtag.formatear(e.version())).body(MapeadorEmpresa.aDto(e));
    }
}
