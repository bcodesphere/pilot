package com.bcodesphere.pilot.compartido;

/**
 * Claves del MDC de SLF4J que llevan los logs JSON (CLAUDE.md 1.1.12 y 16.4). Son la única fuente de estos nombres:
 * quien publique o lea el MDC debe usar estas constantes y no repetir el literal.
 */
public final class ClavesMdc {

    /** Identificador de correlación de la petición ({@code X-Request-Id}). */
    public static final String TRACE_ID = "traceId";

    /** Empresa activa de la transacción. */
    public static final String EMPRESA_ID = "empresaId";

    /** Usuario activo de la transacción. */
    public static final String USUARIO_ID = "usuarioId";

    private ClavesMdc() {}
}
