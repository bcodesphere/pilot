package com.bcodesphere.pilot.contabilidad.dominio.reglas;

/**
 * Tipos de operación que Pilot sabe contabilizar mediante reglas de contabilización (ADR-020, ADR-041). Los diez
 * primeros son las operaciones guiadas del motor único {@code ContabilizarOperacion} (B2); el último es el cierre
 * de ingresos diarios que llega por el webhook de n8n (CLAUDE.md 12.1) y usa el mismo motor.
 */
public enum TipoOperacionContable {
    /** Venta: ingresos por concepto gravado, exento o no sujeto, cobrados en una forma de pago. */
    VENTA,
    /** Compra o gasto: un destino de gasto pagado en una forma de pago. */
    COMPRA_GASTO,
    /** Cobro a un cliente o a un emisor de tarjetas, con su contrapartida y forma de cobro. */
    COBRO_CLIENTE,
    /** Pago a un proveedor, con su contrapartida y forma de pago. */
    PAGO_PROVEEDOR,
    /** Aporte de capital de un socio. */
    APORTE_CAPITAL,
    /** Préstamo recibido, a corto o largo plazo. */
    PRESTAMO_RECIBIDO,
    /** Pago de una cuota de préstamo (capital, intereses y comisión). */
    PAGO_CUOTA,
    /** Traslado de fondos entre dos cuentas del grupo 1101 (efectivo y equivalentes). */
    TRASLADO_FONDOS,
    /** Compra de un activo fijo. */
    COMPRA_ACTIVO_FIJO,
    /** Depreciación mensual de los activos fijos en uso. */
    DEPRECIACION_MENSUAL,
    /** Cierre de ingresos diarios enviado por n8n (CLAUDE.md 12.1). */
    CIERRE_INGRESOS_DIARIO
}
