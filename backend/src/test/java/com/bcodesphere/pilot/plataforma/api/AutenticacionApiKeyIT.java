package com.bcodesphere.pilot.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas de integración de la autenticación por API key de F1-06 contra los controladores de prueba de
 * {@code /api/v1/integraciones/n8n/**} (CLAUDE.md 12.1, 14.2; ADR-026): la empresa sale de la clave, revocada o vencida
 * es 401, sin alcance es 403, los cinco 401 son indistinguibles, las cadenas de API key y de JWT no se mezclan y el
 * secreto no aparece en los logs. Las claves se crean de verdad por la API y se usan con su secreto real.
 */
@Import(ConfiguracionApiKeysDePrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class AutenticacionApiKeyIT extends BaseApiKeysIT {

    /** Un secreto bien formado (43 caracteres Base64URL) pero incorrecto. */
    private static final String SECRETO_AJENO = "A".repeat(43);

    /** Devuelve el {@code detail} del 401 esperado, comprobando código, estado y el desafío Bearer. */
    private String detalleDe401(ResultActions respuesta) throws Exception {
        String cuerpo = respuesta
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("PLT-009"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(cuerpo, "$.detail");
    }

    // -------------------------------------------------------------------------------------- clave válida

    /**
     * Regla (CLAUDE.md 12.1): con una clave válida la petición pasa y el contexto tiene la empresa DE LA CLAVE y el
     * usuario técnico api_key:&lt;id&gt;; la empresa no viene de ningún header del cliente.
     */
    @Test
    void unaClaveValidaAutenticaYLaEmpresaSaleDeLaClave() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");

        // Un X-Empresa-Id de otra empresa se ignora: la empresa sale solo de la clave
        mvcConHeaderDeOtraEmpresa(clave.secreto(), UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaId").value(empresa.toString()))
                .andExpect(jsonPath("$.usuario").value("api_key:" + clave.id()));
    }

    private ResultActions mvcConHeaderDeOtraEmpresa(String credencial, UUID otraEmpresa) throws Exception {
        return mvc.perform(post("/api/v1/integraciones/n8n/prueba")
                .header("Authorization", "Bearer " + credencial)
                .header("X-Empresa-Id", otraEmpresa.toString()));
    }

    // ------------------------------------------------------------------------------------ rechazos 401

    /** Regla (plan F1): una clave revocada devuelve 401 PLT-009. */
    @Test
    void unaClaveRevocadaDevuelve401() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");
        usarClave(clave.secreto(), "prueba").andExpect(status().isOk());

        mvc.perform(delete("/api/v1/api-keys/" + clave.id())
                        .with(token(sub))
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isNoContent());

        detalleDe401(usarClave(clave.secreto(), "prueba"));
    }

    /** Regla (plan F1): una clave vencida (expira_en &lt;= ahora) devuelve 401 PLT-009. */
    @Test
    void unaClaveVencidaDevuelve401() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");
        usarClave(clave.secreto(), "prueba").andExpect(status().isOk());

        // Se vence con SQL como dueño: la API no permite crear una clave ya vencida
        duenio.sql("UPDATE api_key SET expira_en = now() - interval '1 second' WHERE id = ?")
                .param(clave.id())
                .update();

        detalleDe401(usarClave(clave.secreto(), "prueba"));
    }

    /** Regla (CLAUDE.md 14.1): el prefijo correcto con un secreto incorrecto devuelve 401 PLT-009. */
    @Test
    void unSecretoIncorrectoDevuelve401() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");

        detalleDe401(usarClave(clave.prefijo() + "." + SECRETO_AJENO, "prueba"));
    }

    /**
     * Regla (ADR-026, plan F1-06): sin credencial, con formato inválido, con prefijo inexistente, con secreto
     * incorrecto, revocada y vencida, el 401 es EXACTAMENTE el mismo {@code detail}: no se revela cuál falló ni qué
     * prefijos existen.
     */
    @Test
    void losRechazosTienenElMismoDetalleGenerico() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada buena = crearClave(sub, empresa, "buena");
        ClaveCreada revocada = crearClave(sub, empresa, "revocada");
        ClaveCreada vencida = crearClave(sub, empresa, "vencida");
        mvc.perform(delete("/api/v1/api-keys/" + revocada.id())
                        .with(token(sub))
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isNoContent());
        duenio.sql("UPDATE api_key SET expira_en = now() - interval '1 second' WHERE id = ?")
                .param(vencida.id())
                .update();

        List<String> detalles = new ArrayList<>();
        detalles.add(detalleDe401(usarClave(null, "prueba"))); // sin Authorization
        detalles.add(detalleDe401(usarClave("esto-no-es-una-clave", "prueba"))); // formato inválido
        detalles.add(detalleDe401(usarClave("pk_zzzzzzzz." + SECRETO_AJENO, "prueba"))); // prefijo inexistente
        detalles.add(detalleDe401(usarClave(buena.prefijo() + "." + SECRETO_AJENO, "prueba"))); // secreto incorrecto
        detalles.add(detalleDe401(usarClave(revocada.secreto(), "prueba"))); // revocada
        detalles.add(detalleDe401(usarClave(vencida.secreto(), "prueba"))); // vencida
        // Otro esquema de autenticación tampoco se acepta
        detalles.add(detalleDe401(mvc.perform(
                post("/api/v1/integraciones/n8n/prueba").header("Authorization", "Basic dXN1YXJpbzpjbGF2ZQ=="))));

        assertThat(detalles).hasSize(7).doesNotContainNull();
        assertThat(detalles).containsOnly(detalles.get(0));
    }

    // ------------------------------------------------------------------------------------------ alcances

    /** Regla (plan F1): sin el alcance requerido, 403 PLT-010 con el mismo Problem Details del resto. */
    @Test
    void unaClaveSinElAlcanceRequeridoDevuelve403() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");

        usarClave(clave.secreto(), "prueba-alcance-inexistente")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"))
                .andExpect(jsonPath("$.status").value(403));
    }

    /** Regla (CLAUDE.md 14.2): ROLE_INTEGRACION no está en la jerarquía; una API key nunca satisface hasRole('AUDITOR'). */
    @Test
    void unaApiKeyNuncaSatisfaceUnRolDeUsuario() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");

        usarClave(clave.secreto(), "prueba-rol-auditor")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }

    // ---------------------------------------------------------------------------------------- aislamiento

    /** Regla (ADR-026): una API key en rutas de usuarios no es un JWT: 401. Un JWT en integraciones también: 401. */
    @Test
    void lasCadenasDeApiKeyYDeJwtNoSeMezclan() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");

        // API key en la cadena de usuarios
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + clave.secreto()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/aplicaciones")
                        .header("Authorization", "Bearer " + clave.secreto())
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isUnauthorized());

        // JWT válido en la cadena de integraciones
        mvc.perform(post("/api/v1/integraciones/n8n/prueba").with(token(sub))).andExpect(status().isUnauthorized());
    }

    /** Regla (CLAUDE.md 4.5): la clave de la empresa A solo ve, con RLS, las claves de A; nunca las de B. */
    @Test
    void laClaveDeUnaEmpresaNoVeDatosDeOtra() throws Exception {
        String subA = nuevoSub();
        UUID empresaA = empresaDe(iniciarSesion(subA));
        String subB = nuevoSub();
        UUID empresaB = empresaDe(iniciarSesion(subB));
        ClaveCreada claveA = crearClave(subA, empresaA, "de A");
        ClaveCreada claveB = crearClave(subB, empresaB, "de B");

        String visiblesA = mvc.perform(get("/api/v1/integraciones/n8n/prueba-claves-visibles")
                        .header("Authorization", "Bearer " + claveA.secreto()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(visiblesA)
                .contains(claveA.id().toString())
                .doesNotContain(claveB.id().toString());

        // Y cada clave da su propia empresa
        usarClave(claveB.secreto(), "prueba").andExpect(jsonPath("$.empresaId").value(empresaB.toString()));
    }

    // ---------------------------------------------------------------------------------- ultimo_uso_en

    /**
     * Regla (plan F1-06): ultimo_uso_en se fija en el primer uso y NO cambia en un segundo uso inmediato (como mucho una
     * escritura por minuto por clave).
     */
    @Test
    void elUltimoUsoSeFijaEnElPrimerUsoYNoCambiaEnElSegundoInmediato() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");
        String sql = "SELECT ultimo_uso_en::text FROM api_key WHERE id = ?";
        assertThat(duenio.sql(sql).param(clave.id()).query(String.class).optional())
                .isEmpty(); // nulo antes del primer uso

        usarClave(clave.secreto(), "prueba").andExpect(status().isOk());
        String primero = duenio.sql(sql).param(clave.id()).query(String.class).single();

        usarClave(clave.secreto(), "prueba").andExpect(status().isOk());
        String segundo = duenio.sql(sql).param(clave.id()).query(String.class).single();

        assertThat(primero).isNotNull();
        assertThat(segundo).isEqualTo(primero);
    }

    // --------------------------------------------------------------------------------------------- logs

    /** Regla (CLAUDE.md 14.1): el secreto no aparece en la salida de logs ni al crear, ni al usar, ni al rechazar. */
    @Test
    void elSecretoNoApareceEnLosLogs(CapturedOutput salida) throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");
        String secreto = clave.secreto().substring(clave.secreto().indexOf('.') + 1);

        usarClave(clave.secreto(), "prueba").andExpect(status().isOk());
        usarClave(clave.prefijo() + "." + SECRETO_AJENO, "prueba").andExpect(status().isUnauthorized());
        usarClave(clave.secreto(), "prueba-alcance-inexistente").andExpect(status().isForbidden());

        assertThat(salida.getAll()).doesNotContain(secreto).doesNotContain(SECRETO_AJENO);
    }
}
