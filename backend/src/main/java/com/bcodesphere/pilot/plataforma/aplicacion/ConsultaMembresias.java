package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.dominio.MembresiaUsuario;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: consultas de membresías previas a conocer la empresa. Se implementan con las funciones
 * {@code SECURITY DEFINER} {@code membresias_de_usuario} y {@code membresia_activa} y deben ejecutarse en el modo
 * «sin empresa» (ADR-026).
 */
public interface ConsultaMembresias {

    /**
     * Membresías activas de un usuario en empresas activas.
     *
     * @param usuarioId usuario
     * @return membresías ordenadas por nombre de empresa; lista vacía si no tiene
     */
    List<MembresiaUsuario> deUsuario(UUID usuarioId);

    /**
     * Rol del usuario en una empresa.
     *
     * @param usuarioId usuario
     * @param empresaId empresa
     * @return el rol si la membresía y la empresa están activas; vacío en cualquier otro caso
     */
    Optional<Rol> rolActivo(UUID usuarioId, UUID empresaId);
}
