package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Asiento contable guardado, con sus líneas (CLAUDE.md 9.3 y 10.1). Es inmutable: solo puede pasar a
 * {@code REVERTIDO} mediante su reversión (ADR-019), y eso lo hace la base de datos con un {@code UPDATE} acotado, no
 * este objeto.
 *
 * @param id identificador (UUID v7)
 * @param anio año de la fecha contable (numeración)
 * @param numero correlativo por empresa y año
 * @param fecha fecha contable
 * @param concepto concepto o glosa
 * @param estado {@code CONTABILIZADO} o {@code REVERTIDO}
 * @param origenTipo {@code MANUAL}, {@code N8N} o {@code REVERSION}
 * @param origenId operación externa de origen, o nulo
 * @param modoPrecio modo con que se separó el IVA, o nulo si ninguna línea lo llevó
 * @param asientoRevertidoId en una reversión: el asiento que revierte; nulo en las demás
 * @param asientoReversionId en un asiento revertido: su reversión; nulo en los demás
 * @param totalDebe Σ Debe
 * @param totalHaber Σ Haber
 * @param creadoEn instante de creación (UTC)
 * @param lineas líneas en orden de {@code numeroLinea}
 */
public record Asiento(
        UUID id,
        int anio,
        long numero,
        LocalDate fecha,
        String concepto,
        EstadoAsiento estado,
        OrigenAsiento origenTipo,
        UUID origenId,
        ModoPrecio modoPrecio,
        UUID asientoRevertidoId,
        UUID asientoReversionId,
        Dinero totalDebe,
        Dinero totalHaber,
        Instant creadoEn,
        List<LineaAsiento> lineas) {

    /** Copia defensiva de las líneas. */
    public Asiento {
        lineas = List.copyOf(lineas);
    }

    /**
     * Arma un asiento manual {@code CONTABILIZADO} a partir de uno ya expandido y validado.
     *
     * @param expandido asiento con el IVA expandido que cumple la partida doble
     * @param numero correlativo ya asignado para el año de la fecha
     * @param creadoEn instante de creación
     * @return el asiento con ids nuevos y las líneas de IVA enlazadas a su línea base
     */
    public static Asiento manual(AsientoExpandido expandido, long numero, Instant creadoEn) {
        // 1. Cada línea recibe su id; el enlace de IVA se resuelve por la posición de su línea base (numeroLineaOrigen)
        Map<Integer, UUID> idPorNumero = new HashMap<>();
        expandido.lineas().forEach(l -> idPorNumero.put(l.numeroLinea(), GeneradorId.nuevo()));
        List<LineaAsiento> lineas = new ArrayList<>();
        for (LineaExpandida l : expandido.lineas()) {
            UUID base = l.numeroLineaOrigen() == null ? null : idPorNumero.get(l.numeroLineaOrigen());
            lineas.add(new LineaAsiento(
                    idPorNumero.get(l.numeroLinea()),
                    l.numeroLinea(),
                    l.cuenta(),
                    l.descripcion(),
                    l.debe(),
                    l.haber(),
                    l.origenLinea(),
                    base));
        }
        // 2. Cabecera vigente: la fecha fija el año de numeración y los totales salen de las líneas expandidas
        return new Asiento(
                GeneradorId.nuevo(),
                expandido.fecha().getYear(),
                numero,
                expandido.fecha(),
                expandido.concepto(),
                EstadoAsiento.CONTABILIZADO,
                OrigenAsiento.MANUAL,
                null,
                expandido.modoPrecio(),
                null,
                null,
                expandido.totalDebe(),
                expandido.totalHaber(),
                creadoEn,
                lineas);
    }

    /**
     * Número visible del asiento, {@code numero/anio}.
     *
     * @return por ejemplo {@code 15/2026}
     */
    public String numeroVisible() {
        return numero + "/" + anio;
    }
}
