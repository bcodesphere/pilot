package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;

/**
 * Comprobación del Estado de Situación Financiera (ADR-016, CLAUDE.md 10.4): Activo debe ser igual a Pasivo más
 * Patrimonio más los resultados no cerrados. Con la partida doble garantizada por el trigger de base de datos, solo
 * puede fallar ante un error de datos (por ejemplo, alguien alteró {@code saldo_cuenta_mensual} directamente).
 *
 * @param cuadra {@code true} si Activo es igual a Pasivo + Patrimonio + resultados no cerrados + utilidad del ejercicio
 * @param diferencia Activo menos ese total, con signo; cero cuando {@code cuadra} es {@code true}
 */
public record ComprobacionSituacionFinanciera(boolean cuadra, Dinero diferencia) {

    /**
     * Compara el Activo contra el resto de la ecuación.
     *
     * @param activo total del Activo
     * @param totalPasivoPatrimonio Pasivo + Patrimonio + resultados anteriores + utilidad del ejercicio
     * @return la comprobación con su diferencia exacta
     */
    public static ComprobacionSituacionFinanciera de(Dinero activo, Dinero totalPasivoPatrimonio) {
        Dinero diferencia = activo.restar(totalPasivoPatrimonio);
        return new ComprobacionSituacionFinanciera(diferencia.esCero(), diferencia);
    }
}
