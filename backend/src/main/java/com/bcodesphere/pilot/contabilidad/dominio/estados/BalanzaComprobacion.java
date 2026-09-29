package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Balanza de Comprobación de un rango de fechas (CLAUDE.md 10.5, ADR-038 §6): una fila por cuenta con saldo o
 * movimiento hasta el nivel pedido, con saldo inicial, Debe, Haber y saldo final. Los totales salen solo de las
 * cuentas de detalle, para no contar dos veces una cuenta y sus agrupaciones.
 *
 * @param desde inicio del rango, inclusive
 * @param hasta fin del rango, inclusive
 * @param nivel nivel de la jerarquía pedido
 * @param filas cuentas de nivel 1 al nivel pedido con saldo o movimiento
 * @param totalDebe Σ Debe de las cuentas de detalle
 * @param totalHaber Σ Haber de las cuentas de detalle
 * @param totalSaldosDeudores Σ de los saldos finales Deudores de las cuentas de detalle
 * @param totalSaldosAcreedores Σ de los saldos finales Acreedores de las cuentas de detalle
 * @param cuadra {@code true} si {@code totalDebe == totalHaber} y {@code totalSaldosDeudores == totalSaldosAcreedores}
 */
public record BalanzaComprobacion(
        LocalDate desde,
        LocalDate hasta,
        int nivel,
        List<FilaBalanza> filas,
        Dinero totalDebe,
        Dinero totalHaber,
        Dinero totalSaldosDeudores,
        Dinero totalSaldosAcreedores,
        boolean cuadra) {

    /** Copia defensiva de las filas. */
    public BalanzaComprobacion {
        filas = List.copyOf(filas);
    }

    /**
     * Genera la Balanza de Comprobación del rango pedido.
     *
     * @param catalogoCompleto todas las cuentas de la empresa
     * @param saldoInicialPorCuenta neto acumulado de cada cuenta hasta el día anterior a {@code desde}
     *     (CLAUDE.md 10.3)
     * @param movimientosRango neto de cada cuenta dentro de {@code [desde, hasta]}
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @param nivel nivel de la jerarquía pedido (1 a 5)
     * @return la balanza calculada
     */
    public static BalanzaComprobacion generar(
            List<Cuenta> catalogoCompleto,
            Map<UUID, NetoCuenta> saldoInicialPorCuenta,
            Map<UUID, NetoCuenta> movimientosRango,
            LocalDate desde,
            LocalDate hasta,
            int nivel) {
        // 1. Un acumulado (saldo inicial, Debe, Haber) por cada cuenta de detalle
        Map<Cuenta, AcumuladoBalanza> detalle = new HashMap<>();
        for (Cuenta c : catalogoCompleto) {
            if (!c.aceptaMovimientos()) {
                continue;
            }
            Dinero saldoInicial =
                    saldoInicialPorCuenta.getOrDefault(c.id(), NetoCuenta.CERO).neto();
            NetoCuenta movimiento = movimientosRango.getOrDefault(c.id(), NetoCuenta.CERO);
            detalle.put(c, new AcumuladoBalanza(saldoInicial, movimiento.debe(), movimiento.haber()));
        }

        // 2. Los totales salen solo del detalle (ADR-038 §6), sin importar el nivel pedido
        Dinero totalDebe = Dinero.CERO;
        Dinero totalHaber = Dinero.CERO;
        Dinero totalDeudores = Dinero.CERO;
        Dinero totalAcreedores = Dinero.CERO;
        for (Map.Entry<Cuenta, AcumuladoBalanza> e : detalle.entrySet()) {
            AcumuladoBalanza v = e.getValue();
            totalDebe = totalDebe.sumar(v.debe());
            totalHaber = totalHaber.sumar(v.haber());
            Saldo saldoFinal = Saldo.de(v.saldoFinal(), e.getKey().naturaleza());
            if (saldoFinal.lado() == LadoSaldo.DEUDOR) {
                totalDeudores = totalDeudores.sumar(saldoFinal.monto());
            } else if (saldoFinal.lado() == LadoSaldo.ACREEDOR) {
                totalAcreedores = totalAcreedores.sumar(saldoFinal.monto());
            }
        }
        boolean cuadra = totalDebe.comparar(totalHaber) == 0 && totalDeudores.comparar(totalAcreedores) == 0;

        // 3. Filas de nivel 1 al nivel pedido, agrupando el detalle por el prefijo del código
        List<FilaBalanza> filas = Jerarquia.construir(
                        catalogoCompleto,
                        detalle,
                        1,
                        nivel,
                        AcumuladoBalanza::sumar,
                        AcumuladoBalanza.CERO,
                        AcumuladoBalanza::esCero,
                        false)
                .stream()
                .map(f -> {
                    AcumuladoBalanza v = f.valor();
                    return new FilaBalanza(
                            f.cuenta(),
                            f.nivel(),
                            f.cuenta().aceptaMovimientos(),
                            Saldo.de(v.saldoInicial(), f.cuenta().naturaleza()),
                            v.debe(),
                            v.haber(),
                            Saldo.de(v.saldoFinal(), f.cuenta().naturaleza()));
                })
                .toList();

        return new BalanzaComprobacion(
                desde, hasta, nivel, filas, totalDebe, totalHaber, totalDeudores, totalAcreedores, cuadra);
    }
}
