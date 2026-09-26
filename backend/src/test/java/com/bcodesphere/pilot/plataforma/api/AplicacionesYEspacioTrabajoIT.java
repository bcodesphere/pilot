package com.bcodesphere.pilot.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.compartido.api.FiltroRequestId;
import com.bcodesphere.pilot.plataforma.AplicacionInstalada;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de integración de F1-05: catálogo e instalación de apps, evento {@code AplicacionInstalada}, nombre del
 * espacio de trabajo con ETag/If-Match, exigencia de {@code X-Empresa-Id} en rutas de app y aislamiento RLS
 * (CLAUDE.md 4.4, 4.5, 8.3, 8.4; ADR-026, ADR-030, ADR-032). Contexto completo contra PostgreSQL real como
 * {@code pilot_app}; el JWT se simula igual que en {@code IdentidadYEmpresaActivaIT}.
 */
@Import(AplicacionesYEspacioTrabajoIT.ConfiguracionDePrueba.class)
class AplicacionesYEspacioTrabajoIT extends BasePlataformaIT {

    /** Oyente de prueba del evento: registra qué recibió, si había transacción y puede demorarse o fallar. */
    static class OyenteDePrueba {
        final List<AplicacionInstalada> recibidos = new CopyOnWriteArrayList<>();
        final List<Boolean> conTransaccion = new CopyOnWriteArrayList<>();
        volatile boolean fallar;
        volatile long demoraMs;

        @EventListener
        void alInstalar(AplicacionInstalada evento) throws InterruptedException {
            recibidos.add(evento);
            conTransaccion.add(TransactionSynchronizationManager.isActualTransactionActive());
            // La demora fuerza que dos instalaciones concurrentes se traslapen de verdad
            if (demoraMs > 0) {
                Thread.sleep(demoraMs);
            }
            if (fallar) {
                throw new IllegalStateException("oyente de prueba fallido");
            }
        }
    }

    /** Ruta de una app sin header obligatorio: si el filtro no la corta, llega aquí y el contador lo delata. */
    @RestController
    @RequestMapping("/api/v1/contabilidad")
    public static class ControladorContabilidadPrueba {
        static final AtomicInteger LLAMADAS = new AtomicInteger();

        @GetMapping("/prueba")
        public String prueba() {
            LLAMADAS.incrementAndGet();
            return "ok";
        }
    }

