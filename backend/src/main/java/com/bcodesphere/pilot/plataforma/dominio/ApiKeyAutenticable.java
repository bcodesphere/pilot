package com.bcodesphere.pilot.plataforma.dominio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lo que la función {@code api_key_por_prefijo} entrega para autenticar una petición (ADR-026): incluye el hash del
 * secreto, así que jamás sale de la capa de aplicación ni se registra. Su {@link #toString()} lo oculta.
 *
 * @param id identificador de la clave
 * @param empresaId empresa a la que pertenece; de ella sale el contexto de la petición
 * @param hashSecreto Argon2id del secreto
 * @param alcances códigos de alcance concedidos
 * @param expiraEn vencimiento, o nulo
 * @param revocadaEn revocación, o nulo (las revocadas también se devuelven: la aplicación decide el 401)
 */
public record ApiKeyAutenticable(
        UUID id, UUID empresaId, String hashSecreto, List<String> alcances, Instant expiraEn, Instant revocadaEn) {

    /** Copia defensiva de los alcances: el registro es inmutable y no expone la lista recibida. */
    public ApiKeyAutenticable {
        alcances = List.copyOf(alcances);
    }

    @Override
    public String toString() {
        return "ApiKeyAutenticable[id=" + id + ", empresaId=" + empresaId + ", hashSecreto=***]";
    }
}
