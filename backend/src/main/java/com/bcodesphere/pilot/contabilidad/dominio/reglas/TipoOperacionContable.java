package com.bcodesphere.pilot.contabilidad.dominio.reglas;

/** Tipos de operación externa que Pilot sabe contabilizar. En 1.0 solo uno (CLAUDE.md 12.1). */
public enum TipoOperacionContable {
    /** Cierre de ingresos diarios enviado por n8n. */
    CIERRE_INGRESOS_DIARIO
}
