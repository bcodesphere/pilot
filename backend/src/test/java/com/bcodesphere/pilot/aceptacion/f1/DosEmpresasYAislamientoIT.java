package com.bcodesphere.pilot.aceptacion.f1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Aceptación F1, criterio 3 del plan: "con membresías de prueba en dos empresas, cambiar X-Empresa-Id solo muestra los
 * datos de la empresa activa; un usuario nunca ve los datos de otro" (docs/plan-de-trabajo.md F1; CLAUDE.md 4.5;
 * ADR-002, ADR-032: en 1.0 no se agregan miembros desde la interfaz, por eso las dos empresas se siembran).
 *
 * <p>Datos: el usuario U es admin de la empresa A (su personal) y de la B (sembrada); V tiene su propia empresa C.
 * Cada empresa tiene una API key con nombre distinto y un estado distinto de Contabilidad (A instalada, B no).
 */
class DosEmpresasYAislamientoIT extends BaseAceptacionF1IT {

    private String subU;
    private String subV;
    private UUID empresaA;
    private UUID empresaB;
    private UUID empresaC;
    private String claveA;
    private String claveB;
    private String claveC;

    /** Siembra U (A y B), V (C), una API key por empresa y Contabilidad instalada solo en A y en C. */
    @BeforeEach
    void sembrar() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        claveA = "clave-A-" + sufijo;
        claveB = "clave-B-" + sufijo;
        claveC = "clave-C-" + sufijo;

        // 1. U y V inician sesión: cada uno recibe su empresa personal (A y C)
        subU = nuevoSub();
        subV = nuevoSub();
        String meU = iniciarSesion(subU);
        empresaA = empresaDe(meU);
        empresaC = empresaDe(iniciarSesion(subV));

        // 2. B se siembra como dueño con U como admin (ADR-032: no hay interfaz para agregar miembros)
        empresaB = sembrarEmpresaConAdmin(usuarioDe(meU), "Empresa B " + sufijo);

        // 3. Una API key por empresa, creada por la API con la empresa activa correspondiente
        crearClaveApi(subU, empresaA, claveA);
        crearClaveApi(subU, empresaB, claveB);
        crearClaveApi(subV, empresaC, claveC);

        // 4. Estados distintos de Contabilidad: instalada en A y en C, sin instalar en B
        instalar(subU, empresaA, "contabilidad").andExpect(status().isCreated());
        instalar(subV, empresaC, "contabilidad").andExpect(status().isCreated());
    }

    /** Nombres de las API keys que devuelve {@code GET /api-keys} para la empresa activa. */
    private List<String> nombresDeClaves(String sub, UUID empresaActiva) throws Exception {
        String cuerpo = consultar(sub, empresaActiva, "/api-keys")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(cuerpo, "$.elementos[*].nombre");
    }

    /** Fuente: criterio 3 del plan. Con X-Empresa-Id = A, U ve solo lo de A: sus claves, su app instalada y su empresa. */
    @Test
    void conLaEmpresaAActivaSoloSeVeLoDeA() throws Exception {
        assertThat(nombresDeClaves(subU, empresaA)).containsExactly(claveA);
        consultar(subU, empresaA, "/aplicaciones")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'contabilidad')].estado").value("INSTALADA"));
        consultar(subU, empresaA, "/empresas/" + empresaA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(empresaA.toString()))
                .andExpect(jsonPath("$.tipo").value("PERSONAL"));
    }

    /** Fuente: criterio 3 del plan. Al cambiar X-Empresa-Id a B, U ve solo lo de B (claves, estado de la app y empresa). */
    @Test
    void alCambiarALaEmpresaBSoloSeVeLoDeB() throws Exception {
        assertThat(nombresDeClaves(subU, empresaB)).containsExactly(claveB);
        consultar(subU, empresaB, "/aplicaciones")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'contabilidad')].estado").value("DISPONIBLE"));
        consultar(subU, empresaB, "/empresas/" + empresaB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(empresaB.toString()))
                .andExpect(jsonPath("$.tipo").value("JURIDICA"));
    }

    /**
     * Fuente: F1-05 ({@code GestionarEspacioTrabajo}: la ruta solo admite la empresa activa). Pedir la empresa B con A
     * activa NO la muestra: responde 404 {@code PLT-017} (no 403), sin revelar si existe.
     */
    @Test
    void pedirLaEmpresaBConLaAActivaNoLaMuestra() throws Exception {
        consultar(subU, empresaA, "/empresas/" + empresaB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
    }

    /**
     * Fuente: criterio 3 del plan (un usuario nunca ve los datos de otro). V, con su propia empresa C, no ve nada de A
     * ni de B: con C activa solo ve lo suyo, y pedir A o B con C activa da 404.
     */
    @Test
    void otroUsuarioNuncaVeLosDatosDeAniDeB() throws Exception {
        // 1. V ve solo lo suyo
        assertThat(nombresDeClaves(subV, empresaC)).containsExactly(claveC);
        // 2. V no puede leer A ni B pidiéndolas por la ruta con su empresa activa
        consultar(subV, empresaC, "/empresas/" + empresaA).andExpect(status().isNotFound());
        consultar(subV, empresaC, "/empresas/" + empresaB).andExpect(status().isNotFound());
        // 3. Ninguna respuesta de V contiene claves de A ni de B
        String claves =
                consultar(subV, empresaC, "/api-keys").andReturn().getResponse().getContentAsString();
        assertThat(claves).doesNotContain(claveA).doesNotContain(claveB);
    }

    /**
     * Fuente: CLAUDE.md 4.5 (RLS forzado en toda tabla de negocio) y 1.2.11. Por SQL con el rol {@code pilot_app}, y sin
     * pasar por la API, fijar app.empresa_id = A solo deja ver las claves de A, y B solo las de B.
     */
    @Test
    void conPilotAppLaBaseSoloDevuelveLasFilasDeLaEmpresaFijada() throws Exception {
        assertThat(clavesVistasPorPilotApp(empresaA)).containsExactly(claveA);
        assertThat(clavesVistasPorPilotApp(empresaB)).containsExactly(claveB);
        assertThat(clavesVistasPorPilotApp(empresaC)).containsExactly(claveC);
    }

    /** Abre una transacción como pilot_app, fija la empresa y lista los nombres de api_key visibles (RLS). */
    private List<String> clavesVistasPorPilotApp(UUID empresa) throws Exception {
        try (Connection conexion = PostgresContenedor.dataSourceApp().getConnection()) {
            conexion.setAutoCommit(false);
            // 1. Igual que TenantAwareTransactionManager: set_config(..., true) limita el valor a la transacción
            try (PreparedStatement fijar = conexion.prepareStatement("SELECT set_config('app.empresa_id', ?, true)")) {
                fijar.setString(1, empresa.toString());
                fijar.execute();
            }
            // 2. Sin ningún WHERE por empresa: el filtro lo pone solo la política RLS
            List<String> nombres = new ArrayList<>();
            try (PreparedStatement consulta = conexion.prepareStatement("SELECT nombre FROM api_key ORDER BY nombre");
                    ResultSet filas = consulta.executeQuery()) {
                while (filas.next()) {
                    nombres.add(filas.getString(1));
                }
            }
            conexion.rollback();
            return nombres;
        }
    }
}
