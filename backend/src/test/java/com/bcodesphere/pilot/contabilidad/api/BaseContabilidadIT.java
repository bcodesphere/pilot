package com.bcodesphere.pilot.contabilidad.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base de las pruebas de integración de la API contable (F2-03): contexto completo contra PostgreSQL real como
 * {@code pilot_app}, JWT simulado igual que en las IT de F1 y utilidades para crear usuarios, instalar Contabilidad
 * y armar peticiones con {@code X-Empresa-Id}. Cada prueba usa usuarios y empresas nuevos, así que no comparten datos.
 */
abstract class BaseContabilidadIT extends BasePlataformaIT {

    /** Usuario con su empresa personal, del que salen el token y la empresa activa. */
    record Sesion(String sub, UUID usuario, UUID empresa) {}

    @Autowired
    private WebApplicationContext contexto;

    /** Cliente MockMvc con el filtro de X-Request-Id y la cadena de seguridad real. */
    protected MockMvc mvc;

    /** Cliente JDBC como dueño del esquema: siembra y verifica sin RLS. */
    protected final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    /** Construye MockMvc con la cadena real de filtros. */
    @BeforeEach
    void montarMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
    }

    /** JWT simulado de un usuario con correo verificado y teléfono salvadoreño. */
    protected static RequestPostProcessor token(String sub) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", sub.replace("sub-", "u") + "@prueba.sv");
            j.claim("name", "Nombre de " + sub);
            j.claim("email_verified", true);
            j.claim("telefono", "70001234");
        });
    }

    /** Primer inicio de sesión de un usuario nuevo: crea su empresa personal (admin_empresa). */
    protected Sesion nuevaSesion() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        String cuerpo = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me").with(token(sub)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Sesion(
                sub,
                UUID.fromString(JsonPath.read(cuerpo, "$.id")),
                UUID.fromString(JsonPath.read(cuerpo, "$.membresias[0].empresaId")));
    }

    /** Sesión nueva con Contabilidad ya instalada (dispara la precarga). */
    protected Sesion sesionConContabilidad() throws Exception {
        Sesion s = nuevaSesion();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/aplicaciones/contabilidad/instalacion")
                        .with(token(s.sub()))
                        .header("X-Empresa-Id", s.empresa().toString()))
                .andExpect(status().isCreated());
        return s;
    }

    /** Un segundo usuario con el rol dado en la empresa (siembra como dueño); devuelve su sesión. */
    protected Sesion sembrarMiembro(Sesion duenioEmpresa, String rol) throws Exception {
        Sesion otro = nuevaSesion();
        duenio.sql("INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')")
                .params(duenioEmpresa.empresa(), otro.usuario(), rol)
                .update();
        // El miembro actúa sobre la empresa del dueño
        return new Sesion(otro.sub(), otro.usuario(), duenioEmpresa.empresa());
    }

    /** Id de una cuenta de la empresa por su código (como dueño, sin RLS). */
    protected UUID cuentaId(UUID empresa, String codigo) {
        return duenio.sql("SELECT id FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?")
                .params(empresa, codigo)
                .query(UUID.class)
                .single();
    }

    /** Cuenta filas con una consulta como dueño. */
    protected int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    // ---------------------------------------------------------------------------------- peticiones

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder b, Sesion s) {
        return b.with(token(s.sub())).header("X-Empresa-Id", s.empresa().toString());
    }

    protected ResultActions get(Sesion s, String ruta) throws Exception {
        return mvc.perform(con(MockMvcRequestBuilders.get("/api/v1" + ruta), s));
    }

    protected ResultActions post(Sesion s, String ruta, String json) throws Exception {
        return mvc.perform(con(MockMvcRequestBuilders.post("/api/v1" + ruta), s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** POST con {@code Idempotency-Key} (registro y reversión de asientos); la clave nula omite el header. */
    protected ResultActions postConClave(Sesion s, String ruta, String clave, String json) throws Exception {
        MockHttpServletRequestBuilder b = con(MockMvcRequestBuilders.post("/api/v1" + ruta), s);
        if (clave != null) {
            b.header("Idempotency-Key", clave);
        }
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** PATCH con {@code If-Match} opcional (nulo = sin el header). */
    protected ResultActions patch(Sesion s, String ruta, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder b = con(MockMvcRequestBuilders.patch("/api/v1" + ruta), s);
        if (ifMatch != null) {
            b.header("If-Match", ifMatch);
        }
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** PUT con {@code If-Match} opcional (nulo = sin el header). */
    protected ResultActions put(Sesion s, String ruta, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder b = con(MockMvcRequestBuilders.put("/api/v1" + ruta), s);
        if (ifMatch != null) {
            b.header("If-Match", ifMatch);
        }
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Lee un valor del cuerpo de una respuesta. */
    protected static <T> T leer(ResultActions r, String jsonPath) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), jsonPath);
    }
}
