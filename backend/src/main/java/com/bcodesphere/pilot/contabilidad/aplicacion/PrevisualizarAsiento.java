package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.AsientoExpandido;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.SolicitudAsiento;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso «vista previa de un asiento» (CLAUDE.md 10.1, ADR-036). Rol mínimo: {@code contador}. Corre en una
 * transacción de solo lectura, sin idempotencia ni auditoría, y no escribe nada. Devuelve las líneas expandidas y los
 * totales aunque el asiento no cuadre; solo rechaza lo que impide expandirlo ({@code CON-002}, {@code CON-003},
 * {@code CON-006}, {@code CON-007}, {@code CON-013}, {@code CON-017}).
 */
@Service
public class PrevisualizarAsiento {

    private final PreparadorAsiento preparador;

    /**
     * Crea el caso de uso.
     *
     * @param preparador lecturas y expansión del asiento
     */
    public PrevisualizarAsiento(PreparadorAsiento preparador) {
        this.preparador = preparador;
    }

    /**
     * Expande el asiento sin guardarlo ni validar la partida doble.
     *
     * @param solicitud datos capturados
     * @return el asiento expandido con sus totales, diferencia y {@code cuadra}
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional(readOnly = true)
    public AsientoExpandido previsualizar(SolicitudAsiento solicitud) {
        return preparador.preparar(solicitud);
    }
}
