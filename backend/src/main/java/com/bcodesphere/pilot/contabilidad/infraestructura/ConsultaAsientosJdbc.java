package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.aplicacion.ConsultaAsientos;
import com.bcodesphere.pilot.contabilidad.aplicacion.FiltroAsientos;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.EstadoAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenLinea;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ResumenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link ConsultaAsientos} (CQRS ligero, CLAUDE.md 4.1): consultas SQL de solo lectura sobre
 * {@code asiento} y {@code asiento_linea}. Toda sentencia filtra por {@code empresa_id} además de RLS y los JOIN
 * igualan también la empresa (CLAUDE.md 1.1.3). Sus helpers estáticos los reutiliza {@link RepositorioAsientosJdbc}
 * para leer un asiento con bloqueo.
 */
@Repository
class ConsultaAsientosJdbc implements ConsultaAsientos {

    /** Columnas de la cabecera; el alias {@code a} lo usan también las consultas con bloqueo. */
    static final String SELECT_CABECERA = "SELECT a.id, a.anio, a.numero, a.fecha, a.concepto, a.estado, a.origen_tipo,"
            + " a.origen_id, a.modo_precio, a.asiento_revertido_id, a.asiento_reversion_id, a.total_debe, a.total_haber, a.creado_en"
            + " FROM asiento a";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    ConsultaAsientosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Cabecera leída de la base, aún sin líneas. */
    record Cabecera(Asiento sinLineas) {

        /** Completa el asiento con sus líneas. */
        Asiento conLineas(List<LineaAsiento> lineas) {
            Asiento a = sinLineas;
            return new Asiento(
                    a.id(),
                    a.anio(),
                    a.numero(),
                    a.fecha(),
                    a.concepto(),
                    a.estado(),
                    a.origenTipo(),
                    a.origenId(),
                    a.modoPrecio(),
                    a.asientoRevertidoId(),
                    a.asientoReversionId(),
                    a.totalDebe(),
                    a.totalHaber(),
                    a.creadoEn(),
                    lineas);
        }
    }

    /** Convierte una fila de {@link #SELECT_CABECERA} en cabecera. */
    static Cabecera aCabecera(ResultSet rs) throws SQLException {
        String modo = rs.getString("modo_precio");
        return new Cabecera(new Asiento(
                rs.getObject("id", UUID.class),
                rs.getInt("anio"),
                rs.getLong("numero"),
                rs.getObject("fecha", LocalDate.class),
                rs.getString("concepto"),
                EstadoAsiento.valueOf(rs.getString("estado")),
                OrigenAsiento.valueOf(rs.getString("origen_tipo")),
                rs.getObject("origen_id", UUID.class),
                modo == null ? null : ModoPrecio.valueOf(modo),
                rs.getObject("asiento_revertido_id", UUID.class),
                rs.getObject("asiento_reversion_id", UUID.class),
                new Dinero(rs.getBigDecimal("total_debe")),
                new Dinero(rs.getBigDecimal("total_haber")),
                rs.getObject("creado_en", OffsetDateTime.class).toInstant(),
                List.of()));
    }

