package com.bcodesphere.pilot.contabilidad.dominio.configuracion;

/** Modo de precio: solo cambia cómo se interpreta el monto respecto del IVA (CLAUDE.md 3, ADR-015). */
public enum ModoPrecio {
    /** El monto incluye el IVA. */
    CON_IVA,
    /** El IVA se suma al monto. */
    SIN_IVA
}
