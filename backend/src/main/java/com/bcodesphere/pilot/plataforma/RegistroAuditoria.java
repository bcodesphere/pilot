package com.bcodesphere.pilot.plataforma;

/**
 * Puerto de auditoría (CLAUDE.md 1.1.11): quién, qué, cuándo, valor anterior y nuevo.
 * Los casos de uso lo llaman de forma explícita, dentro de la transacción de la mutación, sin listeners de JPA.
 * Toma empresa y usuario del {@link ContextoEmpresa} y el {@code traceId} del MDC.
 *
 * <p>ADR-025: las entidades con empresa van a {@code auditoria} ({@link #registrar}); las que no tienen empresa
 * (p. ej. {@code usuario}) van a {@code auditoria_global} ({@link #registrarGlobal}).
 */
public interface RegistroAuditoria {

    /**
     * Registra una mutación de una entidad de la empresa activa.
     *
     * @param entidad nombre lógico de la entidad (p. ej. {@code asiento})
     * @param entidadId identificador de la entidad
     * @param accion acción realizada (p. ej. {@code CREAR}, {@code ACTUALIZAR}, {@code REVERTIR})
     * @param valorAnterior estado previo; nulo en creaciones. Se guarda como JSON (un {@code String} es un texto JSON,
     *     no JSON ya serializado)
     * @param valorNuevo estado posterior; nulo en eliminaciones
     */
    void registrar(String entidad, String entidadId, String accion, Object valorAnterior, Object valorNuevo);

    /**
     * Registra una mutación de una entidad global (sin empresa), como {@code usuario} (ADR-025). No exige empresa en
     * el contexto, así que sirve también en el modo «sin empresa» (ADR-026). Los valores no deben llevar datos
     * personales sin enmascarar.
     *
     * @param entidad nombre lógico de la entidad (p. ej. {@code usuario})
     * @param entidadId identificador de la entidad
     * @param accion acción realizada (p. ej. {@code CREAR}, {@code ACTUALIZAR})
     * @param valorAnterior estado previo; nulo en creaciones
     * @param valorNuevo estado posterior; nulo en eliminaciones
     */
    void registrarGlobal(String entidad, String entidadId, String accion, Object valorAnterior, Object valorNuevo);
}
