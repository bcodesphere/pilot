/**
 * Módulo plataforma: usuarios, empresas, membresías y roles, API keys, registro de apps,
 * auditoría, idempotencia y contexto de empresa. Solo depende de compartido y del contrato REST generado (CLAUDE.md 4.2, ADR-003).
 */
@ApplicationModule(
        displayName = "plataforma",
        allowedDependencies = {"compartido", "compartido::contrato"})
package com.bcodesphere.pilot.plataforma;

import org.springframework.modulith.ApplicationModule;
