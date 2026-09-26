package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.plataforma.dominio.AplicacionEmpresa;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: catálogo de apps visto desde la empresa activa e instalación (ADR-030). Se usa dentro de una
 * transacción con el contexto de la empresa, porque {@code empresa_aplicacion} tiene RLS.
 */
public interface RepositorioAplicaciones {

    /**
     * Lista el catálogo disponible con el estado de cada app para la empresa, ordenado por {@code orden}. Una sola
     * consulta (sin N+1).
     *
     * @param empresaId empresa activa
     * @return apps con {@code disponible = true}
     */
    List<AplicacionEmpresa> listar(UUID empresaId);

    /**
     * Busca una app disponible del catálogo con su estado para la empresa.
     *
     * @param codigo código de la app
     * @param empresaId empresa activa
     * @return la app, o vacío si no existe o no está disponible en el catálogo
     */
    Optional<AplicacionEmpresa> buscar(String codigo, UUID empresaId);

    /**
     * Registra la instalación si aún no existe ({@code ON CONFLICT DO NOTHING}), a nombre del usuario del contexto.
     *
     * @param empresaId empresa activa
     * @param codigo código de la app
     * @return {@code true} si esta llamada insertó la fila; {@code false} si ya estaba instalada (incluida una
     *     instalación concurrente que se confirmó antes)
     */
    boolean instalar(UUID empresaId, String codigo);
}
