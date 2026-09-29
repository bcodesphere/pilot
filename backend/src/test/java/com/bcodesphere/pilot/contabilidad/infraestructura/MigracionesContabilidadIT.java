package com.bcodesphere.pilot.contabilidad.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.soporte.PostgresContenedor;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * Pruebas de las migraciones V9 a V12 (F2-02): tasa de IVA, plantillas globales, catálogo, configuración y reglas
 * por empresa. Flyway corre por API sobre el dueño, sin contexto de Spring.
 * Fuente: CLAUDE.md 4.5, 9.3, 10.2, 12.5; ADR-002, ADR-020, ADR-034, ADR-035.
 */
class MigracionesContabilidadIT {

    // UUID fijos de la siembra (prefijo propio: no chocan con los de otras clases IT que comparten el contenedor)
    private static final UUID E1 = UUID.fromString("00000000-0000-7000-8000-0000000000e1");
    private static final UUID E2 = UUID.fromString("00000000-0000-7000-8000-0000000000e2");
    private static final UUID E3 = UUID.fromString("00000000-0000-7000-8000-0000000000e3");

    /** Cuentas de las empresas: clase (código 1), IVA débito (21020101), IVA crédito (11040101) y hoja de regla. */
    private static final UUID E1_CLASE = UUID.fromString("00000000-0000-7000-8000-0000000001a1");

    private static final UUID E1_DEBITO = UUID.fromString("00000000-0000-7000-8000-0000000001a2");
    private static final UUID E1_CREDITO = UUID.fromString("00000000-0000-7000-8000-0000000001a3");
    private static final UUID E1_CAJA = UUID.fromString("00000000-0000-7000-8000-0000000001a4");
    private static final UUID E2_CLASE = UUID.fromString("00000000-0000-7000-8000-0000000001b1");
    private static final UUID E2_DEBITO = UUID.fromString("00000000-0000-7000-8000-0000000001b2");
    private static final UUID E2_CREDITO = UUID.fromString("00000000-0000-7000-8000-0000000001b3");
    private static final UUID E2_CAJA = UUID.fromString("00000000-0000-7000-8000-0000000001b4");
    private static final UUID E3_DEBITO = UUID.fromString("00000000-0000-7000-8000-0000000001c2");
    private static final UUID E3_CREDITO = UUID.fromString("00000000-0000-7000-8000-0000000001c3");
    private static final UUID REGLA_E1 = UUID.fromString("00000000-0000-7000-8000-0000000001d1");
    private static final UUID REGLA_E2 = UUID.fromString("00000000-0000-7000-8000-0000000001d2");

