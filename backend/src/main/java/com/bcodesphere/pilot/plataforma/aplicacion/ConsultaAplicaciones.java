package com.bcodesphere.pilot.plataforma.aplicacion;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de salida: catálogo de apps y su instalación por empresa (ADR-021, ADR-030). Se usa con el contexto de la
 * empresa activa, porque {@code empresa_aplicacion} tiene RLS.
 */
public interface ConsultaAplicaciones {

    /**
     * Indica si un código pertenece al catálogo y, si es así, si la empresa la tiene instalada.
     *
     * @param codigo primer segmento de la ruta después de {@code /api/v1/}
     * @param empresaId empresa activa
     * @return vacío si el código no es una app del catálogo; {@code true} si está instalada; {@code false} si no
     */
    Optional<Boolean> estaInstalada(String codigo, UUID empresaId);

    /**
     * Indica si un código es el de una app del catálogo. Lee solo la tabla global {@code aplicacion}, así que no
     * necesita empresa (se usa en el modo «sin empresa», ADR-026).
     *
     * @param codigo primer segmento de la ruta después de {@code /api/v1/}
     * @return {@code true} si el código está en el catálogo
     */
    boolean existeEnCatalogo(String codigo);
}
