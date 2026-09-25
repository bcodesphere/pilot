package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.aplicacion.RepositorioEmpresas;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
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
}
