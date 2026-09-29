package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.GeneradorId;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reglas de la reversión de un asiento (CLAUDE.md 10.1, ADR-019, ADR-036): quién puede revertirse y cómo se arma el
 * contra-asiento. Funciones puras, sin acceso a la base de datos.
 */
public final class ReglasReversion {

    private ReglasReversion() {}

    /**
     * Comprueba que el asiento pueda revertirse en la fecha indicada.
     *
     * @param original asiento que se quiere revertir
     * @param fecha fecha contable de la reversión
     * @param hoy hoy en {@code America/El_Salvador}
     * @throws ExcepcionContabilidad {@code CON-009} (es una reversión), {@code CON-008} (ya revertido),
     *     {@code CON-007} (fecha futura) o {@code CON-018} (fecha anterior a la del original)
     */
    public static void validar(Asiento original, LocalDate fecha, LocalDate hoy) {
        // 1. Una reversión no se revierte: se corrige con un asiento nuevo (CON-009)
        if (original.origenTipo() == OrigenAsiento.REVERSION) {
            throw ExcepcionContabilidad.reversionNoRevertible();
        }
        // 2. Un asiento se revierte una sola vez (CON-008)
        if (original.estado() == EstadoAsiento.REVERTIDO) {
            throw ExcepcionContabilidad.yaRevertido();
        }
        // 3. La fecha no puede ser futura (CON-007) ni anterior a la del original (CON-018)
        ReglasAsiento.validarFecha(fecha, hoy);
        if (fecha.isBefore(original.fecha())) {
            throw ExcepcionContabilidad.fechaAnteriorAlOriginal();
        }
    }

    /**
     * Arma el contra-asiento: mismas cuentas y descripciones con el Debe y el Haber intercambiados, y los enlaces de
     * IVA equivalentes. Mayoriza igual que cualquier asiento, por eso los saldos vuelven a su valor anterior.
     *
     * @param original asiento que se revierte (ya validado)
     * @param fecha fecha contable de la reversión
     * @param numero correlativo ya asignado en el año de {@code fecha}
     * @param creadoEn instante de creación
     * @return la reversión {@code CONTABILIZADO} con {@code origenTipo = REVERSION}
     */
    public static Asiento armar(Asiento original, LocalDate fecha, long numero, Instant creadoEn) {
        // 1. Cada línea nueva recibe su id; los enlaces de IVA se traducen del id original al de la línea nueva
        Map<UUID, UUID> idNuevoPorOriginal = new HashMap<>();
        original.lineas().forEach(l -> idNuevoPorOriginal.put(l.id(), GeneradorId.nuevo()));
        List<LineaAsiento> lineas = original.lineas().stream()
                .map(l -> new LineaAsiento(
                        idNuevoPorOriginal.get(l.id()),
                        l.numeroLinea(),
                        l.cuenta(),
                        l.descripcion(),
                        // 2. Lados intercambiados: lo que se debitó se acredita y viceversa
                        l.haber(),
                        l.debe(),
                        l.origenLinea(),
                        l.lineaBaseId() == null ? null : idNuevoPorOriginal.get(l.lineaBaseId())))
                .toList();
        // 3. Cabecera de la reversión, enlazada al original; los totales son los mismos
        return new Asiento(
                GeneradorId.nuevo(),
                fecha.getYear(),
                numero,
                fecha,
                "Reversión del asiento N.º " + original.numeroVisible(),
                EstadoAsiento.CONTABILIZADO,
                OrigenAsiento.REVERSION,
                null,
                original.modoPrecio(),
                original.id(),
                null,
                original.totalHaber(),
                original.totalDebe(),
                creadoEn,
                lineas);
    }
}
