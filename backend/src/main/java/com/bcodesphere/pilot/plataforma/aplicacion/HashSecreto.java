package com.bcodesphere.pilot.plataforma.aplicacion;

/**
 * Puerto de salida: hash y verificación del secreto de una API key (CLAUDE.md 14.1). El adaptador usa Argon2id; el
 * caso de uso no conoce el algoritmo.
 */
public interface HashSecreto {

    /**
     * Calcula el hash del secreto, con sal aleatoria propia dentro del resultado.
     *
     * @param secreto secreto en claro
     * @return hash codificado (para {@code api_key.hash_secreto})
     */
    String hashear(String secreto);

    /**
     * Comprueba un secreto contra un hash guardado. Devuelve {@code false} (no lanza) si el hash no es válido.
     *
     * @param secreto secreto presentado
     * @param hash hash guardado
     * @return {@code true} si coinciden
     */
    boolean coincide(String secreto, String hash);
}
