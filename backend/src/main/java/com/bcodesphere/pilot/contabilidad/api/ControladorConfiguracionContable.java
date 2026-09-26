package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.VersionEtag;
import com.bcodesphere.pilot.compartido.api.contrato.ActualizacionConfiguracionContable;
import com.bcodesphere.pilot.compartido.api.contrato.ConfiguracionContable;
import com.bcodesphere.pilot.compartido.api.contrato.ConfiguracionContableApi;
import com.bcodesphere.pilot.contabilidad.aplicacion.GestionarConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de {@code /contabilidad/configuracion}: modo de precio y cuentas de IVA. No lleva lógica: llama al caso
 * de uso y mapea; la versión viaja como {@code ETag}.
 */
@RestController
class ControladorConfiguracionContable implements ConfiguracionContableApi {

    private final GestionarConfiguracionContable configuracion;

    /**
     * Crea el controlador.
     *
     * @param configuracion caso de uso de la configuración contable
     */
    ControladorConfiguracionContable(GestionarConfiguracionContable configuracion) {
        this.configuracion = configuracion;
    }

    @Override
    public ResponseEntity<ConfiguracionContable> obtenerConfiguracionContable(UUID xEmpresaId, String xRequestId) {
        return respuesta(configuracion.obtener());
    }

    @Override
    public ResponseEntity<ConfiguracionContable> actualizarConfiguracionContable(
            UUID xEmpresaId, String ifMatch, ActualizacionConfiguracionContable actualizacion, String xRequestId) {
        return respuesta(configuracion.actualizar(
                ifMatch,
                ModoPrecio.valueOf(actualizacion.getModoPrecioDefecto().getValue()),
                actualizacion.getCuentaIvaDebitoId(),
                actualizacion.getCuentaIvaCreditoId()));
    }

    /** Arma la respuesta 200 con el DTO y el ETag de la versión. */
    private static ResponseEntity<ConfiguracionContable> respuesta(
            com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable c) {
        return ResponseEntity.ok().eTag(VersionEtag.formatear(c.version())).body(MapeadorContabilidad.aDto(c));
    }
}
