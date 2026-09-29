package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import java.util.Map;

/**
 * Resumen de IVA del mes (CLAUDE.md 10.5, ADR-038 §8): IVA débito fiscal (Haber − Debe de la cuenta configurada) e
 * IVA crédito fiscal (Debe − Haber), cada uno desglosado por el origen del asiento, y la diferencia estimada entre
 * ambos. Es un punto de partida para preparar la declaración de IVA, no la reemplaza.
 *
 * @param anio año del período
 * @param mes mes del período (1 a 12)
 * @param cuentaIvaDebito cuenta de IVA débito fiscal de la configuración
 * @param cuentaIvaCredito cuenta de IVA crédito fiscal de la configuración
 * @param ivaDebito IVA débito fiscal del mes, desglosado por origen
 * @param ivaCredito IVA crédito fiscal del mes, desglosado por origen
 * @param diferenciaEstimada IVA débito menos IVA crédito
 * @param nota advertencia de que el resumen no reemplaza la declaración
 */
public record ResumenIva(
        int anio,
        int mes,
        ResumenCuenta cuentaIvaDebito,
        ResumenCuenta cuentaIvaCredito,
        DesgloseIva ivaDebito,
        DesgloseIva ivaCredito,
        Dinero diferenciaEstimada,
        String nota) {

    /** Nota fija de CLAUDE.md 10.5: el resumen es un punto de partida, no la declaración de IVA. */
    public static final String NOTA =
            "Punto de partida para preparar la declaración de IVA; no la reemplaza (CLAUDE.md §10.5).";

    /**
     * Arma el resumen de IVA del mes.
     *
     * @param anio año del período
     * @param mes mes del período
     * @param cuentaIvaDebito cuenta de IVA débito fiscal
     * @param cuentaIvaCredito cuenta de IVA crédito fiscal
     * @param debitoPorOrigen Haber − Debe de la cuenta de IVA débito, por origen de asiento
     * @param creditoPorOrigen Debe − Haber de la cuenta de IVA crédito, por origen de asiento
     * @return el resumen con su diferencia estimada
     */
    public static ResumenIva de(
            int anio,
            int mes,
            ResumenCuenta cuentaIvaDebito,
            ResumenCuenta cuentaIvaCredito,
            Map<OrigenAsiento, Dinero> debitoPorOrigen,
            Map<OrigenAsiento, Dinero> creditoPorOrigen) {
        DesgloseIva ivaDebito = DesgloseIva.de(debitoPorOrigen);
        DesgloseIva ivaCredito = DesgloseIva.de(creditoPorOrigen);
        return new ResumenIva(
                anio,
                mes,
                cuentaIvaDebito,
                cuentaIvaCredito,
                ivaDebito,
                ivaCredito,
                ivaDebito.total().restar(ivaCredito.total()),
                NOTA);
    }
}
