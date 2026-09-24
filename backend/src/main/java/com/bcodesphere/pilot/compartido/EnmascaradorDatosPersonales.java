package com.bcodesphere.pilot.compartido;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enmascara datos personales (DUI, NIT, correo, teléfono) en textos de log (CLAUDE.md 1.1.12).
 * Los formatos de DUI y NIT están marcados {@code [VERIFICAR]} (CLAUDE.md 9.2): se aceptan con y sin guiones
 * y, por conservador, se enmascara todo lo que tenga esa forma aunque no sea un documento real.
 */
public final class EnmascaradorDatosPersonales {

    /** Dígitos que se conservan al final de cada dato. */
    private static final int CONSERVAR = 2;

    /** NIT: 14 dígitos, con guiones 4-6-3-1 o sin ellos. */
    private static final Pattern NIT = Pattern.compile("(?<![\\d-])\\d{4}-?\\d{6}-?\\d{3}-?\\d(?![\\d-])");

    /** DUI: 9 dígitos, con guion antes del verificador o sin él. */
    private static final Pattern DUI = Pattern.compile("(?<![\\d-])\\d{8}-?\\d(?![\\d-])");

    /** Teléfono: 8 dígitos que empiezan en 2, 6 o 7, con prefijo +503 y separador opcionales. */
    private static final Pattern TELEFONO =
            Pattern.compile("(?<![\\d-])(?:\\+?503[ -]?)?[267]\\d{3}[ -]?\\d{4}(?![\\d-])");

    /** Correo: parte local y dominio. */
    private static final Pattern CORREO = Pattern.compile("([A-Za-z0-9._%+-]+)@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private EnmascaradorDatosPersonales() {}

    /**
     * Enmascara todos los datos personales reconocibles.
     *
     * @param texto texto de log, o nulo
     * @return el texto con los datos enmascarados (nulo si la entrada era nula)
     */
    public static String enmascarar(String texto) {
        if (texto == null) {
            return null;
        }
        // 1. Orden importa: NIT (14 dígitos) antes que DUI (9) y teléfono (8) para no cortarlo a pedazos
        String resultado = reemplazarDigitos(NIT, texto);
        resultado = reemplazarDigitos(DUI, resultado);
        resultado = reemplazarDigitos(TELEFONO, resultado);
        // 2. Correo: se oculta la parte local y queda el dominio, que no es dato personal
        Matcher correo = CORREO.matcher(resultado);
        StringBuilder salida = new StringBuilder();
        while (correo.find()) {
            String local = correo.group(1);
            String oculto = "*".repeat(Math.max(0, local.length() - CONSERVAR))
                    + local.substring(Math.max(0, local.length() - CONSERVAR));
            correo.appendReplacement(salida, Matcher.quoteReplacement(oculto + "@" + correo.group(2)));
        }
        correo.appendTail(salida);
        return salida.toString();
    }

    /** Sustituye por {@code *} cada dígito de la coincidencia salvo los últimos {@link #CONSERVAR}; conserva separadores. */
    private static String reemplazarDigitos(Pattern patron, String texto) {
        Matcher m = patron.matcher(texto);
        StringBuilder salida = new StringBuilder();
        while (m.find()) {
            String hallado = m.group();
            // 1. Cuenta los dígitos totales para saber cuáles son los últimos
            long total = hallado.chars().filter(Character::isDigit).count();
            long vistos = 0;
            StringBuilder oculto = new StringBuilder();
            for (char c : hallado.toCharArray()) {
                if (Character.isDigit(c)) {
                    vistos++;
                    oculto.append(vistos > total - CONSERVAR ? c : '*');
                } else {
                    oculto.append(c);
                }
            }
            m.appendReplacement(salida, Matcher.quoteReplacement(oculto.toString()));
        }
        m.appendTail(salida);
        return salida.toString();
    }
}
