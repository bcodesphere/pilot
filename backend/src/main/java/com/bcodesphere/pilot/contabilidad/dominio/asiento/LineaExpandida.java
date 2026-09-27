package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;

/**
 * Línea ya expandida (con el IVA separado) pero aún sin guardar: es la que muestra la vista previa y la que se valida.
 *
 * @param numeroLinea posición en el asiento expandido, desde 1
 * @param cuenta cuenta de la línea
 * @param descripcion descripción, o nulo
 * @param debe monto al Debe (cero si va al Haber)
 * @param haber monto al Haber (cero si va al Debe)
 * @param origenLinea {@code USUARIO} o {@code IVA_CALCULADO}
 * @param numeroLineaOrigen en una línea de IVA, el {@code numeroLinea} de su línea base; nulo en las demás
 */
public record LineaExpandida(
        int numeroLinea,
        ResumenCuenta cuenta,
        String descripcion,
        Dinero debe,
        Dinero haber,
        OrigenLinea origenLinea,
        Integer numeroLineaOrigen) {}
