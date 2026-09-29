package com.bcodesphere.pilot.contabilidad.dominio.asiento;

/** Estado de un asiento; {@code REVERTIDO} es el único cambio permitido tras guardarlo (ADR-019). */
public enum EstadoAsiento {
    /** Asiento vigente y mayorizado. */
    CONTABILIZADO,
    /** Asiento anulado por su reversión. */
    REVERTIDO
}
