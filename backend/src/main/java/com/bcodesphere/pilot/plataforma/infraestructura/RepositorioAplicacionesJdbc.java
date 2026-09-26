package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.aplicacion.RepositorioAplicaciones;
import com.bcodesphere.pilot.plataforma.dominio.AplicacionEmpresa;
import com.bcodesphere.pilot.plataforma.dominio.EdicionAplicacion;
import com.bcodesphere.pilot.plataforma.dominio.EstadoAplicacionEmpresa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioAplicaciones} sobre {@code aplicacion} (global) y {@code empresa_aplicacion}
 * (RLS, por eso exige el contexto de la empresa activa). Todo el SQL va parametrizado.
 */
@Repository
class RepositorioAplicacionesJdbc implements RepositorioAplicaciones {

    /** Catálogo con la instalación de la empresa en una sola consulta: LEFT JOIN, sin N+1. */
    private static final String SELECT_CATALOGO = "SELECT a.codigo, a.nombre, a.descripcion, a.edicion,"
            + " ea.instalada_en FROM aplicacion a"
            + " LEFT JOIN empresa_aplicacion ea ON ea.aplicacion_codigo = a.codigo AND ea.empresa_id = :empresa"
            + " WHERE a.disponible";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioAplicacionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<AplicacionEmpresa> listar(UUID empresaId) {
        return jdbc.sql(SELECT_CATALOGO + " ORDER BY a.orden")
                .param("empresa", empresaId)
                .query((rs, n) -> mapear(rs))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<AplicacionEmpresa> buscar(String codigo, UUID empresaId) {
        return jdbc.sql(SELECT_CATALOGO + " AND a.codigo = :codigo")
                .param("empresa", empresaId)
                .param("codigo", codigo)
                .query((rs, n) -> mapear(rs))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean instalar(UUID empresaId, String codigo) {
        // ON CONFLICT DO NOTHING hace la instalación idempotente y segura ante concurrencia: la segunda transacción
        // espera a la primera y, si esta confirma, no inserta (0 filas) en lugar de fallar con 500
        return jdbc.sql("INSERT INTO empresa_aplicacion (empresa_id, aplicacion_codigo, instalada_por)"
                                + " VALUES (:empresa, :codigo, :por) ON CONFLICT DO NOTHING")
                        .param("empresa", empresaId)
                        .param("codigo", codigo)
                        .param("por", ContextoEmpresa.usuarioOSistema())
                        .update()
                > 0;
    }

    /** Convierte una fila del catálogo; el estado lo calcula la regla de dominio. */
    private static AplicacionEmpresa mapear(ResultSet rs) throws SQLException {
        EdicionAplicacion edicion =
                EdicionAplicacion.deCodigo(rs.getString("edicion")).orElseThrow();
        Timestamp instalada = rs.getTimestamp("instalada_en");
        return new AplicacionEmpresa(
                rs.getString("codigo"),
                rs.getString("nombre"),
                rs.getString("descripcion"),
                edicion,
                EstadoAplicacionEmpresa.calcular(edicion, instalada != null),
                instalada == null ? null : instalada.toInstant());
    }
}
