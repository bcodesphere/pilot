package com.bcodesphere.pilot.plataforma.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.compartido.api.ManejadorErroresGlobal;
import com.bcodesphere.pilot.plataforma.RequiereIdempotencia;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pruebas del interceptor de {@code Idempotency-Key} sobre MockMvc autónomo: sin header responde 428 {@code PLT-006}
 * traducido por el manejador global (CLAUDE.md 8.4).
 */
class InterceptorIdempotenciaTest {

    /** Controlador de prueba: un endpoint que exige la clave, uno de clase completa y uno libre. */
    @RestController
    static class ControladorMarcado {

        /** Exige la clave por anotación en el método. */
        @PostMapping("/prueba/exige")
        @RequiereIdempotencia
        String exige() {
            return "ok";
        }

        /** No exige la clave. */
        @GetMapping("/prueba/libre")
        String libre() {
            return "ok";
        }
    }

    /** Controlador con la anotación en la clase. */
    @RestController
    @RequiereIdempotencia
    static class ControladorMarcadoEnClase {

        /** Hereda la exigencia de la clase. */
        @PostMapping("/prueba/clase")
        String clase() {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new ControladorMarcado(), new ControladorMarcadoEnClase())
            .setControllerAdvice(new ManejadorErroresGlobal())
            .addInterceptors(new InterceptorIdempotencia())
            .build();

    /** Caso: falta el header en un endpoint marcado -> 428 PLT-006 problem+json. */
    @Test
    void sinHeaderResponde428() throws Exception {
        mvc.perform(post("/prueba/exige"))
                .andExpect(status().is(428))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-006"));
    }

    /** Caso: header en blanco equivale a faltante. */
    @Test
    void headerEnBlancoResponde428() throws Exception {
        mvc.perform(post("/prueba/exige").header(RequiereIdempotencia.HEADER, "  "))
                .andExpect(status().is(428));
    }

    /** Caso: con el header el endpoint responde normalmente. */
    @Test
    void conHeaderPasa() throws Exception {
        mvc.perform(post("/prueba/exige").header(RequiereIdempotencia.HEADER, "abc"))
                .andExpect(status().isOk());
    }

    /** Caso: la anotación en la clase también se exige. */
    @Test
    void anotacionEnLaClaseSeExige() throws Exception {
        mvc.perform(post("/prueba/clase"))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.codigo").value("PLT-006"));
    }

    /** Caso: un endpoint sin la anotación no exige el header. */
    @Test
    void endpointSinAnotacionNoExigeHeader() throws Exception {
        mvc.perform(get("/prueba/libre")).andExpect(status().isOk());
    }
}
