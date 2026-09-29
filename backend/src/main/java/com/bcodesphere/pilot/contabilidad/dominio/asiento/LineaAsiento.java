package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import java.util.UUID;

/**
 * Línea de un asiento guardado (CLAUDE.md 9.3): es Debe o Haber, nunca ambos.
 *
 * @param id identificador (UUID v7)
 * @param numeroLinea posición dentro del asiento, desde 1
 * @param cuenta cuenta de detalle
 * @param descripcion descripción, o nulo
 * @param debe monto al Debe (cero si va al Haber)
 * @param haber monto al Haber (cero si va al Debe)
 * @param origenLinea origen de la línea
 * @param lineaBaseId en una línea de IVA, la línea que la originó; nulo en las demás
 */
public record LineaAsiento(
        UUID id,
        int numeroLinea,
        ResumenCuenta cuenta,
        String descripcion,
        Dinero debe,
        Dinero haber,
        OrigenLinea origenLinea,
        UUID lineaBaseId) {}
