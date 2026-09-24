package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import com.bcodesphere.pilot.plataforma.aplicacion.AlmacenIdempotencia;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador JDBC de {@link AlmacenIdempotencia} sobre la tabla {@code idempotencia} (CLAUDE.md 9.2).
 * Usa la conexión de la transacción del llamador, así que ve el {@code app.empresa_id} que fijó el gestor de
 * transacciones; además filtra por {@code empresa_id} de forma explícita (CLAUDE.md 1.1.3).
 */
@Repository
class AlmacenIdempotenciaJdbc implements AlmacenIdempotencia {

    /** Texto con que PostgreSQL devuelve un JSON {@code null}; se traduce a "sin cuerpo". */
    private static final String JSON_NULO = "null";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    AlmacenIdempotenciaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bloquear(EmpresaId empresaId, String clave) {
        // 1. Bloqueo consultivo de la transacción, liberado solo al confirmar o revertir. La colisión de hash entre
        //    dos claves distintas solo causa una espera innecesaria, nunca un error.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:llave, 0))")
                .param("llave", empresaId + "/" + clave)
                .query((rs, fila) -> fila)
                .list();
    }

    @Override
    public Optional<RegistroGuardado> buscar(EmpresaId empresaId, String clave) {
        return jdbc.sql("SELECT hash_solicitud, estado_http, CAST(respuesta AS text) AS respuesta"
                        + " FROM idempotencia WHERE empresa_id = :empresa AND clave = :clave")
                .param("empresa", empresaId.valor())
                .param("clave", clave)
                .query((rs, fila) -> {
                    String json = rs.getString("respuesta");
                    return new RegistroGuardado(
                            rs.getString("hash_solicitud"),
                            new RespuestaIdempotente(
                                    rs.getInt("estado_http"), JSON_NULO.equals(json) ? null : json, true));
                })
                .optional();
    }

    @Override
    public Optional<RespuestaIdempotente> guardar(
            EmpresaId empresaId, String clave, String hashSolicitud, RespuestaIdempotente respuesta) {
        // ON CONFLICT DO NOTHING: un choque de clave primaria no aborta la transacción y no devuelve fila.
        // RETURNING devuelve el JSONB ya normalizado por PostgreSQL, igual a lo que verán las repeticiones.
        return jdbc.sql("INSERT INTO idempotencia (empresa_id, clave, hash_solicitud, estado_http, respuesta)"
                        + " VALUES (:empresa, :clave, :hash, :estado, CAST(:respuesta AS jsonb))"
                        + " ON CONFLICT (empresa_id, clave) DO NOTHING RETURNING CAST(respuesta AS text)")
                .param("empresa", empresaId.valor())
                .param("clave", clave)
                .param("hash", hashSolicitud)
                .param("estado", respuesta.estadoHttp())
                .param("respuesta", respuesta.cuerpoJson() == null ? JSON_NULO : respuesta.cuerpoJson())
                .query(String.class)
                .optional()
                .map(json ->
                        new RespuestaIdempotente(respuesta.estadoHttp(), JSON_NULO.equals(json) ? null : json, false));
    }
}
