package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.AplicacionCatalogo;
import com.bcodesphere.pilot.compartido.api.contrato.EdicionAplicacion;
import com.bcodesphere.pilot.compartido.api.contrato.EstadoAplicacion;
import com.bcodesphere.pilot.plataforma.dominio.AplicacionEmpresa;
import java.time.ZoneOffset;

/** Convierte las apps del dominio al DTO del contrato (sin lógica de negocio, CLAUDE.md 8.3). */
final class MapeadorAplicaciones {

    private MapeadorAplicaciones() {}

    /**
     * Convierte una app del catálogo a su DTO.
     *
     * @param app app con su estado para la empresa activa
     * @return DTO {@code AplicacionCatalogo}; {@code instaladaEn} nulo si no está instalada
     */
    static AplicacionCatalogo aDto(AplicacionEmpresa app) {
        return new AplicacionCatalogo(
                app.codigo(),
                app.nombre(),
                app.descripcion(),
                EdicionAplicacion.fromValue(app.edicion().name()),
                EstadoAplicacion.fromValue(app.estado().name()),
                app.instaladaEn() == null ? null : app.instaladaEn().atOffset(ZoneOffset.UTC));
    }
}
