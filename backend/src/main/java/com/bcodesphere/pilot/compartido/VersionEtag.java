package com.bcodesphere.pilot.compartido;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traducción entre la columna {@code version} y el {@code ETag}/{@code If-Match} de la API (CLAUDE.md 8.3): la versión
 * entre comillas dobles, p. ej. {@code "3"}. Solo se aceptan ETags fuertes con un entero no negativo. Vive en
 * {@code compartido} para que plataforma y contabilidad la usen sin depender una de la otra (F2-03).
 */
public final class VersionEtag {

    /** Comillas dobles, un entero sin signo y comillas de cierre. */
    private static final Pattern FORMATO = Pattern.compile("^\"(\\d{1,18})\"$");

    private VersionEtag() {}

    /**
     * Da formato de ETag a una versión.
     *
     * @param version versión de la fila
     * @return la versión entre comillas dobles
     */
    public static String formatear(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Lee el valor de {@code If-Match}.
     *
     * @param ifMatch valor del header
     * @return la versión que el cliente dice haber leído
     * @throws ExcepcionVersionNoCoincide 412 {@code PLT-016} si el valor no tiene el formato de un ETag de versión
     */
    public static long parsear(String ifMatch) {
        // 1. Un valor ausente o mal formado (sin comillas, débil "W/", "*", lista) no puede coincidir con ninguna
        // versión
        Matcher m = ifMatch == null ? null : FORMATO.matcher(ifMatch.strip());
        if (m == null || !m.matches()) {
            throw new ExcepcionVersionNoCoincide();
        }
        return Long.parseLong(m.group(1));
    }
}
