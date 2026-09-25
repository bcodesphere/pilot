package com.bcodesphere.pilot.plataforma.dominio;

import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import java.util.Locale;
import java.util.Map;

/**
 * Datos de identidad que Pilot toma del token de acceso de Keycloak (ADR-028): identificador, correo verificado,
 * nombre, teléfono y, solo para el alta, el consentimiento inicial de recomendaciones. Objeto de valor de dominio,
 * sin Spring: recibe los claims como un mapa.
 *
 * @param sub claim {@code sub}; enlaza la sesión con el usuario
 * @param correo correo en minúsculas
 * @param nombre nombre completo (claim {@code name}; si falta, el correo)
 * @param telefono teléfono normalizado
 * @param recomendacionesAceptadas casilla del registro; solo se usa en el alta (después la fuente es Pilot)
 */
public record DatosIdentidad(
        String sub, String correo, String nombre, TelefonoSalvadoreno telefono, boolean recomendacionesAceptadas) {

    /** Largo máximo de {@code usuario.correo} (V4). */
    private static final int MAX_CORREO = 254;

    /** Largo máximo de {@code usuario.nombre} (V4). */
    private static final int MAX_NOMBRE = 200;

    /** Largo máximo de {@code usuario.sub_keycloak} (V4). */
    private static final int MAX_SUB = 64;

    /**
     * Extrae y valida los datos de identidad de los claims de un token.
     *
     * @param claims claims del JWT ({@code sub}, {@code email}, {@code email_verified}, {@code name}, {@code telefono},
     *     {@code recomendaciones_correo})
     * @return los datos listos para dar de alta o sincronizar al usuario
     * @throws ExcepcionPlataforma 401 {@code PLT-009} si falta el {@code sub}, el correo o el teléfono, si el correo
     *     no está verificado o si algún valor no cabe en su columna
     */
    public static DatosIdentidad desdeClaims(Map<String, Object> claims) {
        // 1. Sin sub no hay identidad; se rechaza antes de mirar el resto
        String sub = texto(claims.get("sub"));
        if (sub == null || sub.length() > MAX_SUB) {
            throw ExcepcionPlataforma.noAutenticado("El token no identifica al usuario");
        }

        // 2. El correo debe venir verificado por Keycloak (ADR-028): sin email_verified = true no se crea ni se
        // sincroniza nada
        String correo = texto(claims.get("email"));
        if (!esVerdadero(claims.get("email_verified")) || correo == null || correo.length() > MAX_CORREO) {
            throw ExcepcionPlataforma.noAutenticado("El correo del usuario no está verificado");
        }

        // 3. El teléfono es obligatorio y solo de El Salvador; se normaliza a +503XXXXXXXX
        TelefonoSalvadoreno telefono = TelefonoSalvadoreno.de(texto(claims.get("telefono")))
                .orElseThrow(() -> ExcepcionPlataforma.noAutenticado("El teléfono del usuario no es válido"));

        // 4. Keycloak guarda el correo en minúsculas; la base lo exige así (ck_usuario_correo_minuscula)
        String correoNormalizado = correo.toLowerCase(Locale.ROOT);

        // 5. El nombre viene del claim name; si Keycloak no lo trae se usa el correo para no dejar la columna vacía
        String nombre = texto(claims.get("name"));
        if (nombre == null) {
            nombre = correoNormalizado;
        }
        if (nombre.length() > MAX_NOMBRE) {
            nombre = nombre.substring(0, MAX_NOMBRE);
        }

        return new DatosIdentidad(
                sub, correoNormalizado, nombre, telefono, esVerdadero(claims.get("recomendaciones_correo")));
    }

    /** Texto sin espacios en los extremos; nulo si el claim no es texto o está en blanco. */
    private static String texto(Object valor) {
        if (valor instanceof String s && !s.isBlank()) {
            return s.strip();
        }
        return null;
    }

    /** {@code true} solo si el claim es el booleano verdadero o la cadena {@code "true"}; ausente equivale a falso. */
    private static boolean esVerdadero(Object valor) {
        return Boolean.TRUE.equals(valor) || (valor instanceof String s && "true".equalsIgnoreCase(s.strip()));
    }
}