    /** Beans de prueba. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionDePrueba {
        @Bean
        OyenteDePrueba oyenteDePrueba() {
            return new OyenteDePrueba();
        }

        @Bean
        ControladorContabilidadPrueba controladorContabilidadPrueba() {
            return new ControladorContabilidadPrueba();
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private OyenteDePrueba oyente;

    private MockMvc mvc;

    /** Cliente JDBC como dueño para sembrar y verificar sin RLS. */
    private final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    /** MockMvc con el filtro de X-Request-Id y la cadena de seguridad real; deja el oyente en su estado normal. */
    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
        oyente.fallar = false;
        oyente.demoraMs = 0;
        ControladorContabilidadPrueba.LLAMADAS.set(0);
    }

    // ---------------------------------------------------------------------------------------- utilidades

    private static RequestPostProcessor token(String sub) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", sub.replace("sub-", "u") + "@prueba.sv");
            j.claim("name", "Nombre de " + sub);
            j.claim("email_verified", true);
            j.claim("telefono", "70001234");
        });
    }

    private static String nuevoSub() {
        return "sub-" + UUID.randomUUID();
    }

    /** Primer inicio de sesión: devuelve el cuerpo de {@code GET /me}. */
    private String iniciarSesion(String sub) throws Exception {
        return mvc.perform(get("/api/v1/me").with(token(sub)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static UUID empresaDe(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.membresias[0].empresaId"));
    }

    private static UUID usuarioDe(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.id"));
    }

    private int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    /** Un segundo usuario con el rol dado en la empresa (siembra como dueño). */
    private String sembrarMiembro(UUID empresa, String rol) throws Exception {
        String sub = nuevoSub();
        UUID usuario = usuarioDe(iniciarSesion(sub));
        duenio.sql("INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')")
                .params(empresa, usuario, rol)
                .update();
        return sub;
    }

    private ResultActions instalar(String sub, UUID empresa, String codigo) throws Exception {
        return mvc.perform(post("/api/v1/aplicaciones/" + codigo + "/instalacion")
                .with(token(sub))
                .header("X-Empresa-Id", empresa.toString()));
    }

    private ResultActions renombrar(String sub, UUID empresa, UUID ruta, String ifMatch, String nombre)
            throws Exception {
        var peticion = patch("/api/v1/empresas/" + ruta)
                .with(token(sub))
                .header("X-Empresa-Id", empresa.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"" + nombre + "\"}");
        if (ifMatch != null) {
            peticion.header("If-Match", ifMatch);
        }
        return mvc.perform(peticion);
    }

    // ------------------------------------------------------------------ tarea 1: rutas de app sin empresa

    /**
     * Regla (F1-04 menor 1): una ruta de app sin X-Empresa-Id se corta en el filtro con 422 PLT-002, sin llegar al
     * controlador (el contador lo demuestra); {@code /me} sin header sigue en 200.
     */
    @Test
    void unaRutaDeAppSinHeaderDeEmpresaSeCortaAntesDelControlador() throws Exception {
        String sub = nuevoSub();

        mvc.perform(get("/api/v1/contabilidad/prueba").with(token(sub)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("X-Empresa-Id"));
        assertThat(ControladorContabilidadPrueba.LLAMADAS.get()).isZero();

        mvc.perform(get("/api/v1/me").with(token(sub))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------ catálogo

    /**
     * Regla (ADR-030): empresa nueva ve contabilidad DISPONIBLE y las 5 Enterprise BLOQUEADA_ENTERPRISE en el orden
     * del catálogo; tras instalar, contabilidad es INSTALADA con instaladaEn. Un auditor puede listar.
     */
    @Test
    void elCatalogoMuestraElEstadoDeCadaAppYCambiaTrasInstalar() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        String auditor = sembrarMiembro(empresa, "auditor");

        mvc.perform(get("/api/v1/aplicaciones").with(token(auditor)).header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[*].codigo")
                        .value(org.hamcrest.Matchers.contains(
                                "contabilidad", "ventas", "clientes", "proveedores", "inventario", "marketing")))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"))
                .andExpect(jsonPath("$[0].instaladaEn").doesNotExist())
                .andExpect(jsonPath("$[1].estado").value("BLOQUEADA_ENTERPRISE"))
                .andExpect(jsonPath("$[5].estado").value("BLOQUEADA_ENTERPRISE"));

        instalar(sub, empresa, "contabilidad").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/aplicaciones").with(token(auditor)).header("X-Empresa-Id", empresa.toString()))
                .andExpect(jsonPath("$[0].estado").value("INSTALADA"))
                .andExpect(jsonPath("$[0].instaladaEn").isNotEmpty())
                .andExpect(jsonPath("$[1].estado").value("BLOQUEADA_ENTERPRISE"));
    }

    // ---------------------------------------------------------------------------------------- instalación

    /**
     * Regla (ADR-030 punto 4): la primera instalación es 201 y la segunda 200, con una sola fila, una sola auditoría y
     * un solo evento; después la ruta de la app deja de dar PLT-004.
     */
    @Test
    void instalarEsIdempotenteYAuditaYPublicaUnaSolaVez() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        instalar(sub, empresa, "contabilidad")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("INSTALADA"));
        instalar(sub, empresa, "contabilidad")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INSTALADA"));

        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa_aplicacion'",
                        empresa))
                .isEqualTo(1);
        assertThat(oyente.recibidos.stream()
                        .filter(e -> e.empresaId().valor().equals(empresa))
                        .count())
                .isEqualTo(1);

        // PLT-004 superado: la ruta de la app ya responde
        mvc.perform(get("/api/v1/contabilidad/prueba").with(token(sub)).header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isOk());
    }

    /** Regla (ADR-030): una Enterprise da 403 PLT-011 y un código inexistente 404 PLT-017; sin filas ni eventos. */
    @Test
    void unaAppEnterpriseOInexistenteNoSeInstala() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        instalar(sub, empresa, "ventas")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-011"));
        instalar(sub, empresa, "inexistente")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));

        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isZero();
        assertThat(oyente.recibidos.stream()
                        .filter(e -> e.empresaId().valor().equals(empresa))
                        .count())
                .isZero();
    }

    /** Regla (CLAUDE.md 14.2): instalar exige admin_empresa; un contador recibe 403 PLT-010 y no cambia nada. */
    @Test
    void unContadorNoPuedeInstalar() throws Exception {
        UUID empresa = empresaDe(iniciarSesion(nuevoSub()));
        String contador = sembrarMiembro(empresa, "contador");

        instalar(contador, empresa, "contabilidad")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isZero();
    }

    /** Regla (ADR-030 punto 5): el oyente recibe el evento con una transacción activa (el de la instalación). */
    @Test
    void elEventoSePublicaDentroDeLaTransaccion() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        int antes = oyente.conTransaccion.size();

        instalar(sub, empresa, "contabilidad").andExpect(status().isCreated());

        assertThat(oyente.conTransaccion.subList(antes, oyente.conTransaccion.size()))
                .containsExactly(true);
    }

    /**
     * Regla (ADR-030 punto 5): si un oyente lanza una excepción, la instalación se revierte: sin fila, sin auditoría y
     * con respuesta distinta de 201; después, sin el fallo, se puede instalar.
     */
    @Test
    void siElOyenteFallaLaInstalacionSeRevierte() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        oyente.fallar = true;

        instalar(sub, empresa, "contabilidad").andExpect(status().is5xxServerError());

        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isZero();
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa_aplicacion'",
                        empresa))
                .isZero();

        oyente.fallar = false;
        instalar(sub, empresa, "contabilidad").andExpect(status().isCreated());
    }

    /** Regla (ADR-030): dos instalaciones simultáneas dan un 201 y un 200, nunca 500, y un solo evento. */
    @Test
    void dosInstalacionesEnParaleloDan201Y200ConUnSoloEvento() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));
        // La demora dentro de la transacción obliga a que la segunda espere a la primera en el índice único
        oyente.demoraMs = 400;
        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    return instalar(sub, empresa, "contabilidad")
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            salida.countDown();
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> r : resultados) {
                estados.add(r.get(30, TimeUnit.SECONDS));
            }
            assertThat(estados).containsExactlyInAnyOrder(200, 201);
        } finally {
            pool.shutdownNow();
        }
        assertThat(oyente.recibidos.stream()
                        .filter(e -> e.empresaId().valor().equals(empresa))
                        .count())
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------ empresa

    /**
     * Regla (CLAUDE.md 8.3, ADR-032): GET da ETag "0"; PATCH con If-Match "0" da 200 y ETag "1"; repetir el mismo
     * If-Match da 412 PLT-016; sin If-Match, 428 PLT-015. NIT, NRC y nombre comercial siguen nulos.
     */
    @Test
    void elNombreSeEditaConEtagYIfMatch() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        mvc.perform(get("/api/v1/empresas/" + empresa).with(token(sub)).header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.nit").doesNotExist());

        renombrar(sub, empresa, empresa, "\"0\"", "Espacio nuevo")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.nombre").value("Espacio nuevo"));
        renombrar(sub, empresa, empresa, "\"0\"", "Otro nombre")
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.codigo").value("PLT-016"));
        renombrar(sub, empresa, empresa, null, "Otro nombre")
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.codigo").value("PLT-015"));

        // Solo cambió el nombre: los datos empresariales siguen nulos
        assertThat(contar(
                        "SELECT count(*) FROM empresa WHERE id = ? AND nit IS NULL AND nrc IS NULL"
                                + " AND nombre_comercial IS NULL AND nombre = 'Espacio nuevo' AND version = 1",
                        empresa))
                .isEqualTo(1);
    }

    /** Regla: un If-Match mal formado da 412 PLT-016 aunque el nombre no cambie. */
    @Test
    void unIfMatchMalFormadoDa412AunqueElNombreNoCambie() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        renombrar(sub, empresa, empresa, "0", "Nombre de " + sub)
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.codigo").value("PLT-016"));
        // Con versión vieja y el mismo nombre también es 412
        renombrar(sub, empresa, empresa, "\"7\"", "Nombre de " + sub).andExpect(status().isPreconditionFailed());
    }

    /** Regla: nombre vacío tras quitar espacios = 422 PLT-002; el mismo nombre otra vez = 200 sin cambiar versión. */
    @Test
    void nombreVacioDa422YElMismoNombreNoCambiaLaVersion() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        renombrar(sub, empresa, empresa, "\"0\"", "   ")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"));
        // Los espacios de los extremos no cuentan: es el mismo nombre, así que no se escribe nada
        renombrar(sub, empresa, empresa, "\"0\"", "  Nombre de " + sub + "  ")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""));
        assertThat(contar("SELECT version FROM empresa WHERE id = ?", empresa)).isZero();
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa'"
                                + " AND accion = 'ACTUALIZAR'",
                        empresa))
                .isZero();
    }

    /** Regla (CLAUDE.md 1.1.11): el cambio de nombre se audita con el valor anterior y el nuevo, sin espacios sobrantes. */
    @Test
    void elCambioDeNombreSeAuditaConAnteriorYNuevo() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaDe(iniciarSesion(sub));

        renombrar(sub, empresa, empresa, "\"0\"", "  Mi taller  ").andExpect(status().isOk());

        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa'"
                                + " AND accion = 'ACTUALIZAR' AND valor_anterior->>'nombre' = ?"
                                + " AND valor_nuevo->>'nombre' = 'Mi taller'",
                        empresa,
                        "Nombre de " + sub))
                .isEqualTo(1);
    }

    /** Regla (CLAUDE.md 14.2): un contador no puede ver ni editar el espacio: 403 PLT-010. */
    @Test
    void unContadorNoAccedeAlEspacioDeTrabajo() throws Exception {
        UUID empresa = empresaDe(iniciarSesion(nuevoSub()));
        String contador = sembrarMiembro(empresa, "contador");

        mvc.perform(get("/api/v1/empresas/" + empresa).with(token(contador)).header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        renombrar(contador, empresa, empresa, "\"0\"", "Intento")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }

    // ------------------------------------------------------------------------------------------------ RLS

    /**
     * Regla (CLAUDE.md 4.5): el usuario de la empresa A no lee ni modifica la empresa ni las apps de la B: por la API
     * (404 PLT-017 con la empresa B en la ruta; 403 PLT-003 con la B en el header) y por SQL como pilot_app.
     */
    @Test
    void unaEmpresaNoVeNiModificaLaOtra() throws Exception {
        String subA = nuevoSub();
        String subB = nuevoSub();
        UUID empresaA = empresaDe(iniciarSesion(subA));
        UUID empresaB = empresaDe(iniciarSesion(subB));
        instalar(subB, empresaB, "contabilidad").andExpect(status().isCreated());

        // 1. Por la API: la empresa ajena en la ruta es 404 y en el header es 403; nada cambia
        mvc.perform(get("/api/v1/empresas/" + empresaB).with(token(subA)).header("X-Empresa-Id", empresaA.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        renombrar(subA, empresaA, empresaB, "\"0\"", "Robado")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        mvc.perform(get("/api/v1/empresas/" + empresaB).with(token(subA)).header("X-Empresa-Id", empresaB.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-003"));
        instalar(subA, empresaB, "contabilidad").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/aplicaciones").with(token(subA)).header("X-Empresa-Id", empresaA.toString()))
                .andExpect(jsonPath("$[0].estado").value("DISPONIBLE"));
        assertThat(contar("SELECT count(*) FROM empresa WHERE id = ? AND nombre = 'Robado'", empresaB))
                .isZero();

        // 2. Por SQL como pilot_app con la empresa A fijada: la B es invisible e intocable
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement fija = c.prepareStatement("SELECT set_config('app.empresa_id', ?, true)")) {
                fija.setString(1, empresaA.toString());
                fija.execute();
            }
            try (PreparedStatement ps =
                    c.prepareStatement("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?")) {
                ps.setObject(1, empresaB);
                var rs = ps.executeQuery();
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE empresa SET nombre = 'Robado' WHERE id = ?")) {
                ps.setObject(1, empresaB);
                assertThat(ps.executeUpdate()).isZero();
            }
            c.rollback();
        }
    }
}
