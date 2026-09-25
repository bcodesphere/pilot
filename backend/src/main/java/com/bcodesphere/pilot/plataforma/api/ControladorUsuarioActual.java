package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.ActualizacionUsuarioActual;
import com.bcodesphere.pilot.compartido.api.contrato.UsuarioActual;
import com.bcodesphere.pilot.compartido.api.contrato.UsuarioActualApi;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.aplicacion.ConsultarUsuarioActual;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de {@code GET /me} y {@code PATCH /me} (interfaz generada desde el contrato, ADR-003). No lleva lógica:
 * toma el usuario de la petición, llama al caso de uso y mapea la respuesta (CLAUDE.md 8.3). El usuario ya lo resolvió
 * el {@link FiltroEmpresaActiva}, que deja su UUID como nombre de la autenticación.
 */
@RestController
class ControladorUsuarioActual implements UsuarioActualApi {

    private final ConsultarUsuarioActual usuarioActual;

    /**
     * Crea el controlador.
     *
     * @param usuarioActual caso de uso de {@code /me}
     */
    ControladorUsuarioActual(ConsultarUsuarioActual usuarioActual) {
        this.usuarioActual = usuarioActual;
    }

    @Override
    public ResponseEntity<UsuarioActual> obtenerUsuarioActual(String xRequestId) {
        return ResponseEntity.ok(MapeadorUsuarioActual.aDto(usuarioActual.obtener(usuarioAutenticado())));
    }

    @Override
    public ResponseEntity<UsuarioActual> actualizarUsuarioActual(
            ActualizacionUsuarioActual actualizacionUsuarioActual, String xRequestId) {
        return ResponseEntity.ok(MapeadorUsuarioActual.aDto(usuarioActual.actualizarConsentimiento(
                usuarioAutenticado(), actualizacionUsuarioActual.getRecomendacionesCorreo())));
    }

    /** UUID del usuario que dejó el filtro de empresa activa; sin él la petición no está identificada (401). */
    private static UUID usuarioAutenticado() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null) {
            throw ExcepcionPlataforma.noAutenticado("No fue posible identificar al usuario");
        }
        try {
            return UUID.fromString(autenticacion.getName());
        } catch (IllegalArgumentException e) {
            throw ExcepcionPlataforma.noAutenticado("No fue posible identificar al usuario");
        }
    }
}
