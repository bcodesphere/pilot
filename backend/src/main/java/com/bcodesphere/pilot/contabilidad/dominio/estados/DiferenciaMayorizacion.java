package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;

/**
 * Diferencia entre {@code saldo_cuenta_mensual} y la suma de {@code asiento_linea} para una cuenta, año y mes
 * (invariante de ADR-018). Solo puede aparecer por una alteración directa de la base de datos, porque el asiento y
 * su mayorización se guardan en la misma transacción.
 *
 * @param cuenta cuenta con la diferencia
 * @param anio año del período
 * @param mes mes del período (1 a 12)
 * @param saldoDebe Debe acumulado en {@code saldo_cuenta_mensual}
 * @param saldoHaber Haber acumulado en {@code saldo_cuenta_mensual}
 * @param lineasDebe Debe acumulado al sumar {@code asiento_linea} del mismo período
 * @param lineasHaber Haber acumulado al sumar {@code asiento_linea} del mismo período
 */
public record DiferenciaMayorizacion(
        Cuenta cuenta, int anio, int mes, Dinero saldoDebe, Dinero saldoHaber, Dinero lineasDebe, Dinero lineasHaber) {}
