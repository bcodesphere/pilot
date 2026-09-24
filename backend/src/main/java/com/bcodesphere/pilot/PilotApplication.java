package com.bcodesphere.pilot;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de Pilot 1.0: monolito modular con los módulos compartido, plataforma,
 * contabilidad e integración (CLAUDE.md 4.1 y 4.2, ADR-001).
 */
@SpringBootApplication
public class PilotApplication {

    /**
     * Arranca la aplicación.
     *
     * @param args argumentos de línea de comandos
     */
    public static void main(String[] args) {
        // 1. La JVM trabaja en UTC: las fechas-hora se guardan en UTC (CLAUDE.md 1.1.10);
        //    la fecha contable de El Salvador se maneja aparte como DATE
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        // 2. Inicia el contexto de Spring
        SpringApplication.run(PilotApplication.class, args);
    }
}
