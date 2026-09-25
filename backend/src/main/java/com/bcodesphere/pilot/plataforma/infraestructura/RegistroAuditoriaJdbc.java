package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador JDBC de {@link RegistroAuditoria}: inserta en {@code auditoria} (solo inserción, CLAUDE.md 9.2) dentro
 * de la transacción del llamador, que es obligatoria (MANDATORY): la auditoría se confirma o revierte con la mutación.
 * Empresa y usuario salen del {@link ContextoEmpresa}; el {@code traceId}, del MDC.
 *
 * <p>Destino global (ADR-025): {@link #registrarGlobal} inserta en {@code auditoria_global}, sin empresa. Ese INSERT no
 * lleva {@code RETURNING} porque {@code pilot_app} solo tiene {@code INSERT} sobre la tabla (V4).
 */
@Repository
class RegistroAuditoriaJdbc implements RegistroAuditoria {

    private final JdbcClient jdbc;
    private final JsonMapper mapeador;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     * @param mapeador serializador JSON de la aplicación (montos como cadena decimal, ADR-013)
     */
    RegistroAuditoriaJdbc(JdbcClient jdbc, JsonMapper mapeador) {
        this.jdbc = jdbc;
        this.mapeador = mapeador;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String entidad, String entidadId, String accion, Object valorAnterior, Object valorNuevo) {
        // 1. Empresa y usuario del contexto; sin empresa falla (no se audita sin saber de quién es el dato)
        EmpresaId empresaId = ContextoEmpresa.empresaRequerida();
        String usuarioId = ContextoEmpresa.usuarioOSistema();
        // 2. Inserta con id nuevo (UUID v7), los valores como JSON y el traceId de la petición
        insertar(
                empresaId.valor(),
                entidad,
                entidadId,
                accion,
                usuarioId,
                aJson(valorAnterior),
                aJson(valorNuevo),
                MDC.get(ClavesMdc.TRACE_ID));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarGlobal(
            String entidad, String entidadId, String accion, Object valorAnterior, Object valorNuevo) {
        // 1. Sin empresa: solo el usuario del contexto (o "sistema") y el traceId; nunca RETURNING (solo INSERT)
        jdbc.sql("INSERT INTO auditoria_global (id, entidad, entidad_id, accion, usuario_id,"
                        + " valor_anterior, valor_nuevo, trace_id)"
                        + " VALUES (:id, :entidad, :entidadId, :accion, :usuario,"
                        + " CAST(:anterior AS jsonb), CAST(:nuevo AS jsonb), :trace)")
                .param("id", GeneradorId.nuevo())
                .param("entidad", entidad)
                .param("entidadId", entidadId)
                .param("accion", accion)
                .param("usuario", ContextoEmpresa.usuarioOSistema())
                .param("anterior", aJson(valorAnterior))
                .param("nuevo", aJson(valorNuevo))
                .param("trace", MDC.get(ClavesMdc.TRACE_ID))
                .update();
    }

    /** Ejecuta el INSERT en {@code auditoria} (entidades con empresa). */
    private void insertar(
            UUID empresaId,
            String entidad,
            String entidadId,
            String accion,
            String usuarioId,
            String anteriorJson,
            String nuevoJson,
            String traceId) {
        jdbc.sql("INSERT INTO auditoria (id, empresa_id, entidad, entidad_id, accion, usuario_id,"
                        + " valor_anterior, valor_nuevo, trace_id)"
                        + " VALUES (:id, :empresa, :entidad, :entidadId, :accion, :usuario,"
                        + " CAST(:anterior AS jsonb), CAST(:nuevo AS jsonb), :trace)")
                .param("id", GeneradorId.nuevo())
                .param("empresa", empresaId)
                .param("entidad", entidad)
                .param("entidadId", entidadId)
                .param("accion", accion)
                .param("usuario", usuarioId)
                .param("anterior", anteriorJson)
                .param("nuevo", nuevoJson)
                .param("trace", traceId)
                .update();
    }

    /** Serializa un valor a JSON; nulo se guarda como NULL de SQL. */
    private String aJson(Object valor) {
        return valor == null ? null : mapeador.writeValueAsString(valor);
    }
}
