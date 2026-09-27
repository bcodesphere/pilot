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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * Pruebas de las migraciones V13 y V14 (F3-02): correlativo, asientos, líneas y saldos mensuales.
 * Flyway corre por API sobre el dueño, sin contexto de Spring. Se siembra como dueño y se comprueba con pilot_app.
 * Fuente: CLAUDE.md 4.5, 9.3, 10.1, 10.3; ADR-002, ADR-018, ADR-019, ADR-035, ADR-036.
 */
class MigracionesLibroDiarioIT {

    // UUID fijos de la siembra (prefijo propio: no chocan con los de otras clases IT que comparten el contenedor)
    private static final UUID A = UUID.fromString("00000000-0000-7000-8000-0000000000f1");
    private static final UUID B = UUID.fromString("00000000-0000-7000-8000-0000000000f2");
    /** Empresa sin correlativos ni saldos sembrados: numeración y upsert de saldos parten de cero. */
    private static final UUID G = UUID.fromString("00000000-0000-7000-8000-0000000000f3");

    private static final UUID A_DEBE = UUID.fromString("00000000-0000-7000-8000-0000000002a1");
    private static final UUID A_HABER = UUID.fromString("00000000-0000-7000-8000-0000000002a2");
    private static final UUID B_DEBE = UUID.fromString("00000000-0000-7000-8000-0000000002b1");
    private static final UUID B_HABER = UUID.fromString("00000000-0000-7000-8000-0000000002b2");
    private static final UUID G_CAJA = UUID.fromString("00000000-0000-7000-8000-0000000002c1");

    /** Fecha contable de los asientos de prueba y su año. */
    private static final LocalDate FECHA = LocalDate.of(2026, 3, 15);

    private static final short ANIO = 2026;

    /** Números de asiento únicos por empresa y año en toda la clase (los datos sembrados persisten entre pruebas). */
    private static final AtomicInteger NUMERO = new AtomicInteger(1);

    private static final String SQL_ASIENTO = "INSERT INTO asiento (id, empresa_id, anio, numero, fecha, concepto,"
            + " estado, origen_tipo, origen_id, asiento_revertido_id, asiento_reversion_id, total_debe, total_haber,"
            + " creado_por) VALUES (?, ?, ?, ?, ?, 'Asiento de prueba', ?, ?, ?, ?, ?, ?, ?, 'sistema')";

    private static final String SQL_LINEA = "INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea,"
            + " fecha, cuenta_id, debe, haber, origen_linea) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'USUARIO')";

    private static final String SQL_UPSERT_CORRELATIVO = "INSERT INTO correlativo_asiento (empresa_id, anio, ultimo)"
            + " VALUES (?, ?, 1) ON CONFLICT (empresa_id, anio) DO UPDATE SET ultimo = correlativo_asiento.ultimo + 1"
            + " RETURNING ultimo";

    /** Upsert de mayorización tal como lo define CLAUDE.md 10.3. */
    private static final String SQL_UPSERT_SALDO = "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes,"
            + " total_debe, total_haber) VALUES (?, ?, 2026, 3, ?, ?) ON CONFLICT (empresa_id, cuenta_id, anio, mes)"
            + " DO UPDATE SET total_debe = saldo_cuenta_mensual.total_debe + EXCLUDED.total_debe,"
            + " total_haber = saldo_cuenta_mensual.total_haber + EXCLUDED.total_haber, actualizado_en = now()";

