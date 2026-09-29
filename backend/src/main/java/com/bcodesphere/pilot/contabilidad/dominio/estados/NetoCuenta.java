package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;

/**
 * Movimiento neto (Debe y Haber acumulados) de una cuenta en un período o a una fecha de corte (CLAUDE.md 10.3).
 * Es la unidad mínima que entregan los puertos de lectura de reportes; todo el resto de los cálculos de la sección
 * 10.4 (saldos, jerarquía, estados) se construye a partir de sumas y restas de este tipo.
 *
 * @param debe total acumulado del lado Debe
 * @param haber total acumulado del lado Haber
 */
public record NetoCuenta(Dinero debe, Dinero haber) {

    /** Neto sin movimiento, usado como valor por defecto de una cuenta sin filas en el período consultado. */
    public static final NetoCuenta CERO = new NetoCuenta(Dinero.CERO, Dinero.CERO);

    /**
     * Diferencia Debe menos Haber, con signo (positivo = saldo Deudor, negativo = saldo Acreedor).
     *
     * @return {@code debe - haber}
     */
    public Dinero neto() {
        return debe.restar(haber);
    }
}
