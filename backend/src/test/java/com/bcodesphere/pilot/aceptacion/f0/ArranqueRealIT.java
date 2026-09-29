package com.bcodesphere.pilot.aceptacion.f0;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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

    /** Prefijo real de las rutas de los controladores solo de prueba de F0 (ver IdempotenciaExtremoAExtremoIT y ProblemDetailsContextoCompletoIT). */
    private static final String PREFIJO_RUTAS_PRUEBA_F0 = "/prueba-aceptacion-f0";

    /**
     * Criterio F0 (aislamiento de pruebas), verificado POR TIPO: ningún bean de los tipos de prueba de F0 existe fuera de
     * las clases que los importan. Comprobar por nombre de bean no basta: un {@code @Controller} anidado se registraría
     * como {@code idempotenciaExtremoAExtremoIT.EndpointContador} y una lista de nombres fijos no lo vería.
     */
    @Test
    void losControladoresDePruebaDeF0NoSeFiltranAOtrosContextosPorTipo() {
        // 1. Tipos de prueba de F0: controladores, estado compartido y configuraciones que los registran
        List<Class<?>> tiposDePrueba = List.of(
                IdempotenciaExtremoAExtremoIT.EndpointContador.class,
                IdempotenciaExtremoAExtremoIT.ControladorContador.class,
                IdempotenciaExtremoAExtremoIT.ConfiguracionDePrueba.class,
                ProblemDetailsContextoCompletoIT.EndpointValidacion.class,
                ProblemDetailsContextoCompletoIT.ConfiguracionDePrueba.class);

        // 2. getBeanNamesForType no depende del nombre con que se registró el bean: devuelve vacío o hay fuga
        for (Class<?> tipo : tiposDePrueba) {
            assertThat(contexto.getBeanNamesForType(tipo))
                    .as("beans de tipo %s en un contexto que no importa la prueba", tipo.getName())
                    .isEmpty();
        }
    }

    /**
     * Criterio F0 (aislamiento de pruebas), verificado POR RUTA: ningún mapeo {@code /prueba-aceptacion-f0/**} está
     * registrado en los {@code RequestMappingHandlerMapping} del contexto, con independencia de cómo se llame el bean.
     */
    @Test
    void ningunaRutaDePruebaDeF0SeRegistraEnOtrosContextos() {
        // 1. Todas las rutas de todos los mapeos de handlers de Spring MVC del contexto
        List<String> rutas = contexto.getBeansOfType(RequestMappingHandlerMapping.class).values().stream()
                .flatMap(mapeo -> mapeo.getHandlerMethods().keySet().stream())
                .flatMap(info -> info.getPatternValues().stream())
                .toList();

        // 2. Salvaguarda: el contexto sí tiene rutas (si la lista estuviera vacía, la comprobación no probaría nada)
        assertThat(rutas).contains("/api/v1/me");

        // 3. Ninguna ruta de prueba de F0
        assertThat(rutas).noneMatch(ruta -> ruta.startsWith(PREFIJO_RUTAS_PRUEBA_F0));
    }
}
