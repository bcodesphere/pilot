package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.VersionEtag;
import com.bcodesphere.pilot.compartido.api.contrato.ActualizacionReglaContabilizacion;
import com.bcodesphere.pilot.compartido.api.contrato.ReglaContabilizacion;
import com.bcodesphere.pilot.compartido.api.contrato.ReglasContabilizacionApi;
import com.bcodesphere.pilot.compartido.api.contrato.TipoOperacion;
import com.bcodesphere.pilot.contabilidad.aplicacion.GestionarReglas;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de {@code /contabilidad/reglas-contabilizacion}. No lleva lógica: llama al caso de uso y mapea; la
 * versión de la regla viaja como {@code ETag} en la respuesta de edición.
 */
@RestController
class ControladorReglasContabilizacion implements ReglasContabilizacionApi {

    private final GestionarReglas reglas;

    /**
     * Crea el controlador.
     *
     * @param reglas caso de uso de las reglas de contabilización
     */
    ControladorReglasContabilizacion(GestionarReglas reglas) {
        this.reglas = reglas;
    }

    @Override
    public ResponseEntity<List<ReglaContabilizacion>> listarReglasContabilizacion(
            UUID xEmpresaId, String xRequestId, TipoOperacion tipoOperacion) {
        // B1/B3: el dominio (com.bcodesphere.pilot.contabilidad.dominio.reglas.TipoOperacionContable) todavía solo
        // conoce CIERRE_INGRESOS_DIARIO; filtrar por un tipo guiado nuevo aquí es responsabilidad de B1/B3.
        var tipo = tipoOperacion == null
                ? null
                : com.bcodesphere.pilot.contabilidad.dominio.reglas.TipoOperacionContable.valueOf(
                        tipoOperacion.getValue());
        return ResponseEntity.ok(
                reglas.listar(tipo).stream().map(MapeadorContabilidad::aDto).toList());
    }

    @Override
    public ResponseEntity<ReglaContabilizacion> actualizarReglaContabilizacion(
            UUID xEmpresaId,
            UUID reglaId,
            String ifMatch,
            ActualizacionReglaContabilizacion actualizacion,
            String xRequestId) {
        var regla = reglas.actualizar(reglaId, ifMatch, actualizacion.getCuentaId(), actualizacion.getActiva());
        return ResponseEntity.ok().eTag(VersionEtag.formatear(regla.version())).body(MapeadorContabilidad.aDto(regla));
    }
}
