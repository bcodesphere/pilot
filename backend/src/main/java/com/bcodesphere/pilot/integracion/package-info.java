/**
 * Módulo integración: webhook de n8n, API keys, validación de esquemas y bitácora de operaciones.
 * Entrega operaciones a la API pública de contabilidad; no crea asientos por sí mismo.
 */
@ApplicationModule(
        displayName = "integracion",
        allowedDependencies = {"contabilidad", "plataforma", "compartido"})
package com.bcodesphere.pilot.integracion;

import org.springframework.modulith.ApplicationModule;
