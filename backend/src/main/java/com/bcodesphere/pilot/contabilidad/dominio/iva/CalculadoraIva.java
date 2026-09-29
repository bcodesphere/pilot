package com.bcodesphere.pilot.contabilidad.dominio.iva;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Cálculo único de la separación de base e IVA (CLAUDE.md 11.1, ADR-015). Toda línea de asiento manual y, en F5, toda
 * operación de n8n pasa por aquí, con la tasa vigente a la fecha leída de {@code tasa_impuesto}.
 */
public final class CalculadoraIva {

    private CalculadoraIva() {}

    /**
     * Separa un monto en base e IVA según el modo de precio.
     *
     * <ul>
     *   <li>{@code CON_IVA}: el monto es el total; {@code iva = round(monto × t / (1 + t), 2)} y {@code base = monto − iva}.
     *   <li>{@code SIN_IVA}: el monto es la base; {@code iva = round(monto × t, 2)}.
     * </ul>
     *
     * @param monto monto ingresado, no negativo
     * @param modo cómo se interpreta el monto
     * @param tasa tasa vigente como fracción ({@code 0.1300} = 13 %)
     * @return base e IVA con 2 decimales
     */
    public static SeparacionIva separar(Dinero monto, ModoPrecio modo, BigDecimal tasa) {
        // 1. CON_IVA: el IVA es la parte del total que corresponde a la tasa; la base es el resto, así base + iva =
        // monto
        //    sin perder centavos por el redondeo
        if (modo == ModoPrecio.CON_IVA) {
            BigDecimal bruto = monto.valor().multiply(tasa).divide(BigDecimal.ONE.add(tasa), MathContext.DECIMAL128);
            Dinero iva = Dinero.redondear(bruto);
            return new SeparacionIva(monto.restar(iva), iva);
        }
        // 2. SIN_IVA: el monto es la base y el IVA se suma; el producto exacto se redondea HALF_UP a 2 decimales
        return new SeparacionIva(monto, Dinero.redondear(monto.valor().multiply(tasa)));
    }
}
