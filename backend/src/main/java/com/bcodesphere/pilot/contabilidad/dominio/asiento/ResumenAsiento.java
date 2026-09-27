package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cabecera de un asiento para el listado del Libro Diario, sin líneas.
 *
 * @param id identificador
 * @param anio año de la fecha contable
 * @param numero correlativo dentro de la empresa y el año
 * @param fecha fecha contable
 * @param concepto concepto o glosa
 * @param estado estado actual
 * @param origenTipo origen del asiento
 * @param totalDebe Σ Debe
 * @param totalHaber Σ Haber
 */
public record ResumenAsiento(
        UUID id,
        int anio,
        long numero,
        LocalDate fecha,
        String concepto,
        EstadoAsiento estado,
        OrigenAsiento origenTipo,
        Dinero totalDebe,
        Dinero totalHaber) {}
