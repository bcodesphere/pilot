package com.bcodesphere.pilot.aceptacion.f1;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * Base de las pruebas de aceptación de la fase F1 (F1-10): contexto completo de Spring contra PostgreSQL real, con la
 * aplicación conectada como {@code pilot_app} y las dos cadenas de seguridad reales. Los JWT se simulan igual que en
 * F1-04 (el emisor OIDC de {@link BasePlataformaIT} es inalcanzable a propósito) y la siembra y las verificaciones se
 * hacen con el dueño de la base, que salta RLS. Cada clase hija siembra sus propios datos con UUID propios, así que
 * las clases son independientes entre sí.
 */
abstract class BaseAceptacionF1IT extends BasePlataformaIT {

    @Autowired
    private WebApplicationContext contexto;

    /** MockMvc con el filtro de X-Request-Id y las cadenas de seguridad reales. */
    protected MockMvc mvc;

    /** Cliente JDBC como dueño de la base: siembra y verifica sin RLS. */
    protected final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    /** Construye el MockMvc antes de cada prueba. */
    @BeforeEach
    void prepararMvc() {
        // 1. Filtro de correlación y seguridad reales, sin atajos de prueba
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
    }

    /** JWT simulado con los claims que Keycloak entrega en el realm {@code pilot} (correo, nombre y teléfono). */
    protected static RequestPostProcessor token(String sub) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", sub.replace("sub-", "u") + "@prueba.sv");
            j.claim("name", "Nombre de " + sub);
            j.claim("email_verified", true);
            j.claim("telefono", "70001234");
        });
    }

    /** Un {@code sub} nuevo y único, como el de un usuario que nunca inició sesión. */
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

    /** Id de la primera empresa de la respuesta de {@code GET /me}. */
    protected static UUID empresaDe(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.membresias[0].empresaId"));
    }

    /** Id del usuario de la respuesta de {@code GET /me}. */
    protected static UUID usuarioDe(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.id"));
    }

    /** Cuenta filas con SQL como dueño. */
    protected int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    /** {@code GET} de una ruta de usuario con la empresa activa indicada. */
    protected ResultActions consultar(String sub, UUID empresaActiva, String ruta) throws Exception {
        return mvc.perform(get("/api/v1" + ruta).with(token(sub)).header("X-Empresa-Id", empresaActiva.toString()));
    }

    /** {@code POST /aplicaciones/{codigo}/instalacion} con la empresa activa indicada. */
    protected ResultActions instalar(String sub, UUID empresaActiva, String codigo) throws Exception {
        return mvc.perform(post("/api/v1/aplicaciones/" + codigo + "/instalacion")
                .with(token(sub))
                .header("X-Empresa-Id", empresaActiva.toString()));
    }

    /** {@code POST /api-keys} con alcance único de 1.0 y sin vencimiento; devuelve el cuerpo de la respuesta 201. */
    protected String crearClaveApi(String sub, UUID empresaActiva, String nombre) throws Exception {
        return mvc.perform(post("/api/v1/api-keys")
                        .with(token(sub))
                        .header("X-Empresa-Id", empresaActiva.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre
                                + "\",\"alcances\":[\"integracion:operaciones\"],\"expiraEn\":null}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** {@code DELETE /api-keys/{id}} con la empresa activa indicada. */
    protected ResultActions revocarClaveApi(String sub, UUID empresaActiva, UUID claveId) throws Exception {
        return mvc.perform(delete("/api/v1/api-keys/" + claveId)
                .with(token(sub))
                .header("X-Empresa-Id", empresaActiva.toString()));
    }

    /**
     * Siembra (como dueño) una empresa jurídica con NIT aleatorio y membresía de {@code admin_empresa} para el
     * usuario. En 1.0 no hay interfaz para agregar miembros (ADR-032), así que la segunda empresa solo puede sembrarse.
     */
    protected UUID sembrarEmpresaConAdmin(UUID usuario, String nombre) {
        UUID empresa = UUID.randomUUID();
        // 1. NIT de 14 dígitos único por siembra (la columna es UNIQUE)
        String nit =
                String.format("%014d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000_000L));
        duenio.sql("INSERT INTO empresa (id, tipo, nit, nombre) VALUES (?, 'JURIDICA', ?, ?)")
                .params(empresa, nit, nombre)
                .update();
        // 2. Membresía activa del usuario como administrador
        duenio.sql(
                        "INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, 'admin_empresa', 'ACTIVA')")
                .params(empresa, usuario)
                .update();
        return empresa;
    }
}
