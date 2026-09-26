package com.bcodesphere.pilot.plataforma.dominio;

import java.util.Arrays;
import java.util.Optional;

/**
 * Alcance que puede otorgar una API key. En 1.0 solo existe el del webhook de operaciones de n8n (CLAUDE.md 12.1);
 * un alcance nuevo requiere ADR y una migración que amplíe el {@code CHECK} de {@code api_key.alcances} (V7).
 */
public enum AlcanceClave {

    /** Enviar operaciones de negocio a {@code POST /integraciones/n8n/operaciones}. */
    INTEGRACION_OPERACIONES("integracion:operaciones");

    /** Prefijo de las autoridades de Spring Security que se derivan de un alcance. */
    private static final String PREFIJO_AUTORIDAD = "SCOPE_";

    private final String codigo;

    AlcanceClave(String codigo) {
        this.codigo = codigo;
    }

    /**
     * Código tal como se guarda en la base y viaja en la API.
     *
     * @return por ejemplo {@code integracion:operaciones}
     */
    public String codigo() {
        return codigo;
    }

    /**
     * Autoridad de Spring Security que representa el alcance, para {@code hasAuthority(...)}.
     *
     * @return por ejemplo {@code SCOPE_integracion:operaciones}
     */
    public String autoridad() {
        return PREFIJO_AUTORIDAD + codigo;
    }

    /**
     * Busca un alcance por su código.
     *
     * @param codigo código guardado o recibido
     * @return el alcance, o vacío si el código no existe
     */
    public static Optional<AlcanceClave> deCodigo(String codigo) {
        return Arrays.stream(values()).filter(a -> a.codigo.equals(codigo)).findFirst();
    }
}
