package com.bcodesphere.pilot.contabilidad.dominio.iva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Puerto de lectura de la tasa de IVA vigente (CLAUDE.md 1.1.13): la tasa nunca es una constante en el código, se lee
 * de {@code tasa_impuesto} con su vigencia (desde/hasta). El adaptador vive en infraestructura.
 */
public interface ConsultaTasaIva {

    /**
     * Busca la tasa de IVA vigente en una fecha.
     *
     * @param fecha fecha contable del asiento o de la operación
     * @return la tasa como fracción decimal ({@code 0.1300} = 13 %), o vacío si ninguna vigencia cubre la fecha
     */
    Optional<BigDecimal> vigenteA(LocalDate fecha);
}
