package com.bcodesphere.pilot.contabilidad.infraestructura;

import com.bcodesphere.pilot.contabilidad.dominio.iva.ConsultaTasaIva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador JDBC de {@link ConsultaTasaIva} sobre {@code tasa_impuesto} (V9). La tabla es global y de solo lectura:
 * no tiene {@code empresa_id} ni RLS, así que esta consulta no filtra por empresa (CLAUDE.md 9.1). La tasa nunca es
 * una constante en el código (regla 1.1.13).
 */
@Component
class ConsultaTasaIvaJdbc implements ConsultaTasaIva {

    private final JdbcClient jdbc;

    /**
     * Crea el adaptador.
     *
     * @param jdbc cliente JDBC de la aplicación
     */
    ConsultaTasaIvaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<BigDecimal> vigenteA(LocalDate fecha) {
        // Vigencia con ambos extremos inclusivos; el índice de exclusión garantiza que a lo sumo una fila cubre la
        // fecha
        return jdbc.sql("SELECT tasa FROM tasa_impuesto WHERE tipo = 'IVA' AND vigente_desde <= :fecha"
                        + " AND (vigente_hasta IS NULL OR vigente_hasta >= :fecha)")
                .param("fecha", fecha)
                .query(BigDecimal.class)
                .optional();
    }
}
