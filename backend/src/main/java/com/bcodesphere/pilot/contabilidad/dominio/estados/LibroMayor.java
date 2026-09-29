package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Libro Mayor / auxiliar de una cuenta en un rango de fechas (CLAUDE.md 10.5, ADR-038 §5): saldo inicial,
 * movimientos con saldo acumulado línea a línea, totales y saldo final. Acepta cualquier cuenta; en una cuenta
 * padre, los movimientos son los de todas sus cuentas de detalle, ya mezclados y ordenados cronológicamente por
 * quien llama, y el saldo se acumula con la naturaleza de la cuenta consultada (no la de cada línea individual).
 *
 * @param cuenta cuenta consultada (de detalle o padre)
 * @param naturaleza naturaleza de la cuenta consultada, usada para presentar el saldo Deudor/Acreedor
 * @param desde inicio del rango, inclusive
 * @param hasta fin del rango, inclusive
 * @param saldoInicial saldo de presentación al inicio del rango
 * @param movimientos líneas del rango con su saldo acumulado
 * @param totalDebe Σ Debe del rango
 * @param totalHaber Σ Haber del rango
 * @param saldoFinal saldo de presentación al final del rango
 */
public record LibroMayor(
        Cuenta cuenta,
        NaturalezaCuenta naturaleza,
        LocalDate desde,
        LocalDate hasta,
        Saldo saldoInicial,
        List<MovimientoMayor> movimientos,
        Dinero totalDebe,
        Dinero totalHaber,
        Saldo saldoFinal) {

    /** Copia defensiva de los movimientos. */
    public LibroMayor {
        movimientos = List.copyOf(movimientos);
    }

    /**
     * Genera el Libro Mayor de la cuenta consultada.
     *
     * @param cuenta cuenta consultada
     * @param saldoInicialNeto Debe − Haber acumulado hasta el día anterior a {@code desde} (CLAUDE.md 10.3), sumado
     *     entre todas las cuentas de detalle involucradas si {@code cuenta} es una cuenta padre
     * @param lineas líneas del rango, ya ordenadas por fecha, año/número de asiento y número de línea
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @return el Libro Mayor con el saldo acumulado línea a línea
     */
    public static LibroMayor generar(
            Cuenta cuenta, Dinero saldoInicialNeto, List<MovimientoLinea> lineas, LocalDate desde, LocalDate hasta) {
        NaturalezaCuenta naturaleza = cuenta.naturaleza();
        Saldo saldoInicial = Saldo.de(saldoInicialNeto, naturaleza);

        // El saldo se acumula con la naturaleza de la cuenta consultada, línea a línea, en el orden ya dado
        List<MovimientoMayor> movimientos = new ArrayList<>(lineas.size());
        Dinero acumulado = saldoInicialNeto;
        Dinero totalDebe = Dinero.CERO;
        Dinero totalHaber = Dinero.CERO;
        for (MovimientoLinea l : lineas) {
            acumulado = acumulado.sumar(l.debe()).restar(l.haber());
            totalDebe = totalDebe.sumar(l.debe());
            totalHaber = totalHaber.sumar(l.haber());
            movimientos.add(new MovimientoMayor(l, Saldo.de(acumulado, naturaleza)));
        }

        return new LibroMayor(
                cuenta,
                naturaleza,
                desde,
                hasta,
                saldoInicial,
                List.copyOf(movimientos),
                totalDebe,
                totalHaber,
                Saldo.de(acumulado, naturaleza));
    }
}
