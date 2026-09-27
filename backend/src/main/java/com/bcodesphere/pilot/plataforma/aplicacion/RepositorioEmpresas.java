package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.dominio.EspacioTrabajo;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import java.util.Optional;
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

    /**
     * Lee el espacio de trabajo de la empresa activa (solo id, tipo, nombre, estado y versión).
     *
     * @param id empresa activa (debe coincidir con {@code ContextoEmpresa.empresaRequerida()}; el adaptador lo exige
     *     como defensa en profundidad además de RLS, CLAUDE.md 1.1.3)
     * @return el espacio, o vacío si no existe
     * @throws IllegalStateException si {@code id} no es la empresa activa del contexto
     */
    Optional<EspacioTrabajo> buscar(UUID id);

    /**
     * Cambia el nombre con concurrencia optimista: solo actualiza si la versión guardada es la esperada, la
     * incrementa y fija quién y cuándo. No toca ninguna otra columna.
     *
     * @param id empresa activa (mismo requisito que {@link #buscar(UUID)})
     * @param nombre nombre nuevo, ya normalizado
     * @param versionEsperada versión que el cliente leyó
     * @return {@code true} si actualizó la fila; {@code false} si la versión ya no coincide (carrera o dato viejo)
     * @throws IllegalStateException si {@code id} no es la empresa activa del contexto
     */
    boolean actualizarNombre(UUID id, String nombre, long versionEsperada);
}
