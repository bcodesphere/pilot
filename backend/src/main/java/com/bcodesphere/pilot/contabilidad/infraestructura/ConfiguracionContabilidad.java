package com.bcodesphere.pilot.contabilidad.infraestructura;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de infraestructura del módulo contabilidad. Declara el {@link Clock} con la zona de negocio para calcular
 * «hoy» en hora de El Salvador (CLAUDE.md, tabla de cabecera; CON-007) y poder sustituirlo en las pruebas.
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionContabilidad {

    /** Zona horaria de negocio: UTC−6, sin horario de verano. */
    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/El_Salvador");

    /**
     * Reloj del sistema en la zona de negocio.
     *
     * @return reloj para {@code LocalDate.now(reloj)}
     */
    @Bean
    Clock relojNegocio() {
        return Clock.system(ZONA_NEGOCIO);
    }
}
