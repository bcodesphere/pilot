package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.VersionEtag;
import com.bcodesphere.pilot.compartido.api.contrato.ActualizacionCuentaContable;
import com.bcodesphere.pilot.compartido.api.contrato.CuentaContable;
import com.bcodesphere.pilot.compartido.api.contrato.CuentasContablesApi;
import com.bcodesphere.pilot.compartido.api.contrato.NuevaCuentaContable;
import com.bcodesphere.pilot.contabilidad.aplicacion.GestionarCatalogo;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador del catálogo de cuentas ({@code /contabilidad/cuentas}). No lleva lógica: llama al caso de uso y mapea;
 * la versión de la cuenta viaja como {@code ETag} en las respuestas de una sola cuenta.
 */
@RestController
class ControladorCuentas implements CuentasContablesApi {

    private final GestionarCatalogo catalogo;

    /**
     * Crea el controlador.
     *
     * @param catalogo caso de uso del catálogo de cuentas
     */
    ControladorCuentas(GestionarCatalogo catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public ResponseEntity<List<CuentaContable>> listarCuentasContables(
            UUID xEmpresaId, String xRequestId, String busqueda, Boolean soloDetalle, Boolean soloActivas) {
        List<CuentaContable> cuentas = catalogo.listar(busqueda, soloDetalle, soloActivas).stream()
                .map(MapeadorContabilidad::aDto)
                .toList();
        return ResponseEntity.ok(cuentas);
    }

    @Override
    public ResponseEntity<CuentaContable> obtenerCuentaContable(UUID xEmpresaId, UUID cuentaId, String xRequestId) {
        return respuesta(HttpStatus.OK, catalogo.obtener(cuentaId));
    }

    @Override
    public ResponseEntity<CuentaContable> crearCuentaContable(
            UUID xEmpresaId, NuevaCuentaContable nuevaCuentaContable, String xRequestId) {
        Cuenta creada = catalogo.crear(
                nuevaCuentaContable.getCodigo(),
                nuevaCuentaContable.getNombre(),
                MapeadorContabilidad.aDominio(nuevaCuentaContable.getNaturaleza()));
        return respuesta(HttpStatus.CREATED, creada);
    }

    @Override
    public ResponseEntity<CuentaContable> actualizarCuentaContable(
            UUID xEmpresaId,
            UUID cuentaId,
            String ifMatch,
            ActualizacionCuentaContable actualizacion,
            String xRequestId) {
        Cuenta actualizada = catalogo.actualizar(
                cuentaId,
                ifMatch,
                actualizacion.getCodigo(),
                actualizacion.getNombre(),
                MapeadorContabilidad.aDominio(actualizacion.getNaturaleza()),
                actualizacion.getActiva());
        return respuesta(HttpStatus.OK, actualizada);
    }

    /** Arma la respuesta con el DTO y el ETag de la versión. */
    private static ResponseEntity<CuentaContable> respuesta(HttpStatus estado, Cuenta cuenta) {
        return ResponseEntity.status(estado)
                .eTag(VersionEtag.formatear(cuenta.version()))
                .body(MapeadorContabilidad.aDto(cuenta));
    }
}
