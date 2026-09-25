package com.bcodesphere.pilot.plataforma.dominio;

import java.time.Instant;
import java.util.UUID;

/**
 * Persona que inicia sesión (tabla global {@code usuario}, ADR-028). La identidad vive en Keycloak; aquí solo el
 * perfil, el estado y el consentimiento de recomendaciones por correo.
 *
 * @param id identificador UUID v7
 * @param sub claim {@code sub} del token
 * @param correo correo verificado, en minúsculas
 * @param nombre nombre completo
 * @param telefono teléfono {@code +503XXXXXXXX}
 * @param bloqueado {@code true} si el estado es {@code BLOQUEADO}
 * @param aceptadasEn cuándo aceptó las recomendaciones; nulo si nunca
 * @param retiradasEn último retiro del consentimiento; nulo si nunca
 */
public record Usuario(
        UUID id,
        String sub,
        String correo,
        String nombre,
        TelefonoSalvadoreno telefono,
        boolean bloqueado,
        Instant aceptadasEn,
        Instant retiradasEn) {

    /**
     * Consentimiento vigente: aceptado y, si alguna vez se retiró, aceptado de nuevo después del retiro (ADR-028).
     *
     * @return {@code true} si {@code aceptadas_en} no es nulo y es posterior a {@code retiradas_en} (o este es nulo)
     */
    public boolean recomendacionesVigentes() {
        return aceptadasEn != null && (retiradasEn == null || aceptadasEn.isAfter(retiradasEn));
    }

    /**
     * Indica si Keycloak cambió el correo, el nombre o el teléfono respecto de lo guardado.
     *
     * @param datos identidad actual del token
     * @return {@code true} si hay algo que sincronizar
     */
    public boolean difiereDe(DatosIdentidad datos) {
        return !correo.equals(datos.correo()) || !nombre.equals(datos.nombre()) || !telefono.equals(datos.telefono());
    }
}
