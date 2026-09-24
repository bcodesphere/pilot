package com.bcodesphere.pilot.compartido;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Monto en USD con escala fija de 2 decimales (ADR-022) y redondeo HALF_UP (CLAUDE.md 1.1.2).
 * Admite negativos porque también representa diferencias y saldos con signo (esquema {@code MontoConSigno});
 * exigir "no negativo" es responsabilidad de quien lo necesite (esquema {@code Monto}).
 * Nunca expone {@code double} ni {@code float} (CLAUDE.md 1.2.2).
 *
 * @param valor cantidad ya normalizada a 2 decimales
 */
public record Dinero(BigDecimal valor) implements Comparable<Dinero> {

    /** Cero con escala 2. */
    public static final Dinero CERO = new Dinero(BigDecimal.ZERO);

    /** Patrón de {@code MontoConSigno} del contrato OpenAPI: hasta 17 enteros y 2 decimales. */
    private static final Pattern PATRON = Pattern.compile("^-?\\d{1,17}(\\.\\d{1,2})?$");

    /** Escala contable única (ADR-022). */
    private static final int ESCALA = 2;

    /**
     * Normaliza a escala 2 sin redondear en silencio: un valor con más decimales significativos se rechaza,
     * para que nadie pierda centavos sin decidirlo (usar {@link #redondear(BigDecimal)} para cálculos).
     *
     * @throws IllegalArgumentException si el valor es nulo o tiene más de 2 decimales significativos
     */
    public Dinero {
        // 1. El valor es obligatorio
        if (valor == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo");
        }
        // 2. Se elimina el ruido de ceros a la derecha (100.000 -> 100) para medir los decimales reales
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() > ESCALA) {
            throw new IllegalArgumentException("El monto no admite más de 2 decimales");
        }
        // 3. Escala fija: 100.0 y 100.00 quedan idénticos, así equals compara por valor
        valor = valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
    }

    /**
     * Interpreta una cadena decimal del contrato ({@code ^-?\d{1,17}(\.\d{1,2})?$}).
     *
     * @param texto monto como cadena, por ejemplo {@code "123.45"} o {@code "-10.5"}
     * @return el monto normalizado a 2 decimales
     * @throws IllegalArgumentException si no cumple el patrón (notación científica, más de 2 decimales, etc.)
     */
    public static Dinero de(String texto) {
        // 1. Solo el patrón exacto del contrato; nada de notación científica ni espacios
        if (texto == null || !PATRON.matcher(texto).matches()) {
            throw new IllegalArgumentException("Monto inválido: se esperaba una cadena decimal con máximo 2 decimales");
        }
        return new Dinero(new BigDecimal(texto));
    }

    /**
     * Redondea el resultado de un cálculo (p. ej. IVA) a 2 decimales con HALF_UP.
     *
     * @param resultado valor calculado con cualquier escala
     * @return monto con escala 2
     */
    public static Dinero redondear(BigDecimal resultado) {
        if (resultado == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo");
        }
        return new Dinero(resultado.setScale(ESCALA, RoundingMode.HALF_UP));
    }

    /** Suma exacta de dos montos. */
    public Dinero sumar(Dinero otro) {
        return new Dinero(valor.add(otro.valor));
    }

    /** Resta exacta {@code this - otro}; puede dar negativo. */
    public Dinero restar(Dinero otro) {
        return new Dinero(valor.subtract(otro.valor));
    }

    /** Indica si el monto es exactamente cero. */
    public boolean esCero() {
        return valor.signum() == 0;
    }

    /** Indica si el monto es mayor que cero. */
    public boolean esPositivo() {
        return valor.signum() > 0;
    }

    /** Compara por valor con otro monto; negativo, cero o positivo según {@code this} sea menor, igual o mayor. */
    public int comparar(Dinero otro) {
        return valor.compareTo(otro.valor);
    }

    @Override
    public int compareTo(Dinero otro) {
        return comparar(otro);
    }

    /** Representación en cadena decimal con 2 decimales, la misma que viaja en la API (ADR-013). */
    @Override
    public String toString() {
        return valor.toPlainString();
    }
}
