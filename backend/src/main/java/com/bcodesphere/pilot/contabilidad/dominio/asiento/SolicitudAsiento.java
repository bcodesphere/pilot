package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos de un asiento manual antes de expandir el IVA y validarlo (CLAUDE.md 10.1).
 *
 * @param fecha fecha contable (hora de El Salvador)
 * @param concepto concepto o glosa
 * @param modoPrecio modo de precio elegido, o nulo para usar el de la configuración contable
 * @param lineas líneas capturadas, sin expandir
 */
public record SolicitudAsiento(LocalDate fecha, String concepto, ModoPrecio modoPrecio, List<LineaSolicitud> lineas) {

    /** Copia defensiva: la lista de líneas nunca es nula. */
    public SolicitudAsiento {
        lineas = lineas == null ? List.of() : List.copyOf(lineas);
    }
}
