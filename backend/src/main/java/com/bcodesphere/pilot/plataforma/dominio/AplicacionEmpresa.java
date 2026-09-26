package com.bcodesphere.pilot.plataforma.dominio;

import java.time.Instant;

/**
 * Una app del catálogo vista desde la empresa activa: sus datos globales más su estado y, si está instalada, cuándo se
 * instaló (ADR-030).
 *
 * @param codigo identificador estable de la app y prefijo de sus rutas
 * @param nombre nombre visible
 * @param descripcion descripción corta; puede ser nula
 * @param edicion edición de la app
 * @param estado estado para la empresa activa
 * @param instaladaEn instante UTC de la instalación; nulo si no está instalada
 */
public record AplicacionEmpresa(
        String codigo,
        String nombre,
        String descripcion,
        EdicionAplicacion edicion,
        EstadoAplicacionEmpresa estado,
        Instant instaladaEn) {}
