package com.bcodesphere.pilot.plataforma.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de las migraciones del núcleo (F1-03): usuario, empresa, membresías, apps, API keys y funciones de
 * búsqueda sin empresa. Flyway corre por API sobre el dueño, sin contexto de Spring.
 * Fuente: CLAUDE.md 4.5, 9.2; ADR-002, ADR-025, ADR-026, ADR-030.
 */
class MigracionesNucleoIT {

    // UUID fijos de la siembra: usuarios U1..U3 y empresas A..D
    private static final UUID U1 = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final UUID U2 = UUID.fromString("00000000-0000-7000-8000-0000000000a2");
    private static final UUID U3 = UUID.fromString("00000000-0000-7000-8000-0000000000a3");
    private static final UUID A = UUID.fromString("00000000-0000-7000-8000-0000000000b1");
    private static final UUID B = UUID.fromString("00000000-0000-7000-8000-0000000000b2");
    private static final UUID C = UUID.fromString("00000000-0000-7000-8000-0000000000b3");
    private static final UUID D = UUID.fromString("00000000-0000-7000-8000-0000000000b4");
    private static final UUID KEY_A = UUID.fromString("00000000-0000-7000-8000-0000000000c1");
    private static final UUID KEY_B = UUID.fromString("00000000-0000-7000-8000-0000000000c2");

