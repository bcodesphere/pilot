package com.bcodesphere.pilot.contabilidad.dominio.reglas;

/** Categoría de una regla de contabilización (ADR-020). */
public enum CategoriaRegla {
    /** Concepto de ingreso (p. ej. {@code VENTAS_GRAVADAS}). */
    INGRESO,
    /** Forma de pago (p. ej. {@code EFECTIVO}). */
    COBRO
}
