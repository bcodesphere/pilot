package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import java.util.Optional;

/**
 * Código de una cuenta contable (CLAUDE.md 10.2): solo dígitos, primer dígito = clase 1 a 5 y longitud de 1, 2, 4, 6
 * u 8 dígitos (clase, grupo, cuenta, subcuenta y detalle). Un valor de este tipo siempre es válido: la construcción
 * lanza {@link ExcepcionContabilidad} si no cumple. El código de la cuenta padre es el prefijo de la longitud del
 * nivel anterior.
 *
 * @param valor el código como texto (los ceros a la izquierda no existen porque la clase es 1 a 5)
 */
public record CodigoCuenta(String valor) {

    /** Longitud de cada nivel: índice = nivel - 1 (clase 1, grupo 2, cuenta 4, subcuenta 6, detalle 8). */
    private static final int[] LONGITUD_POR_NIVEL = {1, 2, 4, 6, 8};

    /**
     * Valida y crea el código.
     *
     * @param valor texto del código
     * @throws ExcepcionContabilidad {@code CON-010} si el primer carácter no es una clase de 1 a 5; {@code CON-015} si
     *     el código está vacío, tiene caracteres que no son dígitos o su longitud no es 1, 2, 4, 6 u 8
     */
    public CodigoCuenta {
        // 1. Vacío o nulo: no hay clase que evaluar, es un código no válido
        if (valor == null || valor.isEmpty()) {
            throw ExcepcionContabilidad.codigoNoValido("El código de la cuenta es obligatorio");
        }
        // 2. La clase (primer dígito) va primero: la clase 6 u otro dígito se rechaza con CON-010
        char clase = valor.charAt(0);
        if (clase < '1' || clase > '5') {
            throw ExcepcionContabilidad.claseInvalida();
        }
        // 3. El resto debe ser dígitos ASCII (Character.isDigit aceptaría dígitos de otros alfabetos)
        for (int i = 1; i < valor.length(); i++) {
            char c = valor.charAt(i);
            if (c < '0' || c > '9') {
                throw ExcepcionContabilidad.codigoNoValido("El código de la cuenta solo admite dígitos");
            }
        }
        // 4. Longitud de un nivel válido: 1, 2, 4, 6 u 8
        if (nivelDeLongitud(valor.length()) == 0) {
            throw ExcepcionContabilidad.codigoNoValido("El código debe tener 1, 2, 4, 6 u 8 dígitos");
        }
    }

    /**
     * Crea el código (equivale al constructor; nombre expresivo para los llamadores).
     *
     * @param texto texto del código
     * @return el código válido
     */
    public static CodigoCuenta de(String texto) {
        return new CodigoCuenta(texto);
    }

    /**
     * Nivel de la cuenta por la longitud del código.
     *
     * @return 1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5 detalle
     */
    public int nivel() {
        return nivelDeLongitud(valor.length());
    }

    /**
     * Clase contable: el primer dígito del código.
     *
     * @return 1 Activo, 2 Pasivo, 3 Capital, 4 Costos y Gastos, 5 Ingresos
     */
    public int clase() {
        return valor.charAt(0) - '0';
    }

    /**
     * Naturaleza por defecto según la clase (CLAUDE.md 10.2): deudora en 1 y 4; acreedora en 2, 3 y 5.
     *
     * @return naturaleza por defecto
     */
    public NaturalezaCuenta naturalezaPorDefecto() {
        int clase = clase();
        return clase == 1 || clase == 4 ? NaturalezaCuenta.DEUDORA : NaturalezaCuenta.ACREEDORA;
    }

    /**
     * Código de la cuenta padre: el prefijo con la longitud del nivel anterior.
     *
     * @return el código esperado del padre, o vacío en una clase (nivel 1), que no tiene padre
     */
    public Optional<CodigoCuenta> codigoPadreEsperado() {
        int nivel = nivel();
        if (nivel == 1) {
            return Optional.empty();
        }
        return Optional.of(new CodigoCuenta(valor.substring(0, LONGITUD_POR_NIVEL[nivel - 2])));
    }

    /** Devuelve el nivel de una longitud, o 0 si no corresponde a ningún nivel. */
    private static int nivelDeLongitud(int longitud) {
        for (int i = 0; i < LONGITUD_POR_NIVEL.length; i++) {
            if (LONGITUD_POR_NIVEL[i] == longitud) {
                return i + 1;
            }
        }
        return 0;
    }
}
