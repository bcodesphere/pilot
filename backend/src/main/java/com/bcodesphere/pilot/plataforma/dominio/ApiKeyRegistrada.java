package com.bcodesphere.pilot.plataforma.dominio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API key tal como se lista o se devuelve al crearla: nunca lleva el secreto ni su hash (CLAUDE.md 14.1).
 *
 * @param id identificador (UUID v7)
 * @param nombre nombre descriptivo
 * @param prefijo parte visible {@code pk_xxxxxxxx}
 * @param alcances códigos de los alcances concedidos
 * @param expiraEn vencimiento, o nulo si no vence
 * @param revocadaEn revocación, o nulo si está vigente
 * @param ultimoUsoEn último uso, o nulo si nunca se usó
 * @param creadaEn momento de creación (UTC, a microsegundos)
 */
public record ApiKeyRegistrada(
        UUID id,
        String nombre,
        String prefijo,
        List<String> alcances,
        Instant expiraEn,
        Instant revocadaEn,
        Instant ultimoUsoEn,
        Instant creadaEn) {

    /** Copia defensiva de los alcances: el registro es inmutable y no expone la lista recibida. */
    public ApiKeyRegistrada {
        alcances = List.copyOf(alcances);
    }
}
