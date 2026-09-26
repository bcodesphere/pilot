package com.bcodesphere.pilot.plataforma.dominio;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formato de la API key {@code pk_xxxxxxxx.secreto} (CLAUDE.md 9.2, 12.2 y 14.1) y su generación.
 *
 * <ul>
 *   <li>Prefijo: {@code pk_} + 8 caracteres {@code [a-z0-9]}; es la parte visible que identifica la clave y por la que
 *       se busca sin conocer la empresa (ADR-026). Cumple el {@code CHECK} de V7.
 *   <li>Secreto: 32 bytes aleatorios (256 bits) en Base64URL sin relleno, 43 caracteres; nunca contiene {@code .}, así
 *       que el primer punto separa siempre prefijo y secreto.
 * </ul>
 *
 * Es dominio puro: no depende de Spring. La aleatoriedad usa {@link SecureRandom}.
 */
public final class ClaveApi {

    /** Longitud de la parte aleatoria del prefijo (después de {@code pk_}). */
    static final int LARGO_PREFIJO = 8;

    /** Bytes aleatorios del secreto: 256 bits, más que suficiente contra fuerza bruta. */
    static final int BYTES_SECRETO = 32;

    /** Alfabeto del prefijo; coincide con {@code ^pk_[a-z0-9]{4,13}$} de V7. */
    private static final String ALFABETO = "abcdefghijklmnopqrstuvwxyz0123456789";

    /** Clave completa presentada por el cliente: prefijo, punto y secreto Base64URL (se acota el largo del secreto). */
    private static final Pattern FORMATO =
            Pattern.compile("^(pk_[a-z0-9]{" + LARGO_PREFIJO + "})\\.([A-Za-z0-9_-]{43,128})$");

    /** Fuente de aleatoriedad criptográfica; es segura entre hilos. */
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private ClaveApi() {}

    /**
     * Clave recién generada. Su {@link #toString()} oculta el secreto para que no llegue a logs ni a excepciones por
     * accidente (CLAUDE.md 14.1).
     *
     * @param prefijo parte visible {@code pk_xxxxxxxx}
     * @param secreto parte secreta en Base64URL; se muestra una sola vez
     */
    public record Generada(String prefijo, String secreto) {

        /**
         * Clave completa {@code prefijo.secreto}, la que recibe el usuario en el 201.
         *
         * @return la clave completa
         */
        public String completa() {
            return prefijo + "." + secreto;
        }

        @Override
        public String toString() {
            // El secreto nunca se imprime, ni siquiera en depuración
            return "ClaveApi.Generada[prefijo=" + prefijo + ", secreto=***]";
        }
    }

    /**
     * Credencial leída del header, ya separada en prefijo y secreto. Su {@link #toString()} oculta el secreto.
     *
     * @param prefijo parte visible
     * @param secreto parte secreta presentada por el cliente
     */
    public record Presentada(String prefijo, String secreto) {

        @Override
        public String toString() {
            return "ClaveApi.Presentada[prefijo=" + prefijo + ", secreto=***]";
        }
    }

    /**
     * Genera un prefijo y un secreto nuevos. El llamador reintenta con otro prefijo si choca con el {@code UNIQUE}.
     *
     * @return la clave generada
     */
    public static Generada generar() {
        // 1. Prefijo: 8 caracteres del alfabeto elegidos uniformemente (índice aleatorio acotado, sin sesgo de módulo)
        StringBuilder prefijo = new StringBuilder("pk_");
        for (int i = 0; i < LARGO_PREFIJO; i++) {
            prefijo.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        // 2. Secreto: 32 bytes aleatorios en Base64URL sin relleno (43 caracteres, sin punto)
        byte[] bytes = new byte[BYTES_SECRETO];
        ALEATORIO.nextBytes(bytes);
        String secreto = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Generada(prefijo.toString(), secreto);
    }

    /**
     * Separa la credencial {@code prefijo.secreto} del header. No distingue por qué un formato es inválido: el
     * llamador responde siempre el mismo 401.
     *
     * @param credencial valor tras {@code Bearer }; puede ser nulo
     * @return prefijo y secreto, o vacío si el formato no es el de una API key
     */
    public static Optional<Presentada> interpretar(String credencial) {
        if (credencial == null) {
            return Optional.empty();
        }
        Matcher m = FORMATO.matcher(credencial);
        return m.matches() ? Optional.of(new Presentada(m.group(1), m.group(2))) : Optional.empty();
    }
}
