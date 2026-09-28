package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;

/**
 * Fila de la Balanza de Comprobación: una cuenta (de cualquier nivel entre 1 y el nivel pedido) con su saldo
 * inicial, sus movimientos del período y su saldo final (ADR-038 §6).
 *
 * @param cuenta cuenta de agrupación o de detalle
 * @param nivel nivel de la cuenta (1 a 5)
 * @param esDetalle {@code true} si la cuenta es de detalle (acepta movimientos); solo esas cuentas entran en los
 *     totales de la balanza
 * @param saldoInicial saldo de presentación al inicio del rango
 * @param debe movimiento Debe del rango
 * @param haber movimiento Haber del rango
 * @param saldoFinal saldo de presentación al final del rango
 */
public record FilaBalanza(
        Cuenta cuenta, int nivel, boolean esDetalle, Saldo saldoInicial, Dinero debe, Dinero haber, Saldo saldoFinal) {}
