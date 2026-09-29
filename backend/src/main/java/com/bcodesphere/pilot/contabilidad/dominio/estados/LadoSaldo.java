package com.bcodesphere.pilot.contabilidad.dominio.estados;

/**
 * Lado en que se presenta el saldo de una cuenta (CLAUDE.md 10.3): {@code debe − haber} positivo es Deudor,
 * negativo es Acreedor (se muestra el valor absoluto) y cero no tiene lado.
 */
public enum LadoSaldo {
    /** {@code debe − haber} es mayor que cero. */
    DEUDOR,
    /** {@code debe − haber} es menor que cero. */
    ACREEDOR,
    /** {@code debe − haber} es exactamente cero. */
    CERO
}
