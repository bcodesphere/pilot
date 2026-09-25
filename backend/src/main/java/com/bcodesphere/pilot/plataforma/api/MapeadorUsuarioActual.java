package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.Membresia;
import com.bcodesphere.pilot.compartido.api.contrato.Rol;
import com.bcodesphere.pilot.compartido.api.contrato.TipoEmpresa;
import com.bcodesphere.pilot.compartido.api.contrato.UsuarioActual;
import com.bcodesphere.pilot.plataforma.aplicacion.ConsultarUsuarioActual;
import com.bcodesphere.pilot.plataforma.dominio.MembresiaUsuario;
import com.bcodesphere.pilot.plataforma.dominio.Usuario;
import java.util.List;

/**
 * Convierte el resultado del caso de uso en el DTO {@code UsuarioActual} del contrato. Solo copia campos: sin lógica de
 * negocio (CLAUDE.md 8.3).
 */
final class MapeadorUsuarioActual {

    private MapeadorUsuarioActual() {}

    /**
     * Arma la respuesta de {@code GET /me} y {@code PATCH /me}.
     *
     * @param vista usuario y membresías activas
     * @return DTO del contrato
     */
    static UsuarioActual aDto(ConsultarUsuarioActual.Vista vista) {
        Usuario usuario = vista.usuario();
        List<Membresia> membresias =
                vista.membresias().stream().map(MapeadorUsuarioActual::aDto).toList();
        return new UsuarioActual(
                usuario.id(),
                usuario.correo(),
                usuario.nombre(),
                usuario.telefono().valor(),
                usuario.recomendacionesVigentes(),
                membresias);
    }

    /** Membresía de dominio a DTO; el rol y el tipo se convierten a los enums del contrato por su valor. */
    private static Membresia aDto(MembresiaUsuario m) {
        return new Membresia(
                m.empresaId(),
                m.nombreEmpresa(),
                TipoEmpresa.fromValue(m.tipoEmpresa()),
                Rol.fromValue(m.rol().codigo()));
    }
}
