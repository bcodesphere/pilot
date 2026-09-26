package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Puerto de lectura de movimientos y saldo de una cuenta (ADR-035, decisión 5). Las reglas {@code CON-011} (código o
 * hijas con movimientos) y {@code CON-012} (desactivar con saldo) dependen de él. En F2 responde «sin movimientos»
 * porque aún no existen asientos; F3 lo reemplaza por la consulta a {@code asiento_linea} y
 * {@code saldo_cuenta_mensual}.
 */
public interface ConsultaMovimientosCuenta {

    /**
     * Indica si la cuenta tiene al menos una línea de asiento.
     *
     * @param cuentaId cuenta consultada
     * @return {@code true} si tiene movimientos
     */
    boolean tieneMovimientos(UUID cuentaId);

    /**
     * Saldo actual de la cuenta (Debe menos Haber, con dos decimales).
     *
     * @param cuentaId cuenta consultada
     * @return el saldo; cero si no tiene movimientos
     */
    BigDecimal saldo(UUID cuentaId);
}
