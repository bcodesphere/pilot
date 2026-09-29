package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.aplicacion.ConsultaReportes;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.CodigoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DiferenciaMayorizacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.MovimientoLinea;
import com.bcodesphere.pilot.contabilidad.dominio.estados.NetoCuenta;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link ConsultaReportes} (CQRS ligero, CLAUDE.md 4.1): consultas SQL de solo lectura y
 * agregadas sobre {@code asiento}, {@code asiento_linea}, {@code saldo_cuenta_mensual} y {@code cuenta_contable}.
 * Toda sentencia filtra por {@code empresa_id} además de RLS y los JOIN igualan también la empresa
 * (CLAUDE.md 1.1.3). Cada método hace una sola consulta agregada por bloque de datos, nunca una por cuenta, para no
 * caer en N+1 con catálogos o períodos grandes (objetivo de rendimiento del plan de trabajo: menos de 2 s con
 * 10 000 asientos).
 */
@Repository
class ConsultaReportesJdbc implements ConsultaReportes {

    /** Columnas de una cuenta completa, con el alias dado, para reconstruir el {@link Cuenta} del dominio. */
    private static String columnasCuenta(String alias) {
        return alias + ".id AS " + alias + "_id, " + alias + ".codigo AS " + alias + "_codigo, "
                + alias + ".nombre AS " + alias + "_nombre, " + alias + ".cuenta_padre_id AS " + alias + "_padre, "
                + alias + ".naturaleza AS " + alias + "_naturaleza, "
                + alias + ".acepta_movimientos AS " + alias + "_acepta, " + alias + ".activa AS " + alias
                + "_activa, " + alias + ".version AS " + alias + "_version";
    }

