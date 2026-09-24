package com.bcodesphere.pilot.aceptacion.f0;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Aceptación F0, criterio 1 del plan ("./mvnw verify pasa"): el contexto completo de Spring arranca contra PostgreSQL
 * real, con esquema controlado solo por Flyway ({@code ddl-auto=validate}) y conectado como {@code pilot_app}.
 * Si el contexto no arrancara, esta clase (y todas las demás IT) fallaría al cargarlo.
 */
class ArranqueRealIT extends BasePlataformaIT {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private Environment entorno;

    @Autowired
    private JdbcClient jdbc;

    /** Criterio F0 (arranque real): el contexto arranca con Hibernate en modo validate, sin crear ni alterar tablas. */
    @Test
    void elContextoArrancaConDdlAutoValidate() {
        assertThat(contexto.getBean(EntityManagerFactory.class).isOpen()).isTrue();
        assertThat(entorno.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }

    /** Criterio F0 (arranque real): la aplicación quedó conectada como pilot_app y Flyway dejó el esquema listo. */
    @Test
    void laAplicacionQuedaConectadaComoPilotApp() {
        // 1. Usuario real de la sesión de base de datos
        assertThat(jdbc.sql("SELECT current_user").query(String.class).single()).isEqualTo("pilot_app");

        // 2. Las migraciones de F0 (V1 a V3) se aplicaron con el dueño: existen las tablas transversales
        Integer tablas = jdbc.sql("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"
                        + " AND tablename IN ('idempotencia', 'auditoria')")
                .query(Integer.class)
                .single();
        assertThat(tablas).isEqualTo(2);
    }

    /** Criterio F0 (aislamiento de pruebas): los controladores solo de prueba de esta fase no aparecen en otros contextos. */
    @Test
    void losControladoresDePruebaDeAceptacionNoSeFiltranAOtrosContextos() {
        assertThat(contexto.getBeanNamesForType(Object.class))
                .doesNotContain("endpointContador", "controladorContador", "endpointValidacion");
    }
}
