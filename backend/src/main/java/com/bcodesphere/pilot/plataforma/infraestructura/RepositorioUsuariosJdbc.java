package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.aplicacion.RepositorioUsuarios;
import com.bcodesphere.pilot.plataforma.dominio.DatosIdentidad;
import com.bcodesphere.pilot.plataforma.dominio.TelefonoSalvadoreno;
import com.bcodesphere.pilot.plataforma.dominio.Usuario;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link RepositorioUsuarios} sobre la tabla global {@code usuario} (V4, sin RLS). Respeta los
 * permisos de {@code pilot_app}: {@code SELECT}, {@code INSERT} y {@code UPDATE} solo de las columnas de perfil y
 * consentimiento. Exige una transacción abierta por el llamador.
 */
@Repository
class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    /** Columnas que forman un {@link Usuario}. */
    private static final String COLUMNAS = "id, sub_keycloak, correo, nombre, telefono, estado,"
            + " recomendaciones_aceptadas_en, recomendaciones_retiradas_en";

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    RepositorioUsuariosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Usuario> buscarPorSub(String sub) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE sub_keycloak = :sub")
                .param("sub", sub)
                .query(RepositorioUsuariosJdbc::aUsuario)
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Usuario> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE id = :id")
                .param("id", id)
                .query(RepositorioUsuariosJdbc::aUsuario)
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void crear(UUID id, DatosIdentidad datos) {
        // El consentimiento inicial usa la hora de la base de datos (mismo reloj que las demás marcas de tiempo)
        jdbc.sql("INSERT INTO usuario (id, sub_keycloak, correo, nombre, telefono, recomendaciones_aceptadas_en)"
                        + " VALUES (:id, :sub, :correo, :nombre, :telefono, CASE WHEN :acepta THEN now() END)")
                .param("id", id)
                .param("sub", datos.sub())
                .param("correo", datos.correo())
                .param("nombre", datos.nombre())
                .param("telefono", datos.telefono().valor())
                .param("acepta", datos.recomendacionesAceptadas())
                .update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void actualizarPerfil(UUID id, DatosIdentidad datos) {
        // Solo columnas de perfil: pilot_app no puede tocar sub_keycloak, estado ni creado_en (V4)
        jdbc.sql("UPDATE usuario SET correo = :correo, nombre = :nombre, telefono = :telefono,"
                        + " actualizado_en = now() WHERE id = :id")
                .param("correo", datos.correo())
                .param("nombre", datos.nombre())
                .param("telefono", datos.telefono().valor())
                .param("id", id)
                .update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void fijarConsentimiento(UUID id, boolean aceptado) {
        // Dar el consentimiento fija aceptadas_en; retirarlo, retiradas_en. Vigente si aceptadas > retiradas
        String columna = aceptado ? "recomendaciones_aceptadas_en" : "recomendaciones_retiradas_en";
        jdbc.sql("UPDATE usuario SET " + columna + " = now(), actualizado_en = now() WHERE id = :id")
                .param("id", id)
                .update();
    }

    /** Convierte una fila de {@code usuario} en el objeto de dominio. */
    private static Usuario aUsuario(ResultSet rs, int fila) throws SQLException {
        return new Usuario(
                rs.getObject("id", UUID.class),
                rs.getString("sub_keycloak"),
                rs.getString("correo"),
                rs.getString("nombre"),
                new TelefonoSalvadoreno(rs.getString("telefono")),
                "BLOQUEADO".equals(rs.getString("estado")),
                instante(rs, "recomendaciones_aceptadas_en"),
                instante(rs, "recomendaciones_retiradas_en"));
    }

    /** Lee una columna TIMESTAMPTZ como instante; nulo si la columna es nula. */
    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