    /** Migra y siembra, como dueño (salta RLS), tres empresas: E1 y E2 completas y E3 solo con cuentas. */
    @BeforeAll
    static void migrarYSembrar() throws SQLException {
        // 1. Migraciones reales del classpath
        Flyway.configure()
                .dataSource(PostgresContenedor.dataSourceDuenio())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            // 2. Empresas personales sin propietario (no se necesita usuario para estas pruebas)
            for (UUID e : List.of(E1, E2, E3)) {
                ejecutar(c, "INSERT INTO empresa (id, tipo, nombre) VALUES (?, 'PERSONAL', 'Empresa de prueba')", e);
            }
            // 3. Cuentas: primero la clase (padre) y luego las de detalle
            cuenta(c, E1_CLASE, E1, "1", 1, null, false);
            cuenta(c, E1_DEBITO, E1, "21020101", 5, null, true);
            cuenta(c, E1_CREDITO, E1, "11040101", 5, null, true);
            cuenta(c, E1_CAJA, E1, "11010101", 5, null, true);
            cuenta(c, E2_CLASE, E2, "1", 1, null, false);
            cuenta(c, E2_DEBITO, E2, "21020101", 5, null, true);
            cuenta(c, E2_CREDITO, E2, "11040101", 5, null, true);
            cuenta(c, E2_CAJA, E2, "11010101", 5, null, true);
            cuenta(c, E3_DEBITO, E3, "21020101", 5, null, true);
            cuenta(c, E3_CREDITO, E3, "11040101", 5, null, true);
            // 4. Configuración y una regla por empresa (E1 y E2)
            configuracion(c, E1, E1_DEBITO, E1_CREDITO);
            configuracion(c, E2, E2_DEBITO, E2_CREDITO);
            regla(c, REGLA_E1, E1, E1_CAJA);
            regla(c, REGLA_E2, E2, E2_CAJA);
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

    private static void cuenta(
            Connection c, UUID id, UUID empresa, String codigo, int nivel, UUID padre, boolean detalle)
            throws SQLException {
        ejecutar(
                c,
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, cuenta_padre_id, naturaleza,"
                        + " acepta_movimientos, creado_por) VALUES (?, ?, ?, 'Cuenta de prueba', ?, ?, 'DEUDORA', ?, 'sistema')",
                id,
                empresa,
                codigo,
                nivel,
                padre,
                detalle);
    }

    private static void configuracion(Connection c, UUID empresa, UUID debito, UUID credito) throws SQLException {
        ejecutar(
                c,
                "INSERT INTO configuracion_contable (empresa_id, cuenta_iva_debito_id, cuenta_iva_credito_id)"
                        + " VALUES (?, ?, ?)",
                empresa,
                debito,
                credito);
    }

    // prefijo_permitido es NOT NULL desde V16 (ADR-042); '1101' es el mismo valor que V16 asigna a
    // CIERRE_INGRESOS_DIARIO/COBRO/EFECTIVO al completar las empresas ya instaladas.
    private static void regla(Connection c, UUID id, UUID empresa, UUID cuenta) throws SQLException {
        ejecutar(
                c,
                "INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id,"
                        + " prefijo_permitido, creado_por)"
                        + " VALUES (?, ?, 'CIERRE_INGRESOS_DIARIO', 'COBRO', 'EFECTIVO', ?, '1101', 'sistema')",
                id,
                empresa,
                cuenta);
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

    /** Consulta escalar como dueño (tablas globales y conteos). */
    private static String escalarDuenio(String sql, Object... params) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            return columna(c, sql, params).get(0);
        }
    }

    /** Ejecuta con pilot_app y la empresa E1 y exige "permission denied" (permiso a nivel de tabla o columna). */
    private static void assertDenegado(String sql, Object... params) throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            assertThatThrownBy(() -> ejecutar(c, sql, params))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    /**
     * Ejecuta como dueño una sentencia que debe violar una restricción (aplican a todos los roles) y afirma el
     * SQLState y el nombre exacto de la restricción, para que la prueba no pase por otra causa.
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

    // ---------------------------------------------------------------- Datos precargados

    /**
     * Fuente: docs/contabilidad/catalogo-base.md — catálogo del PDF de la U. Católica (ADR-044, tarea CAT):
     * 455 cuentas, de ellas 333 de detalle (sin hijas en la plantilla).
     */
    @Test
    void plantillaCuentaTiene455CuentasY333DeDetalle() throws SQLException {
        assertThat(escalarDuenio("SELECT count(*) FROM plantilla_cuenta")).isEqualTo("455");
        assertThat(escalarDuenio(
                        "SELECT count(*) FROM plantilla_cuenta h WHERE NOT EXISTS (SELECT 1 FROM plantilla_cuenta k"
                                + " WHERE k.codigo <> h.codigo AND starts_with(k.codigo, h.codigo))"))
                .isEqualTo("333");
    }

