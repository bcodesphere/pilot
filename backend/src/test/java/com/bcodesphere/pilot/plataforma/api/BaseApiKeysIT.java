package com.bcodesphere.pilot.plataforma.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.compartido.api.FiltroRequestId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base de las pruebas de integración de API keys (F1-06): MockMvc con la cadena de seguridad real (las dos cadenas),
 * siembra con el dueño de la base y utilidades para iniciar sesión, crear claves y usarlas con su secreto real. Los
 * JWT se simulan igual que en {@code IdentidadYEmpresaActivaIT}; las API keys se crean de verdad por la API.
 */
abstract class BaseApiKeysIT extends BasePlataformaIT {

    @Autowired
    private WebApplicationContext contexto;

    /** MockMvc con el filtro de X-Request-Id y las cadenas de seguridad reales. */
    protected MockMvc mvc;

    /** Cliente JDBC como dueño de la base: siembra y verifica sin RLS. */
    protected final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    @BeforeEach
    void prepararMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
    }

    /** JWT simulado de un usuario. */
    protected static RequestPostProcessor token(String sub) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", sub.replace("sub-", "u") + "@prueba.sv");
            j.claim("name", "Nombre de " + sub);
            j.claim("email_verified", true);
            j.claim("telefono", "70001234");
        });
    }

    protected static String nuevoSub() {
        return "sub-" + UUID.randomUUID();
    }

    /** Primer inicio de sesión: crea usuario y empresa personal y devuelve el cuerpo de {@code GET /me}. */
    protected String iniciarSesion(String sub) throws Exception {
        return mvc.perform(get("/api/v1/me").with(token(sub)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    protected static UUID empresaDe(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.membresias[0].empresaId"));
    }

    protected static UUID usuarioDe(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.id"));
    }

    /** Un segundo usuario con el rol dado en la empresa (siembra como dueño). */
    protected String sembrarMiembro(UUID empresa, String rol) throws Exception {
        String sub = nuevoSub();
        UUID usuario = usuarioDe(iniciarSesion(sub));
        duenio.sql("INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')")
                .params(empresa, usuario, rol)
                .update();
        return sub;
    }

    protected int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    /** {@code POST /api-keys} con el cuerpo dado. */
    protected ResultActions crear(String sub, UUID empresa, String cuerpo) throws Exception {
        return mvc.perform(post("/api/v1/api-keys")
                .with(token(sub))
                .header("X-Empresa-Id", empresa.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    /** Cuerpo válido con el nombre dado, el alcance único de 1.0 y sin vencimiento. */
    protected static String cuerpoValido(String nombre) {
        return "{\"nombre\":\"" + nombre + "\",\"alcances\":[\"integracion:operaciones\"],\"expiraEn\":null}";
    }

    /** Clave creada de verdad por la API: su id y el secreto completo (pk_xxxx.secreto). */
    protected record ClaveCreada(UUID id, String prefijo, String secreto) {}

    /** Crea una clave válida por la API y devuelve su id, prefijo y secreto completo. */
    protected ClaveCreada crearClave(String sub, UUID empresa, String nombre) throws Exception {
        String cuerpo = crear(sub, empresa, cuerpoValido(nombre))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new ClaveCreada(
                UUID.fromString(JsonPath.read(cuerpo, "$.id")),
                JsonPath.read(cuerpo, "$.prefijo"),
                JsonPath.read(cuerpo, "$.secreto"));
    }

    /** {@code POST} al controlador de prueba con una API key (o sin credencial si es nula). */
    protected ResultActions usarClave(String credencial, String ruta) throws Exception {
        var peticion = post("/api/v1/integraciones/n8n/" + ruta);
        if (credencial != null) {
            peticion.header("Authorization", "Bearer " + credencial);
        }
        return mvc.perform(peticion);
    }
}
