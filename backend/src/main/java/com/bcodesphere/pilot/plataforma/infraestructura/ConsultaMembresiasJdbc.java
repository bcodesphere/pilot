package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.aplicacion.ConsultaMembresias;
import com.bcodesphere.pilot.plataforma.dominio.MembresiaUsuario;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link ConsultaMembresias}: llama a las funciones {@code SECURITY DEFINER} de V8
 * ({@code membresias_de_usuario} y {@code membresia_activa}), propiedad de {@code pilot_busqueda} (ADR-026). Nunca
 * consulta {@code empresa} ni {@code empresa_usuario} directamente: en modo sin empresa esas tablas fallan por RLS.
 */
@Repository
class ConsultaMembresiasJdbc implements ConsultaMembresias {

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    ConsultaMembresiasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MembresiaUsuario> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT empresa_id, nombre_empresa, tipo_empresa, rol FROM membresias_de_usuario(:usuario)")
                .param("usuario", usuarioId)
                .query((rs, fila) -> new MembresiaUsuario(
                        rs.getObject("empresa_id", UUID.class),
                        rs.getString("nombre_empresa"),
                        rs.getString("tipo_empresa"),
                        // La función solo devuelve roles válidos (restricción ck_empresa_usuario_rol)
                        Rol.deCodigo(rs.getString("rol"))
                                .orElseThrow(() -> new IllegalStateException("Rol de membresía desconocido"))))
                .list();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Rol> rolActivo(UUID usuarioId, UUID empresaId) {
        // La función devuelve NULL si la membresía o la empresa no están activas
        return jdbc.sql("SELECT membresia_activa(:usuario, :empresa)")
                .param("usuario", usuarioId)
                .param("empresa", empresaId)
                .query(String.class)
                .optional()
                .flatMap(Rol::deCodigo);
    }
}
