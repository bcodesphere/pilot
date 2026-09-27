package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Asiento con el IVA ya expandido, listo para validar la partida doble y guardarse (o solo mostrarse en la vista
 * previa, ADR-036). Los totales se calculan sobre las líneas expandidas, no sobre las capturadas: en {@code SIN_IVA}
 * la expansión cambia los totales.
 *
 * @param fecha fecha contable
 * @param concepto concepto o glosa
 * @param modoPrecio modo de precio aplicado, o nulo si ninguna línea llevó IVA (ADR-036)
 * @param tasaIva tasa vigente usada, o nula si ninguna línea llevó IVA
 * @param lineas líneas expandidas, en orden
 * @param lineasCapturadas cuántas líneas capturó el usuario (antes de expandir)
 */
public record AsientoExpandido(
        LocalDate fecha,
        String concepto,
        ModoPrecio modoPrecio,
        BigDecimal tasaIva,
        List<LineaExpandida> lineas,
        int lineasCapturadas) {

    /** Copia defensiva de las líneas. */
    public AsientoExpandido {
        lineas = List.copyOf(lineas);
    }

    /** Σ Debe de las líneas expandidas. */
    public Dinero totalDebe() {
        return lineas.stream().map(LineaExpandida::debe).reduce(Dinero.CERO, Dinero::sumar);
    }

    /** Σ Haber de las líneas expandidas. */
    public Dinero totalHaber() {
        return lineas.stream().map(LineaExpandida::haber).reduce(Dinero.CERO, Dinero::sumar);
    }

    /** Σ Debe − Σ Haber, exacta y con signo. */
    public Dinero diferencia() {
        return totalDebe().restar(totalHaber());
    }

    /**
     * Indica si el asiento cumple la partida doble: al menos dos líneas, totales mayores que cero y Debe = Haber.
     *
     * @return {@code true} si se puede guardar en cuanto a la partida doble
     */
    public boolean cuadra() {
        return lineas.size() >= 2 && totalDebe().esPositivo() && diferencia().esCero();
    }
}
