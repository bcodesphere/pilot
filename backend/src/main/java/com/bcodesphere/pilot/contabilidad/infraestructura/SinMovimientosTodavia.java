package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ConsultaMovimientosCuenta;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador de F2 del puerto de movimientos (ADR-035, decisión 5): aún no existen asientos, así que ninguna cuenta
 * tiene movimientos ni saldo. <strong>F3 lo reemplaza</strong> por la consulta a {@code asiento_linea} y
 * {@code saldo_cuenta_mensual}; entonces {@code CON-011} y {@code CON-012} se probarán con movimientos reales.
 */
@Component
class SinMovimientosTodavia implements ConsultaMovimientosCuenta {

    @Override
    public boolean tieneMovimientos(UUID cuentaId) {
        // F2: no hay tabla de asientos todavía
        return false;
    }

    @Override
    public BigDecimal saldo(UUID cuentaId) {
        // Cero con dos decimales, igual que un saldo real de NUMERIC(19,2)
        return new BigDecimal("0.00");
    }
}
