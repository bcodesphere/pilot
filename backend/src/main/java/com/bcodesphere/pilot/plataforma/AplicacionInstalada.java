package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.compartido.EmpresaId;

/**
 * Evento síncrono publicado cuando una empresa instala una app (ADR-030, CLAUDE.md 4.4). Es API pública de
 * {@code plataforma}: el módulo de cada app lo escucha para hacer su precarga (p. ej. el catálogo de cuentas de
 * Contabilidad) sin que {@code plataforma} dependa de ningún módulo de app.
 *
 * <p>Se publica dentro de la misma transacción de la instalación y solo cuando la app se instaló de verdad, así que
 * un oyente que falla revierte la instalación completa. Por eso los oyentes deben usar {@code @EventListener} (no
 * {@code @TransactionalEventListener}) y el contexto de empresa ya está fijado.
 *
 * @param empresaId empresa que instaló la app
 * @param codigo código de la app instalada (p. ej. {@code contabilidad})
 */
public record AplicacionInstalada(EmpresaId empresaId, String codigo) {}
