package com.bcodesphere.pilot.aceptacion.f1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aceptación F1, criterio 1 del plan: "instala Contabilidad desde Apps y la ve en el lanzador" (docs/plan-de-trabajo.md
 * F1; CLAUDE.md 4.4; ADR-030). Cubre el catálogo antes y después, la idempotencia de la instalación (201 y luego 200)
 * y que exista una sola fila en {@code empresa_aplicacion}.
 */
class InstalarContabilidadIT extends BaseAceptacionF1IT {

    /**
     * Fuente: ADR-030 punto 4. Contabilidad pasa de DISPONIBLE a INSTALADA; la primera instalación responde 201 y la
     * repetida 200; queda una sola fila en empresa_aplicacion para la empresa.
     */
    @Test
    void contabilidadSeInstalaUnaVezYElCatalogoLaMuestraInstalada() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        // 1. Antes de instalar: DISPONIBLE y sin fecha de instalación (la app se ubica por código, no por posición)
        consultar(sub, empresa, "/aplicaciones")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'contabilidad')].estado").value("DISPONIBLE"))
                .andExpect(
                        jsonPath("$[?(@.codigo == 'contabilidad')].instaladaEn").value((Object) null));

        // 2. Primera instalación: 201; repetida: 200 (idempotente)
        instalar(sub, empresa, "contabilidad")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INSTALADA"));
        instalar(sub, empresa, "contabilidad")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INSTALADA"));

        // 3. Después: INSTALADA con su fecha
        consultar(sub, empresa, "/aplicaciones")
                .andExpect(jsonPath("$[?(@.codigo == 'contabilidad')].estado").value("INSTALADA"))
                .andExpect(
                        jsonPath("$[?(@.codigo == 'contabilidad')].instaladaEn").isNotEmpty());

        // 4. Una sola fila, aunque se instaló dos veces
        assertThat(contar(
                        "SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ? AND aplicacion_codigo = 'contabilidad'",
                        empresa))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isEqualTo(1);
    }
}
