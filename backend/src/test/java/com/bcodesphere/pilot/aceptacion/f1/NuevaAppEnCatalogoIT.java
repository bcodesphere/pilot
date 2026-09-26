package com.bcodesphere.pilot.aceptacion.f1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Aceptación F1, criterio 6 del plan: "agregar una fila en aplicacion no requiere cambios en el shell" (lado backend:
 * la API la publica y la instala sin cambios de código; el lado del shell está en {@code src/aceptacion/f1} del
 * frontend). La tabla {@code aplicacion} es global y las demás clases la leen, así que la fila de prueba se borra al
 * terminar (incluidas las instalaciones que la referencian).
 */
class NuevaAppEnCatalogoIT extends BaseAceptacionF1IT {

    private String codigoNuevo;

    /** Inserta, como dueño, una app COMUNITARIA de prueba con un orden entre Contabilidad (10) y Ventas (20). */
    @BeforeEach
    void insertarApp() {
        codigoNuevo = "prueba_f1_" + UUID.randomUUID().toString().substring(0, 8);
        duenio.sql("INSERT INTO aplicacion (codigo, nombre, descripcion, edicion, orden)"
                        + " VALUES (?, 'App de prueba F1', 'Fila agregada solo para la prueba de aceptación.',"
                        + " 'COMUNITARIA', 15)")
                .param(codigoNuevo)
                .update();
    }

    /** Deja la tabla global como estaba: borra las instalaciones de la app de prueba y luego la fila. */
    @AfterEach
    void borrarApp() {
        duenio.sql("DELETE FROM empresa_aplicacion WHERE aplicacion_codigo = ?")
                .param(codigoNuevo)
                .update();
        duenio.sql("DELETE FROM aplicacion WHERE codigo = ?").param(codigoNuevo).update();
    }

    /**
     * Fuente: criterio 6 del plan y ADR-030. Sin tocar código, {@code GET /aplicaciones} devuelve la app nueva
     * DISPONIBLE, con sus datos y en su posición según {@code orden} (después de Contabilidad, antes de Ventas), y se
     * puede instalar (201) pasando a INSTALADA.
     */
    @Test
    void unaFilaNuevaEnAplicacionApareceDisponibleEnSuOrdenYSePuedeInstalar() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        // 1. Aparece DISPONIBLE con sus datos, y en su lugar según el campo orden
        String catalogo = consultar(sub, empresa, "/aplicaciones")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == '" + codigoNuevo + "')].estado")
                        .value("DISPONIBLE"))
                .andExpect(jsonPath("$[?(@.codigo == '" + codigoNuevo + "')].nombre")
                        .value("App de prueba F1"))
                .andExpect(jsonPath("$[?(@.codigo == '" + codigoNuevo + "')].edicion")
                        .value("COMUNITARIA"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> codigos = JsonPath.read(catalogo, "$[*].codigo");
        assertThat(codigos.indexOf(codigoNuevo)).isEqualTo(codigos.indexOf("contabilidad") + 1);
        assertThat(codigos.indexOf(codigoNuevo)).isLessThan(codigos.indexOf("ventas"));

        // 2. Se puede instalar sin cambios de código: 201 y luego INSTALADA
        instalar(sub, empresa, codigoNuevo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INSTALADA"));
        consultar(sub, empresa, "/aplicaciones")
                .andExpect(jsonPath("$[?(@.codigo == '" + codigoNuevo + "')].estado")
                        .value("INSTALADA"));
        assertThat(contar(
                        "SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ? AND aplicacion_codigo = ?",
                        empresa,
                        codigoNuevo))
                .isEqualTo(1);
    }
}
