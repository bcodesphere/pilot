package com.bcodesphere.pilot.contabilidad.dominio.reglas;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import java.util.List;
import java.util.UUID;

/**
 * Regla de contabilización: qué cuenta usar para un código de una operación de n8n (ADR-020, ADR-035).
 *
 * @param id identificador
 * @param tipoOperacion tipo de operación a la que aplica
 * @param categoria INGRESO o COBRO
 * @param codigo concepto o forma de pago (p. ej. {@code EFECTIVO})
 * @param cuenta cuenta asignada, o nulo si la regla no tiene cuenta (solo puede estar inactiva)
 * @param activa si la regla está activa
 * @param version versión para concurrencia optimista
 */
public record ReglaContabilizacion(
        UUID id,
        TipoOperacionContable tipoOperacion,
        CategoriaRegla categoria,
        String codigo,
        ResumenCuenta cuenta,
        boolean activa,
        long version) {

    /**
     * Valida el estado que se quiere dejar: una regla activa siempre tiene cuenta (ADR-035; la base de datos lo
     * refuerza con un {@code CHECK}).
     *
     * @param activa estado pedido
     * @param cuentaId cuenta pedida, o nulo
     * @throws ExcepcionValidacion 422 {@code PLT-002} con el campo {@code cuentaId} si se pide activa sin cuenta
     */
    public static void validarEstado(boolean activa, UUID cuentaId) {
        if (activa && cuentaId == null) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("cuentaId", "Una regla activa debe tener una cuenta")));
        }
    }
}