    /** Migra y siembra, como dueño, tres empresas con sus cuentas de detalle y filas base en las cuatro tablas. */
    @BeforeAll
    static void migrarYSembrar() throws SQLException {
        // 1. Migraciones reales del classpath
        Flyway.configure()
                .dataSource(PostgresContenedor.dataSourceDuenio())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            // 2. Empresas personales sin propietario y sus cuentas de detalle
            for (UUID e : List.of(A, B, G)) {
                ejecutar(c, "INSERT INTO empresa (id, tipo, nombre) VALUES (?, 'PERSONAL', 'Empresa de prueba')", e);
            }
            cuenta(c, A_DEBE, A, "11010101");
            cuenta(c, A_HABER, A, "51010101");
            cuenta(c, B_DEBE, B, "11010101");
            cuenta(c, B_HABER, B, "51010101");
            cuenta(c, G_CAJA, G, "11010101");
            // 3. Una fila por empresa A y B en correlativo y saldos (base de las pruebas de aislamiento)
            ejecutar(c, "INSERT INTO correlativo_asiento (empresa_id, anio, ultimo) VALUES (?, 2020, 5)", A);
            ejecutar(c, "INSERT INTO correlativo_asiento (empresa_id, anio, ultimo) VALUES (?, 2020, 5)", B);
            ejecutar(
                    c,
                    "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes) VALUES (?, ?, 2026, 1)",
                    A,
                    A_DEBE);
            ejecutar(
                    c,
                    "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes) VALUES (?, ?, 2026, 1)",
                    B,
                    B_DEBE);
        }
        // 4. Un asiento válido por empresa A y B (base de las pruebas de aislamiento)
        sembrarAsiento(A, UUID.randomUUID(), "MANUAL", null, null, A_DEBE, A_HABER);
        sembrarAsiento(B, UUID.randomUUID(), "MANUAL", null, null, B_DEBE, B_HABER);
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

    /** Inserta cabecera de un asiento CONTABILIZADO con totales 100.00 (sin líneas; el llamador agrega las suyas). */
    private static void cabecera(
            Connection c, UUID empresa, UUID id, String origenTipo, UUID origenId, UUID revertido, BigDecimal total)
            throws SQLException {
        ejecutar(
                c,
                SQL_ASIENTO,
                id,
                empresa,
                ANIO,
                NUMERO.getAndIncrement(),
                FECHA,
                "CONTABILIZADO",
                origenTipo,
                origenId,
                revertido,
                null,
                total,
                total);
    }

    /** Inserta una línea del asiento en la fecha indicada. */
    private static void linea(
            Connection c, UUID empresa, UUID asiento, int n, LocalDate fecha, UUID cuenta, String debe, String haber)
            throws SQLException {
        ejecutar(
                c,
                SQL_LINEA,
                UUID.randomUUID(),
                empresa,
                asiento,
                n,
                fecha,
                cuenta,
                new BigDecimal(debe),
                new BigDecimal(haber));
    }

