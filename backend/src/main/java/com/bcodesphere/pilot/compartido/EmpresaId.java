package com.bcodesphere.pilot.compartido;

import java.util.UUID;

/**
 * Identificador de empresa (tenant), tipado para no confundirlo con otros UUID (CLAUDE.md 4.5).
 *
 * @param valor UUID de la empresa
 */
public record EmpresaId(UUID valor) {

    /**
     * Valida que el UUID exista.
     *
     * @throws IllegalArgumentException si es nulo
     */
    public EmpresaId {
        if (valor == null) {
            throw new IllegalArgumentException("El id de empresa no puede ser nulo");
        }
    }

    /** Crea el identificador desde su forma textual (p. ej. el header {@code X-Empresa-Id}). */
    public static EmpresaId de(String texto) {
        return new EmpresaId(UUID.fromString(texto));
    }

    @Override
    public String toString() {
        return valor.toString();
    }
}
