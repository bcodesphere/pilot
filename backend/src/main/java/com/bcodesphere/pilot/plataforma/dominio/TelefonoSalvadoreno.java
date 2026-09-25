package com.bcodesphere.pilot.plataforma.dominio;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Teléfono de El Salvador normalizado a {@code +503XXXXXXXX} (ADR-028). Objeto de valor de dominio, sin Spring.
 * El realm de Keycloak acepta 8 dígitos con o sin el prefijo {@code +503}; la base de datos exige la forma completa
 * (restricción {@code ck_usuario_telefono} de V4), así que la normalización ocurre aquí.
 *
 * @param valor teléfono en la forma {@code +503} seguido de 8 dígitos
 */
public record TelefonoSalvadoreno(String valor) {

    /** Forma que acepta el realm: 8 dígitos con prefijo {@code +503} opcional (infra/keycloak/README.md). */
    private static final Pattern ENTRADA = Pattern.compile("^(\\+503)?([0-9]{8})$");

    /** Prefijo de El Salvador. */
    private static final String PREFIJO = "+503";

    /**
     * Comprueba que el valor ya esté normalizado.
     *
     * @throws IllegalArgumentException si no es {@code +503} seguido de 8 dígitos
     */
    public TelefonoSalvadoreno {
        if (valor == null || !ENTRADA.matcher(valor).matches() || !valor.startsWith(PREFIJO)) {
            throw new IllegalArgumentException("Teléfono no normalizado");
        }
    }

    /**
     * Normaliza el teléfono que llega en el token.
     *
     * @param texto teléfono con o sin {@code +503}; se ignoran espacios en los extremos
     * @return el teléfono normalizado, o vacío si el texto no tiene la forma esperada (o es nulo)
     */
    public static Optional<TelefonoSalvadoreno> de(String texto) {
        // 1. Nulo o vacío: el token no trae teléfono utilizable
        if (texto == null) {
            return Optional.empty();
        }
        // 2. Solo las dos formas del realm; cualquier otra (extranjero, con letras, con separadores) se rechaza
        Matcher m = ENTRADA.matcher(texto.strip());
        if (!m.matches()) {
            return Optional.empty();
        }
        // 3. Siempre se guarda con prefijo, para que la búsqueda y la restricción de la base coincidan
        return Optional.of(new TelefonoSalvadoreno(PREFIJO + m.group(2)));
    }
}
