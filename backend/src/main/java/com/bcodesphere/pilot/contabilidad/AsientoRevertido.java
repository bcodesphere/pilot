package com.bcodesphere.pilot.contabilidad;

import com.bcodesphere.pilot.compartido.EmpresaId;
import java.util.UUID;

/**
 * Evento síncrono publicado cuando se revierte un asiento (CLAUDE.md 10.1, ADR-036). Es API pública de
 * {@code contabilidad}: la app de operaciones externas (F5) lo escuchará para pasar la operación de origen a
 * {@code REVERTIDO} sin que {@code contabilidad} dependa de ella, con el mismo patrón de {@code AplicacionInstalada}
 * (ADR-030).
 *
 * <p>Se publica dentro de la misma transacción de la reversión, así que un oyente que falla la revierte completa. Los
 * oyentes deben usar {@code @EventListener} (no {@code @TransactionalEventListener}). Nadie lo escucha todavía.
 *
 * @param empresaId empresa dueña del asiento
 * @param asientoOriginalId asiento que quedó {@code REVERTIDO}
 * @param asientoReversionId contra-asiento creado
 * @param origenTipo origen del asiento original ({@code MANUAL} o {@code N8N})
 * @param origenId operación externa del asiento original, o nulo si no viene de una
 */
public record AsientoRevertido(
        EmpresaId empresaId, UUID asientoOriginalId, UUID asientoReversionId, String origenTipo, UUID origenId) {}
