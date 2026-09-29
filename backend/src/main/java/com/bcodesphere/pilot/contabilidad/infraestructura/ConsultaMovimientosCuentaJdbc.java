package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ConsultaMovimientosCuenta;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC del puerto de movimientos (ADR-035, decisión 5), que reemplaza al de F2 que respondía «sin
 * movimientos». Lee {@code asiento_linea} y {@code saldo_cuenta_mensual}; ambas consultas filtran por
 * {@code empresa_id} además de RLS (CLAUDE.md 1.1.3). Sostiene {@code CON-011} y {@code CON-012}.
 */
@Component
class ConsultaMovimientosCuentaJdbc implements ConsultaMovimientosCuenta {

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    ConsultaMovimientosCuentaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public boolean tieneMovimientos(UUID cuentaId) {
        // Basta una línea de la cuenta; el índice idx_linea_mayor cubre (empresa_id, cuenta_id)
        return jdbc.sql(
                        "SELECT EXISTS (SELECT 1 FROM asiento_linea WHERE empresa_id = :empresa AND cuenta_id = :cuenta)")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("cuenta", cuentaId)
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public BigDecimal saldo(UUID cuentaId) {
        // Debe menos Haber de todos los meses; sin filas el COALESCE da 0.00, igual que un NUMERIC(19,2)
        return jdbc.sql("SELECT COALESCE(SUM(total_debe - total_haber), 0.00) FROM saldo_cuenta_mensual"
                        + " WHERE empresa_id = :empresa AND cuenta_id = :cuenta")
                .param("empresa", ContextoEmpresa.empresaRequerida().valor())
                .param("cuenta", cuentaId)
                .query(BigDecimal.class)
                .single();
    }
}
