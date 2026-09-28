package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;

/**
 * Regla de presentación de los estados financieros por clase (CLAUDE.md 10.4): el monto de cada rubro se presenta
 * en positivo según la naturaleza de la <strong>clase</strong> (1 y 4 deudoras, 2, 3 y 5 acreedoras), sin importar
 * la naturaleza propia de cada cuenta. Así una cuenta complementaria (por ejemplo, depreciación acumulada, clase 1
 * pero acreedora) resta correctamente del total de su clase en vez de sumarse con el signo equivocado.
 */
final class NaturalezaClase {

    private NaturalezaClase() {}

    /**
     * Monto de un rubro en positivo, según la clase.
     *
     * @param clase clase contable (1 a 5, CLAUDE.md 10.2)
     * @param neto movimiento neto de la cuenta o del grupo
     * @return {@code debe − haber} en las clases 1 y 4 (deudoras); {@code haber − debe} en 2, 3 y 5 (acreedoras)
     */
    static Dinero enPositivo(int clase, NetoCuenta neto) {
        boolean deudora = clase == 1 || clase == 4;
        return deudora ? neto.debe().restar(neto.haber()) : neto.haber().restar(neto.debe());
    }
}
