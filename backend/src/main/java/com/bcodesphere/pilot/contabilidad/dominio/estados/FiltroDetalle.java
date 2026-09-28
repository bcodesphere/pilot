package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Selecciona las cuentas de detalle del catálogo que cumplen un filtro (por ejemplo, una clase, o una clase sin el
 * grupo 44, ADR-037 punto 4) y les asocia su movimiento neto; solo las cuentas de detalle mueven saldo
 * (CLAUDE.md 10.2), así que las de agrupación nunca entran en este mapa.
 */
final class FiltroDetalle {

    private FiltroDetalle() {}

    /**
     * Filtra el catálogo y asocia el neto de cada cuenta de detalle resultante.
     *
     * @param catalogoCompleto todas las cuentas de la empresa
     * @param movimientos neto por cuenta (id), ya leído del puerto de reportes; una cuenta sin entrada no tuvo
     *     movimiento en el período consultado
     * @param filtro condición adicional sobre la cuenta (por ejemplo, la clase o el prefijo del código)
     * @return cuentas de detalle que cumplen el filtro, con su neto (cero si no tuvieron movimiento)
     */
    static Map<Cuenta, NetoCuenta> seleccionar(
            List<Cuenta> catalogoCompleto, Map<UUID, NetoCuenta> movimientos, Predicate<Cuenta> filtro) {
        Map<Cuenta, NetoCuenta> resultado = new HashMap<>();
        for (Cuenta c : catalogoCompleto) {
            if (c.aceptaMovimientos() && filtro.test(c)) {
                resultado.put(c, movimientos.getOrDefault(c.id(), NetoCuenta.CERO));
            }
        }
        return resultado;
    }
}
