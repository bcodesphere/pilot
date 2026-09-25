package com.bcodesphere.pilot.plataforma.infraestructura;

import com.bcodesphere.pilot.plataforma.aplicacion.ConsultaAplicaciones;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link ConsultaAplicaciones} sobre {@code aplicacion} (global) y {@code empresa_aplicacion}
 * (RLS, por eso exige el contexto de la empresa activa).
 */
@Repository
class ConsultaAplicacionesJdbc implements ConsultaAplicaciones {

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    ConsultaAplicacionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Boolean> estaInstalada(String codigo, UUID empresaId) {
        // Una sola consulta: si el código no está en el catálogo no hay fila (no es una app); si está, dice si se
        // instaló
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM empresa_aplicacion ea"
                        + " WHERE ea.aplicacion_codigo = a.codigo AND ea.empresa_id = :empresa)"
                        + " FROM aplicacion a WHERE a.codigo = :codigo")
                .param("empresa", empresaId)
                .param("codigo", codigo)
                .query(Boolean.class)
                .optional();
    }
}
