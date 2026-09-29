package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

/** Naturaleza de una cuenta: el lado en que aumenta su saldo (CLAUDE.md 3, glosario). */
public enum NaturalezaCuenta {
    /** Aumenta con el Debe (clases 1 y 4 por defecto). */
    DEUDORA,
    /** Aumenta con el Haber (clases 2, 3 y 5 por defecto). */
    ACREEDORA
}
