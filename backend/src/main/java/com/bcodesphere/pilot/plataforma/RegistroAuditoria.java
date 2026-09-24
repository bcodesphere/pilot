package com.bcodesphere.pilot.plataforma;

/**
 * Puerto de auditoría (CLAUDE.md 1.1.11): quién, qué, cuándo, valor anterior y nuevo.
 * Los casos de uso lo llaman de forma explícita, dentro de la transacción de la mutación, sin listeners de JPA.
 * Toma empresa y usuario del {@link ContextoEmpresa} y el {@code traceId} del MDC.
 *
 * <p>Diseño para ADR-025: hoy toda entrada va a {@code auditoria} (entidades con empresa). Cuando F1 cree
 * {@code auditoria_global}, la elección de tabla se hará en el adaptador según la entidad (o con un método hermano),
 * sin cambiar a los llamadores que ya usan este puerto.
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
}
