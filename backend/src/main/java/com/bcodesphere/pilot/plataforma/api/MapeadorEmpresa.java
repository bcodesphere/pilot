package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.api.contrato.Empresa;
import com.bcodesphere.pilot.compartido.api.contrato.EstadoEmpresa;
import com.bcodesphere.pilot.compartido.api.contrato.TipoEmpresa;
import com.bcodesphere.pilot.plataforma.dominio.EspacioTrabajo;

/** Convierte el espacio de trabajo del dominio al DTO {@code Empresa} del contrato (sin lógica, CLAUDE.md 8.3). */
final class MapeadorEmpresa {

    private MapeadorEmpresa() {}

    /**
     * Convierte el espacio de trabajo a su DTO; no incluye nombre comercial, NIT ni NRC (ADR-032).
     *
     * @param e espacio de trabajo
     * @return DTO del contrato
     */
    static Empresa aDto(EspacioTrabajo e) {
        return new Empresa(
                e.id(), TipoEmpresa.fromValue(e.tipo()), e.nombre(), EstadoEmpresa.fromValue(e.estado()), e.version());
    }
}
