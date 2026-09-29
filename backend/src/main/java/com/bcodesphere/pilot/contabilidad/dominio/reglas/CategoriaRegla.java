package com.bcodesphere.pilot.contabilidad.dominio.reglas;

/** Categoría de una regla de contabilización (ADR-020, ampliada por ADR-041 para las operaciones guiadas). */
public enum CategoriaRegla {
    /** Concepto de ingreso (p. ej. {@code VENTAS_GRAVADAS} o {@code GRAVADO}). */
    INGRESO,
    /** Forma de cobro (p. ej. {@code EFECTIVO}). */
    COBRO,
    /** Forma de pago (p. ej. {@code EFECTIVO}). */
    PAGO,
    /** Destino de una compra o un gasto (p. ej. {@code ALQUILER}). */
    GASTO,
    /** Cuenta de la contraparte de un cobro, pago, aporte o préstamo (p. ej. {@code CLIENTES}). */
    CONTRAPARTIDA,
    /** Categoría de activo fijo, para la cuenta de activo al comprarlo. */
    ACTIVO,
    /** Categoría de activo fijo, para su cuenta de depreciación acumulada o de gasto por depreciación. */
    DEPRECIACION
}
