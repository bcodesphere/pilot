package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;

/**
 * Saldo de presentación de una cuenta (esquema {@code Saldo} del contrato, ADR-038 §4): el monto siempre en valor
 * absoluto, con el lado (Deudor, Acreedor o Cero) y la alerta de {@code contrarioNaturaleza} cuando el lado no
 * coincide con la naturaleza propia de la cuenta (CLAUDE.md 10.3: "se alerta cuando el saldo es contrario a la
 * naturaleza de la cuenta").
 *
 * @param monto valor absoluto del saldo (nunca negativo)
 * @param lado {@link LadoSaldo#DEUDOR}, {@link LadoSaldo#ACREEDOR} o {@link LadoSaldo#CERO}
 * @param contrarioNaturaleza {@code true} si el lado del saldo es opuesto a la naturaleza de la cuenta; siempre
 *     {@code false} en {@link LadoSaldo#CERO}, porque un saldo en cero no contradice ninguna naturaleza
 */
public record Saldo(Dinero monto, LadoSaldo lado, boolean contrarioNaturaleza) {

    /**
     * Construye el saldo de presentación a partir del neto Debe − Haber y de la naturaleza propia de la cuenta.
     *
     * @param debeMenosHaber diferencia con signo (positivo = Deudor, negativo = Acreedor)
     * @param naturaleza naturaleza propia de la cuenta (CLAUDE.md 9.3 {@code cuenta_contable.naturaleza})
     * @return el saldo de presentación
     */
    public static Saldo de(Dinero debeMenosHaber, NaturalezaCuenta naturaleza) {
        // 1. El signo del neto determina el lado; cero no tiene lado y nunca es "contrario"
        if (debeMenosHaber.esCero()) {
            return new Saldo(Dinero.CERO, LadoSaldo.CERO, false);
        }
        boolean esDeudor = debeMenosHaber.comparar(Dinero.CERO) > 0;
        LadoSaldo lado = esDeudor ? LadoSaldo.DEUDOR : LadoSaldo.ACREEDOR;
        // 2. El monto de presentación siempre es el valor absoluto
        Dinero monto = esDeudor ? debeMenosHaber : negar(debeMenosHaber);
        boolean contrario = (lado == LadoSaldo.DEUDOR && naturaleza == NaturalezaCuenta.ACREEDORA)
                || (lado == LadoSaldo.ACREEDOR && naturaleza == NaturalezaCuenta.DEUDORA);
        return new Saldo(monto, lado, contrario);
    }

    /** Valor absoluto de un monto negativo: {@code 0 - valor}. */
    private static Dinero negar(Dinero valor) {
        return Dinero.CERO.restar(valor);
    }
}
