package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import java.time.LocalDate;
import java.util.List;

/**
 * Reglas del Libro Diario como funciones puras (CLAUDE.md 10.1): fecha y partida doble. No leen la base de datos, así
 * que se prueban sin infraestructura. El frontend las replica y el trigger de PostgreSQL es la última defensa.
 */
public final class ReglasAsiento {

    private ReglasAsiento() {}

    /**
     * Valida la fecha contable: obligatoria y no posterior a hoy en hora de El Salvador (ADR-036).
     *
     * @param fecha fecha del asiento o de la reversión
     * @param hoy fecha de hoy en {@code America/El_Salvador}
     * @throws ExcepcionContabilidad {@code CON-007}
     */
    public static void validarFecha(LocalDate fecha, LocalDate hoy) {
        // 1. Sin fecha no hay período contable; una fecha futura registraría hechos que aún no ocurren
        if (fecha == null || fecha.isAfter(hoy)) {
            throw ExcepcionContabilidad.fechaInvalida();
        }
    }

    /**
     * Valida un asiento ya expandido antes de guardarlo: mínimo de líneas capturadas y partida doble sobre las líneas
     * expandidas (incluidas las de IVA calculado).
     *
     * @param asiento asiento con el IVA expandido
     * @throws ExcepcionContabilidad {@code CON-001}, {@code CON-004} o {@code CON-005} (con la diferencia exacta)
     */
    public static void validar(AsientoExpandido asiento) {
        // 1. El usuario debe capturar al menos dos líneas (CON-001), como exige el formulario
        if (asiento.lineasCapturadas() < 2) {
            throw ExcepcionContabilidad.pocasLineas();
        }
        // 2. Partida doble sobre lo que realmente se guardará
        validarPartidaDoble(asiento.lineas());
    }

    /**
     * Valida la partida doble de las líneas expandidas (ejemplo de CLAUDE.md 8.2).
     *
     * @param lineas líneas ya expandidas, incluidas las de IVA calculado
     * @throws ExcepcionContabilidad {@code CON-001}, {@code CON-004} o {@code CON-005} con la diferencia exacta
     */
    public static void validarPartidaDoble(List<LineaExpandida> lineas) {
        // 1. Un asiento necesita al menos una cuenta que se debite y otra que se acredite
        if (lineas.size() < 2) {
            throw ExcepcionContabilidad.pocasLineas();
        }

        // 2. Suma ambos lados con Dinero (BigDecimal) para evitar errores de redondeo
        Dinero debe = lineas.stream().map(LineaExpandida::debe).reduce(Dinero.CERO, Dinero::sumar);
        Dinero haber = lineas.stream().map(LineaExpandida::haber).reduce(Dinero.CERO, Dinero::sumar);

        // 3. Un asiento en cero no representa ninguna operación (CON-004)
        if (debe.esCero()) {
            throw ExcepcionContabilidad.totalesEnCero();
        }

        // 4. Si no cuadra, se informa la diferencia exacta (CON-005)
        Dinero diferencia = debe.restar(haber);
        if (!diferencia.esCero()) {
            throw ExcepcionContabilidad.descuadrado(diferencia);
        }
    }
}
