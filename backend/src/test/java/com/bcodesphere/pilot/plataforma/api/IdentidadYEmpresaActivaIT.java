package com.bcodesphere.pilot.plataforma.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.compartido.api.FiltroRequestId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Pruebas de integración de F1-04: token, alta automática, {@code /me}, empresa activa, roles y app instalada
 * (CLAUDE.md 4.5, 8.4, 14; ADR-026, ADR-028, ADR-029). Usa el contexto completo de Spring contra PostgreSQL real,
 * conectado como {@code pilot_app}; la siembra y las verificaciones usan el dueño (superusuario en pruebas, salta RLS).
 * El JWT se simula con el soporte de pruebas de Spring Security.
 */
@Import(IdentidadYEmpresaActivaIT.ConfiguracionDePrueba.class)
@ExtendWith(OutputCaptureExtension.class)
class IdentidadYEmpresaActivaIT extends BasePlataformaIT {

    /** Controladores solo de prueba, fuera de {@code /me}: consulta con RLS, roles y una ruta de la app contabilidad. */
    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionDePrueba {

        @Bean
        ControladorPlataformaPrueba controladorPlataformaPrueba(PlatformTransactionManager gestor, JdbcClient jdbc) {
            return new ControladorPlataformaPrueba(gestor, jdbc);
        }

        @Bean
        ControladorContabilidadPrueba controladorContabilidadPrueba() {
            return new ControladorContabilidadPrueba();
        }
    }

    /** Endpoints de prueba que no pertenecen a ninguna app del catálogo. */
    @RestController
    @RequestMapping("/api/v1/prueba-plataforma")
    public static class ControladorPlataformaPrueba {

        private final TransactionTemplate transaccion;
        private final JdbcClient jdbc;

        ControladorPlataformaPrueba(PlatformTransactionManager gestor, JdbcClient jdbc) {
            this.transaccion = new TransactionTemplate(gestor);
            this.jdbc = jdbc;
        }

        /** Devuelve las empresas que RLS deja ver dentro de la petición: solo debe ser la empresa activa. */
        @GetMapping("/empresas")
        public List<String> empresasVisibles() {
            return transaccion.execute(s ->
                    jdbc.sql("SELECT id::text FROM empresa").query(String.class).list());
        }

        /** Solo admin_empresa. */
        @GetMapping("/admin")
        @PreAuthorize("hasRole('ADMIN_EMPRESA')")
        public String admin() {
            return "ok";
        }

        /** Contador o superior. */
        @GetMapping("/contador")
        @PreAuthorize("hasRole('CONTADOR')")
        public String contador() {
            return "ok";
        }

        /** Auditor o superior. */
        @GetMapping("/auditor")
        @PreAuthorize("hasRole('AUDITOR')")
        public String auditor() {
            return "ok";
        }
    }

    /** Ruta bajo {@code /api/v1/contabilidad}: el filtro debe exigir la app instalada antes de llegar aquí. */
    @RestController
    @RequestMapping("/api/v1/contabilidad")
    public static class ControladorContabilidadPrueba {

        /** Exige el header de empresa, como lo hará todo endpoint de una app. */
        @GetMapping("/prueba")
        public String prueba(@RequestHeader("X-Empresa-Id") String empresa) {
            return "ok";
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    /** Cliente JDBC como dueño para sembrar y verificar sin las restricciones de RLS. */
    private final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    /** MockMvc con el filtro de X-Request-Id y la cadena de seguridad real (springSecurity). */
    @BeforeEach
    void prepararMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
    }

    // ---------------------------------------------------------------------------------------- utilidades

