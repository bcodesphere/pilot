package com.bcodesphere.pilot.contabilidad.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * Pruebas de las migraciones V16 a V18 (F4.5, tarea B1): tabla {@code operacion}, origen {@code OPERACION} del
 * asiento, reglas de los diez tipos guiados con {@code prefijo_permitido}, {@code cuenta_contable.sistema},
 * {@code activo_fijo} y {@code depreciacion_registrada}. Flyway corre por API sobre el dueño, sin contexto de
 * Spring, igual que {@link MigracionesContabilidadIT} y {@link MigracionesLibroDiarioIT}. Fuente: CLAUDE.md 4.5,
 * 9.3, 10.2, 10.6; ADR-018, ADR-019, ADR-035, ADR-041, ADR-042; docs/diseno/2026-09-27-plan-f45-…md, tarea B1.
 */
class MigracionesOperacionesIT {

    // Prefijo propio 0000000003xx: no choca con los UUID fijos de otras clases IT que comparten el contenedor.
    private static final UUID E1 = UUID.fromString("00000000-0000-7000-8000-0000000003e1");
    private static final UUID E2 = UUID.fromString("00000000-0000-7000-8000-0000000003e2");
    private static final UUID E1_DEBE = UUID.fromString("00000000-0000-7000-8000-0000000003a1");
    private static final UUID E1_HABER = UUID.fromString("00000000-0000-7000-8000-0000000003a2");
    private static final UUID E2_DEBE = UUID.fromString("00000000-0000-7000-8000-0000000003b1");
    private static final UUID E2_HABER = UUID.fromString("00000000-0000-7000-8000-0000000003b2");
    private static final UUID OPERACION_E1 = UUID.fromString("00000000-0000-7000-8000-0000000003c1");
    private static final UUID OPERACION_E2 = UUID.fromString("00000000-0000-7000-8000-0000000003c2");
    private static final UUID ACTIVO_E1 = UUID.fromString("00000000-0000-7000-8000-0000000003d1");
    private static final UUID ACTIVO_E2 = UUID.fromString("00000000-0000-7000-8000-0000000003d2");

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);
    private static final short ANIO = 2026;

    /** Números de asiento únicos en toda la clase (los datos sembrados persisten entre pruebas). */
    private static final AtomicInteger NUMERO = new AtomicInteger(1);

    /** Esquema aislado de la prueba de migración "sobre datos anteriores" (paso 1 de B1): no comparte tablas con
     * el esquema "public" que usan todas las demás clases IT del mismo contenedor, así que puede migrarse solo
     * hasta V15 sin que otra clase ya haya avanzado el historial de Flyway a la última versión. */
    private static final String ESQUEMA_AISLADO = "b1_migracion_v16";

    // ---------------------------------------------------------------- Siembra sobre el esquema "public"

    /** Migra el esquema compartido a la última versión y siembra dos empresas con operacion, activo_fijo y
     * depreciacion_registrada, para las pruebas de aislamiento (RLS) y de permisos. */
    @BeforeAll
    static void migrarYSembrar() throws SQLException {
        Flyway.configure()
                .dataSource(PostgresContenedor.dataSourceDuenio())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            for (UUID e : List.of(E1, E2)) {
                ejecutar(c, "INSERT INTO empresa (id, tipo, nombre) VALUES (?, 'PERSONAL', 'Empresa de prueba')", e);
            }
            cuenta(c, E1_DEBE, E1, "11010101");
            cuenta(c, E1_HABER, E1, "51010101");
            cuenta(c, E2_DEBE, E2, "11010101");
            cuenta(c, E2_HABER, E2, "51010101");
        }
        // Un asiento OPERACION válido por empresa, la operación que lo generó y el activo que dio de alta
        UUID asientoOperacionE1 = sembrarAsientoOperacion(E1, E1_DEBE, E1_HABER);
        UUID asientoOperacionE2 = sembrarAsientoOperacion(E2, E2_DEBE, E2_HABER);
        sembrarOperacion(E1, OPERACION_E1, asientoOperacionE1);
        sembrarOperacion(E2, OPERACION_E2, asientoOperacionE2);
        sembrarActivo(E1, ACTIVO_E1, OPERACION_E1);
        sembrarActivo(E2, ACTIVO_E2, OPERACION_E2);
        // Un segundo asiento OPERACION por empresa, para la cuota de depreciación (asiento distinto al del alta)
        UUID asientoDepreciacionE1 = sembrarAsientoOperacion(E1, E1_DEBE, E1_HABER);
        UUID asientoDepreciacionE2 = sembrarAsientoOperacion(E2, E2_DEBE, E2_HABER);
        sembrarDepreciacion(E1, ACTIVO_E1, asientoDepreciacionE1);
        sembrarDepreciacion(E2, ACTIVO_E2, asientoDepreciacionE2);
    }

    /** Regla (ADR-002): con app.empresa_id = E1 fijado, no se leen filas de E2 en las tres tablas nuevas. */
    @Test
    void conEmpresaE1NoSeLeenFilasDeE2EnLasTablasNuevas() throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            for (String tabla : List.of("operacion", "activo_fijo", "depreciacion_registrada")) {
                List<String> empresas = columna(c, "SELECT DISTINCT empresa_id::text FROM " + tabla);
                assertThat(empresas).as(tabla).containsExactly(E1.toString());
            }
        }
    }

    /** Regla (ADR-019): pilot_app no puede escribir columnas fuera de las concedidas ni borrar filas. */
    @Test
    void permisosDeInmutabilidadEnLasTablasNuevasYSistema() throws SQLException {
        assertDenegado("UPDATE operacion SET total = 1");
        assertDenegado("UPDATE cuenta_contable SET sistema = false");
        assertDenegado("DELETE FROM operacion");
        assertDenegado("DELETE FROM activo_fijo");
        assertDenegado("DELETE FROM depreciacion_registrada");
    }

    /** Regla (ADR-026): tras V16-V18, regla_contabilizacion y cuenta_contable siguen con FORCE ROW LEVEL SECURITY
     * (se levantó solo dentro de la transacción de cada migración, para completar las empresas existentes). */
    @Test
    void reglaContabilizacionYCuentaContableSiguenConForceRls() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            List<String> con = columna(
                    c,
                    "SELECT relname FROM pg_class WHERE relnamespace = 'public'::regnamespace"
                            + " AND relname IN ('regla_contabilizacion', 'cuenta_contable', 'operacion', 'activo_fijo',"
                            + " 'depreciacion_registrada') AND relrowsecurity AND relforcerowsecurity ORDER BY relname");
            assertThat(con)
                    .containsExactly(
                            "activo_fijo",
                            "cuenta_contable",
                            "depreciacion_registrada",
                            "operacion",
                            "regla_contabilizacion");
        }
    }

    /** Regla (ADR-019): dos depreciaciones vigentes del mismo activo y mes chocan con uq_depreciacion_vigente. */
    @Test
    void unaSolaDepreciacionVigentePorActivoYMes() throws SQLException {
        UUID asiento = sembrarAsientoOperacion(E1, E1_DEBE, E1_HABER);
        assertRestriccion(
                "23505",
                "uq_depreciacion_vigente",
                "INSERT INTO depreciacion_registrada (id, empresa_id, activo_id, anio, mes, monto, asiento_id,"
                        + " creado_por) VALUES (?, ?, ?, 2026, 3, 16.67, ?, 'prueba')",
                UUID.randomUUID(),
                E1,
                ACTIVO_E1,
                asiento);
    }

    // ---------------------------------------------------------------- Paso 1 de B1: migración sobre datos anteriores

    /**
     * Una empresa que instaló Contabilidad antes de V16 recibe las reglas nuevas y sus cuentas base quedan
     * marcadas como sistema (spec §5.6 y §6). Corre en un esquema propio ({@link #ESQUEMA_AISLADO}), aislado del
     * esquema "public" que comparten las demás clases IT: solo así se puede migrar solo hasta V15 sin que el
     * historial de Flyway ya esté en la última versión por otra clase del mismo contenedor.
     */
    @Test
    void unaEmpresaAnteriorAV16QuedaCompletaTrasMigrar() throws SQLException {
        crearEsquemaAislado();
        try {
            // 1. Migra solo hasta V15
            flywayEnEsquemaAislado().target("15").load().migrate();

            // 2. Siembra, como dueño y en el esquema aislado, una empresa con el catálogo y las reglas de F2 (imita
            //    la precarga de PrecargaContableJdbc con los datos que existían hasta V15: sin prefijo_permitido ni
            //    sistema)
            UUID empresaCompleta = UUID.randomUUID();
            UUID empresaSinAnticipoIva = UUID.randomUUID();
            try (Connection c = conexionEnEsquemaAislado()) {
                c.setAutoCommit(false);
                sembrarEmpresaConContabilidadV15(c, empresaCompleta, true);
                sembrarEmpresaConContabilidadV15(c, empresaSinAnticipoIva, false);
                c.commit();
            }

            // 3. Migra hasta la última versión
            flywayEnEsquemaAislado().load().migrate();

            verificarEmpresasTrasLaMigracion(empresaCompleta, empresaSinAnticipoIva);
        } finally {
            // El esquema aislado no debe sobrevivir a esta prueba: sus tablas (empresa, auditoria, cuenta_contable…)
            // comparten relname con las de "public" y contaminarían las consultas de pg_class sin filtro de esquema
            // de otras clases IT que comparten el contenedor (p. ej. MigracionesIT, MigracionesNucleoIT).
            try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
                ejecutar(c, "DROP SCHEMA IF EXISTS " + ESQUEMA_AISLADO + " CASCADE");
            }
        }
    }

    /**
     * Afirma, ya en la última versión, los tres efectos de V16-V18 sobre las dos empresas sembradas a V15 (paso 1
     * del plan B1).
     */
    private static void verificarEmpresasTrasLaMigracion(UUID empresaCompleta, UUID empresaSinAnticipoIva)
            throws SQLException {
        try (Connection c = conexionEnEsquemaAislado()) {
            // 1. Reglas nuevas copiadas con su cuenta: VENTA tiene 3 de INGRESO + 4 de COBRO = 7 (spec §5.3)
            assertThat(escalar(
                            c,
                            "SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ? AND tipo_operacion ="
                                    + " 'VENTA'",
                            empresaCompleta))
                    .isEqualTo("7");
            // 2. Cuentas base marcadas como sistema: V17 las marca por coincidencia de código con la plantilla
            //    ACTUAL (tarea CAT, V19), no con el catálogo con el que se sembró esta empresa a V15; se compara
            //    contra el número de códigos en común entre su catálogo sembrado y la plantilla actual, no contra
            //    el total de la plantilla (que ya no es el mismo catálogo que esta empresa recibió).
            assertThat(escalar(
                            c,
                            "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND sistema",
                            empresaCompleta))
                    .isEqualTo(escalar(
                            c,
                            "SELECT count(*) FROM cuenta_contable cc JOIN plantilla_cuenta pc ON pc.codigo ="
                                    + " cc.codigo WHERE cc.empresa_id = ?",
                            empresaCompleta));
            // 3. La segunda empresa, sin la cuenta 11040104, deja COBRO_CLIENTE/CONTRAPARTIDA/ANTICIPO_IVA inactiva
            //    y sin cuenta (paso 4 del plan B1): la copia no falla el CHECK de una regla activa sin cuenta.
            //    count(*) en vez de comparar el booleano como texto: boolean::text no es portable entre el cast de
            //    PostgreSQL y el driver JDBC.
            assertThat(escalar(
                            c,
                            "SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ? AND tipo_operacion ="
                                    + " 'COBRO_CLIENTE' AND categoria = 'CONTRAPARTIDA' AND codigo = 'ANTICIPO_IVA'"
                                    + " AND NOT activa AND cuenta_id IS NULL",
                            empresaSinAnticipoIva))
                    .isEqualTo("1");
            // 4. Toda regla de la empresa completa (cierre + guiadas) tiene prefijo_permitido (columna NOT NULL)
            assertThat(escalar(
                            c,
                            "SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ? AND prefijo_permitido"
                                    + " IS NULL",
                            empresaCompleta))
                    .isEqualTo("0");
        }
    }

    // ---------------------------------------------------------------- Ayudas del esquema aislado (paso 1)

    /** Crea (o recrea) el esquema aislado, como dueño. */
    private static void crearEsquemaAislado() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            ejecutar(c, "DROP SCHEMA IF EXISTS " + ESQUEMA_AISLADO + " CASCADE");
            ejecutar(c, "CREATE SCHEMA " + ESQUEMA_AISLADO);
        }
    }

    /** Configuración de Flyway apuntando al esquema aislado (sin target: por defecto migra a la última versión). */
    private static FluentConfiguration flywayEnEsquemaAislado() {
        return Flyway.configure()
                .dataSource(PostgresContenedor.dataSourceDuenio())
                .locations("classpath:db/migration")
                .schemas(ESQUEMA_AISLADO)
                .defaultSchema(ESQUEMA_AISLADO);
    }

    /** Conexión del dueño con el search_path fijado al esquema aislado. */
    private static Connection conexionEnEsquemaAislado() throws SQLException {
        Connection c = PostgresContenedor.dataSourceDuenio().getConnection();
        try (PreparedStatement ps = c.prepareStatement("SET search_path TO " + ESQUEMA_AISLADO)) {
            ps.execute();
        }
        return c;
    }

    /**
     * Siembra, en el esquema aislado y sobre una empresa nueva, el catálogo, la configuración y las reglas tal
     * como habría quedado la precarga de F2-03 antes de V16 (sin prefijo_permitido ni cuenta_contable.sistema, que
     * todavía no existen a V15). Reproduce {@link PrecargaContableJdbc} con SQL puro, porque esta prueba no levanta
     * el contexto de Spring (patrón de {@link MigracionesContabilidadIT}).
     *
     * @param conAnticipoIva si es falso, se omite la cuenta 11040104 (IVA anticipo a cuenta, tarjetas) del catálogo
     *     copiado, para probar que V16 deja sin cuenta la regla que la necesita (paso 4 del plan B1)
     */
    private static void sembrarEmpresaConContabilidadV15(Connection c, UUID empresa, boolean conAnticipoIva)
            throws SQLException {
        ejecutar(c, "INSERT INTO empresa (id, tipo, nombre) VALUES (?, 'PERSONAL', 'Empresa anterior a V16')", empresa);
        ejecutar(
                c,
                "INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por)"
                        + " VALUES (?, 'contabilidad', 'prueba')",
                empresa);
        // 1. Catálogo: copia todo plantilla_cuenta (o todo menos 11040104), sin padre todavía
        String filtroCuenta = conAnticipoIva ? "" : " WHERE p.codigo <> '11040104'";
        ejecutar(
                c,
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " activa, creado_por)"
                        + " SELECT gen_random_uuid(), ?, p.codigo, p.nombre, p.nivel, p.naturaleza,"
                        + " NOT EXISTS (SELECT 1 FROM plantilla_cuenta h"
                        + "             WHERE h.codigo <> p.codigo AND starts_with(h.codigo, p.codigo)),"
                        + " true, 'prueba' FROM plantilla_cuenta p" + filtroCuenta,
                empresa);
        // 2. Padres, por prefijo del código según el nivel (gen_random_uuid(): UUID v4 solo en esta siembra de
        //    prueba que imita la precarga; ADR-010 aplica a los id que genera la aplicación, no a fixtures de test)
        ejecutar(
                c,
                "UPDATE cuenta_contable hijo SET cuenta_padre_id = padre.id"
                        + " FROM cuenta_contable padre"
                        + " WHERE hijo.empresa_id = ? AND padre.empresa_id = ?"
                        + " AND padre.codigo = CASE length(hijo.codigo) WHEN 2 THEN left(hijo.codigo, 1)"
                        + " WHEN 4 THEN left(hijo.codigo, 2) WHEN 6 THEN left(hijo.codigo, 4)"
                        + " WHEN 8 THEN left(hijo.codigo, 6) END",
                empresa,
                empresa);
        // 3. Configuración: una fila, resolviendo los códigos de IVA de la plantilla a los id ya copiados
        ejecutar(
                c,
                "INSERT INTO configuracion_contable (empresa_id, modo_precio_defecto, cuenta_iva_debito_id,"
                        + " cuenta_iva_credito_id)"
                        + " SELECT ?, t.modo_precio_defecto, d.id, cr.id FROM plantilla_configuracion_contable t"
                        + " JOIN cuenta_contable d ON d.empresa_id = ? AND d.codigo = t.cuenta_iva_debito_codigo"
                        + " JOIN cuenta_contable cr ON cr.empresa_id = ? AND cr.codigo = t.cuenta_iva_credito_codigo",
                empresa,
                empresa,
                empresa);
        // 4. Las 9 reglas del cierre (únicas en la plantilla a V15), resolviendo cuenta_codigo al id ya copiado
        ejecutar(
                c,
                "INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id,"
                        + " activa, creado_por)"
                        + " SELECT gen_random_uuid(), ?, r.tipo_operacion, r.categoria, r.codigo, cu.id, r.activa,"
                        + " 'prueba' FROM plantilla_regla_contabilizacion r"
                        + " LEFT JOIN cuenta_contable cu ON cu.empresa_id = ? AND cu.codigo = r.cuenta_codigo",
                empresa,
                empresa);
    }

    // ---------------------------------------------------------------- Ayudas de siembra (esquema "public")

    /** Ejecuta una sentencia parametrizada (los parámetros se asignan por posición con setObject). */
    private static int ejecutar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        }
    }

    /** Inserta una cuenta de detalle (nivel 5, sin padre) como dueño. */
    private static void cuenta(Connection c, UUID id, UUID empresa, String codigo) throws SQLException {
        ejecutar(
                c,
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " creado_por) VALUES (?, ?, ?, 'Cuenta de prueba', 5, 'DEUDORA', true, 'sistema')",
                id,
                empresa,
                codigo);
    }

    /** Siembra, como dueño, un asiento CONTABILIZADO válido de 100.00 con origen_tipo = OPERACION (admitido desde
     * V16) y dos líneas; devuelve su id. */
    private static UUID sembrarAsientoOperacion(UUID empresa, UUID debe, UUID haber) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            c.setAutoCommit(false);
            ejecutar(
                    c,
                    "INSERT INTO asiento (id, empresa_id, anio, numero, fecha, concepto, estado, origen_tipo,"
                            + " total_debe, total_haber, creado_por)"
                            + " VALUES (?, ?, ?, ?, ?, 'Operación de prueba', 'CONTABILIZADO', 'OPERACION', 100.00,"
                            + " 100.00, 'sistema')",
                    id,
                    empresa,
                    ANIO,
                    NUMERO.getAndIncrement(),
                    FECHA);
            ejecutar(
                    c,
                    "INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea, fecha, cuenta_id, debe,"
                            + " haber, origen_linea) VALUES (?, ?, ?, 1, ?, ?, 100.00, 0, 'OPERACION')",
                    UUID.randomUUID(),
                    empresa,
                    id,
                    FECHA,
                    debe);
            ejecutar(
                    c,
                    "INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea, fecha, cuenta_id, debe,"
                            + " haber, origen_linea) VALUES (?, ?, ?, 2, ?, ?, 0, 100.00, 'OPERACION')",
                    UUID.randomUUID(),
                    empresa,
                    id,
                    FECHA,
                    haber);
            c.commit();
        }
        return id;
    }

    /** Siembra, como dueño, una fila de operacion (tipo VENTA, datos y resumen vacíos) enlazada al asiento dado. */
    private static void sembrarOperacion(UUID empresa, UUID id, UUID asiento) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            ejecutar(
                    c,
                    "INSERT INTO operacion (id, empresa_id, tipo, fecha, datos, resumen, total, asiento_id,"
                            + " creado_por) VALUES (?, ?, 'VENTA', ?, '{}'::jsonb, '{}'::jsonb, 100.00, ?, 'sistema')",
                    id,
                    empresa,
                    FECHA,
                    asiento);
        }
    }

    /** Siembra, como dueño, un activo fijo de la categoría MOBILIARIO_EQUIPO, dado de alta por la operación dada. */
    private static void sembrarActivo(UUID empresa, UUID id, UUID operacion) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            ejecutar(
                    c,
                    "INSERT INTO activo_fijo (id, empresa_id, descripcion, categoria, fecha_adquisicion, costo,"
                            + " vida_util_meses, operacion_id, creado_por)"
                            + " VALUES (?, ?, 'Activo de prueba', 'MOBILIARIO_EQUIPO', ?, 1000.00, 60, ?, 'sistema')",
                    id,
                    empresa,
                    FECHA,
                    operacion);
        }
    }

    /** Siembra, como dueño, una cuota de depreciación vigente de marzo de 2026, del activo y asiento dados. */
    private static void sembrarDepreciacion(UUID empresa, UUID activo, UUID asiento) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            ejecutar(
                    c,
                    "INSERT INTO depreciacion_registrada (id, empresa_id, activo_id, anio, mes, monto, asiento_id,"
                            + " creado_por) VALUES (?, ?, ?, 2026, 3, 16.67, ?, 'sistema')",
                    UUID.randomUUID(),
                    empresa,
                    activo,
                    asiento);
        }
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

    /** Consulta escalar sobre una conexión ya abierta (para el esquema aislado, con su search_path fijado). */
    private static String escalar(Connection c, String sql, Object... params) throws SQLException {
        return columna(c, sql, params).get(0);
    }

    /** Ejecuta con pilot_app y la empresa E1 y exige "permission denied" (SQLState 42501, permiso de tabla o columna). */
    private static void assertDenegado(String sql, Object... params) throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            assertThatThrownBy(() -> ejecutar(c, sql, params)).isInstanceOfSatisfying(SQLException.class, e -> {
                assertThat(e.getSQLState()).isEqualTo("42501");
                assertThat((Object) e).isInstanceOf(PSQLException.class);
            });
        }
    }

    /**
     * Ejecuta como dueño una sentencia que debe violar una restricción y afirma el SQLState y el nombre exacto de
     * la restricción, para que la prueba no pase por otra causa (patrón de {@link MigracionesContabilidadIT}).
     */
    private static void assertRestriccion(String sqlState, String restriccion, String sql, Object... params)
            throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            assertThatThrownBy(() -> ejecutar(c, sql, params)).isInstanceOfSatisfying(SQLException.class, e -> {
                assertThat(e.getSQLState()).isEqualTo(sqlState);
                assertThat((Object) e).isInstanceOf(PSQLException.class);
                assertThat(((PSQLException) e).getServerErrorMessage().getConstraint())
                        .isEqualTo(restriccion);
            });
        }
    }
}
