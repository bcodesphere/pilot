package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.aplicacion.RepositorioEmpresas;
import com.bcodesphere.pilot.plataforma.dominio.EspacioTrabajo;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioEmpresas} sobre {@code empresa} y {@code empresa_usuario} (V5). Ambas tablas
 * tienen RLS forzado, así que solo funciona dentro de una transacción con el contexto de la empresa que se inserta.
 */
@Repository
class RepositorioEmpresasJdbc implements RepositorioEmpresas {

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioEmpresasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void crearPersonal(UUID id, UUID propietarioId, String nombre) {
        // NIT y NRC quedan nulos: en la empresa personal son opcionales (ADR-029). Sin RETURNING.
        jdbc.sql("INSERT INTO empresa (id, tipo, propietario_id, nombre, actualizado_por)"
                        + " VALUES (:id, 'PERSONAL', :propietario, :nombre, :por)")
                .param("id", id)
                .param("propietario", propietarioId)
                .param("nombre", nombre)
                .param("por", ContextoEmpresa.usuarioOSistema())
                .update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void crearMembresia(UUID empresaId, UUID usuarioId, Rol rol) {
        jdbc.sql("INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado, actualizado_por)"
                        + " VALUES (:empresa, :usuario, :rol, 'ACTIVA', :por)")
                .param("empresa", empresaId)
                .param("usuario", usuarioId)
                .param("rol", rol.codigo())
                .param("por", ContextoEmpresa.usuarioOSistema())
                .update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<EspacioTrabajo> buscar(UUID id) {
        // Solo las columnas que expone la versión abierta: nunca nit, nrc ni nombre_comercial (ADR-032)
        return jdbc.sql("SELECT id, tipo, nombre, estado, version FROM empresa WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new EspacioTrabajo(
                        rs.getObject("id", UUID.class),
                        rs.getString("tipo"),
                        rs.getString("nombre"),
                        rs.getString("estado"),
                        rs.getLong("version")))
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean actualizarNombre(UUID id, String nombre, long versionEsperada) {
        // WHERE version = :v es el control optimista: si otra petición ganó la carrera no actualiza filas y el caso de
        // uso responde 412. Solo se escriben las columnas concedidas a pilot_app que usa esta operación (V5)
        return jdbc.sql("UPDATE empresa SET nombre = :nombre, version = version + 1, actualizado_en = now(),"
                                + " actualizado_por = :por WHERE id = :id AND version = :version")
                        .param("nombre", nombre)
                        .param("por", ContextoEmpresa.usuarioOSistema())
                        .param("id", id)
                        .param("version", versionEsperada)
                        .update()
                > 0;
    }
}
