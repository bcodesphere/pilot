package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DiferenciaMayorizacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.MovimientoLinea;
import com.bcodesphere.pilot.contabilidad.dominio.estados.NetoCuenta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de lectura de los reportes contables (CQRS ligero, CLAUDE.md 4.1): todo el cálculo de reportes vive en
 * {@code dominio.estados}; este puerto solo entrega los datos crudos que el dominio necesita para calcularlos. Solo
 * puede usarse dentro de una transacción con el contexto de empresa (RLS).
 */
public interface ConsultaReportes {

    /**
     * Movimiento neto (Debe, Haber) acumulado desde el primer movimiento de cada cuenta de detalle hasta la fecha
     * dada, inclusive (CLAUDE.md 10.3: meses completos de {@code saldo_cuenta_mensual} más el mes parcial de
     * {@code asiento_linea}).
     *
     * @param fecha fecha de corte, inclusive
     * @return neto por cuenta de detalle; una cuenta sin movimiento hasta la fecha no aparece en el mapa
     */
    Map<UUID, NetoCuenta> saldoAcumuladoAFecha(LocalDate fecha);

    /**
     * Movimiento neto (Debe, Haber) de cada cuenta de detalle dentro de un rango de fechas, inclusive.
     *
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @return neto por cuenta de detalle; una cuenta sin movimiento en el rango no aparece en el mapa
     */
    Map<UUID, NetoCuenta> movimientosEnRango(LocalDate desde, LocalDate hasta);

    /**
     * Movimiento neto acumulado de un conjunto de cuentas de detalle hasta una fecha, ya sumado (para el saldo
     * inicial del Libro Mayor de una cuenta padre, cuyo saldo es la suma de sus cuentas de detalle, ADR-038 §5).
     *
     * @param cuentaIds cuentas de detalle a sumar
     * @param fecha fecha de corte, inclusive
     * @return el neto ya sumado; {@link NetoCuenta#CERO} si ninguna tuvo movimiento
     */
    NetoCuenta saldoAcumuladoAFecha(Set<UUID> cuentaIds, LocalDate fecha);

    /**
     * Líneas de asiento de un conjunto de cuentas de detalle dentro de un rango de fechas, ordenadas por fecha,
     * año/número de asiento y número de línea (para el Libro Mayor, ADR-038 §5).
     *
     * @param cuentaIds cuentas de detalle a incluir
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @return las líneas en orden cronológico
     */
    List<MovimientoLinea> listarMovimientos(Set<UUID> cuentaIds, LocalDate desde, LocalDate hasta);

    /**
     * Diferencias entre {@code saldo_cuenta_mensual} y la suma de {@code asiento_linea}, por cuenta, año y mes
     * (invariante de ADR-018).
     *
     * @return las diferencias encontradas; vacío si todo cuadra
     */
    List<DiferenciaMayorizacion> diagnosticarMayorizacion();

    /**
     * Cantidad de combinaciones distintas de cuenta, año y mes con al menos una fila en
     * {@code saldo_cuenta_mensual} o en {@code asiento_linea} (para informar cuántas combinaciones revisó el
     * diagnóstico; una misma cuenta con movimiento en varios meses cuenta una vez por mes).
     *
     * @return la cantidad de combinaciones cuenta/año/mes revisadas
     */
    int contarCombinacionesRevisadas();

    /**
     * Movimiento de una cuenta dentro de un mes, agrupado por el origen del asiento (para el resumen de IVA,
     * ADR-038 §8).
     *
     * @param cuentaId cuenta de IVA consultada
     * @param anio año del período
     * @param mes mes del período (1 a 12)
     * @return el neto (Debe, Haber) por cada origen que tuvo movimiento
     */
    Map<OrigenAsiento, NetoCuenta> movimientosPorOrigen(UUID cuentaId, int anio, int mes);
}
