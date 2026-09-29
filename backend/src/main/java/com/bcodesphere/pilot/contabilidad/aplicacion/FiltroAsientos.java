package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.EstadoAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros opcionales del Libro Diario (CLAUDE.md 10.5); se combinan con AND y un valor nulo no filtra.
 *
 * @param desde fecha contable mínima, inclusive
 * @param hasta fecha contable máxima, inclusive
 * @param anio año de numeración
 * @param numero número correlativo
 * @param origen origen del asiento
 * @param estado estado del asiento
 * @param cuentaId asientos con alguna línea en esa cuenta
 */
public record FiltroAsientos(
        LocalDate desde,
        LocalDate hasta,
        Integer anio,
        Long numero,
        OrigenAsiento origen,
        EstadoAsiento estado,
        UUID cuentaId) {}