    /** Migra y siembra, como dueño (superusuario en pruebas, salta RLS), un escenario con dos empresas aisladas. */
    @BeforeAll
    static void migrarYSembrar() throws SQLException {
        // 1. Migraciones reales del classpath
        Flyway.configure()
                .dataSource(PostgresContenedor.dataSourceDuenio())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            // 2. Usuarios
            usuario(c, U1, "sub-u1", "u1@prueba.sv");
            usuario(c, U2, "sub-u2", "u2@prueba.sv");
            usuario(c, U3, "sub-u3", "u3@prueba.sv");
            // 3. Empresas: A y B personales, C jurídica, D inactiva
            empresa(c, A, "PERSONAL", U1, null, "Empresa A prueba", "ACTIVA");
            empresa(c, B, "PERSONAL", U2, null, "Empresa B prueba", "ACTIVA");
            empresa(c, C, "JURIDICA", null, "01234567890123", "Empresa C prueba", "ACTIVA");
            empresa(c, D, "JURIDICA", null, "01234567890124", "Empresa D prueba", "INACTIVA");
            // 4. Membresías: U1 activa en A y C, inactiva en B y activa en la empresa inactiva D; U2 activa en B
            membresia(c, A, U1, "admin_empresa", "ACTIVA");
            membresia(c, C, U1, "contador", "ACTIVA");
            membresia(c, B, U1, "auditor", "INACTIVA");
            membresia(c, D, U1, "auditor", "ACTIVA");
            membresia(c, B, U2, "admin_empresa", "ACTIVA");
            // 5. Apps instaladas y API keys (la de B revocada, para comprobar que la función igual la devuelve)
            ejecutar(
                    c,
                    "INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por)"
                            + " VALUES (?, 'contabilidad', 'sistema')",
                    A);
            ejecutar(
                    c,
                    "INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por)"
                            + " VALUES (?, 'contabilidad', 'sistema')",
                    B);
            apiKey(c, KEY_A, A, "pk_aaaa1111", null);
            apiKey(c, KEY_B, B, "pk_bbbb2222", Timestamp.valueOf("2026-01-01 00:00:00"));
        }
    }

    // ---------------------------------------------------------------- Ayudas de siembra y conexión

    /** Ejecuta una sentencia parametrizada (los parámetros se asignan por posición con setObject). */
    private static int ejecutar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }

    private static void usuario(Connection c, UUID id, String sub, String correo) throws SQLException {
        ejecutar(
                c,
                "INSERT INTO usuario (id, sub_keycloak, correo, nombre, telefono)"
                        + " VALUES (?, ?, ?, 'Usuario de prueba', '+50370000000')",
                id,
                sub,
                correo);
    }

    private static void empresa(
            Connection c, UUID id, String tipo, UUID propietario, String nit, String nombre, String estado)
            throws SQLException {
        ejecutar(
                c,
                "INSERT INTO empresa (id, tipo, propietario_id, nit, nombre, estado) VALUES (?, ?, ?, ?, ?, ?)",
                id,
                tipo,
                propietario,
                nit,
                nombre,
                estado);
    }

    private static void membresia(Connection c, UUID empresa, UUID usuario, String rol, String estado)
            throws SQLException {
        ejecutar(
                c,
                "INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, ?, ?)",
                empresa,
                usuario,
                rol,
                estado);
    }

    private static void apiKey(Connection c, UUID id, UUID empresa, String prefijo, Timestamp revocada)
            throws SQLException {
        ejecutar(
                c,
                "INSERT INTO api_key (id, empresa_id, nombre, prefijo, hash_secreto, alcances, revocada_en, creado_por)"
                        + " VALUES (?, ?, 'clave de prueba', ?, 'hash-de-prueba', ARRAY['integracion:operaciones'], ?, 'sistema')",
                id,
                empresa,
                prefijo,
                revocada);
    }

    /** Conexión de pilot_app en una transacción con app.empresa_id fijado (equivale al gestor de transacciones). */
    private static Connection appConEmpresa(UUID empresa) throws SQLException {
        Connection c = PostgresContenedor.dataSourceApp().getConnection();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement("SELECT set_config('app.empresa_id', ?, true)")) {
            ps.setString(1, empresa.toString());
            ps.execute();
        }
        return c;
    }

    /** Devuelve la primera columna de todas las filas de una consulta parametrizada, como texto. */
    private static List<String> columna(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<String> filas = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    filas.add(rs.getString(1));
                }
            }
            return filas;
        }
    }

    /** Ejecuta con pilot_app y la empresa A y exige "permission denied" (permiso a nivel de tabla o columna). */
    private static void assertDenegado(String sql, Object... params) throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() -> ejecutar(c, sql, params))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    /** Ejecuta como dueño una sentencia que debe violar una restricción CHECK o UNIQUE (que aplican a todos los roles). */
    private static void assertRestriccion(String sql, Object... params) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            assertThatThrownBy(() -> ejecutar(c, sql, params)).isInstanceOf(SQLException.class);
        }
    }

    // ---------------------------------------------------------------- RLS

    /** Regla: RLS habilitado y forzado en las tablas por empresa; las tablas globales no tienen RLS (CLAUDE.md 4.5, 9.1). */
    @Test
    void rlsEstaForzadoEnTablasDeEmpresaYAusenteEnGlobales() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            for (String t : List.of("empresa", "empresa_usuario", "empresa_aplicacion", "api_key")) {
                assertThat(columna(c, "SELECT relrowsecurity::text FROM pg_class WHERE relname = ?", t))
                        .as("relrowsecurity de " + t)
                        .containsExactly("true");
                assertThat(columna(c, "SELECT relforcerowsecurity::text FROM pg_class WHERE relname = ?", t))
                        .as("relforcerowsecurity de " + t)
                        .containsExactly("true");
            }
            for (String t : List.of("usuario", "aplicacion", "auditoria_global")) {
                assertThat(columna(c, "SELECT relrowsecurity::text FROM pg_class WHERE relname = ?", t))
                        .as("RLS en tabla global " + t)
                        .containsExactly("false");
            }
        }
    }

    /** Regla: con la empresa A en sesión pilot_app no ve la empresa B ni sus membresías, apps o API keys (ADR-002). */
    @Test
    void conEmpresaAnoVeDatosDeLaEmpresaB() throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThat(columna(c, "SELECT id::text FROM empresa")).containsExactly(A.toString());
            assertThat(columna(c, "SELECT empresa_id::text FROM empresa_usuario"))
                    .containsOnly(A.toString());
            assertThat(columna(c, "SELECT empresa_id::text FROM empresa_aplicacion"))
                    .containsExactly(A.toString());
            assertThat(columna(c, "SELECT id::text FROM api_key")).containsExactly(KEY_A.toString());
        }
    }

    /** Regla: WITH CHECK impide insertar filas de otra empresa (o una empresa distinta de la activa). */
    @Test
    void noPuedeInsertarFilasDeLaEmpresaB() throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() ->
                            empresa(c, UUID.randomUUID(), "JURIDICA", null, "99999999999999", "Intrusa", "ACTIVA"))
                    .as("empresa con id distinto al activo")
                    .hasMessageContaining("row-level security");
        }
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() -> membresia(c, B, U3, "auditor", "ACTIVA"))
                    .as("membresía de B")
                    .hasMessageContaining("row-level security");
        }
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() -> ejecutar(
                            c,
                            "INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por)"
                                    + " VALUES (?, 'contabilidad', 'x')",
                            B))
                    .as("app de B")
                    .hasMessageContaining("row-level security");
        }
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() -> apiKey(c, UUID.randomUUID(), B, "pk_intruso1", null))
                    .as("api key de B")
                    .hasMessageContaining("row-level security");
        }
    }

    /** Regla: falla cerrada; una conexión nueva sin app.empresa_id no puede leer empresa (ADR-002, ADR-026). */
    @Test
    void sinEmpresaEnSesionLaConsultaSobreEmpresaFalla() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            assertThatThrownBy(() -> columna(c, "SELECT id::text FROM empresa"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("app.empresa_id");
        }
    }

    // ---------------------------------------------------------------- Permisos

    /** Regla: nada se borra; ninguna tabla nueva concede DELETE a pilot_app. */
    @Test
    void deleteFallaEnTodasLasTablasNuevas() throws SQLException {
        for (String t : List.of(
                "usuario",
                "auditoria_global",
                "empresa",
                "empresa_usuario",
                "aplicacion",
                "empresa_aplicacion",
                "api_key")) {
            assertDenegado("DELETE FROM " + t); // nombre de tabla fijo del propio test
        }
    }

    /** Regla: no hay desinstalación de apps (ADR-030); empresa_aplicacion no admite UPDATE. */
    @Test
    void noPuedeActualizarEmpresaAplicacion() throws SQLException {
        assertDenegado("UPDATE empresa_aplicacion SET instalada_por = ?", "otro");
    }

    /** Regla: el secreto, el prefijo y los alcances de una API key son inmutables; solo se revoca. */
    @Test
    void noPuedeActualizarElHashDeUnaApiKey() throws SQLException {
        assertDenegado("UPDATE api_key SET hash_secreto = ?", "otro");
        assertDenegado("UPDATE api_key SET alcances = ARRAY['integracion:operaciones']");
        assertDenegado("UPDATE api_key SET prefijo = ?", "pk_otro1234");
    }

    /** Regla: revocar y registrar el último uso sí están permitidos (columnas concedidas). */
    @Test
    void puedeRevocarUnaApiKey() throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThat(ejecutar(c, "UPDATE api_key SET revocada_en = now(), ultimo_uso_en = now()"))
                    .isEqualTo(1);
            c.rollback();
        }
    }

    /** Regla: el tipo de empresa y el propietario no cambian (ADR-029). */
    @Test
    void noPuedeActualizarElTipoDeEmpresa() throws SQLException {
        assertDenegado("UPDATE empresa SET tipo = 'JURIDICA'");
        assertDenegado("UPDATE empresa SET propietario_id = ?", U3);
    }

    /** Regla: la identidad de Keycloak no se reasigna. */
    @Test
    void noPuedeActualizarElSubDeKeycloak() throws SQLException {
        assertDenegado("UPDATE usuario SET sub_keycloak = ?", "otro");
    }

    /** Regla: el perfil sí se edita (columnas concedidas de usuario y empresa). */
    @Test
    void puedeEditarPerfilYEmpresa() throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThat(ejecutar(c, "UPDATE usuario SET nombre = 'Nuevo' WHERE id = ?", U3))
                    .isEqualTo(1);
            assertThat(ejecutar(c, "UPDATE empresa SET nombre = 'Nuevo', version = version + 1"))
                    .isEqualTo(1);
            c.rollback();
        }
    }

    /** Regla: el catálogo de apps es de solo lectura para la aplicación. */
    @Test
    void noPuedeInsertarEnAplicacion() throws SQLException {
        assertDenegado(
                "INSERT INTO aplicacion (codigo, nombre, edicion, orden) VALUES ('nueva', 'Nueva', 'COMUNITARIA', 99)");
    }

    /** Regla (ADR-025): auditoria_global es insert-only; no se puede leer pero sí insertar. */
    @Test
    void auditoriaGlobalSoloPermiteInsertar() throws SQLException {
        assertDenegado("SELECT count(*) FROM auditoria_global");
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            assertThat(ejecutar(
                            c,
                            "INSERT INTO auditoria_global (id, entidad, entidad_id, accion, usuario_id)"
                                    + " VALUES (?, 'usuario', ?, 'CREAR', 'sistema')",
                            UUID.randomUUID(),
                            U1.toString()))
                    .isEqualTo(1);
        }
    }

    // ---------------------------------------------------------------- Restricciones

    /** Regla: teléfono solo de El Salvador (+503 y 8 dígitos), ADR-028. */
    @Test
    void rechazaTelefonoInvalido() throws SQLException {
        assertRestriccion(
                "INSERT INTO usuario (id, sub_keycloak, correo, nombre, telefono)"
                        + " VALUES (?, 'sub-tel', 'tel@prueba.sv', 'X', '+5037000000')",
                UUID.randomUUID());
    }

    /** Regla: el correo se guarda en minúsculas para que la búsqueda exacta (ADR-028) sea confiable. */
    @Test
    void rechazaCorreoConMayusculas() throws SQLException {
        assertRestriccion(
                "INSERT INTO usuario (id, sub_keycloak, correo, nombre, telefono)"
                        + " VALUES (?, 'sub-may', 'Mayus@prueba.sv', 'X', '+50370000000')",
                UUID.randomUUID());
    }

    /** Regla: el estado del usuario es ACTIVO o BLOQUEADO. */
    @Test
    void rechazaEstadoDeUsuarioDesconocido() throws SQLException {
        assertRestriccion(
                "INSERT INTO usuario (id, sub_keycloak, correo, nombre, telefono, estado)"
                        + " VALUES (?, 'sub-est', 'est@prueba.sv', 'X', '+50370000000', 'OTRO')",
                UUID.randomUUID());
    }

    /** Regla: el NIT tiene exactamente 14 dígitos (CLAUDE.md 9.2). */
    @Test
    void rechazaNitDeTrece() throws SQLException {
        assertRestriccion(
                "INSERT INTO empresa (id, tipo, nit, nombre) VALUES (?, 'JURIDICA', '0123456789012', 'X')",
                UUID.randomUUID());
    }

    /** Regla: la empresa jurídica exige NIT (ADR-029). */
    @Test
    void rechazaJuridicaSinNit() throws SQLException {
        assertRestriccion("INSERT INTO empresa (id, tipo, nombre) VALUES (?, 'JURIDICA', 'X')", UUID.randomUUID());
    }

    /** Regla: una sola empresa PERSONAL por propietario (índice uq_empresa_personal). */
    @Test
    void rechazaSegundaEmpresaPersonalDelMismoPropietario() throws SQLException {
        assertRestriccion(
                "INSERT INTO empresa (id, tipo, propietario_id, nombre) VALUES (?, 'PERSONAL', ?, 'Otra')",
                UUID.randomUUID(),
                U1);
    }

    /** Regla: el rol de una membresía es admin_empresa, contador o auditor (CLAUDE.md 14.2). */
    @Test
    void rechazaRolInexistente() throws SQLException {
        assertRestriccion(
                "INSERT INTO empresa_usuario (empresa_id, usuario_id, rol) VALUES (?, ?, 'superadmin')", C, U2);
    }

    /** Regla: una API key solo puede tener alcances conocidos (integracion:operaciones) y al menos uno. */
    @Test
    void rechazaAlcanceDesconocidoOVacio() throws SQLException {
        assertRestriccion(
                "INSERT INTO api_key (id, empresa_id, nombre, prefijo, hash_secreto, alcances, creado_por)"
                        + " VALUES (?, ?, 'n', 'pk_alc00001', 'h', ARRAY['contabilidad:escribir'], 'sistema')",
                UUID.randomUUID(),
                A);
        assertRestriccion(
                "INSERT INTO api_key (id, empresa_id, nombre, prefijo, hash_secreto, alcances, creado_por)"
                        + " VALUES (?, ?, 'n', 'pk_alc00002', 'h', ARRAY[]::text[], 'sistema')",
                UUID.randomUUID(),
                A);
    }

    /** Regla: el prefijo de una API key tiene el formato pk_ + 4 a 13 caracteres en minúscula o dígitos. */
    @Test
    void rechazaPrefijoConFormatoInvalido() throws SQLException {
        assertRestriccion(
                "INSERT INTO api_key (id, empresa_id, nombre, prefijo, hash_secreto, alcances, creado_por)"
                        + " VALUES (?, ?, 'n', 'PK_MAYUS123', 'h', ARRAY['integracion:operaciones'], 'sistema')",
                UUID.randomUUID(),
                A);
    }

    // ---------------------------------------------------------------- Funciones de búsqueda sin empresa

    /**
     * Regla (ADR-026): en una conexión NUEVA de pilot_app, sin app.empresa_id, solo las membresías ACTIVAS de empresas
     * ACTIVAS del usuario pedido. Si la política aislamiento_empresa aplicara a pilot_busqueda, esto fallaría.
     */
    @Test
    void membresiasDeUsuarioSinEmpresaDevuelveSoloLasActivasDelUsuario() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            // U1: activa en A y C; excluye B (membresía inactiva) y D (empresa inactiva); ordenadas por nombre
            assertThat(columna(c, "SELECT nombre_empresa FROM membresias_de_usuario(?)", U1))
                    .containsExactly("Empresa A prueba", "Empresa C prueba");
            assertThat(columna(c, "SELECT rol FROM membresias_de_usuario(?)", U1))
                    .containsExactly("admin_empresa", "contador");
            assertThat(columna(c, "SELECT tipo_empresa FROM membresias_de_usuario(?)", U1))
                    .containsExactly("PERSONAL", "JURIDICA");
            // U2 solo ve la suya y nunca las de U1
            assertThat(columna(c, "SELECT empresa_id::text FROM membresias_de_usuario(?)", U2))
                    .containsExactly(B.toString());
            assertThat(columna(c, "SELECT empresa_id::text FROM membresias_de_usuario(?)", U3))
                    .isEmpty();
        }
    }

    /** Regla (ADR-026): membresia_activa devuelve el rol, o NULL si no hay membresía activa en empresa activa. */
    @Test
    void membresiaActivaDevuelveElRolONull() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            assertThat(columna(c, "SELECT membresia_activa(?, ?)", U1, A)).containsExactly("admin_empresa");
            assertThat(columna(c, "SELECT membresia_activa(?, ?)", U1, C)).containsExactly("contador");
            assertThat(columna(c, "SELECT membresia_activa(?, ?)", U1, B))
                    .as("membresía inactiva")
                    .containsOnlyNulls();
            assertThat(columna(c, "SELECT membresia_activa(?, ?)", U1, D))
                    .as("empresa inactiva")
                    .containsOnlyNulls();
            assertThat(columna(c, "SELECT membresia_activa(?, ?)", U2, A))
                    .as("otro usuario")
                    .containsOnlyNulls();
            assertThat(columna(c, "SELECT membresia_activa(?, ?)", U3, UUID.randomUUID()))
                    .containsOnlyNulls();
        }
    }

    /** Regla (ADR-026): la API key de la empresa B se encuentra sin contexto, incluso revocada (la app decide el 401). */
    @Test
    void apiKeyPorPrefijoEncuentraLaClaveSinContexto() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT id, empresa_id, hash_secreto, alcances, expira_en, revocada_en FROM api_key_por_prefijo(?)")) {
            ps.setString(1, "pk_bbbb2222");
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("id", UUID.class)).isEqualTo(KEY_B);
                assertThat(rs.getObject("empresa_id", UUID.class)).isEqualTo(B);
                assertThat(rs.getString("hash_secreto")).isEqualTo("hash-de-prueba");
                assertThat((String[]) rs.getArray("alcances").getArray()).containsExactly("integracion:operaciones");
                assertThat(rs.getTimestamp("revocada_en"))
                        .as("revocada, pero devuelta")
                        .isNotNull();
                assertThat(rs.next()).isFalse();
            }
        }
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            assertThat(columna(c, "SELECT id::text FROM api_key_por_prefijo(?)", "pk_noexiste1"))
                    .isEmpty();
        }
    }

    /** Regla (ADR-026): las funciones son de pilot_busqueda, un rol sin login, sin superusuario y sin BYPASSRLS. */
    @Test
    void lasFuncionesPertenecenAPilotBusquedaSinPrivilegios() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            for (String firma : List.of(
                    "membresias_de_usuario(uuid)", "membresia_activa(uuid, uuid)", "api_key_por_prefijo(varchar)")) {
                assertThat(columna(
                                c, "SELECT pg_get_userbyid(proowner) FROM pg_proc WHERE oid = ?::regprocedure", firma))
                        .as("dueño de " + firma)
                        .containsExactly("pilot_busqueda");
                assertThat(columna(c, "SELECT prosecdef::text FROM pg_proc WHERE oid = ?::regprocedure", firma))
                        .as("SECURITY DEFINER de " + firma)
                        .containsExactly("true");
                assertThat(columna(
                                c,
                                "SELECT array_to_string(proconfig, ',') FROM pg_proc WHERE oid = ?::regprocedure",
                                firma))
                        .as("search_path fijo de " + firma)
                        .containsExactly("search_path=public, pg_temp");
            }
            assertThat(columna(c, "SELECT rolsuper::text FROM pg_roles WHERE rolname = 'pilot_busqueda'"))
                    .containsExactly("false");
            assertThat(columna(c, "SELECT rolbypassrls::text FROM pg_roles WHERE rolname = 'pilot_busqueda'"))
                    .containsExactly("false");
            assertThat(columna(c, "SELECT rolcanlogin::text FROM pg_roles WHERE rolname = 'pilot_busqueda'"))
                    .containsExactly("false");
        }
    }

    /** Regla (ADR-026): EXECUTE se revocó a PUBLIC y solo lo tiene pilot_app; un rol nuevo sin permisos no puede ejecutarlas. */
    @Test
    void soloPilotAppPuedeEjecutarLasFunciones() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            // Rol de prueba sin ningún permiso: hereda solo lo de PUBLIC. Se elimina siempre al terminar.
            ejecutar(c, "CREATE ROLE rol_sin_permisos_prueba NOLOGIN");
            try {
                for (String firma : List.of(
                        "membresias_de_usuario(uuid)",
                        "membresia_activa(uuid, uuid)",
                        "api_key_por_prefijo(varchar)")) {
                    assertThat(columna(
                                    c,
                                    "SELECT has_function_privilege('rol_sin_permisos_prueba', ?::regprocedure, 'EXECUTE')::text",
                                    firma))
                            .as("rol nuevo sobre " + firma)
                            .containsExactly("false");
                    assertThat(columna(
                                    c,
                                    "SELECT has_function_privilege('pilot_app', ?::regprocedure, 'EXECUTE')::text",
                                    firma))
                            .as("pilot_app sobre " + firma)
                            .containsExactly("true");
                }
            } finally {
                ejecutar(c, "DROP ROLE rol_sin_permisos_prueba");
            }
        }
    }

    // ---------------------------------------------------------------- Carga inicial

    /** Regla (ADR-030): hay 6 apps y solo contabilidad es COMUNITARIA; las demás son ENTERPRISE. */
    @Test
    void cargaInicialTieneSeisAppsYSoloContabilidadEsComunitaria() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
            assertThat(columna(c, "SELECT codigo FROM aplicacion ORDER BY orden"))
                    .containsExactly("contabilidad", "ventas", "clientes", "proveedores", "inventario", "marketing");
            assertThat(columna(c, "SELECT codigo FROM aplicacion WHERE edicion = 'COMUNITARIA'"))
                    .containsExactly("contabilidad");
        }
    }
}
