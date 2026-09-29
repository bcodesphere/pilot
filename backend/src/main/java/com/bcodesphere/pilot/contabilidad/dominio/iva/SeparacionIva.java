package com.bcodesphere.pilot.contabilidad.dominio.iva;

import com.bcodesphere.pilot.compartido.Dinero;

/**
 * Resultado de separar un monto en base imponible e IVA (CLAUDE.md 11.1).
 *
 * @param base monto sin IVA
 * @param iva IVA calculado, redondeado a 2 decimales con {@code HALF_UP}
 */
public record SeparacionIva(Dinero base, Dinero iva) {}