    /**
     * Lee las líneas de un asiento de la empresa activa, con el resumen de su cuenta, en orden de línea.
     *
     * @param jdbc cliente JDBC
     * @param asientoId asiento cuyas líneas se leen
     * @return las líneas
     */
    static List<LineaAsiento> leerLineas(JdbcClient jdbc, UUID asientoId) {
        return jdbc.sql("SELECT l.id, l.numero_linea, l.descripcion, l.debe, l.haber, l.origen_linea, l.linea_base_id,"
                        + " c.id AS c_id, c.codigo AS c_codigo, c.nombre AS c_nombre"
                        + " FROM asiento_linea l"
                        + " JOIN cuenta_contable c ON c.empresa_id = l.empresa_id AND c.id = l.cuenta_id"
                        + " WHERE l.asiento_id = :asiento AND l.empresa_id = :empresa ORDER BY l.numero_linea")
                .param("asiento", asientoId)
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .query((rs, n) -> new LineaAsiento(
                        rs.getObject("id", UUID.class),
                        rs.getInt("numero_linea"),
                        new ResumenCuenta(
                                rs.getObject("c_id", UUID.class), rs.getString("c_codigo"), rs.getString("c_nombre")),
                        rs.getString("descripcion"),
                        new Dinero(rs.getBigDecimal("debe")),
                        new Dinero(rs.getBigDecimal("haber")),
                        OrigenLinea.valueOf(rs.getString("origen_linea")),
                        rs.getObject("linea_base_id", UUID.class)))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Asiento> obtener(UUID id) {
        return jdbc.sql(SELECT_CABECERA + " WHERE a.id = :id AND a.empresa_id = :empresa")
                .param("id", id)
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .query((rs, n) -> aCabecera(rs))
                .optional()
                .map(c -> c.conLineas(leerLineas(jdbc, id)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ResumenAsiento> listar(FiltroAsientos f, Integer despuesDeAnio, Long despuesDeNumero, int cantidad) {
        // 1. Condiciones fijas acumuladas según los filtros presentes (nunca se concatena texto del cliente) y sus
        //    parámetros en un mapa: un filtro nulo no agrega ni condición ni parámetro
        StringBuilder sql = new StringBuilder("SELECT a.id, a.anio, a.numero, a.fecha, a.concepto, a.estado,"
                + " a.origen_tipo, a.total_debe, a.total_haber FROM asiento a WHERE a.empresa_id = :empresa");
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("empresa", ContextoEmpresa.empresaRequerida().valor());
        filtrar(sql, parametros, " AND a.fecha >= :desde", "desde", f.desde());
        filtrar(sql, parametros, " AND a.fecha <= :hasta", "hasta", f.hasta());
        filtrar(sql, parametros, " AND a.anio = :anio", "anio", f.anio());
        filtrar(sql, parametros, " AND a.numero = :numero", "numero", f.numero());
        filtrar(
                sql,
                parametros,
                " AND a.origen_tipo = :origen",
                "origen",
                f.origen() == null ? null : f.origen().name());
        filtrar(
                sql,
                parametros,
                " AND a.estado = :estado",
                "estado",
                f.estado() == null ? null : f.estado().name());
        // Asientos con alguna línea en la cuenta, sin duplicar filas
        filtrar(
                sql,
                parametros,
                " AND EXISTS (SELECT 1 FROM asiento_linea l WHERE l.empresa_id = a.empresa_id"
                        + " AND l.asiento_id = a.id AND l.cuenta_id = :cuenta)",
                "cuenta",
                f.cuentaId());
        if (despuesDeAnio != null) {
            // Paginación por llave: solo lo posterior al último asiento de la página anterior
            sql.append(" AND (a.anio, a.numero) > (:llaveAnio, :llaveNumero)");
            parametros.put("llaveAnio", despuesDeAnio);
            parametros.put("llaveNumero", despuesDeNumero);
        }
        sql.append(" ORDER BY a.anio, a.numero LIMIT :cantidad");
        parametros.put("cantidad", cantidad);

        // 2. Ejecuta con todos los parámetros presentes
        return jdbc.sql(sql.toString())
                .params(parametros)
                .query((rs, n) -> new ResumenAsiento(
                        rs.getObject("id", UUID.class),
                        rs.getInt("anio"),
                        rs.getLong("numero"),
                        rs.getObject("fecha", LocalDate.class),
                        rs.getString("concepto"),
                        EstadoAsiento.valueOf(rs.getString("estado")),
                        OrigenAsiento.valueOf(rs.getString("origen_tipo")),
                        new Dinero(rs.getBigDecimal("total_debe")),
                        new Dinero(rs.getBigDecimal("total_haber"))))
                .list();
    }

    /** Agrega la condición y su parámetro solo si el filtro trae valor. */
    private static void filtrar(
            StringBuilder sql, Map<String, Object> parametros, String condicion, String nombre, Object valor) {
        if (valor != null) {
            sql.append(condicion);
            parametros.put(nombre, valor);
        }
    }
}