    /** Reconstruye un {@link Cuenta} de dominio desde las columnas de {@link #columnasCuenta(String)}. */
    private static Cuenta leerCuenta(ResultSet rs, String alias) throws SQLException {
        return new Cuenta(
                rs.getObject(alias + "_id", UUID.class),
                CodigoCuenta.de(rs.getString(alias + "_codigo")),
                rs.getString(alias + "_nombre"),
                rs.getObject(alias + "_padre", UUID.class),
                NaturalezaCuenta.valueOf(rs.getString(alias + "_naturaleza")),
                rs.getBoolean(alias + "_acepta"),
                rs.getBoolean(alias + "_activa"),
                rs.getLong(alias + "_version"));
    }

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    ConsultaReportesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<UUID, NetoCuenta> saldoAcumuladoAFecha(LocalDate fecha) {
        // 1. Meses completos anteriores al mes de la fecha, ya acumulados en saldo_cuenta_mensual (CLAUDE.md 10.3)
        // 2. El mes de la fecha (parcial) se suma directo de asiento_linea, solo hasta el día de corte
        return jdbc
                .sql("WITH meses AS ("
                        + "  SELECT cuenta_id, SUM(total_debe) AS debe, SUM(total_haber) AS haber"
                        + "  FROM saldo_cuenta_mensual"
                        + "  WHERE empresa_id = :empresa AND (anio < :anio OR (anio = :anio AND mes < :mes))"
                        + "  GROUP BY cuenta_id"
                        + "), parcial AS ("
                        + "  SELECT cuenta_id, SUM(debe) AS debe, SUM(haber) AS haber"
                        + "  FROM asiento_linea"
                        + "  WHERE empresa_id = :empresa AND fecha <= :fecha"
                        + "    AND EXTRACT(YEAR FROM fecha) = :anio AND EXTRACT(MONTH FROM fecha) = :mes"
                        + "  GROUP BY cuenta_id"
                        + ")"
                        + " SELECT COALESCE(m.cuenta_id, p.cuenta_id) AS cuenta_id,"
                        + "        COALESCE(m.debe, 0) + COALESCE(p.debe, 0) AS debe,"
                        + "        COALESCE(m.haber, 0) + COALESCE(p.haber, 0) AS haber"
                        + " FROM meses m FULL OUTER JOIN parcial p ON p.cuenta_id = m.cuenta_id")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("anio", fecha.getYear())
                .param("mes", fecha.getMonthValue())
                .param("fecha", fecha)
                .query(this::filaNeto)
                .stream()
                .collect(Collectors.toMap(Fila::cuentaId, Fila::neto));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<UUID, NetoCuenta> movimientosEnRango(LocalDate desde, LocalDate hasta) {
        return jdbc
                .sql("SELECT cuenta_id, SUM(debe) AS debe, SUM(haber) AS haber FROM asiento_linea"
                        + " WHERE empresa_id = :empresa AND fecha >= :desde AND fecha <= :hasta GROUP BY cuenta_id")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("desde", desde)
                .param("hasta", hasta)
                .query(this::filaNeto)
                .stream()
                .collect(Collectors.toMap(Fila::cuentaId, Fila::neto));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public NetoCuenta saldoAcumuladoAFecha(Set<UUID> cuentaIds, LocalDate fecha) {
        if (cuentaIds.isEmpty()) {
            return NetoCuenta.CERO;
        }
        // Mismo cálculo que saldoAcumuladoAFecha(fecha), pero ya sumado sobre el conjunto de cuentas dado
        // (el Libro Mayor de una cuenta padre suma el saldo de todas sus cuentas de detalle, ADR-038 §5)
        return jdbc.sql("WITH meses AS ("
                        + "  SELECT COALESCE(SUM(total_debe), 0) AS debe, COALESCE(SUM(total_haber), 0) AS haber"
                        + "  FROM saldo_cuenta_mensual"
                        + "  WHERE empresa_id = :empresa AND cuenta_id = ANY(string_to_array(:ids, ',')::uuid[])"
                        + "    AND (anio < :anio OR (anio = :anio AND mes < :mes))"
                        + "), parcial AS ("
                        + "  SELECT COALESCE(SUM(debe), 0) AS debe, COALESCE(SUM(haber), 0) AS haber"
                        + "  FROM asiento_linea"
                        + "  WHERE empresa_id = :empresa AND cuenta_id = ANY(string_to_array(:ids, ',')::uuid[])"
                        + "    AND fecha <= :fecha AND EXTRACT(YEAR FROM fecha) = :anio"
                        + "    AND EXTRACT(MONTH FROM fecha) = :mes"
                        + ")"
                        + " SELECT (m.debe + p.debe) AS debe, (m.haber + p.haber) AS haber FROM meses m, parcial p")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("ids", unirIds(cuentaIds))
                .param("anio", fecha.getYear())
                .param("mes", fecha.getMonthValue())
                .param("fecha", fecha)
                .query((rs, n) ->
                        new NetoCuenta(new Dinero(rs.getBigDecimal("debe")), new Dinero(rs.getBigDecimal("haber"))))
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<MovimientoLinea> listarMovimientos(Set<UUID> cuentaIds, LocalDate desde, LocalDate hasta) {
        if (cuentaIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT l.fecha, l.asiento_id, a.anio, a.numero, a.concepto, l.descripcion, l.debe, l.haber,"
                        + " " + columnasCuenta("c")
                        + " FROM asiento_linea l"
                        + " JOIN asiento a ON a.id = l.asiento_id AND a.empresa_id = l.empresa_id"
                        + " JOIN cuenta_contable c ON c.empresa_id = l.empresa_id AND c.id = l.cuenta_id"
                        + " WHERE l.empresa_id = :empresa AND l.cuenta_id = ANY(string_to_array(:ids, ',')::uuid[])"
                        + "   AND l.fecha >= :desde AND l.fecha <= :hasta"
                        + " ORDER BY l.fecha, a.anio, a.numero, l.numero_linea")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("ids", unirIds(cuentaIds))
                .param("desde", desde)
                .param("hasta", hasta)
                .query((rs, n) -> new MovimientoLinea(
                        rs.getObject("fecha", LocalDate.class),
                        rs.getObject("asiento_id", UUID.class),
                        rs.getInt("anio"),
                        rs.getLong("numero"),
                        rs.getString("concepto"),
                        rs.getString("descripcion"),
                        leerCuenta(rs, "c"),
                        new Dinero(rs.getBigDecimal("debe")),
                        new Dinero(rs.getBigDecimal("haber"))))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<DiferenciaMayorizacion> diagnosticarMayorizacion() {
        // Compara, cuenta por cuenta/año/mes, el acumulado de saldo_cuenta_mensual contra la suma de asiento_linea
        // (invariante de ADR-018); solo trae las filas donde difieren
        return jdbc.sql("WITH saldos AS ("
                        + "  SELECT cuenta_id, anio, mes, total_debe, total_haber"
                        + "  FROM saldo_cuenta_mensual WHERE empresa_id = :empresa"
                        + "), lineas AS ("
                        + "  SELECT cuenta_id, EXTRACT(YEAR FROM fecha)::smallint AS anio,"
                        + "         EXTRACT(MONTH FROM fecha)::smallint AS mes,"
                        + "         SUM(debe) AS total_debe, SUM(haber) AS total_haber"
                        + "  FROM asiento_linea WHERE empresa_id = :empresa GROUP BY cuenta_id, anio, mes"
                        + ")"
                        + " SELECT COALESCE(s.cuenta_id, l.cuenta_id) AS cuenta_id,"
                        + "        COALESCE(s.anio, l.anio) AS anio, COALESCE(s.mes, l.mes) AS mes,"
                        + "        COALESCE(s.total_debe, 0) AS saldo_debe, COALESCE(s.total_haber, 0) AS saldo_haber,"
                        + "        COALESCE(l.total_debe, 0) AS lineas_debe, COALESCE(l.total_haber, 0) AS lineas_haber,"
                        + "        " + columnasCuenta("c")
                        + " FROM saldos s FULL OUTER JOIN lineas l"
                        + "        ON l.cuenta_id = s.cuenta_id AND l.anio = s.anio AND l.mes = s.mes"
                        + " JOIN cuenta_contable c ON c.empresa_id = :empresa"
                        + "      AND c.id = COALESCE(s.cuenta_id, l.cuenta_id)"
                        + " WHERE COALESCE(s.total_debe, 0) <> COALESCE(l.total_debe, 0)"
                        + "    OR COALESCE(s.total_haber, 0) <> COALESCE(l.total_haber, 0)"
                        + " ORDER BY c.codigo, anio, mes")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .query((rs, n) -> new DiferenciaMayorizacion(
                        leerCuenta(rs, "c"),
                        rs.getInt("anio"),
                        rs.getInt("mes"),
                        new Dinero(rs.getBigDecimal("saldo_debe")),
                        new Dinero(rs.getBigDecimal("saldo_haber")),
                        new Dinero(rs.getBigDecimal("lineas_debe")),
                        new Dinero(rs.getBigDecimal("lineas_haber"))))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public int contarCombinacionesRevisadas() {
        // UNION (no UNION ALL) descarta duplicados sobre las tres columnas, así que cada fila resultante es una
        // combinación cuenta/año/mes distinta, aunque saldo_cuenta_mensual y asiento_linea coincidan en varias
        return jdbc.sql("SELECT COUNT(*) FROM ("
                        + "  SELECT cuenta_id, anio, mes FROM saldo_cuenta_mensual WHERE empresa_id = :empresa"
                        + "  UNION"
                        + "  SELECT cuenta_id, EXTRACT(YEAR FROM fecha)::smallint AS anio,"
                        + "         EXTRACT(MONTH FROM fecha)::smallint AS mes"
                        + "  FROM asiento_linea WHERE empresa_id = :empresa"
                        + ") t")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .query(Integer.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Map<OrigenAsiento, NetoCuenta> movimientosPorOrigen(UUID cuentaId, int anio, int mes) {
        return jdbc
                .sql("SELECT a.origen_tipo AS origen, SUM(l.debe) AS debe, SUM(l.haber) AS haber"
                        + " FROM asiento_linea l JOIN asiento a ON a.id = l.asiento_id AND a.empresa_id = l.empresa_id"
                        + " WHERE l.empresa_id = :empresa AND l.cuenta_id = :cuenta"
                        + "   AND EXTRACT(YEAR FROM l.fecha) = :anio AND EXTRACT(MONTH FROM l.fecha) = :mes"
                        + " GROUP BY a.origen_tipo")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("cuenta", cuentaId)
                .param("anio", anio)
                .param("mes", mes)
                .query((rs, n) -> Map.entry(
                        OrigenAsiento.valueOf(rs.getString("origen")),
                        new NetoCuenta(new Dinero(rs.getBigDecimal("debe")), new Dinero(rs.getBigDecimal("haber")))))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** Fila cruda (cuenta_id, debe, haber) para las consultas agregadas por cuenta. */
    private record Fila(UUID cuentaId, NetoCuenta neto) {}

    /** Mapea una fila con columnas {@code cuenta_id}, {@code debe} y {@code haber} a {@link Fila}. */
    private Fila filaNeto(ResultSet rs, int n) throws SQLException {
        return new Fila(
                rs.getObject("cuenta_id", UUID.class),
                new NetoCuenta(new Dinero(rs.getBigDecimal("debe")), new Dinero(rs.getBigDecimal("haber"))));
    }

    /** Junta los ids en una cadena separada por comas, para pasarlos como {@code string_to_array(...)::uuid[]}. */
    private static String unirIds(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }
}
