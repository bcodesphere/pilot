/**
 * Módulo plataforma: usuarios, empresas, membresías y roles, API keys, registro de apps,
 * auditoría, idempotencia y contexto de empresa. Solo depende de compartido (CLAUDE.md 4.2).
 */
@ApplicationModule(
        displayName = "plataforma",
        allowedDependencies = {"compartido"})
package com.bcodesphere.pilot.plataforma;

import org.springframework.modulith.ApplicationModule;
