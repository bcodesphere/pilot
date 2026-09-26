package com.bcodesphere.pilot.plataforma.dominio;

/**
 * Estado de una app del catálogo para la empresa activa (ADR-030, CLAUDE.md 4.4). Es una regla de dominio pura: se
 * calcula a partir de la edición de la app y de si la empresa ya la tiene instalada.
 */
public enum EstadoAplicacionEmpresa {
    /** La empresa la tiene instalada. */
    INSTALADA,
    /** Es comunitaria y se puede instalar. */
    DISPONIBLE,
    /** Es de la edición Enterprise: se muestra bloqueada y no se puede instalar en 1.0. */
    BLOQUEADA_ENTERPRISE;

    /**
     * Calcula el estado de una app para una empresa.
     *
     * @param edicion edición de la app en el catálogo
     * @param instalada si existe la fila en {@code empresa_aplicacion} para la empresa
     * @return {@code INSTALADA} si está instalada; si no, {@code BLOQUEADA_ENTERPRISE} para las Enterprise y
     *     {@code DISPONIBLE} para el resto
     */
    public static EstadoAplicacionEmpresa calcular(EdicionAplicacion edicion, boolean instalada) {
        // 1. Lo instalado manda: una app instalada se muestra como tal
        if (instalada) {
            return INSTALADA;
        }
        // 2. Sin instalar, la edición decide si se puede instalar o está bloqueada
        return edicion == EdicionAplicacion.ENTERPRISE ? BLOQUEADA_ENTERPRISE : DISPONIBLE;
    }
}
