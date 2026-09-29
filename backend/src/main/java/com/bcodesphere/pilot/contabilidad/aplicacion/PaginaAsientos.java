package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.ResumenAsiento;
import java.util.List;

/**
 * Página del Libro Diario paginada por llave (año, número).
 *
 * @param elementos asientos de la página, en orden ascendente
 * @param siguienteCursor cursor opaco de la página siguiente, o nulo si no hay más
 */
public record PaginaAsientos(List<ResumenAsiento> elementos, String siguienteCursor) {

    /** Copia defensiva de la lista de elementos. */
    public PaginaAsientos {
        elementos = List.copyOf(elementos);
    }
}
