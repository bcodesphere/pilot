package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

/**
 * Arma la jerarquía de cuentas por prefijo de código hasta un nivel, con subtotales (CLAUDE.md 10.4, ADR-038 §6 y
 * §7: "una fila por cuenta ... hasta el nivel pedido" y "con subtotales por nivel"). Es lógica pura: recibe los
 * valores ya calculados de las cuentas de detalle y el catálogo completo (para nombrar las filas de agrupación) y
 * agrega hacia arriba por el prefijo del código, sin tocar la base de datos.
 *
 * @param <V> tipo del valor agregado por cuenta (un {@link com.bcodesphere.pilot.compartido.Dinero} para los
 *     estados, o una tupla de Debe/Haber/saldo para la Balanza)
 */
public final class Jerarquia {

    /** Longitud del código en cada nivel (CLAUDE.md 10.2): índice 0 = nivel 1 (clase) ... índice 4 = nivel 5 (detalle). */
    private static final int[] LONGITUD_POR_NIVEL = {1, 2, 4, 6, 8};

    private Jerarquia() {}

    /**
     * Una fila de la jerarquía: la cuenta de agrupación (de cualquier nivel) y su valor agregado.
     *
     * @param <V> tipo del valor agregado
     * @param cuenta cuenta de agrupación (existe siempre en el catálogo porque todo código tiene un padre válido)
     * @param valor suma de las cuentas de detalle descendientes
     */
    public record Fila<V>(Cuenta cuenta, V valor) {

        /** Nivel de la fila, derivado del código de su cuenta. */
        public int nivel() {
            return cuenta.codigo().nivel();
        }
    }

    /**
     * Agrupa los valores de las cuentas de detalle por el prefijo de su código, para cada nivel entre
     * {@code nivelDesde} y {@code nivelHasta}, y arma una fila por cada grupo con valor.
     *
     * @param <V> tipo del valor agregado
     * @param catalogoCompleto todas las cuentas de la empresa (de cualquier nivel), para nombrar las filas de grupo
     * @param valoresDetalle valor calculado por cada cuenta de detalle considerada (ya filtrada por clase u otro
     *     criterio de quien llama)
     * @param nivelDesde primer nivel a incluir (1 a 5)
     * @param nivelHasta último nivel a incluir, inclusive (CLAUDE.md 10.2: 1 clase … 5 detalle)
     * @param sumar combina dos valores del mismo grupo
     * @param cero valor neutro de la suma
     * @param esCero indica si un valor agregado equivale a "sin saldo ni movimiento", para omitir la fila
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @return las filas, ordenadas por código (un padre siempre antecede a sus hijas por ser su prefijo)
     */
    public static <V> List<Fila<V>> construir(
            List<Cuenta> catalogoCompleto,
            Map<Cuenta, V> valoresDetalle,
            int nivelDesde,
            int nivelHasta,
            BinaryOperator<V> sumar,
            V cero,
            Predicate<V> esCero,
            boolean incluirCeros) {
        // 1. Índice del catálogo completo por código, para resolver el nombre de cada grupo
        Map<String, Cuenta> porCodigo = new HashMap<>();
        for (Cuenta c : catalogoCompleto) {
            porCodigo.put(c.codigo().valor(), c);
        }

        List<Fila<V>> filas = new ArrayList<>();
        for (int nivel = nivelDesde; nivel <= nivelHasta; nivel++) {
            int longitud = LONGITUD_POR_NIVEL[nivel - 1];
            // 2. Suma el valor de cada cuenta de detalle en el grupo de su código truncado a la longitud del nivel
            Map<String, V> acumulado = new HashMap<>();
            for (Map.Entry<Cuenta, V> e : valoresDetalle.entrySet()) {
                String codigoDetalle = e.getKey().codigo().valor();
                if (codigoDetalle.length() < longitud) {
                    // La cuenta de detalle no llega a este nivel (no debería ocurrir: el detalle siempre es nivel 5)
                    continue;
                }
                String prefijo = codigoDetalle.substring(0, longitud);
                acumulado.merge(prefijo, e.getValue(), sumar);
            }
            // 3. Arma una fila por grupo con valor, resolviendo la cuenta del catálogo completo
            for (Map.Entry<String, V> e : acumulado.entrySet()) {
                if (!incluirCeros && esCero.test(e.getValue())) {
                    continue;
                }
                Cuenta cuenta = porCodigo.get(e.getKey());
                if (cuenta == null) {
                    // Invariante del catálogo (CON-015): el código de un nivel superior siempre existe como cuenta
                    throw new IllegalStateException("No existe la cuenta de agrupación con código " + e.getKey());
                }
                filas.add(new Fila<>(cuenta, e.getValue()));
            }
        }
        filas.sort(Comparator.comparing(f -> f.cuenta().codigo().valor()));
        return filas;
    }
}
