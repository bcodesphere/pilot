package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.aplicacion.RepositorioAsientos;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaAsiento;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioAsientos} sobre {@code correlativo_asiento}, {@code asiento},
 * {@code asiento_linea} y {@code saldo_cuenta_mensual} (V13 y V14). Las tablas tienen RLS forzado, así que solo
 * funciona dentro de una transacción con el contexto de la empresa.
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): además de RLS, toda sentencia filtra o fija explícitamente el
 * {@code empresa_id} de la empresa activa. {@code pilot_app} solo tiene {@code SELECT, INSERT} sobre las líneas y un
 * {@code UPDATE} acotado sobre la cabecera (ADR-019).
 */
@Repository
class RepositorioAsientosJdbc implements RepositorioAsientos {

    /** Constraint que impide dos reversiones del mismo asiento (V13); su violación es {@code CON-008}. */
    private static final String INDICE_UNA_REVERSION = "uq_asiento_revertido_una_vez";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioAsientosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Empresa activa del contexto. */
    private static UUID empresa() {
        return ContextoEmpresa.empresaRequerida().valor();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long siguienteNumero(int anio) {
        // Upsert atómico: la primera vez inserta 1; después incrementa. El bloqueo de la fila dura hasta el COMMIT,
        // así que las transacciones concurrentes numeran en fila y el número de una que falla no se pierde
        return jdbc.sql("INSERT INTO correlativo_asiento (empresa_id, anio, ultimo) VALUES (:empresa, :anio, 1)"
                        + " ON CONFLICT (empresa_id, anio)"
                        + " DO UPDATE SET ultimo = correlativo_asiento.ultimo + 1 RETURNING ultimo")
                .param("empresa", empresa())
                .param("anio", anio)
                .query(Long.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void guardar(Asiento a) {
        try {
            // 1. Cabecera
            jdbc.sql("INSERT INTO asiento (id, empresa_id, anio, numero, fecha, concepto, estado, origen_tipo,"
                            + " origen_id, modo_precio, asiento_revertido_id, asiento_reversion_id,"
                            + " total_debe, total_haber, creado_en, creado_por)"
                            + " VALUES (:id, :empresa, :anio, :numero, :fecha, :concepto, :estado, :origen,"
                            + " :origenId, :modo, :revertido, :reversion, :debe, :haber, :creadoEn, :por)")
                    .param("id", a.id())
                    .param("empresa", empresa())
                    .param("anio", a.anio())
                    .param("numero", a.numero())
                    .param("fecha", a.fecha())
                    .param("concepto", a.concepto())
                    .param("estado", a.estado().name())
                    .param("origen", a.origenTipo().name())
                    .param("origenId", a.origenId())
                    .param(
                            "modo",
                            a.modoPrecio() == null ? null : a.modoPrecio().name())
                    .param("revertido", a.asientoRevertidoId())
                    .param("reversion", a.asientoReversionId())
                    .param("debe", a.totalDebe().valor())
                    .param("haber", a.totalHaber().valor())
                    .param("creadoEn", java.time.OffsetDateTime.ofInstant(a.creadoEn(), java.time.ZoneOffset.UTC))
                    .param("por", ContextoEmpresa.usuarioOSistema())
                    .update();
        } catch (DuplicateKeyException e) {
            // Carrera con otra reversión del mismo asiento: el índice único es la última defensa (CON-008)
            if (e.getMessage() != null && e.getMessage().contains(INDICE_UNA_REVERSION)) {
                throw ExcepcionContabilidad.yaRevertido();
            }
            throw e;
        }
        // 2. Líneas en orden: la base de una línea de IVA se inserta antes que ella (llave foránea inmediata)
        for (LineaAsiento l : a.lineas()) {
            jdbc.sql("INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea, fecha, cuenta_id,"
                            + " descripcion, debe, haber, origen_linea, linea_base_id)"
                            + " VALUES (:id, :empresa, :asiento, :numero, :fecha, :cuenta,"
                            + " :descripcion, :debe, :haber, :origen, :base)")
                    .param("id", l.id())
                    .param("empresa", empresa())
                    .param("asiento", a.id())
                    .param("numero", l.numeroLinea())
                    .param("fecha", a.fecha())
                    .param("cuenta", l.cuenta().id())
                    .param("descripcion", l.descripcion())
                    .param("debe", l.debe().valor())
                    .param("haber", l.haber().valor())
                    .param("origen", l.origenLinea().name())
                    .param("base", l.lineaBaseId())
                    .update();
        }
    }

    /** Llave de un acumulado: cuenta, año y mes; el orden natural (por cuenta) fija el orden de bloqueo. */
    private record LlaveSaldo(UUID cuenta, int anio, int mes) implements Comparable<LlaveSaldo> {
        @Override
        public int compareTo(LlaveSaldo otra) {
            int porCuenta = cuenta.compareTo(otra.cuenta);
            if (porCuenta != 0) {
                return porCuenta;
            }
            return anio != otra.anio ? Integer.compare(anio, otra.anio) : Integer.compare(mes, otra.mes);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void mayorizar(Asiento a) {
        // 1. Agrupa por (cuenta, año, mes) en un mapa ordenado por cuenta: todas las transacciones toman los bloqueos
        //    de las filas de saldo en el mismo orden y no se interbloquean (CLAUDE.md 10.3)
        Map<LlaveSaldo, Dinero[]> acumulados = new TreeMap<>();
        for (LineaAsiento l : a.lineas()) {
            Dinero[] par = acumulados.computeIfAbsent(
                    new LlaveSaldo(
                            l.cuenta().id(), a.fecha().getYear(), a.fecha().getMonthValue()),
                    k -> new Dinero[] {Dinero.CERO, Dinero.CERO});
            par[0] = par[0].sumar(l.debe());
            par[1] = par[1].sumar(l.haber());
        }
        // 2. Un upsert por grupo: el incremento es atómico aunque otra transacción mayorice la misma cuenta
        acumulados.forEach((llave, par) -> jdbc.sql(
                        "INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes, total_debe, total_haber)"
                                + " VALUES (:empresa, :cuenta, :anio, :mes, :debe, :haber)"
                                + " ON CONFLICT (empresa_id, cuenta_id, anio, mes)"
                                + " DO UPDATE SET total_debe = saldo_cuenta_mensual.total_debe + EXCLUDED.total_debe,"
                                + " total_haber = saldo_cuenta_mensual.total_haber + EXCLUDED.total_haber,"
                                + " actualizado_en = now()")
                .param("empresa", empresa())
                .param("cuenta", llave.cuenta())
                .param("anio", llave.anio())
                .param("mes", llave.mes())
                .param("debe", par[0].valor())
                .param("haber", par[1].valor())
                .update());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Asiento> buscarBloqueando(UUID id) {
        // Bloquea la cabecera de esta empresa hasta el fin de la transacción; después lee las líneas como cualquier
        // consulta
        return jdbc.sql(ConsultaAsientosJdbc.SELECT_CABECERA
                        + " WHERE a.id = :id AND a.empresa_id = :empresa FOR UPDATE")
                .param("id", id)
                .param("empresa", empresa())
                .query((rs, n) -> ConsultaAsientosJdbc.aCabecera(rs))
                .optional()
                .map(c -> c.conLineas(ConsultaAsientosJdbc.leerLineas(jdbc, id)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void marcarRevertido(UUID id, UUID reversionId) {
        // El trigger de transiciones exige CONTABILIZADO -> REVERTIDO con la reversión enlazada y version + 1 (ADR-019)
        int filas = jdbc.sql(
                        "UPDATE asiento SET estado = 'REVERTIDO', asiento_reversion_id = :reversion,"
                                + " version = version + 1 WHERE id = :id AND empresa_id = :empresa AND estado = 'CONTABILIZADO'")
                .param("reversion", reversionId)
                .param("id", id)
                .param("empresa", empresa())
                .update();
        if (filas != 1) {
            // Con la cabecera bloqueada esto no debería ocurrir; si ocurre, es que ya estaba revertido
            throw ExcepcionContabilidad.yaRevertido();
        }
    }
}
