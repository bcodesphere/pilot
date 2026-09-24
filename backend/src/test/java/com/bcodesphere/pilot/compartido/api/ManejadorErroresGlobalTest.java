package com.bcodesphere.pilot.compartido.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** Pruebas del manejador global y del filtro X-Request-Id sobre MockMvc autónomo (sin contexto Spring). */
class ManejadorErroresGlobalTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba())
            .setControllerAdvice(new ManejadorErroresGlobal())
            // Mismo mapeador que la aplicación: incluye el módulo que serializa Dinero como cadena
            .setMessageConverters(new JacksonJsonHttpMessageConverter(
                    JsonMapper.builder().addModule(new ModuloJacksonDinero()).build()))
            .addFilters(new FiltroRequestId())
            .build();

    /** Caso: ExcepcionValidacion -> 422 problem+json con codigo y errores. */
    @Test
    void validacionProduce422ConErrores() throws Exception {
        mvc.perform(get("/prueba/validacion"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("CON-001"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/prueba/validacion"))
                .andExpect(jsonPath("$.errores[0].campo").value("lineas"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("Mínimo dos líneas"));
    }

    /** Caso: la diferencia viaja como cadena con 2 decimales. */
    @Test
    void diferenciaSeSerializaComoCadena() throws Exception {
        mvc.perform(get("/prueba/descuadre"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.diferencia").value("10.50"))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    /** Caso: una excepción inesperada es 500 PLT-500 y no filtra mensaje ni clases. */
    @Test
    void errorInesperadoNoFiltraInformacion() throws Exception {
        String cuerpo = mvc.perform(get("/prueba/fallo"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("PLT-500"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(cuerpo)
                .doesNotContain("secreto-interno")
                .doesNotContain("RuntimeException")
                .doesNotContain("com.bcodesphere");
    }

    /** Caso: JSON ilegible -> 400 PLT-001. */
    @Test
    void jsonIlegibleProduce400() throws Exception {
        mvc.perform(post("/prueba/cuerpo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLT-001"));
    }

    /** Caso: Bean Validation -> 422 con la lista de campos. */
    @Test
    void beanValidationProduceErroresPorCampo() throws Exception {
        mvc.perform(post("/prueba/cuerpo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    /** Caso: una ruta inexistente conserva su 404 en lugar de volverse 500. */
    @Test
    void rutaInexistenteConservaSuEstado() throws Exception {
        mvc.perform(get("/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-007"));
    }

    /** Caso: X-Request-Id se genera si falta. */
    @Test
    void generaRequestIdSiFalta() throws Exception {
        String id = mvc.perform(get("/prueba/fallo"))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");
        assertThat(id).isNotBlank();
    }

    /** Caso: X-Request-Id se respeta si viene y es seguro; uno con caracteres raros se reemplaza. */
    @Test
    void respetaRequestIdSeguro() throws Exception {
        mvc.perform(get("/prueba/fallo").header("X-Request-Id", "abc-123"))
                .andExpect(header().string("X-Request-Id", "abc-123"));
        String reemplazado = mvc.perform(get("/prueba/fallo").header("X-Request-Id", "mal id con espacios"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");
        assertThat(reemplazado).isNotEqualTo("mal id con espacios").isNotBlank();
    }
}