    /** JWT simulado con los claims del realm "pilot"; un valor nulo omite el claim. */
    private static RequestPostProcessor token(
            String sub, String correo, Object emailVerificado, Object telefono, Object recomendaciones) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", correo);
            j.claim("name", "Nombre de " + sub);
            if (emailVerificado != null) {
                j.claim("email_verified", emailVerificado);
            }
            if (telefono != null) {
                j.claim("telefono", telefono);
            }
            if (recomendaciones != null) {
                j.claim("recomendaciones_correo", recomendaciones);
            }
        });
    }

    /** Token válido: correo verificado, teléfono de 8 dígitos y sin consentimiento de recomendaciones. */
    private static RequestPostProcessor token(String sub, String correo) {
        return token(sub, correo, true, "70001234", null);
    }

    private static String nuevoSub() {
        return "sub-" + UUID.randomUUID();
    }

    private static String correoDe(String sub) {
        return sub.replace("sub-", "u") + "@prueba.sv";
    }

    /** Cuenta filas con SQL como dueño. */
    private int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    /** Primer inicio de sesión: devuelve el cuerpo JSON de {@code GET /me}. */
    private String iniciarSesion(String sub) throws Exception {
        return mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static UUID usuarioId(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.id"));
    }

    private static UUID empresaId(String cuerpoMe) {
        return UUID.fromString(JsonPath.read(cuerpoMe, "$.membresias[0].empresaId"));
    }

    // ---------------------------------------------------------------------------------------------- token

    /** Regla: todo /api/v1/** exige autenticación; sin token → 401 PLT-009 en problem+json con X-Request-Id. */
    @Test
    void sinTokenDevuelve401Plt009EnProblemJson() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.codigo").value("PLT-009"))
                .andExpect(jsonPath("$.status").value(401))
                // Sin detalles internos: ni trazas ni nombres de clases
                .andExpect(
                        content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))));
    }

    /** Regla: solo /actuator/health es público. */
    @Test
    void laSaludDelServicioEsPublica() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /**
     * Regla: un token real (cabecera Authorization) con el emisor INALCANZABLE no provoca un 500: el decodificador
     * falla al primer uso y la petición termina en 401 PLT-009. La aplicación ya arrancó sin Keycloak (este contexto).
     */
    @Test
    void conElEmisorInalcanzableUnTokenRealDa401YNoError500() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer aaa.bbb.ccc"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-009"));
    }

    // ------------------------------------------------------------------------------------ alta automática

    /**
     * Regla (ADR-028, ADR-029): el primer GET /me crea exactamente 1 usuario, 1 empresa PERSONAL y 1 membresía
     * admin_empresa ACTIVA, con su auditoría (global para el usuario; por empresa para empresa y membresía); el
     * segundo GET no duplica nada. Además no se filtran correo ni teléfono a los logs.
     */
    @Test
    void elPrimerMeCreaUsuarioEmpresaPersonalYMembresiaSinDuplicarEnElSegundo(CapturedOutput salida) throws Exception {
        String sub = nuevoSub();
        String correo = correoDe(sub);

        // 1. Primer inicio de sesión: 200 con la empresa personal y el rol admin_empresa
        String cuerpo = mvc.perform(get("/api/v1/me").with(token(sub, correo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.telefono").value("+50370001234"))
                .andExpect(jsonPath("$.recomendacionesCorreo").value(false))
                .andExpect(jsonPath("$.membresias.length()").value(1))
                .andExpect(jsonPath("$.membresias[0].rol").value("admin_empresa"))
                .andExpect(jsonPath("$.membresias[0].tipoEmpresa").value("PERSONAL"))
                .andExpect(jsonPath("$.membresias[0].nombreEmpresa").value("Nombre de " + sub))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID usuario = usuarioId(cuerpo);
        UUID empresa = empresaId(cuerpo);

        // 2. Filas creadas y auditoría (ver verificarAlta)
        verificarAlta(sub, usuario, empresa);

        // 3. Segundo inicio de sesión: no duplica nada y devuelve la misma empresa
        mvc.perform(get("/api/v1/me").with(token(sub, correo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membresias.length()").value(1))
                .andExpect(jsonPath("$.membresias[0].empresaId").value(empresa.toString()));
        assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa WHERE propietario_id = ?", usuario))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM auditoria_global WHERE entidad_id = ?", usuario.toString()))
                .isEqualTo(1);

        // 4. Ni el correo ni el teléfono aparecen sin enmascarar en los logs (CLAUDE.md 1.1.12)
        assertThat(salida.getAll()).doesNotContain(correo).doesNotContain("70001234");
    }

    /** Comprueba las filas del alta (usuario, empresa PERSONAL, membresía, sin apps) y sus filas de auditoría. */
    private void verificarAlta(String sub, UUID usuario, UUID empresa) {
        // 1. Exactamente 1 usuario, 1 empresa PERSONAL sin NIT ni NRC y 1 membresía admin_empresa ACTIVA
        assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM empresa WHERE propietario_id = ? AND tipo = 'PERSONAL'"
                                + " AND nit IS NULL AND nrc IS NULL",
                        usuario))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM empresa_usuario WHERE usuario_id = ? AND empresa_id = ?"
                                + " AND rol = 'admin_empresa' AND estado = 'ACTIVA'",
                        usuario,
                        empresa))
                .isEqualTo(1);
        // 2. No se instala ninguna app (ADR-030)
        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", empresa))
                .isZero();

        // 3. Auditoría: usuario en auditoria_global; empresa y membresía en auditoria con su empresa_id
        assertThat(contar(
                        "SELECT count(*) FROM auditoria_global WHERE entidad = 'usuario' AND entidad_id = ?"
                                + " AND accion = 'CREAR' AND usuario_id = ?",
                        usuario.toString(),
                        usuario.toString()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa'"
                                + " AND entidad_id = ? AND accion = 'CREAR'",
                        empresa,
                        empresa.toString()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa_usuario'"
                                + " AND accion = 'CREAR'",
                        empresa))
                .isEqualTo(1);
    }

    /**
     * Regla (ADR-028, ADR-029): dos primeras peticiones simultáneas del mismo usuario terminan con UNA sola empresa
     * personal y ninguna respuesta 500: la perdedora choca con UNIQUE(sub_keycloak), se revierte completa y relee.
     */
    @Test
    void variasPrimerasPeticionesEnParaleloCreanUnaSolaEmpresaPersonalSinError500() throws Exception {
        String sub = nuevoSub();
        int hilos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            resultados.add(pool.submit(() -> {
                // Todos esperan la señal para arrancar a la vez y maximizar la carrera
                salida.await();
                return mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub))))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }
        salida.countDown();
        for (Future<Integer> resultado : resultados) {
            assertThat(resultado.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        }
        pool.shutdown();

        UUID usuario = duenio.sql("SELECT id FROM usuario WHERE sub_keycloak = ?")
                .param(sub)
                .query(UUID.class)
                .single();
        assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa WHERE propietario_id = ? AND tipo = 'PERSONAL'", usuario))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa_usuario WHERE usuario_id = ?", usuario))
                .isEqualTo(1);
        // Solo una alta se auditó: las perdedoras revirtieron también su auditoría
        assertThat(contar(
                        "SELECT count(*) FROM auditoria_global WHERE entidad_id = ? AND accion = 'CREAR'",
                        usuario.toString()))
                .isEqualTo(1);
    }

    // ----------------------------------------------------------------------------------------- datos

    /** Regla (ADR-028): teléfono de 8 dígitos se guarda como +503XXXXXXXX; la casilla ausente vale false. */
    @Test
    void elTelefonoSeGuardaConPrefijoYLaCasillaAusenteEsFalse() throws Exception {
        String sub = nuevoSub();
        iniciarSesion(sub);

        assertThat(duenio.sql("SELECT telefono FROM usuario WHERE sub_keycloak = ?")
                        .param(sub)
                        .query(String.class)
                        .single())
                .isEqualTo("+50370001234");
        assertThat(contar(
                        "SELECT count(*) FROM usuario WHERE sub_keycloak = ? AND recomendaciones_aceptadas_en IS NULL"
                                + " AND recomendaciones_retiradas_en IS NULL",
                        sub))
                .isEqualTo(1);
    }

    /** Regla (ADR-028): recomendaciones_correo = true en el alta fija recomendaciones_aceptadas_en. */
    @Test
    void laCasillaMarcadaEnElAltaFijaLaFechaDeAceptacion() throws Exception {
        String sub = nuevoSub();
        mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub), true, "+50370001234", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recomendacionesCorreo").value(true));

        assertThat(contar(
                        "SELECT count(*) FROM usuario WHERE sub_keycloak = ? AND recomendaciones_aceptadas_en IS NOT NULL",
                        sub))
                .isEqualTo(1);
    }

    /** Regla (ADR-028): sin email_verified = true (ausente o falso) → 401 PLT-009 y no se crea ninguna fila. */
    @Test
    void sinCorreoVerificadoDa401YNoCreaNada() throws Exception {
        for (Object verificado : new Object[] {null, false}) {
            String sub = nuevoSub();
            mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub), verificado, "70001234", null)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("PLT-009"));

            assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                    .isZero();
        }
    }

    /** Regla (ADR-028): sin teléfono válido → 401 PLT-009 y no se crea ninguna fila. */
    @Test
    void sinTelefonoValidoDa401YNoCreaNada() throws Exception {
        for (Object telefono : new Object[] {null, "+13055550100", "123"}) {
            String sub = nuevoSub();
            mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub), true, telefono, null)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("PLT-009"));

            assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                    .isZero();
        }
    }

    /**
     * Regla (ADR-028): si Keycloak cambia correo, nombre o teléfono, Pilot los sincroniza y lo audita en
     * auditoria_global con datos enmascarados; el consentimiento NUNCA se sobrescribe desde el token después del alta.
     */
    @Test
    void sincronizaElPerfilYNuncaSobrescribeElConsentimiento(CapturedOutput salida) throws Exception {
        String sub = nuevoSub();
        // 1. Alta con la casilla marcada
        mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub), true, "70001234", true)))
                .andExpect(status().isOk());

        // 2. Keycloak ahora trae otro correo y teléfono, y ya no trae la casilla
        String correoNuevo = "nuevo" + correoDe(sub);
        mvc.perform(get("/api/v1/me").with(token(sub, correoNuevo, true, "+50371112222", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correoNuevo))
                .andExpect(jsonPath("$.telefono").value("+50371112222"))
                // El consentimiento sigue como estaba: su fuente es Pilot
                .andExpect(jsonPath("$.recomendacionesCorreo").value(true));

        // 3. Quedó guardado y auditado (ACTUALIZAR en auditoria_global) sin correo ni teléfono en claro
        assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ? AND correo = ?", sub, correoNuevo))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria_global g JOIN usuario u ON u.id::text = g.entidad_id"
                                + " WHERE u.sub_keycloak = ? AND g.accion = 'ACTUALIZAR'",
                        sub))
                .isEqualTo(1);
        String valorNuevo = duenio.sql("SELECT g.valor_nuevo::text FROM auditoria_global g JOIN usuario u"
                        + " ON u.id::text = g.entidad_id WHERE u.sub_keycloak = ? AND g.accion = 'ACTUALIZAR'")
                .param(sub)
                .query(String.class)
                .single();
        assertThat(valorNuevo).doesNotContain(correoNuevo).doesNotContain("71112222");
        assertThat(salida.getAll()).doesNotContain(correoNuevo).doesNotContain("71112222");

        // 4. Repetir con los mismos datos no escribe otra vez
        mvc.perform(get("/api/v1/me").with(token(sub, correoNuevo, true, "+50371112222", null)))
                .andExpect(status().isOk());
        assertThat(contar(
                        "SELECT count(*) FROM auditoria_global g JOIN usuario u ON u.id::text = g.entidad_id"
                                + " WHERE u.sub_keycloak = ? AND g.accion = 'ACTUALIZAR'",
                        sub))
                .isEqualTo(1);
    }

    /** Regla: un usuario BLOQUEADO recibe 403 PLT-010 y no se le sincroniza nada. */
    @Test
    void unUsuarioBloqueadoRecibe403Plt010() throws Exception {
        String sub = nuevoSub();
        iniciarSesion(sub);
        duenio.sql("UPDATE usuario SET estado = 'BLOQUEADO' WHERE sub_keycloak = ?")
                .param(sub)
                .update();

        mvc.perform(get("/api/v1/me").with(token(sub, correoDe(sub))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }

    // ------------------------------------------------------------------------------------------ PATCH /me

    /**
     * Regla (ADR-028): PATCH /me con true fija recomendaciones_aceptadas_en y con false recomendaciones_retiradas_en,
     * ambos auditados en auditoria_global; repetir el mismo valor no escribe (ni fecha ni auditoría).
     */
    @Test
    void patchDaYRetiraElConsentimientoYRepetirNoEscribe() throws Exception {
        String sub = nuevoSub();
        UUID usuario = usuarioId(iniciarSesion(sub));
        String auditoria = "SELECT count(*) FROM auditoria_global WHERE entidad_id = ? AND accion = 'ACTUALIZAR'";

        // 1. Dar el consentimiento
        patchConsentimiento(sub, true)
                .andExpect(jsonPath("$.recomendacionesCorreo").value(true));
        assertThat(contar(
                        "SELECT count(*) FROM usuario WHERE id = ? AND recomendaciones_aceptadas_en IS NOT NULL",
                        usuario))
                .isEqualTo(1);
        assertThat(contar(auditoria, usuario.toString())).isEqualTo(1);

        // 2. Repetir true: mismo valor, no escribe (la fecha y la auditoría quedan igual)
        Object fechaAntes = duenio.sql("SELECT recomendaciones_aceptadas_en FROM usuario WHERE id = ?")
                .param(usuario)
                .query(java.sql.Timestamp.class)
                .single();
        patchConsentimiento(sub, true)
                .andExpect(jsonPath("$.recomendacionesCorreo").value(true));
        assertThat(duenio.sql("SELECT recomendaciones_aceptadas_en FROM usuario WHERE id = ?")
                        .param(usuario)
                        .query(java.sql.Timestamp.class)
                        .single())
                .isEqualTo(fechaAntes);
        assertThat(contar(auditoria, usuario.toString())).isEqualTo(1);

        // 3. Retirar el consentimiento
        patchConsentimiento(sub, false)
                .andExpect(jsonPath("$.recomendacionesCorreo").value(false));
        assertThat(contar(
                        "SELECT count(*) FROM usuario WHERE id = ? AND recomendaciones_retiradas_en IS NOT NULL",
                        usuario))
                .isEqualTo(1);
        assertThat(contar(auditoria, usuario.toString())).isEqualTo(2);

        // 4. Repetir false: no escribe
        patchConsentimiento(sub, false)
                .andExpect(jsonPath("$.recomendacionesCorreo").value(false));
        assertThat(contar(auditoria, usuario.toString())).isEqualTo(2);

        // 5. Volver a darlo: la aceptación posterior al retiro lo deja vigente
        patchConsentimiento(sub, true)
                .andExpect(jsonPath("$.recomendacionesCorreo").value(true));
        assertThat(contar(auditoria, usuario.toString())).isEqualTo(3);
    }

    /** Envía PATCH /me con el consentimiento indicado y comprueba el 200. */
    private org.springframework.test.web.servlet.ResultActions patchConsentimiento(String sub, boolean valor)
            throws Exception {
        return mvc.perform(patch("/api/v1/me")
                        .with(token(sub, correoDe(sub)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recomendacionesCorreo\": " + valor + "}"))
                .andExpect(status().isOk());
    }

    /** Regla (contrato): un cuerpo sin el campo obligatorio → 422 PLT-002 con errores, no 500. */
    @Test
    void patchSinElCampoDa422Plt002() throws Exception {
        String sub = nuevoSub();
        mvc.perform(patch("/api/v1/me")
                        .with(token(sub, correoDe(sub)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("recomendacionesCorreo"));
    }

    // ----------------------------------------------------------------------------------- empresa activa

    /** Regla (CLAUDE.md 4.5): con membresía válida la petición solo ve datos de SU empresa (RLS). */
    @Test
    void conMembresiaValidaLaConsultaSoloVeLaEmpresaActiva() throws Exception {
        String subA = nuevoSub();
        String subB = nuevoSub();
        UUID empresaA = empresaId(iniciarSesion(subA));
        iniciarSesion(subB); // existe otra empresa en la base de datos: no debe verse

        mvc.perform(get("/api/v1/prueba-plataforma/empresas")
                        .with(token(subA, correoDe(subA)))
                        .header("X-Empresa-Id", empresaA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value(empresaA.toString()));
    }

    /** Regla (PLT-003): X-Empresa-Id de OTRA empresa (sin membresía), de una membresía INACTIVA o inexistente → 403. */
    @Test
    void unaEmpresaSinMembresiaActivaDa403Plt003() throws Exception {
        String subA = nuevoSub();
        String subB = nuevoSub();
        UUID empresaA = empresaId(iniciarSesion(subA));
        String cuerpoB = iniciarSesion(subB);
        UUID empresaB = empresaId(cuerpoB);
        UUID usuarioB = usuarioId(cuerpoB);

        // 1. Empresa de otro usuario
        pedirConEmpresa(subA, empresaB.toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-003"));

        // 2. Membresía INACTIVA: B pertenece a A pero fue desactivado
        duenio.sql(
                        "INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, 'auditor', 'INACTIVA')")
                .params(empresaA, usuarioB)
                .update();
        pedirConEmpresa(subB, empresaA.toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-003"));

        // 3. Empresa inexistente
        pedirConEmpresa(subA, UUID.randomUUID().toString())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-003"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** Regla (PLT-001): X-Empresa-Id que no es un UUID → 400. */
    @Test
    void unaEmpresaMalFormadaDa400Plt001() throws Exception {
        String sub = nuevoSub();
        for (String malo : new String[] {"no-es-un-uuid", "", "1-1-1-1-1", UUID.randomUUID() + "x"}) {
            pedirConEmpresa(sub, malo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("PLT-001"));
        }
    }

    /** GET a la ruta de prueba de plataforma con el header de empresa indicado. */
    private org.springframework.test.web.servlet.ResultActions pedirConEmpresa(String sub, String empresa)
            throws Exception {
        return mvc.perform(get("/api/v1/prueba-plataforma/empresas")
                .with(token(sub, correoDe(sub)))
                .header("X-Empresa-Id", empresa));
    }

    // ------------------------------------------------------------------------------------------- roles

    /**
     * Regla (PLT-010, CLAUDE.md 14.2): un auditor en un endpoint de admin_empresa recibe 403 PLT-010 (no 500), y la
     * jerarquía admin_empresa > contador > auditor se cumple en ambos sentidos.
     */
    @Test
    void elRolInsuficienteDa403Plt010YLaJerarquiaSeAplica() throws Exception {
        String subAdmin = nuevoSub();
        String subAuditor = nuevoSub();
        String cuerpoAdmin = iniciarSesion(subAdmin);
        UUID empresa = empresaId(cuerpoAdmin);
        UUID auditor = usuarioId(iniciarSesion(subAuditor));
        duenio.sql(
                        "INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, 'auditor', 'ACTIVA')")
                .params(empresa, auditor)
                .update();

        // 1. El auditor solo llega a lo de auditor
        rolEn(subAuditor, empresa, "auditor").andExpect(status().isOk());
        rolEn(subAuditor, empresa, "contador")
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        rolEn(subAuditor, empresa, "admin")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));

        // 2. El admin_empresa llega a todo lo de sus subordinados
        rolEn(subAdmin, empresa, "admin").andExpect(status().isOk());
        rolEn(subAdmin, empresa, "contador").andExpect(status().isOk());
        rolEn(subAdmin, empresa, "auditor").andExpect(status().isOk());

        // 3. Sin X-Empresa-Id no hay rol de empresa: 403 PLT-010
        mvc.perform(get("/api/v1/prueba-plataforma/auditor").with(token(subAdmin, correoDe(subAdmin))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }

    private org.springframework.test.web.servlet.ResultActions rolEn(String sub, UUID empresa, String ruta)
            throws Exception {
        return mvc.perform(get("/api/v1/prueba-plataforma/" + ruta)
                .with(token(sub, correoDe(sub)))
                .header("X-Empresa-Id", empresa.toString()));
    }

    // ------------------------------------------------------------------------------------------ PLT-004

    /**
     * Regla (PLT-004, ADR-021 y ADR-030): una ruta de una app del catálogo (/api/v1/contabilidad/**) de una empresa
     * que no la instaló → 403 PLT-004; con la fila en empresa_aplicacion → 200. Las variantes de la ruta
     * (codificación de letras) no eluden el control.
     */
    @Test
    void laAppNoInstaladaDa403Plt004YInstaladaDa200() throws Exception {
        String sub = nuevoSub();
        UUID empresa = empresaId(iniciarSesion(sub));

        // 1. Sin instalar: 403 PLT-004, también con la ruta codificada (%63 = "c")
        mvc.perform(get("/api/v1/contabilidad/prueba")
                        .with(token(sub, correoDe(sub)))
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));
        mvc.perform(get(URI.create("/api/v1/%63ontabilidad/prueba"))
                        .with(token(sub, correoDe(sub)))
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));

        // 2. Con la app instalada la petición llega al controlador
        duenio.sql(
                        "INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por) VALUES (?, 'contabilidad', 'prueba')")
                .param(empresa)
                .update();
        mvc.perform(get("/api/v1/contabilidad/prueba")
                        .with(token(sub, correoDe(sub)))
                        .header("X-Empresa-Id", empresa.toString()))
                .andExpect(status().isOk());
    }

    /** Regla (PLT-004): que una app esté instalada en una empresa no la habilita en OTRA empresa. */
    @Test
    void laInstalacionDeUnaEmpresaNoHabilitaLaAppEnOtra() throws Exception {
        String subA = nuevoSub();
        String subB = nuevoSub();
        UUID empresaA = empresaId(iniciarSesion(subA));
        UUID empresaB = empresaId(iniciarSesion(subB));
        duenio.sql(
                        "INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por) VALUES (?, 'contabilidad', 'prueba')")
                .param(empresaA)
                .update();

        mvc.perform(get("/api/v1/contabilidad/prueba")
                        .with(token(subB, correoDe(subB)))
                        .header("X-Empresa-Id", empresaB.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));
    }

    /** Regla (tarea 7): sin X-Empresa-Id un endpoint que lo exige → 422 PLT-002 con errores[].campo. */
    @Test
    void sinElHeaderDeEmpresaUnEndpointQueLoExigeDa422Plt002() throws Exception {
        String sub = nuevoSub();
        mvc.perform(get("/api/v1/contabilidad/prueba").with(token(sub, correoDe(sub))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("X-Empresa-Id"));
    }
}