    /**
     * Regla (ADR-044, tarea CAT): el grupo 44 (impuesto sobre la renta) no existe en el PDF; se conserva igual que
     * en V15 porque el Estado de Resultados lo presenta aparte (ADR-037), con las mismas 5 filas y su naturaleza.
     */
    @Test
    void elGrupo44SeConservaIgualQueEnV15() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            List<String> filas = columna(
                    c,
                    "SELECT codigo || '|' || nombre || '|' || naturaleza FROM plantilla_cuenta"
                            + " WHERE codigo IN ('44','4401','440101','44010101','44010102')");
            assertThat(filas)
                    .containsExactlyInAnyOrder(
                            "44|IMPUESTO SOBRE LA RENTA|DEUDORA",
                            "4401|Impuesto sobre la renta|DEUDORA",
                            "440101|Impuesto sobre la renta|DEUDORA",
                            "44010101|Gasto por impuesto sobre la renta corriente|DEUDORA",
                            "44010102|Gasto (ingreso) por impuesto sobre la renta diferido|DEUDORA");
        }
    }

    /** Regla (ADR-037, decisión 2): la clase 3 pasa de "CAPITAL CONTABLE" a "PATRIMONIO" (terminología NIIF para PYMES). */
    @Test
    void laClase3SeLlamaPatrimonio() throws SQLException {
        assertThat(escalarDuenio("SELECT nombre FROM plantilla_cuenta WHERE codigo = '3'"))
                .isEqualTo("PATRIMONIO");
    }

    /** Regla (CLAUDE.md 10.2): el código del padre es prefijo del de la hija; todo código que no es clase lo tiene. */
    @Test
    void todaCuentaQueNoEsClaseTieneSuPadreEnLaPlantilla() throws SQLException {
        String huerfanas =
                escalarDuenio("SELECT count(*) FROM plantilla_cuenta h WHERE length(h.codigo) > 1 AND NOT EXISTS ("
                        + " SELECT 1 FROM plantilla_cuenta p WHERE p.codigo = left(h.codigo,"
                        + " CASE length(h.codigo) WHEN 2 THEN 1 WHEN 4 THEN 2 WHEN 6 THEN 4 ELSE 6 END))");
        assertThat(huerfanas).isEqualTo("0");
    }

    /**
     * Fuente: catálogo del PDF de la U. Católica (ADR-044, tarea CAT) — las cuentas marcadas "(CR)" en el PDF (y
     * sus descendientes, siempre marcados también) más 410104, cuenta propia de Pilot agregada como "(nueva, CR)".
     * Todas son ACREEDORA aunque su clase sea deudora (docs/contabilidad/catalogo-base.md).
     */
    @Test
    void lasVeintitresNaturalezasDeExcepcionEstanBienCargadas() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            // Por defecto: deudora en clases 1 y 4, acreedora en 2, 3 y 5; se listan solo las que se apartan
            List<String> excepciones = columna(
                    c,
                    "SELECT codigo || ':' || naturaleza FROM plantilla_cuenta WHERE naturaleza <>"
                            + " CASE WHEN clase IN (1, 4) THEN 'DEUDORA' ELSE 'ACREEDORA' END ORDER BY codigo");
            assertThat(excepciones)
                    .containsExactly(
                            "110301:ACREEDORA",
                            "1103:ACREEDORA",
                            "110505:ACREEDORA",
                            "12010801:ACREEDORA",
                            "12010802:ACREEDORA",
                            "12010803:ACREEDORA",
                            "12010804:ACREEDORA",
                            "12010805:ACREEDORA",
                            "12010806:ACREEDORA",
                            "120108:ACREEDORA",
                            "12020701:ACREEDORA",
                            "12020702:ACREEDORA",
                            "12020703:ACREEDORA",
                            "12020704:ACREEDORA",
                            "12020705:ACREEDORA",
                            "120207:ACREEDORA",
                            "12040601:ACREEDORA",
                            "12040602:ACREEDORA",
                            "12040603:ACREEDORA",
                            "12040604:ACREEDORA",
                            "12040605:ACREEDORA",
                            "120406:ACREEDORA",
                            "410104:ACREEDORA");
        }
    }

    /** Fuente: CLAUDE.md 12.5 y ADR-044 (tarea CAT) — las 9 reglas precargadas con sus cuentas por defecto del PDF. */
    @Test
    void lasReglasPrecargadasCoincidenConLaSeccion125() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            List<String> reglas = columna(
                    c,
                    "SELECT categoria || '/' || codigo || '=' || COALESCE(cuenta_codigo, 'NULL') || '/' || activa"
                            + " FROM plantilla_regla_contabilizacion WHERE tipo_operacion = 'CIERRE_INGRESOS_DIARIO'"
                            + " ORDER BY categoria, codigo");
            assertThat(reglas)
                    .containsExactly(
                            "COBRO/CHEQUE=11010201/true",
                            "COBRO/CREDITO=11020101/true",
                            "COBRO/EFECTIVO=11010101/true",
                            "COBRO/OTRO=NULL/false",
                            "COBRO/TARJETA=11020201/true",
                            "COBRO/TRANSFERENCIA=11010201/true",
                            "INGRESO/VENTAS_EXENTAS=51010104/true",
                            "INGRESO/VENTAS_GRAVADAS=51010102/true",
                            "INGRESO/VENTAS_NO_SUJETAS=51010105/true");
        }
    }

    /**
     * Regla (CON-006, ADR-035): las cuentas de reglas y configuración por defecto aceptan movimientos. Ya no se
     * exige nivel = 5: el catálogo del PDF (ADR-044) tiene hojas de nivel 4 sin hijas de 8 dígitos (p. ej. 110901
     * "Compras Locales"), que sí aceptan movimientos porque acepta_movimientos depende de no tener hijas, no del
     * nivel (Cuenta.puedeUsarse, dominio/catalogo/Cuenta.java).
     */
    @Test
    void lasCuentasDeReglasYConfiguracionAceptanMovimientos() throws SQLException {
        // Tiene hijas (por prefijo) = no acepta movimientos; debe ser cero en ambos casos
        String reglasConHijas =
                escalarDuenio("SELECT count(*) FROM plantilla_regla_contabilizacion r JOIN plantilla_cuenta c"
                        + " ON c.codigo = r.cuenta_codigo WHERE EXISTS (SELECT 1 FROM plantilla_cuenta h"
                        + " WHERE h.codigo <> c.codigo AND starts_with(h.codigo, c.codigo))");
        String configConHijas = escalarDuenio(
                "SELECT count(*) FROM plantilla_configuracion_contable f JOIN plantilla_cuenta c"
                        + " ON c.codigo IN (f.cuenta_iva_debito_codigo, f.cuenta_iva_credito_codigo)"
                        + " WHERE EXISTS (SELECT 1 FROM plantilla_cuenta h"
                        + " WHERE h.codigo <> c.codigo AND starts_with(h.codigo, c.codigo))");
        assertThat(reglasConHijas).isEqualTo("0");
        assertThat(configConHijas).isEqualTo("0");
    }

    /** Regla (ADR-035): COBRO/OTRO nace inactiva y sin cuenta; el contador debe configurarla. */
    @Test
    void reglaOtroEstaInactivaYSinCuenta() throws SQLException {
        assertThat(escalarDuenio("SELECT count(*) FROM plantilla_regla_contabilizacion WHERE codigo = 'OTRO'"
                        + " AND NOT activa AND cuenta_codigo IS NULL"))
                .isEqualTo("1");
        // 2 desde V16 (B1/ADR-041): OTRO (CIERRE_INGRESOS_DIARIO) y OTRO_GASTO (COMPRA_GASTO) nacen inactivas y sin
        // cuenta por defecto (ADR-035); las demás reglas guiadas sí tienen cuenta.
        assertThat(escalarDuenio("SELECT count(*) FROM plantilla_regla_contabilizacion WHERE NOT activa"))
                .isEqualTo("2");
    }

    /** Fuente: ADR-044 (tarea CAT) — una sola fila: CON_IVA, débito 21080101, crédito 110901. */
    @Test
    void laConfiguracionPlantillaTieneUnaSolaFila() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            assertThat(columna(
                            c,
                            "SELECT modo_precio_defecto || '/' || cuenta_iva_debito_codigo || '/'"
                                    + " || cuenta_iva_credito_codigo FROM plantilla_configuracion_contable"))
                    .containsExactly("CON_IVA/21080101/110901");
        }
        // Una segunda fila se rechaza por llave primaria (id=true) y por CHECK (id=false)
        assertRestriccion(
                "23505",
                "plantilla_configuracion_contable_pkey",
                "INSERT INTO plantilla_configuracion_contable (modo_precio_defecto, cuenta_iva_debito_codigo,"
                        + " cuenta_iva_credito_codigo) VALUES ('SIN_IVA', '21080101', '110901')");
        assertRestriccion(
                "23514",
                "ck_plantilla_configuracion_unica",
                "INSERT INTO plantilla_configuracion_contable (id, modo_precio_defecto, cuenta_iva_debito_codigo,"
                        + " cuenta_iva_credito_codigo) VALUES (false, 'SIN_IVA', '21080101', '110901')");
    }

    /** Fuente: ADR-034 — una sola tasa de IVA, 0.1300, con fecha técnica de inicio 2000-01-01 y sin fin. */
    @Test
    void hayUnaSolaTasaDeIvaDel13DesdeElAno2000() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection();
                PreparedStatement ps =
                        c.prepareStatement("SELECT tipo, tasa, vigente_desde, vigente_hasta FROM tasa_impuesto");
                ResultSet rs = ps.executeQuery()) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("tipo")).isEqualTo("IVA");
            assertThat(rs.getBigDecimal("tasa")).isEqualByComparingTo(new BigDecimal("0.1300"));
            assertThat(rs.getObject("vigente_desde", LocalDate.class)).isEqualTo(LocalDate.of(2000, 1, 1));
            assertThat(rs.getObject("vigente_hasta")).isNull();
            assertThat(rs.next()).isFalse();
        }
    }

    // ---------------------------------------------------------------- Restricciones

    /** Regla (CON-010): solo clases 1 a 5; un código de clase 6 se rechaza. */
    @Test
    void rechazaCodigoDeClase6() throws SQLException {
        assertRestriccion(
                "23514",
                "ck_cuenta_contable_codigo",
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " creado_por) VALUES (?, ?, '61', 'X', 2, 'DEUDORA', false, 'sistema')",
                UUID.randomUUID(),
                E1);
    }

    /** Regla (CON-015): la longitud del código es 1, 2, 4, 6 u 8; uno de 3 dígitos se rechaza. */
    @Test
    void rechazaCodigoDeLongitud3() throws SQLException {
        assertRestriccion(
                "23514",
                "ck_cuenta_contable_longitud",
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " creado_por) VALUES (?, ?, '111', 'X', 3, 'DEUDORA', false, 'sistema')",
                UUID.randomUUID(),
                E1);
    }

    /** Regla (CLAUDE.md 10.2): el nivel debe coincidir con la longitud (2 dígitos = nivel 2, no 3). */
    @Test
    void rechazaNivelIncoherenteConLaLongitud() throws SQLException {
        assertRestriccion(
                "23514",
                "ck_cuenta_contable_nivel",
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " creado_por) VALUES (?, ?, '12', 'X', 3, 'DEUDORA', false, 'sistema')",
                UUID.randomUUID(),
                E1);
    }

    /** Regla (ADR-035): el código de cuenta es único por empresa (base de CON-014). */
    @Test
    void rechazaCodigoDuplicadoEnLaMismaEmpresa() throws SQLException {
        assertRestriccion(
                "23505",
                "uq_cuenta_contable_codigo",
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " creado_por) VALUES (?, ?, '21020101', 'X', 5, 'DEUDORA', true, 'sistema')",
                UUID.randomUUID(),
                E1);
    }

    /** Regla (ADR-035): una regla activa sin cuenta se rechaza; solo la inactiva puede carecer de ella. */
    @Test
    void rechazaReglaActivaSinCuenta() throws SQLException {
        assertRestriccion(
                "23514",
                "ck_regla_contabilizacion_cuenta",
                "INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id,"
                        + " activa, prefijo_permitido, creado_por) VALUES (?, ?, 'CIERRE_INGRESOS_DIARIO', 'COBRO',"
                        + " 'OTRO', NULL, true, '11', 'sistema')",
                UUID.randomUUID(),
                E1);
    }

    /** Regla (ADR-035, decisión 6): la cuenta padre de una cuenta no puede ser de otra empresa. */
    @Test
    void rechazaCuentaPadreDeOtraEmpresa() throws SQLException {
        assertRestriccion(
                "23503",
                "fk_cuenta_contable_padre",
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, cuenta_padre_id, naturaleza,"
                        + " acepta_movimientos, creado_por) VALUES (?, ?, '12', 'X', 2, ?, 'DEUDORA', false, 'sistema')",
                UUID.randomUUID(),
                E1,
                E2_CLASE);
    }

    /** Regla (ADR-035, decisión 6): la cuenta de una regla no puede ser de otra empresa. */
    @Test
    void rechazaReglaConCuentaDeOtraEmpresa() throws SQLException {
        assertRestriccion(
                "23503",
                "fk_regla_contabilizacion_cuenta",
                "INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id,"
                        + " prefijo_permitido, creado_por)"
                        + " VALUES (?, ?, 'CIERRE_INGRESOS_DIARIO', 'COBRO', 'TARJETA', ?, '1102', 'sistema')",
                UUID.randomUUID(),
                E1,
                E2_CAJA);
    }

    /** Regla (ADR-035, decisión 6): las cuentas de IVA de la configuración son de la misma empresa (débito). */
    @Test
    void rechazaConfiguracionConIvaDebitoDeOtraEmpresa() throws SQLException {
        // E3 aún no tiene configuración; el crédito es propio y el débito es de E1
        assertRestriccion(
                "23503",
                "fk_configuracion_contable_iva_debito",
                "INSERT INTO configuracion_contable (empresa_id, cuenta_iva_debito_id, cuenta_iva_credito_id)"
                        + " VALUES (?, ?, ?)",
                E3,
                E1_DEBITO,
                E3_CREDITO);
    }

    /** Regla (ADR-035, decisión 6): ídem para la cuenta de IVA crédito. */
    @Test
    void rechazaConfiguracionConIvaCreditoDeOtraEmpresa() throws SQLException {
        assertRestriccion(
                "23503",
                "fk_configuracion_contable_iva_credito",
                "INSERT INTO configuracion_contable (empresa_id, cuenta_iva_debito_id, cuenta_iva_credito_id)"
                        + " VALUES (?, ?, ?)",
                E3,
                E3_DEBITO,
                E1_CREDITO);
    }

    /** Regla (CLAUDE.md 9.3, ADR-034): las vigencias de una misma tasa no se traslapan. */
    @Test
    void rechazaTraslapeDeVigenciasDeIva() throws SQLException {
        assertRestriccion(
                "23P01",
                "ex_tasa_impuesto_sin_traslape",
                "INSERT INTO tasa_impuesto (id, tipo, tasa, vigente_desde, vigente_hasta)"
                        + " VALUES (?, 'IVA', 0.1500, DATE '2010-01-01', NULL)",
                UUID.randomUUID());
    }

    /** Regla: la tasa es una fracción entre 0 y 1 (0.13 = 13 %); 13 sería un error de captura. */
    @Test
    void rechazaTasaFueraDeRango() throws SQLException {
        assertRestriccion(
                "23514",
                "ck_tasa_impuesto_tasa",
                "INSERT INTO tasa_impuesto (id, tipo, tasa, vigente_desde) VALUES (?, 'IVA', 13.0000, DATE '1990-01-01')",
                UUID.randomUUID());
    }

    /** Regla (V12): solo se actualizan los comentarios de nit y nrc; no debe quedar el texto "opcional en PERSONAL". */
    @Test
    void v12ActualizaLosComentariosDeNitYNrc() throws SQLException {
        String nit = escalarDuenio("SELECT col_description('empresa'::regclass, attnum) FROM pg_attribute"
                + " WHERE attrelid = 'empresa'::regclass AND attname = 'nit'");
        String nrc = escalarDuenio("SELECT col_description('empresa'::regclass, attnum) FROM pg_attribute"
                + " WHERE attrelid = 'empresa'::regclass AND attname = 'nrc'");
        assertThat(nit).doesNotContain("opcional en PERSONAL").contains("ADR-032");
        assertThat(nrc).contains("ADR-032").contains("Enterprise");
    }

    // ---------------------------------------------------------------- RLS con pilot_app

    /** Regla (CLAUDE.md 4.5): RLS habilitado y forzado en las tres tablas por empresa, y ausente en las globales. */
    @Test
    void rlsEstaForzadoEnLasTresTablasPorEmpresa() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            List<String> con = columna(
                    c,
                    "SELECT relname FROM pg_class WHERE relname IN ('cuenta_contable', 'configuracion_contable',"
                            + " 'regla_contabilizacion') AND relrowsecurity AND relforcerowsecurity ORDER BY relname");
            assertThat(con).containsExactly("configuracion_contable", "cuenta_contable", "regla_contabilizacion");
            List<String> globalesConRls = columna(
                    c,
                    "SELECT relname FROM pg_class WHERE relname IN ('tasa_impuesto', 'plantilla_cuenta',"
                            + " 'plantilla_regla_contabilizacion', 'plantilla_configuracion_contable')"
                            + " AND relrowsecurity");
            assertThat(globalesConRls).isEmpty();
        }
    }

    /** Regla (ADR-002): con app.empresa_id = E1 solo se leen filas de E1 (y hay filas), en las tres tablas. */
    @Test
    void conEmpresaE1NoSeLeenFilasDeE2() throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            for (String tabla : List.of("cuenta_contable", "configuracion_contable", "regla_contabilizacion")) {
                // Nombre de tabla fijo del propio test
                List<String> empresas = columna(c, "SELECT DISTINCT empresa_id::text FROM " + tabla);
                assertThat(empresas).as(tabla).containsExactly(E1.toString());
            }
        }
    }

    /** Regla (ADR-002): no se pueden insertar filas de otra empresa (WITH CHECK), en las tres tablas. */
    @Test
    void noPuedeInsertarFilasDeE2ConLaEmpresaE1() throws SQLException {
        Map<String, Object[]> inserciones = Map.of(
                "INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, naturaleza, acepta_movimientos,"
                        + " creado_por) VALUES (?, ?, '13', 'X', 2, 'DEUDORA', false, 'sistema')",
                new Object[] {UUID.randomUUID(), E2},
                "INSERT INTO configuracion_contable (empresa_id, cuenta_iva_debito_id, cuenta_iva_credito_id)"
                        + " VALUES (?, ?, ?)",
                new Object[] {E2, E2_DEBITO, E2_CREDITO},
                "INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id,"
                        + " prefijo_permitido, creado_por)"
                        + " VALUES (?, ?, 'CIERRE_INGRESOS_DIARIO', 'COBRO', 'TARJETA', ?, '1102', 'sistema')",
                new Object[] {UUID.randomUUID(), E2, E2_CAJA});
        for (Map.Entry<String, Object[]> e : inserciones.entrySet()) {
            try (Connection c = appConEmpresa(E1)) {
                assertThatThrownBy(() -> ejecutar(c, e.getKey(), e.getValue()))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }
        }
    }

    /** Regla (ADR-002): un UPDATE sobre filas de E2 con la empresa E1 no afecta ninguna fila. */
    @Test
    void noPuedeActualizarFilasDeE2ConLaEmpresaE1() throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            assertThat(ejecutar(c, "UPDATE cuenta_contable SET nombre = 'X' WHERE empresa_id = ?", E2))
                    .isZero();
            assertThat(ejecutar(c, "UPDATE configuracion_contable SET version = 9 WHERE empresa_id = ?", E2))
                    .isZero();
            assertThat(ejecutar(c, "UPDATE regla_contabilizacion SET activa = false WHERE empresa_id = ?", E2))
                    .isZero();
        }
    }

    /** Regla (ADR-026): sin app.empresa_id la consulta falla cerrada, en las tres tablas. */
    @Test
    void sinEmpresaEnSesionLasConsultasFallan() throws SQLException {
        for (String tabla : List.of("cuenta_contable", "configuracion_contable", "regla_contabilizacion")) {
            try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
                // Nombre de tabla fijo del propio test
                assertThatThrownBy(() -> columna(c, "SELECT count(*) FROM " + tabla))
                        .as(tabla)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("app.empresa_id");
            }
        }
    }

    // ---------------------------------------------------------------- Permisos de pilot_app

    /** Regla (ADR-019/ADR-035): pilot_app puede editar las columnas concedidas de las tres tablas. */
    @Test
    void puedeEditarLasColumnasConcedidas() throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            assertThat(ejecutar(
                            c,
                            "UPDATE cuenta_contable SET nombre = 'Otro', version = version + 1 WHERE id = ?",
                            E1_CAJA))
                    .isEqualTo(1);
            assertThat(ejecutar(c, "UPDATE configuracion_contable SET modo_precio_defecto = 'SIN_IVA'"))
                    .isEqualTo(1);
            assertThat(ejecutar(
                            c,
                            "UPDATE regla_contabilizacion SET activa = false, cuenta_id = NULL WHERE id = ?",
                            REGLA_E1))
                    .isEqualTo(1);
            // Sin commit: al cerrar la conexión se revierte y no altera la siembra
        }
    }

    /** Regla (ADR-035): las tres tablas por empresa no admiten DELETE (el catálogo se desactiva, no se borra). */
    @Test
    void deleteFallaEnLasTresTablas() throws SQLException {
        for (String t : List.of("cuenta_contable", "configuracion_contable", "regla_contabilizacion")) {
            assertDenegado("DELETE FROM " + t); // nombre de tabla fijo del propio test
        }
    }

    /** Regla: UPDATE solo de las columnas concedidas; empresa_id, clase y las claves de la regla quedan protegidas. */
    @Test
    void updateFallaEnColumnasNoConcedidas() throws SQLException {
        assertDenegado("UPDATE cuenta_contable SET empresa_id = ? WHERE id = ?", E1, E1_CAJA);
        assertDenegado("UPDATE cuenta_contable SET creado_por = 'otro' WHERE id = ?", E1_CAJA);
        assertDenegado("UPDATE configuracion_contable SET empresa_id = ?", E1);
        assertDenegado(
                "UPDATE regla_contabilizacion SET tipo_operacion = 'CIERRE_INGRESOS_DIARIO' WHERE id = ?", REGLA_E1);
        assertDenegado("UPDATE regla_contabilizacion SET empresa_id = ? WHERE id = ?", E1, REGLA_E1);
    }

    /** Regla: las tablas globales son de solo lectura para pilot_app (INSERT, UPDATE y DELETE rechazados). */
    @Test
    void lasTablasGlobalesSonDeSoloLectura() throws SQLException {
        assertDenegado(
                "INSERT INTO tasa_impuesto (id, tipo, tasa, vigente_desde) VALUES (?, 'IVA', 0.2, DATE '1990-01-01')",
                UUID.randomUUID());
        assertDenegado("INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES ('9', 'X', 'DEUDORA')");
        assertDenegado("INSERT INTO plantilla_regla_contabilizacion (tipo_operacion, categoria, codigo, activa)"
                + " VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'X', false)");
        assertDenegado(
                "INSERT INTO plantilla_configuracion_contable (id, modo_precio_defecto, cuenta_iva_debito_codigo,"
                        + " cuenta_iva_credito_codigo) VALUES (false, 'CON_IVA', '21020101', '11040101')");
        assertDenegado("UPDATE tasa_impuesto SET tasa = 0.2");
        assertDenegado("DELETE FROM plantilla_cuenta");
    }

    /** Regla: pilot_app puede leer las plantillas globales (las necesita la precarga de F2-03). */
    @Test
    void pilotAppLeeLasPlantillas() throws SQLException {
        try (Connection c = appConEmpresa(E1)) {
            assertThat(columna(c, "SELECT count(*) FROM plantilla_cuenta")).containsExactly("455");
            assertThat(columna(c, "SELECT count(*) FROM tasa_impuesto")).containsExactly("1");
        }
    }
}
