package com.bcodesphere.pilot.plataforma.dominio;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Cursor opaco de la paginación de API keys (CLAUDE.md 13): la posición del último elemento entregado según el orden
 * {@code (creado_en DESC, id DESC)}. Viaja en Base64URL de {@code <microsegundos>|<uuid>}; PostgreSQL guarda
 * microsegundos, así que ese es el paso exacto para no repetir ni saltar filas.
 *
 * @param creadoEn instante de creación del último elemento de la página anterior (a microsegundos)
 * @param id identificador del último elemento (desempate)
 */
public record CursorApiKey(Instant creadoEn, UUID id) {

    /**
     * Codifica el cursor para enviarlo al cliente.
     *
     * @return texto opaco Base64URL sin relleno
     */
    public String codificar() {
        // 1. Microsegundos desde la época: sin pérdida frente a la columna timestamptz
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, creadoEn);
        String texto = micros + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodifica un cursor recibido.
     *
     * @param cursor texto opaco del cliente
     * @return el cursor
     * @throws ExcepcionValidacion 422 {@code PLT-002} si el texto no es un cursor válido (alterado o inventado)
     */
    public static CursorApiKey decodificar(String cursor) {
        try {
            String texto = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int barra = texto.indexOf('|');
            long micros = Long.parseLong(texto.substring(0, barra));
            UUID id = UUID.fromString(texto.substring(barra + 1));
            return new CursorApiKey(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id);
        } catch (RuntimeException e) {
            // Base64 inválido, sin separador, número o UUID mal formado: un mismo error para todos los casos
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("cursor", "El cursor no es válido")));
        }
    }
}
