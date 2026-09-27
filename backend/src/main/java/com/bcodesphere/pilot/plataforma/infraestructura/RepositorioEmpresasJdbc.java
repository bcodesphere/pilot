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
 *
 * <p>Defensa en profundidad (CLAUDE.md 1.1.3, ADR-002): en esta tabla el tenant ES la fila (la política
 * {@code aislamiento_empresa} de V5 filtra por {@code id = app.empresa_id}), así que un {@code AND id = :empresa}
 * en el SQL sería literalmente la misma expresión que ya evalúa RLS y no añadiría nada. La defensa real de
 * {@link #buscar(UUID)} y {@link #actualizarNombre(UUID, String, long)} es no confiar en que el {@code id} recibido
 * ya fue validado como la empresa activa (lo valida {@code GestionarEspacioTrabajo.exigirEmpresaActiva}, fuera de
 * este adaptador): ambos métodos comprueban aquí, otra vez, que el {@code id} recibido coincide con
 * {@link ContextoEmpresa#empresaRequerida()} antes de tocar la base, y fallan cerrado si no coincide.
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
        // Defensa en profundidad (1.1.3): el id recibido debe ser la empresa activa del contexto; no se confía en que
        // el llamador ya lo validó (exigirEmpresaActiva). Falla cerrado, no lee la fila, si no coincide.
        UUID activa = exigirEmpresaActivaDelContexto(id);
        // Solo las columnas que expone la versión abierta: nunca nit, nrc ni nombre_comercial (ADR-032)
        return jdbc.sql("SELECT id, tipo, nombre, estado, version FROM empresa WHERE id = :id")
                .param("id", activa)
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
        // Defensa en profundidad (1.1.3): mismo resguardo que en buscar(UUID)
        UUID activa = exigirEmpresaActivaDelContexto(id);
        // WHERE version = :v es el control optimista: si otra petición ganó la carrera no actualiza filas y el caso de
        // uso responde 412. Solo se escriben las columnas concedidas a pilot_app que usa esta operación (V5)
        return jdbc.sql("UPDATE empresa SET nombre = :nombre, version = version + 1, actualizado_en = now(),"
                                + " actualizado_por = :por WHERE id = :id AND version = :version")
                        .param("nombre", nombre)
                        .param("por", ContextoEmpresa.usuarioOSistema())
                        .param("id", activa)
                        .param("version", versionEsperada)
                        .update()
                > 0;
    }

    /**
     * Defensa en profundidad (1.1.3): exige que {@code id} sea la empresa activa del contexto y la devuelve. En
     * {@code empresa} el id ES el tenant, así que esto no sustituye a RLS ni a la validación 404 PLT-017 del caso de
     * uso ({@code GestionarEspacioTrabajo.exigirEmpresaActiva}): es una segunda comprobación, independiente de esa,
     * para que este adaptador nunca consulte ni escriba una empresa distinta de la fijada en la transacción, incluso
     * si algún día un llamador nuevo olvidara validar la ruta.
     *
     * @param id empresa solicitada por el llamador
     * @return el mismo {@code id}, ya confirmado como la empresa activa
     * @throws IllegalStateException si {@code id} no es la empresa activa del contexto (error de programación, no de
     *     un usuario: el 404 PLT-017 de una empresa ajena ya lo produjo el caso de uso antes de llegar aquí)
     */
    private static UUID exigirEmpresaActivaDelContexto(UUID id) {
        UUID activa = ContextoEmpresa.empresaRequerida().valor();
        if (!activa.equals(id)) {
            throw new IllegalStateException(
                    "RepositorioEmpresasJdbc invocado con una empresa distinta de la activa del contexto");
        }
        return activa;
    }
}
