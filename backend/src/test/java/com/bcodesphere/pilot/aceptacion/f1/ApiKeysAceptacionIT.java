package com.bcodesphere.pilot.aceptacion.f1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Aceptación F1, criterio 5 del plan: "una API key revocada o vencida devuelve 401; sin el alcance requerido, 403"
 * (docs/plan-de-trabajo.md F1; CLAUDE.md 12.1, 14.1 y 14.2). Usa los controladores de prueba de F1-06 bajo
 * {@code /api/v1/integraciones/n8n/**}, porque el webhook real llega en F5. Confirma además que la revocación responde
 * 204 sin cuerpo y deja {@code revocada_en} y su auditoría en la base.
 */
@Import(ImportarControladoresApiKeysDePrueba.class)
class ApiKeysAceptacionIT extends BaseAceptacionF1IT {

    /** Clave creada por la API: id, prefijo y secreto completo ({@code pk_xxxx.secreto}). */
    private record Clave(UUID id, String secreto) {}

    /** Crea una clave válida por la API (con el token de un admin) y la devuelve con su secreto real. */
    private Clave nuevaClave(String sub, UUID empresa, String nombre) throws Exception {
        String cuerpo = crearClaveApi(sub, empresa, nombre);
        return new Clave(UUID.fromString(JsonPath.read(cuerpo, "$.id")), JsonPath.read(cuerpo, "$.secreto"));
    }

    /** {@code POST} al controlador de prueba de F1-06 con la API key como Bearer. */
    private ResultActions usar(String secreto, String ruta) throws Exception {
        return mvc.perform(post("/api/v1/integraciones/n8n/" + ruta).header("Authorization", "Bearer " + secreto));
    }

    /**
     * Fuente: criterio 5 del plan. Una clave revocada por la API deja de autenticar: antes de revocar, 200; después,
     * 401 PLT-009. La revocación responde 204 SIN cuerpo (lo que en la prueba de Chrome se vio como "DELETE 503"), deja
     * {@code revocada_en} no nulo y una fila de auditoría REVOCAR.
     */
    @Test
    void revocarDevuelve204DejaRevocadaEnYLaClaveDejaDeServir() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        Clave clave = nuevaClave(sub, empresa, "n8n revocable");
        usar(clave.secreto(), "prueba").andExpect(status().isOk());
        assertThat(contar("SELECT count(*) FROM api_key WHERE id = ? AND revocada_en IS NULL", clave.id()))
                .isEqualTo(1);

        // 1. La revocación responde 204 y el cuerpo está vacío
        revocarClaveApi(sub, empresa, clave.id())
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // 2. En la base: revocada_en fijado y auditoría de la revocación
        assertThat(contar("SELECT count(*) FROM api_key WHERE id = ? AND revocada_en IS NOT NULL", clave.id()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'api_key' AND entidad_id = ?"
                                + " AND accion = 'REVOCAR'",
                        empresa,
                        clave.id().toString()))
                .isEqualTo(1);

        // 3. La misma clave ya no autentica
        usar(clave.secreto(), "prueba")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PLT-009"));

        // 4. El listado la muestra como revocada
        consultar(sub, empresa, "/api-keys")
                .andExpect(jsonPath("$.elementos[?(@.id == '" + clave.id() + "')].revocadaEn")
                        .isNotEmpty());
    }

    /** Fuente: criterio 5 del plan. Una clave vencida (expira_en pasado) devuelve 401 PLT-009. */
    @Test
    void unaClaveVencidaDevuelve401() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        Clave clave = nuevaClave(sub, empresa, "n8n vencible");
        usar(clave.secreto(), "prueba").andExpect(status().isOk());

        // La API no permite crear una clave ya vencida (ADR-033): se vence con SQL como dueño
        duenio.sql("UPDATE api_key SET expira_en = now() - interval '1 second' WHERE id = ?")
                .param(clave.id())
                .update();

        usar(clave.secreto(), "prueba")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PLT-009"));
    }

    /** Fuente: criterio 5 del plan. Una clave sin el alcance que exige la ruta devuelve 403 PLT-010. */
    @Test
    void unaClaveSinElAlcanceRequeridoDevuelve403() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        Clave clave = nuevaClave(sub, empresa, "n8n sin alcance");

        usar(clave.secreto(), "prueba-alcance-inexistente")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }

    /**
     * Fuente: F1-06 (cadenas separadas, ADR-026). Una API key válida NO sirve en rutas de usuario: {@code GET /me} y
     * {@code GET /aplicaciones} responden 401 con el código que devuelva la aplicación (PLT-009 esperado, 14.1).
     */
    @Test
    void unaApiKeyValidaNoSirveEnRutasDeUsuario() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        Clave clave = nuevaClave(sub, empresa, "n8n en ruta de usuario");
        usar(clave.secreto(), "prueba").andExpect(status().isOk()); // la clave es buena

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + clave.secreto()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PLT-009"));
        mvc.perform(get("/api/v1/aplicaciones")
                        .header("Authorization", "Bearer " + clave.secreto())
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PLT-009"));
    }
}
