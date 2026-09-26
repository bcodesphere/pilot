package com.bcodesphere.pilot.plataforma.dominio;

import java.util.Optional;

/** Edición a la que pertenece una app del catálogo (columna {@code aplicacion.edicion}, ADR-030 y ADR-031). */
public enum EdicionAplicacion {
    /** Instalable en la versión abierta (plan Gratuito). */
    COMUNITARIA,
    /** Visible pero bloqueada en 1.0 (plan Enterprise). */
    ENTERPRISE;

    /**
     * Convierte el valor guardado en la base de datos.
     *
     * @param codigo valor de {@code aplicacion.edicion}
     * @return la edición, o vacío si el valor no corresponde a ninguna
     */
    public static Optional<EdicionAplicacion> deCodigo(String codigo) {
        for (EdicionAplicacion edicion : values()) {
            if (edicion.name().equals(codigo)) {
                return Optional.of(edicion);
            }
        }
        return Optional.empty();
    }
}
