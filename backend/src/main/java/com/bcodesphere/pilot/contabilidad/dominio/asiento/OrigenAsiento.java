package com.bcodesphere.pilot.contabilidad.dominio.asiento;

/** Cómo nació un asiento (CLAUDE.md 9.3). */
public enum OrigenAsiento {
    /** Registrado por un contador en el Libro Diario. */
    MANUAL,
    /** Generado desde una operación externa de n8n (F5). */
    N8N,
    /** Contra-asiento que revierte a otro. */
    REVERSION
}
