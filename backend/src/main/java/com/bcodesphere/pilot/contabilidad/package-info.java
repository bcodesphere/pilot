/**
 * Módulo contabilidad: catálogo de cuentas, configuración, IVA, reglas de contabilización, asientos,
 * mayorización, estados financieros y reportes. Depende de plataforma y compartido; nunca de integración.
 */
@ApplicationModule(
        displayName = "contabilidad",
        allowedDependencies = {"plataforma", "compartido", "compartido::contrato"})
package com.bcodesphere.pilot.contabilidad;

import org.springframework.modulith.ApplicationModule;
