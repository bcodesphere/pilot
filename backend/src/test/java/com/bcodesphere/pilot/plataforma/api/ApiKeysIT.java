package com.bcodesphere.pilot.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pruebas de integración de la gestión de API keys de F1-06 ({@code GET}, {@code POST} y {@code DELETE /api-keys}):
 * secreto mostrado una sola vez, solo el hash Argon2id en la base, validaciones 422, rol mínimo, paginación por cursor,
 * revocación idempotente y aislamiento por empresa (CLAUDE.md 2.1, 8.4, 13, 14; ADR-026). Contexto completo contra
 * PostgreSQL real como {@code pilot_app}.
 */
@Import(ConfiguracionApiKeysDePrueba.class)
class ApiKeysIT extends BaseApiKeysIT {

    private ResultActions listar(String sub, UUID empresa, String consulta) throws Exception {
        return mvc.perform(
                get("/api/v1/api-keys" + consulta).with(token(sub)).header("X-Empresa-Id", empresa.toString()));
    }

    private ResultActions revocar(String sub, UUID empresa, UUID id) throws Exception {
        return mvc.perform(
                delete("/api/v1/api-keys/" + id).with(token(sub)).header("X-Empresa-Id", empresa.toString()));
    }

    // ------------------------------------------------------------------------------------------------- crear

    /**
     * Regla (CLAUDE.md 14.1, plan F1-06): el 201 trae el secreto completo con formato pk_xxxxxxxx.secreto; en la base
     * queda solo el hash Argon2id, sin el secreto; la lista posterior no trae ni secreto ni hash; y la auditoría no
     * contiene el secreto ni el hash.
     */
    @Test
    void crearMuestraElSecretoUnaVezYGuardaSoloElHashArgon2id() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        String cuerpo = crear(sub, empresa, cuerpoValido("  n8n producción  "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secreto")
                        .value(org.hamcrest.Matchers.matchesPattern("^pk_[a-z0-9]{8}\\.[A-Za-z0-9_-]{43,}$")))
                .andExpect(jsonPath("$.nombre").value("n8n producción"))
                .andExpect(jsonPath("$.alcances[0]").value("integracion:operaciones"))
                .andExpect(jsonPath("$.expiraEn").doesNotExist())
                .andExpect(jsonPath("$.revocadaEn").doesNotExist())
                .andExpect(jsonPath("$.ultimoUsoEn").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String completa = JsonPath.read(cuerpo, "$.secreto");
        String secreto = completa.substring(completa.indexOf('.') + 1);
        String prefijo = JsonPath.read(cuerpo, "$.prefijo");
        UUID id = UUID.fromString(JsonPath.read(cuerpo, "$.id"));
        assertThat(completa).startsWith(prefijo + ".");

        // En la base: hash Argon2id que no contiene el secreto, y el creador registrado
        String hash = duenio.sql("SELECT hash_secreto FROM api_key WHERE id = ?")
                .param(id)
                .query(String.class)
                .single();
        assertThat(hash).startsWith("$argon2id$").doesNotContain(secreto);
        assertThat(contar("SELECT count(*) FROM api_key WHERE id = ? AND creado_por <> ''", id))
                .isEqualTo(1);

        // Un GET posterior no trae el secreto ni el hash
        String lista = listar(sub, empresa, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos[0].prefijo").value(prefijo))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(lista)
                .doesNotContain(secreto)
                .doesNotContain("secreto")
                .doesNotContain(hash)
                .doesNotContain("hash");

        // La auditoría identifica la clave pero nunca lleva el secreto ni el hash
        String auditoria = duenio.sql("SELECT valor_nuevo::text FROM auditoria WHERE entidad = 'api_key'"
                        + " AND entidad_id = ? AND accion = 'CREAR'")
                .param(id.toString())
                .query(String.class)
                .single();
        assertThat(auditoria)
                .contains(prefijo)
                .doesNotContain(secreto)
                .doesNotContain(hash)
                .doesNotContain("argon2");
    }

    /** Regla (plan F1-06): alcances repetidos son 422 PLT-002 con errores[].campo = alcances (contrato sin uniqueItems). */
    @Test
    void losAlcancesRepetidosDan422ConElCampoAlcances() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        crear(
                        sub,
                        empresa,
                        "{\"nombre\":\"n8n\",\"alcances\":[\"integracion:operaciones\",\"integracion:operaciones\"],"
                                + "\"expiraEn\":null}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("alcances"));
        assertThat(contar("SELECT count(*) FROM api_key WHERE empresa_id = ?", empresa))
                .isZero();
    }

    /** Regla (plan F1-06): un vencimiento en el pasado es 422 PLT-002 en expiraEn; uno futuro se acepta. */
    @Test
    void unVencimientoPasadoDa422YUnoFuturoSeAcepta() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        crear(
                        sub,
                        empresa,
                        "{\"nombre\":\"n8n\",\"alcances\":[\"integracion:operaciones\"],"
                                + "\"expiraEn\":\"2020-01-01T00:00:00Z\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("expiraEn"));

        crear(
                        sub,
                        empresa,
                        "{\"nombre\":\"n8n\",\"alcances\":[\"integracion:operaciones\"],"
                                + "\"expiraEn\":\"2999-01-01T00:00:00Z\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty());
    }

    /** Regla (plan F1-06): un nombre que solo tiene espacios queda vacío tras recortarlo: 422 PLT-002 en nombre. */
    @Test
    void unNombreSoloDeEspaciosDa422() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        crear(sub, empresa, cuerpoValido("     "))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
    }

    /** Regla (CLAUDE.md 14.2): el rol mínimo es admin_empresa; contador y auditor reciben 403 PLT-010 en las tres. */
    @Test
    void soloElAdminEmpresaGestionaClaves() throws Exception {
        String admin = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(admin));
        ClaveCreada clave = crearClave(admin, empresa, "existente");

        for (String rol : List.of("contador", "auditor")) {
            String sub = sembrarMiembro(empresa, rol);
            crear(sub, empresa, cuerpoValido("intento"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-010"));
            listar(sub, empresa, "")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-010"));
            revocar(sub, empresa, clave.id())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-010"));
        }
        assertThat(contar("SELECT count(*) FROM api_key WHERE empresa_id = ?", empresa))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------------ listar

    /**
     * Regla (CLAUDE.md 13): con limite=1 el cursor recorre todas las claves (vigentes y revocadas) sin repetir ni
     * saltar ninguna, y solo se ven las de la empresa activa aunque otra empresa tenga claves.
     */
    @Test
    void listarConLimiteUnoRecorreTodasSinRepetirYSoloVeLasDeSuEmpresa() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        String otroSub = nuevoSub();
        UUID otraEmpresa = empresaDe(iniciarSesion(otroSub));
        Set<UUID> esperadas = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            esperadas.add(crearClave(sub, empresa, "clave " + i).id());
        }
        // Una revocada también se lista
        UUID revocada = esperadas.iterator().next();
        revocar(sub, empresa, revocada).andExpect(status().isNoContent());
        UUID ajena = crearClave(otroSub, otraEmpresa, "ajena").id();

        List<UUID> vistas = new ArrayList<>();
        String cursor = null;
        int paginas = 0;
        do {
            String consulta = "?limite=1" + (cursor == null ? "" : "&cursor=" + cursor);
            String cuerpo = listar(sub, empresa, consulta)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.elementos.length()").value(1))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            vistas.add(UUID.fromString(JsonPath.read(cuerpo, "$.elementos[0].id")));
            cursor = JsonPath.read(cuerpo, "$.siguienteCursor");
            paginas++;
        } while (cursor != null && paginas < 20);

        assertThat(vistas).hasSize(4).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(esperadas);
        assertThat(vistas).doesNotContain(ajena);
        // Orden: creación más reciente primero (UUID v7 crece con el tiempo, y creado_en también)
        assertThat(vistas).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        // La revocada trae su fecha de revocación
        listar(sub, empresa, "")
                .andExpect(jsonPath("$.elementos[?(@.id == '" + revocada + "')].revocadaEn")
                        .isNotEmpty());
    }

    /** Regla (CLAUDE.md 13): un cursor alterado (texto inventado) es 422 PLT-002 en el campo cursor. */
    @Test
    void unCursorAlteradoDa422() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        crearClave(sub, empresa, "una");

        listar(sub, empresa, "?cursor=esto-no-es-un-cursor")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("cursor"));
    }

