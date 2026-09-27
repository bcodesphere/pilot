package com.bcodesphere.pilot.contabilidad.dominio.asiento;

/** Origen de una línea de asiento (CLAUDE.md 9.3, 11.2). */
public enum OrigenLinea {
    /** Capturada por el usuario. */
    USUARIO,
    /** IVA calculado por el backend al expandir una línea "lleva IVA". */
    IVA_CALCULADO,
    /** Generada desde una operación externa (F5). */
    OPERACION
}
