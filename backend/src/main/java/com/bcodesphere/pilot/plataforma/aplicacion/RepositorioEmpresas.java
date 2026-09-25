package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.dominio.Rol;
import java.util.UUID;

/**
 * Puerto de salida: alta de la empresa personal y de su membresía (ADR-029). Se usa dentro de una transacción con el
 * contexto de la empresa nueva, porque la política RLS de {@code empresa} filtra por {@code id}.
 */
public interface RepositorioEmpresas {

    /**
     * Crea la empresa PERSONAL de un usuario, sin NIT ni NRC.
     *
     * @param id UUID v7 de la empresa (debe ser el que está fijado en el contexto)
     * @param propietarioId usuario dueño
     * @param nombre nombre de la empresa (el del usuario)
     * @throws org.springframework.dao.DataIntegrityViolationException si el usuario ya tiene empresa personal
     */
    void crearPersonal(UUID id, UUID propietarioId, String nombre);

    /**
     * Crea una membresía activa.
     *
     * @param empresaId empresa
     * @param usuarioId usuario
     * @param rol rol asignado
     */
    void crearMembresia(UUID empresaId, UUID usuarioId, Rol rol);
}