    /** Siembra como dueño, en una transacción, un asiento CONTABILIZADO válido de 100.00 con dos líneas. */
    private static void sembrarAsiento(
            UUID empresa, UUID id, String origenTipo, UUID origenId, UUID revertido, UUID debe, UUID haber)
            throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            c.setAutoCommit(false);
            cabecera(c, empresa, id, origenTipo, origenId, revertido, new BigDecimal("100.00"));
            linea(c, empresa, id, 1, FECHA, debe, "100.00", "0");
            linea(c, empresa, id, 2, FECHA, haber, "0", "100.00");
            c.commit();
        }
    }

    /** Marca como dueño un asiento como REVERTIDO por la reversión indicada (respeta el trigger de transición). */
    private static void marcarRevertido(UUID asiento, UUID reversion) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            ejecutar(
                    c,
                    "UPDATE asiento SET estado = 'REVERTIDO', asiento_reversion_id = ?, version = version + 1"
                            + " WHERE id = ?",
                    reversion,
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

    /** Consulta escalar como dueño (conteos y comprobaciones que saltan RLS). */
    private static String escalarDuenio(String sql, Object... params) throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            return columna(c, sql, params).get(0);
        }
    }

    /** Ejecuta con pilot_app y la empresa A y exige "permission denied" (permiso a nivel de tabla o columna). */
    private static void assertDenegado(String sql, Object... params) throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() -> ejecutar(c, sql, params)).isInstanceOfSatisfying(SQLException.class, e -> {
                assertThat(e.getSQLState()).isEqualTo("42501");
                assertThat(e.getMessage()).contains("permission denied");
            });
        }
    }

    /** Afirma que la acción falla con el SQLState y el nombre exacto de la restricción, para no pasar por otra causa. */
    private static void assertViolacion(String sqlState, String restriccion, ThrowingCallable accion) {
        assertThatThrownBy(accion).isInstanceOfSatisfying(SQLException.class, e -> {
            assertThat(e.getSQLState()).isEqualTo(sqlState);
            assertThat((Object) e).isInstanceOf(PSQLException.class);
            assertThat(((PSQLException) e).getServerErrorMessage().getConstraint())
                    .isEqualTo(restriccion);
        });
    }

    /** Ejecuta como dueño una sentencia que debe violar una restricción (aplican a todos los roles). */
    private static void assertRestriccion(String sqlState, String restriccion, String sql, Object... params) {
        assertViolacion(sqlState, restriccion, () -> {
            try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
                ejecutar(c, sql, params);
            }
        });
    }

    /**
     * Ejecuta con pilot_app y la empresa A el cuerpo dado dentro de una transacción y afirma que el COMMIT falla por
     * el trigger diferido (P0001), y que no quedó nada guardado del asiento.
     */
    private static void assertCommitRechazado(UUID asiento, CuerpoTransaccion cuerpo) throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            cuerpo.ejecutar(c);
            assertThatThrownBy(c::commit).isInstanceOfSatisfying(SQLException.class, e -> {
                assertThat(e.getSQLState()).isEqualTo("P0001");
                assertThat(e.getMessage()).contains("Asiento " + asiento + " inválido");
            });
        }
        // Nada quedó guardado: ni cabecera ni líneas
        assertThat(escalarDuenio("SELECT count(*) FROM asiento WHERE id = ?", asiento))
                .isEqualTo("0");
        assertThat(escalarDuenio("SELECT count(*) FROM asiento_linea WHERE asiento_id = ?", asiento))
                .isEqualTo("0");
    }

    /** Cuerpo de una transacción de prueba que puede lanzar SQLException. */
    @FunctionalInterface
    private interface CuerpoTransaccion {
        void ejecutar(Connection c) throws SQLException;
    }

    // ---------------------------------------------------------------- Trigger diferido de partida doble

    /** Regla (CLAUDE.md 9.3): una cabecera con dos líneas cuadradas guardada por pilot_app pasa el COMMIT. */
    @Test
    void asientoValidoPasaElCommit() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection c = appConEmpresa(A)) {
            cabecera(c, A, id, "MANUAL", null, null, new BigDecimal("100.00"));
            linea(c, A, id, 1, FECHA, A_DEBE, "100.00", "0");
            linea(c, A, id, 2, FECHA, A_HABER, "0", "100.00");
            c.commit();
        }
        assertThat(escalarDuenio("SELECT count(*) FROM asiento_linea WHERE asiento_id = ?", id))
                .isEqualTo("2");
    }

    /** Regla (CON-005): Σ Debe ≠ Σ Haber se rechaza al COMMIT aunque la cabecera diga 100/100. */
    @Test
    void commitRechazaAsientoDescuadrado() throws SQLException {
        UUID id = UUID.randomUUID();
        assertCommitRechazado(id, c -> {
            cabecera(c, A, id, "MANUAL", null, null, new BigDecimal("100.00"));
            linea(c, A, id, 1, FECHA, A_DEBE, "100.00", "0");
            linea(c, A, id, 2, FECHA, A_HABER, "0", "90.00");
        });
    }

    /** Regla (CON-001): un asiento con una sola línea se rechaza al COMMIT. */
    @Test
    void commitRechazaAsientoConUnaSolaLinea() throws SQLException {
        UUID id = UUID.randomUUID();
        assertCommitRechazado(id, c -> {
            cabecera(c, A, id, "MANUAL", null, null, new BigDecimal("100.00"));
            linea(c, A, id, 1, FECHA, A_DEBE, "100.00", "0");
        });
    }

    /** Regla (ADR-036, decisión 7): una cabecera sin ninguna línea tampoco pasa el COMMIT. */
    @Test
    void commitRechazaCabeceraSinLineas() throws SQLException {
        UUID id = UUID.randomUUID();
        assertCommitRechazado(id, c -> cabecera(c, A, id, "MANUAL", null, null, new BigDecimal("100.00")));
    }

    /** Regla (ADR-036, decisión 7): los totales de la cabecera deben ser iguales a la suma de sus líneas. */
    @Test
    void commitRechazaTotalesDeCabeceraDistintosDeLaSumaDeLineas() throws SQLException {
        UUID id = UUID.randomUUID();
        assertCommitRechazado(id, c -> {
            cabecera(c, A, id, "MANUAL", null, null, new BigDecimal("200.00"));
            linea(c, A, id, 1, FECHA, A_DEBE, "100.00", "0");
            linea(c, A, id, 2, FECHA, A_HABER, "0", "100.00");
        });
    }

    /** Regla (ADR-036, decisión 7): la fecha de cada línea es la del asiento. */
    @Test
    void commitRechazaLineaConOtraFecha() throws SQLException {
        UUID id = UUID.randomUUID();
        assertCommitRechazado(id, c -> {
            cabecera(c, A, id, "MANUAL", null, null, new BigDecimal("100.00"));
            linea(c, A, id, 1, FECHA, A_DEBE, "100.00", "0");
            linea(c, A, id, 2, FECHA.plusDays(1), A_HABER, "0", "100.00");
        });
    }

    // ---------------------------------------------------------------- CHECK y llaves foráneas

    /** Regla (CON-002): una línea con Debe y Haber a la vez se rechaza. */
    @Test
    void rechazaLineaConDebeYHaber() {
        assertRestriccion(
                "23514",
                "ck_asiento_linea_un_lado",
                SQL_LINEA,
                UUID.randomUUID(),
                A,
                UUID.randomUUID(),
                1,
                FECHA,
                A_DEBE,
                new BigDecimal("10.00"),
                new BigDecimal("10.00"));
    }

    /** Regla (CON-002): una línea con Debe y Haber en cero se rechaza. */
    @Test
    void rechazaLineaConAmbosLadosEnCero() {
        assertRestriccion(
                "23514",
                "ck_asiento_linea_un_lado",
                SQL_LINEA,
                UUID.randomUUID(),
                A,
                UUID.randomUUID(),
                1,
                FECHA,
                A_DEBE,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    /** Regla (CON-003): un monto negativo se rechaza. */
    @Test
    void rechazaLineaConMontoNegativo() {
        assertRestriccion(
                "23514",
                "ck_asiento_linea_debe",
                SQL_LINEA,
                UUID.randomUUID(),
                A,
                UUID.randomUUID(),
                1,
                FECHA,
                A_DEBE,
                new BigDecimal("-1.00"),
                BigDecimal.ZERO);
    }

    /** Regla (ADR-036, decisión 7): el año de numeración es el de la fecha contable. */
    @Test
    void rechazaAnioDistintoDelAnioDeLaFecha() {
        assertRestriccion(
                "23514",
                "ck_asiento_anio",
                SQL_ASIENTO,
                UUID.randomUUID(),
                A,
                (short) 2025,
                NUMERO.getAndIncrement(),
                FECHA,
                "CONTABILIZADO",
                "MANUAL",
                null,
                null,
                null,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"));
    }

    /** Regla (CON-004): la cabecera con totales en cero se rechaza. */
    @Test
    void rechazaAsientoConTotalEnCero() {
        assertRestriccion(
                "23514",
                "ck_asiento_totales",
                SQL_ASIENTO,
                UUID.randomUUID(),
                A,
                ANIO,
                NUMERO.getAndIncrement(),
                FECHA,
                "CONTABILIZADO",
                "MANUAL",
                null,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    /** Regla (ADR-036, decisión 7): una REVERSION sin asiento_revertido_id es incoherente. */
    @Test
    void rechazaReversionSinAsientoRevertido() {
        assertRestriccion(
                "23514",
                "ck_asiento_reversion_coherente",
                SQL_ASIENTO,
                UUID.randomUUID(),
                A,
                ANIO,
                NUMERO.getAndIncrement(),
                FECHA,
                "CONTABILIZADO",
                "REVERSION",
                null,
                null,
                null,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"));
    }

    /** Regla (CON-009): una reversión nunca queda REVERTIDA. */
    @Test
    void rechazaReversionMarcadaRevertida() throws SQLException {
        UUID original = UUID.randomUUID();
        sembrarAsiento(A, original, "MANUAL", null, null, A_DEBE, A_HABER);
        // Es coherente en todo (revertido y reversión enlazados) salvo que una REVERSION no puede estar REVERTIDA
        assertRestriccion(
                "23514",
                "ck_asiento_reversion_no_revertida",
                SQL_ASIENTO,
                UUID.randomUUID(),
                A,
                ANIO,
                NUMERO.getAndIncrement(),
                FECHA,
                "REVERTIDO",
                "REVERSION",
                null,
                original,
                original,
                new BigDecimal("100.00"),
                new BigDecimal("100.00"));
    }

    /** Regla (ADR-035, decisión 6): una línea no puede apuntar a una cuenta de otra empresa. */
    @Test
    void rechazaLineaConCuentaDeOtraEmpresa() throws SQLException {
        UUID id = UUID.randomUUID();
        sembrarAsiento(A, id, "MANUAL", null, null, A_DEBE, A_HABER);
        assertRestriccion(
                "23503",
                "fk_asiento_linea_cuenta",
                SQL_LINEA,
                UUID.randomUUID(),
                A,
                id,
                9,
                FECHA,
                B_DEBE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO);
    }

    /** Regla (ADR-036, decisión 7): una línea no puede apuntar a un asiento de otra empresa. */
    @Test
    void rechazaLineaConAsientoDeOtraEmpresa() throws SQLException {
        UUID idB = UUID.randomUUID();
        sembrarAsiento(B, idB, "MANUAL", null, null, B_DEBE, B_HABER);
        assertRestriccion(
                "23503",
                "fk_asiento_linea_asiento",
                SQL_LINEA,
                UUID.randomUUID(),
                A,
                idB,
                9,
                FECHA,
                A_DEBE,
                new BigDecimal("10.00"),
                BigDecimal.ZERO);
    }

    /** Regla (ADR-035, decisión 6): un saldo no puede apuntar a una cuenta de otra empresa. */
    @Test
    void rechazaSaldoConCuentaDeOtraEmpresa() {
        assertRestriccion(
                "23503",
                "fk_saldo_cuenta_mensual_cuenta",
                "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes) VALUES (?, ?, 2026, 2)",
                A,
                B_DEBE);
    }

    /** Regla (CLAUDE.md 9.3): la línea base de una línea de IVA es del mismo asiento (llave compuesta). */
    @Test
    void rechazaLineaBaseDeOtroAsiento() throws SQLException {
        UUID id = UUID.randomUUID();
        UUID otro = UUID.randomUUID();
        sembrarAsiento(A, id, "MANUAL", null, null, A_DEBE, A_HABER);
        sembrarAsiento(A, otro, "MANUAL", null, null, A_DEBE, A_HABER);
        UUID lineaDeOtro = UUID.fromString(
                escalarDuenio("SELECT id::text FROM asiento_linea WHERE asiento_id = ? AND numero_linea = 1", otro));
        assertRestriccion(
                "23503",
                "fk_asiento_linea_base",
                "INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea, fecha, cuenta_id, debe, haber,"
                        + " origen_linea, linea_base_id) VALUES (?, ?, ?, 9, ?, ?, 10, 0, 'IVA_CALCULADO', ?)",
                UUID.randomUUID(),
                A,
                id,
                FECHA,
                A_DEBE,
                lineaDeOtro);
    }

    // ---------------------------------------------------------------- Inmutabilidad (ADR-019)

    /** Criterio del plan de F3 (ADR-019): las líneas no se modifican ni se borran con pilot_app. */
    @Test
    void updateYDeleteSobreLineasFallanPorPermisos() throws SQLException {
        assertDenegado("UPDATE asiento_linea SET debe = debe");
        assertDenegado("UPDATE asiento_linea SET descripcion = 'x'");
        assertDenegado("DELETE FROM asiento_linea");
    }

    /** Regla (ADR-019): ninguna de las tablas del Libro Diario admite DELETE con pilot_app. */
    @Test
    void deleteFallaEnAsientoCorrelativoYSaldos() throws SQLException {
        assertDenegado("DELETE FROM asiento");
        assertDenegado("DELETE FROM correlativo_asiento");
        assertDenegado("DELETE FROM saldo_cuenta_mensual");
    }

    /** Regla (ADR-019): del asiento solo se actualizan estado, asiento_reversion_id y version. */
    @Test
    void noSePuedeCambiarConceptoNiTotalesDeUnAsiento() throws SQLException {
        assertDenegado("UPDATE asiento SET concepto = 'otro'");
        assertDenegado("UPDATE asiento SET total_debe = 1");
        assertDenegado("UPDATE asiento SET fecha = DATE '2026-01-01'");
    }

    // ---------------------------------------------------------------- Transiciones de estado

    /** Regla (ADR-019, CON-008): CONTABILIZADO → REVERTIDO con su reversión y version + 1 pasa con pilot_app. */
    @Test
    void permiteElPasoAReversado() throws SQLException {
        UUID original = UUID.randomUUID();
        UUID reversion = UUID.randomUUID();
        sembrarAsiento(A, original, "MANUAL", null, null, A_DEBE, A_HABER);
        sembrarAsiento(A, reversion, "REVERSION", null, original, A_HABER, A_DEBE);
        try (Connection c = appConEmpresa(A)) {
            assertThat(ejecutar(
                            c,
                            "UPDATE asiento SET estado = 'REVERTIDO', asiento_reversion_id = ?, version = version + 1"
                                    + " WHERE id = ?",
                            reversion,
                            original))
                    .isEqualTo(1);
            c.commit();
        }
        assertThat(escalarDuenio("SELECT estado FROM asiento WHERE id = ?", original))
                .isEqualTo("REVERTIDO");
    }

    /** Regla (ADR-019): un asiento REVERTIDO no vuelve a CONTABILIZADO. */
    @Test
    void rechazaVolverDeRevertidoAContabilizado() throws SQLException {
        UUID original = UUID.randomUUID();
        UUID reversion = UUID.randomUUID();
        sembrarAsiento(A, original, "MANUAL", null, null, A_DEBE, A_HABER);
        sembrarAsiento(A, reversion, "REVERSION", null, original, A_HABER, A_DEBE);
        marcarRevertido(original, reversion);
        try (Connection c = appConEmpresa(A)) {
            // Es coherente con los CHECK (CONTABILIZADO sin reversión); solo el trigger de transición lo impide
            assertThatThrownBy(() -> ejecutar(
                            c,
                            "UPDATE asiento SET estado = 'CONTABILIZADO', asiento_reversion_id = NULL,"
                                    + " version = version + 1 WHERE id = ?",
                            original))
                    .isInstanceOfSatisfying(SQLException.class, e -> {
                        assertThat(e.getSQLState()).isEqualTo("P0001");
                        assertThat(e.getMessage()).contains("Transición inválida");
                    });
        }
    }

    /**
     * Regla (ADR-019, F3-03 depende de ella): el paso a REVERTIDO exige version + 1; sin cambiar la versión o con
     * version + 2 el trigger lo rechaza y el asiento sigue CONTABILIZADO.
     */
    @Test
    void rechazaElPasoARevertidoSinVersionMasUno() throws SQLException {
        for (String version : List.of("version", "version + 2")) {
            UUID original = UUID.randomUUID();
            UUID reversion = UUID.randomUUID();
            sembrarAsiento(A, original, "MANUAL", null, null, A_DEBE, A_HABER);
            sembrarAsiento(A, reversion, "REVERSION", null, original, A_HABER, A_DEBE);
            try (Connection c = appConEmpresa(A)) {
                // Nombre de expresión fijo del propio test
                assertThatThrownBy(() -> ejecutar(
                                c,
                                "UPDATE asiento SET estado = 'REVERTIDO', asiento_reversion_id = ?, version = "
                                        + version + " WHERE id = ?",
                                reversion,
                                original))
                        .as(version)
                        .isInstanceOfSatisfying(SQLException.class, e -> {
                            assertThat(e.getSQLState()).isEqualTo("P0001");
                            assertThat(e.getMessage()).contains("Transición inválida");
                        });
            }
            assertThat(escalarDuenio("SELECT estado FROM asiento WHERE id = ?", original))
                    .isEqualTo("CONTABILIZADO");
        }
    }

    /** Regla (ADR-019): el paso a REVERTIDO exige una reversión que sea realmente un contra-asiento suyo. */
    @Test
    void rechazaRevertirConUnAsientoQueNoEsSuReversion() throws SQLException {
        UUID original = UUID.randomUUID();
        UUID ajeno = UUID.randomUUID();
        sembrarAsiento(A, original, "MANUAL", null, null, A_DEBE, A_HABER);
        sembrarAsiento(A, ajeno, "MANUAL", null, null, A_DEBE, A_HABER);
        try (Connection c = appConEmpresa(A)) {
            assertThatThrownBy(() -> ejecutar(
                            c,
                            "UPDATE asiento SET estado = 'REVERTIDO', asiento_reversion_id = ?, version = version + 1"
                                    + " WHERE id = ?",
                            ajeno,
                            original))
                    .isInstanceOfSatisfying(
                            SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("P0001"));
        }
    }

    /** Regla (CON-008): un asiento se revierte una sola vez; la segunda reversión choca con el índice único. */
    @Test
    void rechazaRevertirDosVecesElMismoAsiento() throws SQLException {
        UUID original = UUID.randomUUID();
        sembrarAsiento(A, original, "MANUAL", null, null, A_DEBE, A_HABER);
        sembrarAsiento(A, UUID.randomUUID(), "REVERSION", null, original, A_HABER, A_DEBE);
        assertViolacion(
                "23505",
                "uq_asiento_revertido_una_vez",
                () -> sembrarAsiento(A, UUID.randomUUID(), "REVERSION", null, original, A_HABER, A_DEBE));
    }

    // ---------------------------------------------------------------- Índice de operaciones de n8n

    /** Regla (CLAUDE.md 12.6): dos asientos N8N vigentes de la misma operación se rechazan. */
    @Test
    void rechazaDosAsientosVigentesDeLaMismaOperacion() throws SQLException {
        UUID operacion = UUID.randomUUID();
        sembrarAsiento(A, UUID.randomUUID(), "N8N", operacion, null, A_DEBE, A_HABER);
        assertViolacion(
                "23505",
                "uq_asiento_operacion_vigente",
                () -> sembrarAsiento(A, UUID.randomUUID(), "N8N", operacion, null, A_DEBE, A_HABER));
    }

    /** Regla (CLAUDE.md 12.8): si el primer asiento está REVERTIDO, la operación corregida puede tener otro vigente. */
    @Test
    void permiteNuevoAsientoSiElAnteriorEstaRevertido() throws SQLException {
        UUID operacion = UUID.randomUUID();
        UUID primero = UUID.randomUUID();
        UUID reversion = UUID.randomUUID();
        sembrarAsiento(A, primero, "N8N", operacion, null, A_DEBE, A_HABER);
        sembrarAsiento(A, reversion, "REVERSION", null, primero, A_HABER, A_DEBE);
        marcarRevertido(primero, reversion);
        UUID segundo = UUID.randomUUID();
        sembrarAsiento(A, segundo, "N8N", operacion, null, A_DEBE, A_HABER);
        assertThat(escalarDuenio("SELECT count(*) FROM asiento WHERE id = ?", segundo))
                .isEqualTo("1");
    }

    // ---------------------------------------------------------------- Numeración y saldos

    /** Regla (ADR-036, decisión 8): el upsert numera 1, 2 dentro del año y otro año empieza en 1, sin fila previa. */
    @Test
    void elUpsertDeCorrelativoNumeraPorAnio() throws SQLException {
        try (Connection c = appConEmpresa(G)) {
            assertThat(columna(c, SQL_UPSERT_CORRELATIVO, G, 2026)).containsExactly("1");
            assertThat(columna(c, SQL_UPSERT_CORRELATIVO, G, 2026)).containsExactly("2");
            assertThat(columna(c, SQL_UPSERT_CORRELATIVO, G, 2027)).containsExactly("1");
            // Sin commit: al cerrar se revierte y no altera otras pruebas
        }
    }

    /** Regla (CLAUDE.md 10.3, ADR-018): el upsert crea la fila y la segunda vez acumula Debe y Haber. */
    @Test
    void elUpsertDeSaldosCreaYAcumula() throws SQLException {
        try (Connection c = appConEmpresa(G)) {
            ejecutar(c, SQL_UPSERT_SALDO, G, G_CAJA, new BigDecimal("100.00"), BigDecimal.ZERO);
            ejecutar(c, SQL_UPSERT_SALDO, G, G_CAJA, new BigDecimal("50.00"), new BigDecimal("30.00"));
            assertThat(columna(c, "SELECT total_debe || '/' || total_haber FROM saldo_cuenta_mensual"))
                    .containsExactly("150.00/30.00");
        }
    }

    /** Regla (CLAUDE.md 9.3): el mes va de 1 a 12. */
    @Test
    void rechazaMesFueraDeRango() {
        assertRestriccion(
                "23514",
                "ck_saldo_cuenta_mensual_mes",
                "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes) VALUES (?, ?, 2026, 13)",
                A,
                A_DEBE);
    }

    // ---------------------------------------------------------------- RLS y aislamiento

    /** Regla (CLAUDE.md 4.5): RLS habilitado y forzado en las cuatro tablas. */
    @Test
    void rlsEstaForzadoEnLasCuatroTablas() throws SQLException {
        try (Connection c = PostgresContenedor.dataSourceDuenio().getConnection()) {
            assertThat(columna(
                            c,
                            "SELECT relname FROM pg_class WHERE relname IN ('correlativo_asiento', 'asiento',"
                                    + " 'asiento_linea', 'saldo_cuenta_mensual') AND relrowsecurity"
                                    + " AND relforcerowsecurity ORDER BY relname"))
                    .containsExactly("asiento", "asiento_linea", "correlativo_asiento", "saldo_cuenta_mensual");
            // Y cada una tiene su política aislamiento_empresa dirigida a pilot_app
            assertThat(columna(
                            c,
                            "SELECT count(*) FROM pg_policies WHERE policyname = 'aislamiento_empresa' AND roles ="
                                    + " '{pilot_app}' AND tablename IN ('correlativo_asiento', 'asiento',"
                                    + " 'asiento_linea', 'saldo_cuenta_mensual')"))
                    .containsExactly("4");
        }
    }

    /** Regla (ADR-002): con app.empresa_id = A solo se leen filas de A (y hay filas) en las cuatro tablas. */
    @Test
    void conEmpresaANoSeLeenFilasDeB() throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            for (String tabla : List.of("correlativo_asiento", "asiento", "asiento_linea", "saldo_cuenta_mensual")) {
                // Nombre de tabla fijo del propio test
                assertThat(columna(c, "SELECT DISTINCT empresa_id::text FROM " + tabla))
                        .as(tabla)
                        .containsExactly(A.toString());
            }
        }
    }

    /** Regla (ADR-002): no se pueden insertar filas de otra empresa (WITH CHECK) en las cuatro tablas. */
    @Test
    void noPuedeInsertarFilasDeBConLaEmpresaA() throws SQLException {
        UUID idB = UUID.randomUUID();
        List<CuerpoTransaccion> intentos = List.of(
                c -> ejecutar(c, "INSERT INTO correlativo_asiento (empresa_id, anio) VALUES (?, 2031)", B),
                c -> cabecera(c, B, idB, "MANUAL", null, null, new BigDecimal("100.00")),
                c -> linea(c, B, idB, 9, FECHA, B_DEBE, "10.00", "0"),
                c -> ejecutar(
                        c,
                        "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes) VALUES (?, ?, 2031, 1)",
                        B,
                        B_DEBE));
        for (CuerpoTransaccion intento : intentos) {
            try (Connection c = appConEmpresa(A)) {
                assertThatThrownBy(() -> intento.ejecutar(c))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("row-level security");
            }
        }
    }

    /** Regla (ADR-002): un UPDATE sobre filas de B con la empresa A no afecta ninguna fila. */
    @Test
    void noPuedeActualizarFilasDeBConLaEmpresaA() throws SQLException {
        try (Connection c = appConEmpresa(A)) {
            assertThat(ejecutar(c, "UPDATE correlativo_asiento SET ultimo = 99 WHERE empresa_id = ?", B))
                    .isZero();
            assertThat(ejecutar(c, "UPDATE asiento SET version = version WHERE empresa_id = ?", B))
                    .isZero();
            assertThat(ejecutar(c, "UPDATE saldo_cuenta_mensual SET total_debe = 1 WHERE empresa_id = ?", B))
                    .isZero();
        }
    }

    /** Regla (ADR-026): sin app.empresa_id la consulta falla cerrada, en las cuatro tablas. */
    @Test
    void sinEmpresaEnSesionLasConsultasFallan() throws SQLException {
        for (String tabla : List.of("correlativo_asiento", "asiento", "asiento_linea", "saldo_cuenta_mensual")) {
            try (Connection c = PostgresContenedor.dataSourceApp().getConnection()) {
                // Nombre de tabla fijo del propio test
                assertThatThrownBy(() -> columna(c, "SELECT count(*) FROM " + tabla))
                        .as(tabla)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("app.empresa_id");
            }
        }
    }
}
