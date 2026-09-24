package com.bcodesphere.pilot.aceptacion.f0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Aceptación F0, criterio 4 del plan: "Un error de validación devuelve application/problem+json con codigo y lista
 * errores" (docs/plan-de-trabajo.md, F0; CLAUDE.md 8.4). F0-05 lo probó sin contexto (MockMvc autónomo); aquí se
 * prueba con el contexto completo de Spring y contra PostgreSQL real, y el cuerpo se valida contra el esquema
 * {@code ProblemDetails} leído del contrato {@code api-spec/openapi/pilot-v1.yaml}.
 */
@Import(ProblemDetailsContextoCompletoIT.ConfiguracionDePrueba.class)
class ProblemDetailsContextoCompletoIT extends BasePlataformaIT {

    /** Contrato REST, fuente de verdad de la forma de los errores (ADR-003). Ruta relativa a backend/. */
    private static final Path CONTRATO = Path.of("../api-spec/openapi/pilot-v1.yaml");

    /** DTO de prueba con reglas de Bean Validation. */
    record Peticion(@NotBlank String nombre, @Size(max = 5) String codigo) {}

    /** Endpoint solo de prueba; anidado en la clase de prueba, así el escaneo no lo ve en otros contextos. */
    @Controller
    @RequestMapping("/prueba-aceptacion-f0/problemas")
    static class EndpointValidacion {

        /** Acepta un DTO válido; los inválidos los rechaza el manejador global. */
        @PostMapping
        ResponseEntity<String> recibir(@Valid @RequestBody Peticion peticion) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{}");
        }
    }

    /** Registra el endpoint solo para esta clase. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionDePrueba {

        @Bean
        EndpointValidacion endpointValidacion() {
            return new EndpointValidacion();
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    /** Criterio F0 (Problem Details): cuerpo inválido -> 422 PLT-002 con errores[{campo, mensaje}] y esquema válido. */
    @Test
    void unCuerpoInvalidoDevuelve422Plt002ConErroresPorCampo() throws Exception {
        String cuerpo = mvc.perform(post("/prueba-aceptacion-f0/problemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"codigo\":\"demasiado-largo\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.errores[?(@.campo=='nombre')].mensaje").isNotEmpty())
                .andExpect(jsonPath("$.errores[?(@.campo=='codigo')].mensaje").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCumpleEsquemaProblemDetails(json.readTree(cuerpo));
    }

    /** Criterio F0 (Problem Details): JSON mal formado -> 400 PLT-001 sin detalles internos de Jackson ni de clases. */
    @Test
    void unJsonMalFormadoDevuelve400Plt001SinDetallesInternos() throws Exception {
        String cuerpo = mvc.perform(post("/prueba-aceptacion-f0/problemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": sin-comillas"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-001"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Nada de nombres de clases, paquetes, ni mensajes del parser
        assertThat(cuerpo)
                .doesNotContain("com.bcodesphere")
                .doesNotContain("tools.jackson")
                .doesNotContain("Exception")
                .doesNotContain("sin-comillas")
                .doesNotContain("line:")
                .doesNotContain("at ");
        assertCumpleEsquemaProblemDetails(json.readTree(cuerpo));
    }

    /** Criterio F0 (Problem Details): ruta inexistente -> 404 PLT-007 con el esquema del contrato. */
    @Test
    void unaRutaInexistenteDevuelve404Plt007() throws Exception {
        String cuerpo = mvc.perform(get("/ruta-que-no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-007"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertCumpleEsquemaProblemDetails(json.readTree(cuerpo));
    }

    /**
     * Valida un cuerpo contra los esquemas {@code ProblemDetails} y {@code ErrorCampo} del contrato: campos
     * obligatorios, propiedades declaradas, tipos y el patrón de {@code codigo}. Valida solo lo que el contrato
     * declara; no reimplementa JSON Schema completo.
     */
    @SuppressWarnings("unchecked")
    private static void assertCumpleEsquemaProblemDetails(JsonNode cuerpo) throws IOException {
        // 1. Lee los esquemas del contrato (fuente de verdad), no una copia en la prueba
        Map<String, Object> raiz;
        try (var lector = Files.newBufferedReader(CONTRATO)) {
            raiz = new Yaml().load(lector);
        }
        Map<String, Object> esquemas =
                (Map<String, Object>) ((Map<String, Object>) raiz.get("components")).get("schemas");
        Map<String, Object> problema = (Map<String, Object>) esquemas.get("ProblemDetails");
        Map<String, Object> errorCampo = (Map<String, Object>) esquemas.get("ErrorCampo");

        // 2. Campos obligatorios, propiedades declaradas y tipos del problema
        verificarObjeto(cuerpo, problema);

        // 3. El patrón de codigo del contrato (PLT|CON|INT)-nnn
        Map<String, Object> propiedades = (Map<String, Object>) problema.get("properties");
        String patron = (String) ((Map<String, Object>) propiedades.get("codigo")).get("pattern");
        assertThat(cuerpo.get("codigo").asString()).matches(Pattern.compile(patron));

        // 4. Cada elemento de errores cumple ErrorCampo
        if (cuerpo.has("errores")) {
            assertThat(cuerpo.get("errores").isArray()).isTrue();
            for (JsonNode elemento : cuerpo.get("errores")) {
                verificarObjeto(elemento, errorCampo);
            }
        }
    }

    /** Comprueba required, propiedades declaradas y tipos simples (string, integer, array) de un objeto. */
    @SuppressWarnings("unchecked")
    private static void verificarObjeto(JsonNode nodo, Map<String, Object> esquema) {
        Map<String, Object> propiedades = (Map<String, Object>) esquema.get("properties");
        List<String> requeridos = (List<String>) esquema.get("required");
        assertThat(nodo.isObject()).isTrue();
        // Obligatorios presentes
        for (String campo : requeridos) {
            assertThat(nodo.has(campo)).as("campo obligatorio %s", campo).isTrue();
        }
        // Todo campo devuelto está declarado en el contrato, con el tipo correcto
        nodo.propertyNames().forEach(nombre -> {
            assertThat(propiedades).as("propiedad no declarada %s", nombre).containsKey(nombre);
            Object tipo = ((Map<String, Object>) propiedades.get(nombre)).get("type");
            JsonNode valor = nodo.get(nombre);
            if ("string".equals(tipo)) {
                assertThat(valor.isString()).as("%s es string", nombre).isTrue();
            } else if ("integer".equals(tipo)) {
                assertThat(valor.isIntegralNumber()).as("%s es integer", nombre).isTrue();
            } else if ("array".equals(tipo)) {
                assertThat(valor.isArray()).as("%s es array", nombre).isTrue();
            }
        });
    }
}
