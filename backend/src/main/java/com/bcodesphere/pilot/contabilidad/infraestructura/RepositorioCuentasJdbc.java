package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.contabilidad.aplicacion.RepositorioCuentas;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.CodigoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioCuentas} sobre {@code cuenta_contable} (V11). La tabla tiene RLS forzado, así
 * que solo funciona dentro de una transacción con el contexto de la empresa; el filtro por empresa lo aplica la base
 * de datos y, además, se fija en cada inserción.
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): además de RLS, toda sentencia filtra explícitamente por
 * {@code empresa_id} de la empresa activa, y los JOIN igualan también la empresa.
 */
@Repository
class RepositorioCuentasJdbc implements RepositorioCuentas {

    /** Columnas que forman una {@link Cuenta}. */
    private static final String COLUMNAS =
            "id, codigo, nombre, cuenta_padre_id, naturaleza, acepta_movimientos, activa, version";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioCuentasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Cuenta> listar(String busqueda, boolean soloDetalle, boolean soloActivas) {
        // 1. Filtros opcionales acumulados como condiciones fijas (nunca se concatena texto del cliente)
        StringBuilder sql =
                new StringBuilder("SELECT " + COLUMNAS + " FROM cuenta_contable WHERE empresa_id = :empresa");
        boolean conBusqueda = busqueda != null && !busqueda.isBlank();
        if (conBusqueda) {
            // Prefijo del código o parte del nombre, sin distinguir mayúsculas; % y _ del cliente se escapan
            sql.append(" AND (codigo LIKE :prefijo ESCAPE '\\' OR lower(nombre) LIKE :parte ESCAPE '\\')");
        }
        if (soloDetalle) {
            sql.append(" AND acepta_movimientos");
        }
        if (soloActivas) {
            sql.append(" AND activa");
        }
        sql.append(" ORDER BY codigo");

        // 2. Ejecuta con los parámetros de búsqueda solo si el filtro está presente
        JdbcClient.StatementSpec consulta = jdbc.sql(sql.toString()).param("empresa", empresa());
        if (conBusqueda) {
            String texto = escaparLike(busqueda.strip().toLowerCase(java.util.Locale.ROOT));
            consulta = consulta.param("prefijo", texto + "%").param("parte", "%" + texto + "%");
        }
        return consulta.query((rs, n) -> aCuenta(rs)).list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Cuenta> buscar(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM cuenta_contable WHERE id = :id AND empresa_id = :empresa")
                .param("id", id)
                .param("empresa", empresa())
                .query((rs, n) -> aCuenta(rs))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Cuenta> buscarPorCodigo(String codigo) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM cuenta_contable WHERE codigo = :codigo AND empresa_id = :empresa")
                .param("codigo", codigo)
                .param("empresa", empresa())
                .query((rs, n) -> aCuenta(rs))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean tieneHijas(UUID id) {
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM cuenta_contable WHERE cuenta_padre_id = :id AND empresa_id = :empresa)")
                .param("id", id)
                .param("empresa", empresa())
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean enUsoPorConfiguracionOReglaActiva(UUID id) {
        // Configuración (IVA débito o crédito) o regla ACTIVA; una regla inactiva no bloquea (CON-016)
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM configuracion_contable"
                                + " WHERE empresa_id = :empresa AND (cuenta_iva_debito_id = :id OR cuenta_iva_credito_id = :id))"
                                + " OR EXISTS (SELECT 1 FROM regla_contabilizacion WHERE empresa_id = :empresa AND cuenta_id = :id AND activa)")
                .param("id", id)
                .param("empresa", empresa())
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void crear(Cuenta cuenta) {
        try {
            jdbc.sql("INSERT INTO cuenta_contable (id, empresa_id, codigo, nombre, nivel, cuenta_padre_id, naturaleza,"
                            + " acepta_movimientos, activa, creado_por, actualizado_por)"
                            + " VALUES (:id, :empresa, :codigo, :nombre, :nivel, :padre, :naturaleza,"
                            + " :acepta, :activa, :por, :por)")
                    .param("id", cuenta.id())
                    .param("empresa", empresa())
                    .param("codigo", cuenta.codigo().valor())
                    .param("nombre", cuenta.nombre())
                    .param("nivel", cuenta.codigo().nivel())
                    .param("padre", cuenta.padreId())
                    .param("naturaleza", cuenta.naturaleza().name())
                    .param("acepta", cuenta.aceptaMovimientos())
                    .param("activa", cuenta.activa())
                    .param("por", ContextoEmpresa.usuarioOSistema())
                    .update();
        } catch (DuplicateKeyException e) {
            // Carrera con otra alta del mismo código: el índice único (empresa_id, codigo) es la última defensa
            throw ExcepcionContabilidad.codigoDuplicado();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean actualizar(Cuenta cuenta, long versionEsperada) {
        try {
            // WHERE version = :v es el control optimista: 0 filas = otra petición ganó la carrera (412)
            return jdbc.sql("UPDATE cuenta_contable SET codigo = :codigo, nombre = :nombre, nivel = :nivel,"
                                    + " naturaleza = :naturaleza, activa = :activa, version = version + 1,"
                                    + " actualizado_en = now(), actualizado_por = :por"
                                    + " WHERE id = :id AND empresa_id = :empresa AND version = :version")
                            .param("codigo", cuenta.codigo().valor())
                            .param("nombre", cuenta.nombre())
                            .param("nivel", cuenta.codigo().nivel())
                            .param("naturaleza", cuenta.naturaleza().name())
                            .param("activa", cuenta.activa())
                            .param("por", ContextoEmpresa.usuarioOSistema())
                            .param("id", cuenta.id())
                            .param("empresa", empresa())
                            .param("version", versionEsperada)
                            .update()
                    == 1;
        } catch (DuplicateKeyException e) {
            throw ExcepcionContabilidad.codigoDuplicado();
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void dejarDeAceptarMovimientos(UUID id) {
        jdbc.sql("UPDATE cuenta_contable SET acepta_movimientos = false, version = version + 1,"
                        + " actualizado_en = now(), actualizado_por = :por WHERE id = :id AND empresa_id = :empresa")
                .param("por", ContextoEmpresa.usuarioOSistema())
                .param("id", id)
                .param("empresa", empresa())
                .update();
    }

    /** Empresa activa: la que filtra cada sentencia (defensa en profundidad sobre RLS). */
    private static UUID empresa() {
        return ContextoEmpresa.empresaRequerida().valor();
    }

    /** Convierte una fila en {@link Cuenta}; el código viene de la base de datos y ya es válido. */
    private static Cuenta aCuenta(ResultSet rs) throws SQLException {
        return new Cuenta(
                rs.getObject("id", UUID.class),
                CodigoCuenta.de(rs.getString("codigo")),
                rs.getString("nombre"),
                rs.getObject("cuenta_padre_id", UUID.class),
                NaturalezaCuenta.valueOf(rs.getString("naturaleza")),
                rs.getBoolean("acepta_movimientos"),
                rs.getBoolean("activa"),
                rs.getLong("version"));
    }

    /** Escapa los comodines de LIKE para que el texto del cliente se busque literalmente. */
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
