package com.bcodesphere.pilot.contabilidad.dominio.estados;

/**
 * Fila del Libro Mayor: una línea de asiento con el saldo acumulado hasta esa línea, inclusive (CLAUDE.md 10.5).
 *
 * @param linea la línea original
 * @param saldo saldo de presentación acumulado hasta e incluyendo esta línea
 */
public record MovimientoMayor(MovimientoLinea linea, Saldo saldo) {}
