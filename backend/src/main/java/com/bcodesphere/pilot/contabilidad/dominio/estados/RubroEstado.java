package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.util.List;
import java.util.Map;

/**
 * Rubro de un estado financiero: el total de una clase (o de un subconjunto de ella, como el grupo 44 separado del
 * resto de la clase 4, ADR-037 punto 4) con su jerarquía de cuentas hasta el nivel pedido (CLAUDE.md 10.4).
 *
 * @param clase clase contable del rubro
 * @param nombre nombre del rubro para mostrar (p. ej. "INGRESOS", "IMPUESTO SOBRE LA RENTA")
 * @param filas cuentas de nivel 2 al nivel pedido que componen el rubro, con subtotales por nivel
 * @param total suma de las cuentas de <strong>detalle</strong> del rubro (ADR-038 §6: los totales salen solo del
 *     detalle, para no contar dos veces una cuenta y sus agrupaciones)
 */
public record RubroEstado(int clase, String nombre, List<FilaEstado> filas, Dinero total) {

    /** Copia defensiva de las filas. */
    public RubroEstado {
        filas = List.copyOf(filas);
    }

    /**
     * Construye el rubro a partir de las cuentas de detalle de la clase (ya filtradas por quien llama, por ejemplo
     * para separar el grupo 44) y sus movimientos netos.
     *
     * @param clase clase contable del rubro
     * @param nombre nombre para mostrar
     * @param catalogoCompleto todas las cuentas de la empresa, para nombrar las filas de agrupación
     * @param detalle cuentas de detalle del rubro con su movimiento neto
     * @param nivel nivel pedido (1 a 5); con nivel 1 no hay filas, solo el total del rubro
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @return el rubro con sus filas y su total
     */
    public static RubroEstado construir(
            int clase,
            String nombre,
            List<Cuenta> catalogoCompleto,
            Map<Cuenta, NetoCuenta> detalle,
            int nivel,
            boolean incluirCeros) {
        // 1. El monto de cada cuenta de detalle ya en positivo según la naturaleza de la clase
        Map<Cuenta, Dinero> montoDetalle = new java.util.HashMap<>();
        for (Map.Entry<Cuenta, NetoCuenta> e : detalle.entrySet()) {
            montoDetalle.put(e.getKey(), NaturalezaClase.enPositivo(clase, e.getValue()));
        }
        // 2. El total del rubro es la suma de todo el detalle, sin importar el nivel pedido (ADR-038 §6)
        Dinero total = montoDetalle.values().stream().reduce(Dinero.CERO, Dinero::sumar);
        // 3. Las filas son la jerarquía de nivel 2 al nivel pedido; el nivel 1 (la clase) es este mismo rubro
        List<FilaEstado> filas = nivel < 2
                ? List.of()
                : Jerarquia.construir(
                                catalogoCompleto,
                                montoDetalle,
                                2,
                                nivel,
                                Dinero::sumar,
                                Dinero.CERO,
                                Dinero::esCero,
                                incluirCeros)
                        .stream()
                        .map(f -> new FilaEstado(f.cuenta(), f.nivel(), f.valor()))
                        .toList();
        return new RubroEstado(clase, nombre, filas, total);
    }
}
