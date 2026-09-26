package com.bcodesphere.pilot.plataforma.dominio;

import java.time.Instant;

/**
 * Regla de vigencia de una API key (CLAUDE.md 14.1, plan F1): una clave revocada o vencida no autentica (401).
 * Dominio puro para poder probarla sin base de datos.
 */
public final class VigenciaClave {

    private VigenciaClave() {}

    /**
     * Indica si la clave puede autenticar en el instante dado.
     *
     * @param revocadaEn momento de la revocación; nulo si sigue vigente
     * @param expiraEn vencimiento; nulo si no vence
     * @param ahora instante de la evaluación
     * @return {@code true} si no está revocada y no venció ({@code expira_en <= ahora} ya es vencida)
     */
    public static boolean estaVigente(Instant revocadaEn, Instant expiraEn, Instant ahora) {
        // 1. Revocada: definitiva, sin importar el vencimiento
        if (revocadaEn != null) {
            return false;
        }
        // 2. Vencida cuando expira_en <= ahora (el instante exacto de expiración ya no sirve)
        return expiraEn == null || expiraEn.isAfter(ahora);
    }
}
