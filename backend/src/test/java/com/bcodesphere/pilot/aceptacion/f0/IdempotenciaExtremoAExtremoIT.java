package com.bcodesphere.pilot.aceptacion.f0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.RequiereIdempotencia;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import com.bcodesphere.pilot.plataforma.ServicioIdempotencia;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.WebApplicationContext;

/**
 * Aceptación F0, criterio 3 del plan: "Una petición repetida con el mismo Idempotency-Key devuelve la respuesta original
 * sin volver a ejecutar la operación" (docs/plan-de-trabajo.md, F0; CLAUDE.md 12.6). Recorre la pila real: interceptor
 * que exige el header, servicio de idempotencia y tabla {@code idempotencia} con RLS en PostgreSQL, todo como
 * {@code pilot_app}.
 *
 * <p>El controlador de prueba se registra solo aquí mediante {@link Import}: va anidado en la clase de prueba, así
 * que el escaneo de {@code PilotApplication} no lo ve en los demás contextos (lo verifica {@code ArranqueRealIT}).
 */
@Import(IdempotenciaExtremoAExtremoIT.ConfiguracionDePrueba.class)
class IdempotenciaExtremoAExtremoIT extends BasePlataformaIT {

    /** Empresa fija de prueba: en F0 nada llena ContextoEmpresa desde la petición (por diseño). */
    private static final EmpresaId EMPRESA_PRUEBA =
            new EmpresaId(UUID.fromString("00000000-0000-7000-8000-0000000f0c01"));

    /** Controlador de prueba que incrementa un contador dentro de la operación idempotente. */
    static final class ControladorContador {

        /** Cuenta cuántas veces se ejecutó realmente la operación (no las peticiones recibidas). */
        final AtomicInteger contador = new AtomicInteger();

        private final ServicioIdempotencia servicio;
        private final PlatformTransactionManager gestor;

        ControladorContador(ServicioIdempotencia servicio, PlatformTransactionManager gestor) {
            this.servicio = servicio;
            this.gestor = gestor;
        }
    }

    /**
     * Endpoint solo de prueba. {@code @Controller} lo registra; al estar anidado en la clase de prueba, el filtro de tipos de
     * Spring Boot Test lo excluye del escaneo de los demás contextos; {@code ResponseEntity} evita necesitar {@code @ResponseBody}.
     */
    @Controller
    @RequestMapping("/prueba-aceptacion-f0")
    static class EndpointContador {

        private final ControladorContador estado;

        EndpointContador(ControladorContador estado) {
            this.estado = estado;
        }

        /** Incrementa el contador una sola vez por clave y cuerpo; traduce la repetición al header estándar. */
        @PostMapping("/contador")
        @RequiereIdempotencia
        ResponseEntity<String> incrementar(
                @RequestHeader(RequiereIdempotencia.HEADER) String clave, @RequestBody String cuerpo) {
            // 1. F0 no fija la empresa desde la petición: la prueba la abre a mano, dentro de una transacción
            RespuestaIdempotente respuesta = ContextoEmpresa.ejecutarCon(
                    EMPRESA_PRUEBA,
                    "usuario-prueba",
                    () -> new TransactionTemplate(estado.gestor)
                            .execute(s -> estado.servicio.ejecutar(
                                    clave,
                                    cuerpo,
                                    () -> RespuestaIdempotente.de(
                                            201, "{\"contador\":" + estado.contador.incrementAndGet() + "}"))));

            // 2. La respuesta repetida se marca con Idempotency-Replayed: true (CLAUDE.md 12.6)
            ResponseEntity.BodyBuilder builder =
                    ResponseEntity.status(respuesta.estadoHttp()).contentType(MediaType.APPLICATION_JSON);
            if (respuesta.repetida()) {
                builder.header("Idempotency-Replayed", "true");
            }
            return builder.body(respuesta.cuerpoJson());
        }
    }

    /** Registra el estado y el endpoint de prueba solo para las clases que importan esta configuración. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionDePrueba {

        @Bean
        ControladorContador controladorContador(ServicioIdempotencia servicio, PlatformTransactionManager gestor) {
            return new ControladorContador(servicio, gestor);
        }

        @Bean
        EndpointContador endpointContador(ControladorContador estado) {
            return new EndpointContador(estado);
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ControladorContador estado;

    private MockMvc mvc;

    /** Cada prueba parte con el contador en cero y su propio MockMvc sobre el contexto real. */
    @BeforeEach
    void preparar() {
        estado.contador.set(0);
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    /** Criterio F0 (idempotencia): misma clave y mismo cuerpo -> el contador sube una vez y la respuesta se repite igual. */
    @Test
    void unaPeticionRepetidaDevuelveLaRespuestaOriginalSinEjecutarDeNuevo() throws Exception {
        String clave = "clave-" + UUID.randomUUID();

        // 1. Primera petición: se ejecuta y responde 201 sin marca de repetición
        MvcResult primera = mvc.perform(post("/prueba-aceptacion-f0/contador")
                        .header(RequiereIdempotencia.HEADER, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotency-Replayed"))
                .andExpect(jsonPath("$.contador").value(1))
                .andReturn();

        // 2. Segunda petición idéntica: mismo estado y cuerpo, con Idempotency-Replayed: true
        MvcResult segunda = mvc.perform(post("/prueba-aceptacion-f0/contador")
                        .header(RequiereIdempotencia.HEADER, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();

        // 3. La operación corrió una sola vez y ambas respuestas son idénticas
        assertThat(estado.contador).hasValue(1);
        assertThat(segunda.getResponse().getContentAsString())
                .isEqualTo(primera.getResponse().getContentAsString());
    }

    /** Criterio F0 (idempotencia): claves distintas son operaciones distintas; el contador sube en cada una. */
    @Test
    void clavesDistintasEjecutanLaOperacionCadaVez() throws Exception {
        for (int i = 1; i <= 2; i++) {
            mvc.perform(post("/prueba-aceptacion-f0/contador")
                            .header(RequiereIdempotencia.HEADER, "clave-" + UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.contador").value(i));
        }
        assertThat(estado.contador).hasValue(2);
    }

    /** Criterio F0 (idempotencia + Problem Details): misma clave con otro cuerpo -> 422 PLT-005 y no se ejecuta. */
    @Test
    void mismaClaveConOtroCuerpoEs422Plt005() throws Exception {
        String clave = "clave-" + UUID.randomUUID();
        mvc.perform(post("/prueba-aceptacion-f0/contador")
                        .header(RequiereIdempotencia.HEADER, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":1}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/prueba-aceptacion-f0/contador")
                        .header(RequiereIdempotencia.HEADER, clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"x\":2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-005"));

        assertThat(estado.contador).hasValue(1);
    }

    /** Criterio F0 (idempotencia, regla 1.1.4): sin Idempotency-Key -> 428 Problem Details PLT-006 y no se ejecuta. */
    @Test
    void sinHeaderDeIdempotenciaEs428Plt006() throws Exception {
        mvc.perform(post("/prueba-aceptacion-f0/contador")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isPreconditionRequired())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-006"));

        assertThat(estado.contador).hasValue(0);
    }
}
