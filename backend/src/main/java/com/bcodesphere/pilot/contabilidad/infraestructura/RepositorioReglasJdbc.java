package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.contabilidad.aplicacion.RepositorioReglas;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.reglas.CategoriaRegla;
import com.bcodesphere.pilot.contabilidad.dominio.reglas.ReglaContabilizacion;
import com.bcodesphere.pilot.contabilidad.dominio.reglas.TipoOperacionContable;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioReglas} sobre {@code regla_contabilizacion} (V11). RLS forzado: solo funciona
 * dentro de una transacción con el contexto de la empresa.
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): además de RLS, toda sentencia filtra explícitamente por
 * {@code empresa_id} de la empresa activa, y los JOIN igualan también la empresa.
 */
@Repository
class RepositorioReglasJdbc implements RepositorioReglas {

    /** Regla con el resumen de su cuenta (LEFT JOIN: una regla inactiva puede no tener cuenta). */
    private static final String SELECCION = "SELECT r.id, r.tipo_operacion, r.categoria, r.codigo, r.activa, r.version,"
            + " c.id AS c_id, c.codigo AS c_codigo, c.nombre AS c_nombre"
            + " FROM regla_contabilizacion r LEFT JOIN cuenta_contable c ON c.empresa_id = r.empresa_id AND c.id = r.cuenta_id";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioReglasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<ReglaContabilizacion> listar(TipoOperacionContable tipoOperacion) {
        // Sin filtro devuelve todas; el orden es estable para la pantalla de reglas
        String filtro =
                " WHERE r.empresa_id = :empresa" + (tipoOperacion == null ? "" : " AND r.tipo_operacion = :tipo");
        JdbcClient.StatementSpec consulta =
                jdbc.sql(SELECCION + filtro + " ORDER BY r.tipo_operacion, r.categoria, r.codigo");
        consulta = consulta.param("empresa", empresa());
        if (tipoOperacion != null) {
            consulta = consulta.param("tipo", tipoOperacion.name());
        }
        return consulta.query((rs, n) -> aRegla(rs)).list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<ReglaContabilizacion> buscar(UUID id) {
        return jdbc.sql(SELECCION + " WHERE r.id = :id AND r.empresa_id = :empresa")
                .param("id", id)
                .param("empresa", empresa())
                .query((rs, n) -> aRegla(rs))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean actualizar(UUID id, UUID cuentaId, boolean activa, long versionEsperada) {
        // WHERE version = :v es el control optimista; solo se escriben las columnas concedidas a pilot_app (V11)
        return jdbc.sql("UPDATE regla_contabilizacion SET cuenta_id = :cuenta, activa = :activa,"
                                + " version = version + 1, actualizado_en = now(), actualizado_por = :por"
                                + " WHERE id = :id AND empresa_id = :empresa AND version = :version")
                        .param("cuenta", cuentaId)
                        .param("activa", activa)
                        .param("por", ContextoEmpresa.usuarioOSistema())
                        .param("id", id)
                        .param("empresa", empresa())
                        .param("version", versionEsperada)
                        .update()
                == 1;
    }

    /** Empresa activa: la que filtra cada sentencia (defensa en profundidad sobre RLS). */
    private static UUID empresa() {
        return ContextoEmpresa.empresaRequerida().valor();
    }

    /** Convierte una fila en {@link ReglaContabilizacion}; la cuenta es nula si la regla no tiene. */
    private static ReglaContabilizacion aRegla(ResultSet rs) throws SQLException {
        UUID cuentaId = rs.getObject("c_id", UUID.class);
        ResumenCuenta cuenta = cuentaId == null
                ? null
                : new ResumenCuenta(cuentaId, rs.getString("c_codigo"), rs.getString("c_nombre"));
        return new ReglaContabilizacion(
                rs.getObject("id", UUID.class),
                TipoOperacionContable.valueOf(rs.getString("tipo_operacion")),
                CategoriaRegla.valueOf(rs.getString("categoria")),
                rs.getString("codigo"),
                cuenta,
                rs.getBoolean("activa"),
                rs.getLong("version"));
    }
}