    // ----------------------------------------------------------------------------------------------- revocar

    /**
     * Regla (plan F1-06): revocar es 204 y fija revocada_en una sola vez; revocar otra vez es 204 SIN escribir (misma
     * fecha de revocación y una sola auditoría REVOCAR).
     */
    @Test
    void revocarEsIdempotenteYNoVuelveAEscribir() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        ClaveCreada clave = crearClave(sub, empresa, "n8n");

        revocar(sub, empresa, clave.id()).andExpect(status().isNoContent());
        var primera = duenio.sql("SELECT revocada_en::text FROM api_key WHERE id = ?")
                .param(clave.id())
                .query(String.class)
                .single();
        assertThat(primera).isNotNull();

        revocar(sub, empresa, clave.id()).andExpect(status().isNoContent());
        var segunda = duenio.sql("SELECT revocada_en::text FROM api_key WHERE id = ?")
                .param(clave.id())
                .query(String.class)
                .single();

        assertThat(segunda).isEqualTo(primera);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE entidad = 'api_key' AND entidad_id = ?"
                                + " AND accion = 'REVOCAR'",
                        clave.id().toString()))
                .isEqualTo(1);
    }

    /** Regla (PLT-017): un id inexistente o de otra empresa es 404 y la clave ajena NO se revoca. */
    @Test
    void revocarUnaClaveInexistenteOAjenaDa404() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        String otroSub = nuevoSub();
        UUID otraEmpresa = empresaDe(iniciarSesion(otroSub));
        ClaveCreada ajena = crearClave(otroSub, otraEmpresa, "de la otra empresa");

        revocar(sub, empresa, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        revocar(sub, empresa, ajena.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));

        assertThat(contar("SELECT count(*) FROM api_key WHERE id = ? AND revocada_en IS NULL", ajena.id()))
                .isEqualTo(1);
    }
}
