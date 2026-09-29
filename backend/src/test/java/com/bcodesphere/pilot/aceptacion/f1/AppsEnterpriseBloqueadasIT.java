package com.bcodesphere.pilot.aceptacion.f1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aceptación F1, criterio 2 del plan: "las apps Enterprise aparecen bloqueadas y su instalación se rechaza con 403"
 * (docs/plan-de-trabajo.md F1; ADR-030; CLAUDE.md 8.4, código {@code PLT-011}). Las apps Enterprise se leen de la tabla
 * {@code aplicacion}, no de una lista fija: si mañana se agrega otra, esta prueba la cubre sola.
 */
class AppsEnterpriseBloqueadasIT extends BaseAceptacionF1IT {

    /** Códigos de las apps ENTERPRISE del catálogo, leídos de la base (fuente de verdad del catálogo). */
    private List<String> appsEnterprise() {
        return duenio.sql("SELECT codigo FROM aplicacion WHERE edicion = 'ENTERPRISE' AND disponible ORDER BY orden")
                .query(String.class)
                .list();
    }

    /** Fuente: ADR-030. Cada app ENTERPRISE del catálogo aparece BLOQUEADA_ENTERPRISE, sin fecha de instalación. */
    @Test
    void cadaAppEnterpriseApareceBloqueada() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        List<String> enterprise = appsEnterprise();

        // Salvaguarda: el catálogo trae al menos una app Enterprise (si no, la prueba no probaría nada)
        assertThat(enterprise).isNotEmpty();

        for (String codigo : enterprise) {
            consultar(sub, empresa, "/aplicaciones")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.codigo == '" + codigo + "')].estado")
                            .value("BLOQUEADA_ENTERPRISE"))
                    .andExpect(jsonPath("$[?(@.codigo == '" + codigo + "')].edicion")
                            .value("ENTERPRISE"))
                    .andExpect(jsonPath("$[?(@.codigo == '" + codigo + "')].instaladaEn")
                            .value((Object) null));
        }
    }

    /** Fuente: ADR-030 y catálogo PLT- de CLAUDE.md 8.4: 403 {@code PLT-011} y ninguna fila nueva en empresa_aplicacion. */
    @Test
    void instalarUnaAppEnterpriseSeRechazaConPlt011SinCrearFilas() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        for (String codigo : appsEnterprise()) {
            instalar(sub, empresa, codigo)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-011"));
        }

        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isZero();
    }
}
