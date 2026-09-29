package com.bcodesphere.pilot.aceptacion.f3;

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
 * Base de las pruebas de aceptación de la fase F3 (F3-05): contexto completo de Spring contra PostgreSQL real, con la
 * aplicación conectada como {@code pilot_app} y las cadenas de seguridad reales. Sigue el mismo patrón que
 * {@code aceptacion.f2.BaseAceptacionF2IT} y {@code contabilidad.api.BaseContabilidadIT}: se escribe aquí en vez de
 * extenderlas porque ambas son package-private de otros paquetes. Cada clase hija crea sus propios usuarios y
 * empresas (UUID propios por sesión), así que las clases son independientes entre sí.
 */
abstract class BaseAceptacionF3IT extends BasePlataformaIT {

    /** Usuario con su empresa activa, del que salen el token y el encabezado {@code X-Empresa-Id}. */
    record Sesion(String sub, UUID usuario, UUID empresa) {}

    @Autowired
    private WebApplicationContext contexto;

    /** MockMvc con el filtro de X-Request-Id y las cadenas de seguridad reales. */
    protected MockMvc mvc;

    /** Cliente JDBC como dueño de la base: siembra y verifica sin RLS. */
    protected final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    /** Construye el MockMvc antes de cada prueba. */
    @BeforeEach
    void prepararMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
    }

    /** JWT simulado con los claims del realm {@code pilot} (correo verificado, nombre y teléfono). */
    protected static RequestPostProcessor token(String sub) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", sub.replace("sub-", "u") + "@prueba.sv");
            j.claim("name", "Nombre de " + sub);
            j.claim("email_verified", true);
            j.claim("telefono", "70001234");
        });
    }

    /** Primer inicio de sesión de un usuario nuevo: crea su empresa personal con rol admin_empresa. */
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

    /** Sesión nueva con Contabilidad ya instalada (exige 201; dispara la precarga). */
    protected Sesion sesionConContabilidad() throws Exception {
        Sesion s = nuevaSesion();
        mvc.perform(con(MockMvcRequestBuilders.post("/api/v1/aplicaciones/contabilidad/instalacion"), s))
                .andExpect(status().isCreated());
        return s;
    }

    /** Siembra (como dueño) un segundo usuario con el rol dado en la empresa de {@code duenioEmpresa}. */
    protected Sesion sembrarMiembro(Sesion duenioEmpresa, String rol) throws Exception {
        Sesion otro = nuevaSesion();
        duenio.sql("INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')")
                .params(duenioEmpresa.empresa(), otro.usuario(), rol)
                .update();
        return new Sesion(otro.sub(), otro.usuario(), duenioEmpresa.empresa());
    }

    /** Id de una cuenta de la empresa por su código (como dueño, sin RLS). */
    protected UUID cuentaId(UUID empresa, String codigo) {
        return duenio.sql("SELECT id FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?")
                .params(empresa, codigo)
                .query(UUID.class)
                .single();
    }

    /** Cuenta filas con SQL como dueño. */
    protected int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    // ---------------------------------------------------------------------------------- peticiones

    /** Agrega el token y la empresa activa de la sesión. */
    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder b, Sesion s) {
        return b.with(token(s.sub())).header("X-Empresa-Id", s.empresa().toString());
    }

    /** {@code GET} de una ruta bajo {@code /api/v1} con la empresa activa de la sesión. */
    protected ResultActions get(Sesion s, String ruta) throws Exception {
        return mvc.perform(con(MockMvcRequestBuilders.get("/api/v1" + ruta), s));
    }

    /** {@code POST} con cuerpo JSON, sin {@code Idempotency-Key}. */
    protected ResultActions post(Sesion s, String ruta, String json) throws Exception {
        return mvc.perform(con(MockMvcRequestBuilders.post("/api/v1" + ruta), s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** {@code POST} con {@code Idempotency-Key} (registro y reversión de asientos); la clave nula omite el header. */
    protected ResultActions postConClave(Sesion s, String ruta, String clave, String json) throws Exception {
        MockHttpServletRequestBuilder b = con(MockMvcRequestBuilders.post("/api/v1" + ruta), s);
        if (clave != null) {
            b.header("Idempotency-Key", clave);
        }
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** {@code PATCH} con {@code If-Match} opcional (nulo = sin el header). */
    protected ResultActions patch(Sesion s, String ruta, String ifMatch, String json) throws Exception {
        MockHttpServletRequestBuilder b = con(MockMvcRequestBuilders.patch("/api/v1" + ruta), s);
        if (ifMatch != null) {
            b.header("If-Match", ifMatch);
        }
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Lee un valor del cuerpo de una respuesta con JsonPath. */
    protected static <T> T leer(ResultActions r, String jsonPath) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), jsonPath);
    }
}
