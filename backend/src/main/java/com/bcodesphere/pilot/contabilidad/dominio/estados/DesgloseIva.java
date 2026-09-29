package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import java.util.Map;

/**
 * Desglose de un movimiento de IVA por el origen del asiento que lo generó (ADR-038 §8): manual, de n8n o de una
 * reversión. Hasta que exista F5, ningún asiento tiene origen {@code N8N}, así que ese campo siempre es cero.
 *
 * @param manual monto de asientos {@code MANUAL}
 * @param n8n monto de asientos {@code N8N}
 * @param reversion monto de asientos {@code REVERSION}
 * @param total suma de los tres orígenes
 */
public record DesgloseIva(Dinero manual, Dinero n8n, Dinero reversion, Dinero total) {

    /**
     * Arma el desglose a partir del monto por origen ya calculado (Haber − Debe para el IVA débito, Debe − Haber
     * para el crédito, según quien llama).
     *
     * @param porOrigen monto por origen de asiento; un origen ausente vale cero
     * @return el desglose con su total
     */
    public static DesgloseIva de(Map<OrigenAsiento, Dinero> porOrigen) {
        Dinero manual = porOrigen.getOrDefault(OrigenAsiento.MANUAL, Dinero.CERO);
        Dinero n8n = porOrigen.getOrDefault(OrigenAsiento.N8N, Dinero.CERO);
        Dinero reversion = porOrigen.getOrDefault(OrigenAsiento.REVERSION, Dinero.CERO);
        return new DesgloseIva(manual, n8n, reversion, manual.sumar(n8n).sumar(reversion));
    }
}
