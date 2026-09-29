package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;

/**
 * Valor agregable de una fila de la Balanza de Comprobación: saldo inicial y movimientos del período (Debe y
 * Haber); el saldo final se deriva de los tres (ADR-038 §6).
 *
 * @param saldoInicial saldo (Debe − Haber, con signo) al inicio del rango
 * @param debe movimiento del lado Debe dentro del rango
 * @param haber movimiento del lado Haber dentro del rango
 */
record AcumuladoBalanza(Dinero saldoInicial, Dinero debe, Dinero haber) {

    /** Valor neutro de la suma: sin saldo inicial ni movimiento. */
    static final AcumuladoBalanza CERO = new AcumuladoBalanza(Dinero.CERO, Dinero.CERO, Dinero.CERO);

    /** Suma dos acumulados componente a componente, para agregar un grupo de cuentas de detalle. */
    static AcumuladoBalanza sumar(AcumuladoBalanza a, AcumuladoBalanza b) {
        return new AcumuladoBalanza(a.saldoInicial.sumar(b.saldoInicial), a.debe.sumar(b.debe), a.haber.sumar(b.haber));
    }

    /** {@code true} si no hay saldo inicial ni movimiento (y por lo tanto tampoco saldo final). */
    static boolean esCero(AcumuladoBalanza v) {
        return v.saldoInicial.esCero() && v.debe.esCero() && v.haber.esCero();
    }

    /** Saldo final: saldo inicial más el movimiento neto del período. */
    Dinero saldoFinal() {
        return saldoInicial.sumar(debe).restar(haber);
    }
}
