package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Estado de Resultados de gestión del período (CLAUDE.md 10.4, ADR-037 punto 4): ingresos (clase 5) menos costos y
 * gastos (clase 4 sin el grupo 44) da la utilidad antes de impuesto; se le resta el impuesto sobre la renta (grupo
 * 44, presentado aparte) para llegar a la utilidad del ejercicio.
 *
 * @param desde inicio del período, inclusive
 * @param hasta fin del período, inclusive
 * @param nivel nivel de la jerarquía pedido
 * @param ingresos rubro de la clase 5
 * @param costosGastos rubro de la clase 4 sin el grupo 44
 * @param utilidadAntesImpuesto ingresos menos costos y gastos
 * @param impuestoSobreRenta rubro del grupo 44 (código que empieza con {@code 44})
 * @param utilidadEjercicio utilidad antes de impuesto menos el impuesto sobre la renta
 * @param leyenda leyenda de estado de gestión (ADR-037 punto 3)
 */
public record EstadoResultados(
        LocalDate desde,
        LocalDate hasta,
        int nivel,
        RubroEstado ingresos,
        RubroEstado costosGastos,
        Dinero utilidadAntesImpuesto,
        RubroEstado impuestoSobreRenta,
        Dinero utilidadEjercicio,
        String leyenda) {

    /** Leyenda exacta de estado de gestión (ADR-037 punto 3). */
    public static final String LEYENDA =
            "Estado de gestión generado por Pilot; no constituye un juego completo de estados financieros conforme"
                    + " a NIIF para PYMES.";

    /** Prefijo del código del grupo 44 (Impuesto sobre la renta, ADR-037). */
    private static final String PREFIJO_GRUPO_44 = "44";

    /**
     * Genera el Estado de Resultados del período.
     *
     * @param catalogoCompleto todas las cuentas de la empresa
     * @param movimientosRango neto de cada cuenta dentro de {@code [desde, hasta]}
     * @param desde inicio del período, inclusive
     * @param hasta fin del período, inclusive
     * @param nivel nivel de la jerarquía pedido (1 a 5)
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @return el estado calculado
     */
    public static EstadoResultados generar(
            java.util.List<Cuenta> catalogoCompleto,
            Map<UUID, NetoCuenta> movimientosRango,
            LocalDate desde,
            LocalDate hasta,
            int nivel,
            boolean incluirCeros) {
        // 1. Ingresos: toda la clase 5
        RubroEstado ingresos = RubroEstado.construir(
                5,
                "INGRESOS",
                catalogoCompleto,
                FiltroDetalle.seleccionar(
                        catalogoCompleto, movimientosRango, c -> c.codigo().clase() == 5),
                nivel,
                incluirCeros);
        // 2. Costos y gastos: clase 4 sin el grupo 44 (ADR-037 punto 4)
        RubroEstado costosGastos = RubroEstado.construir(
                4,
                "COSTOS Y GASTOS",
                catalogoCompleto,
                FiltroDetalle.seleccionar(
                        catalogoCompleto,
                        movimientosRango,
                        c -> c.codigo().clase() == 4 && !c.codigo().valor().startsWith(PREFIJO_GRUPO_44)),
                nivel,
                incluirCeros);
        Dinero utilidadAntesImpuesto = ingresos.total().restar(costosGastos.total());
        // 3. Impuesto sobre la renta: solo el grupo 44, presentado aparte (ADR-037 punto 4)
        RubroEstado impuestoSobreRenta = RubroEstado.construir(
                4,
                "IMPUESTO SOBRE LA RENTA",
                catalogoCompleto,
                FiltroDetalle.seleccionar(
                        catalogoCompleto,
                        movimientosRango,
                        c -> c.codigo().valor().startsWith(PREFIJO_GRUPO_44)),
                nivel,
                incluirCeros);
        Dinero utilidadEjercicio = utilidadAntesImpuesto.restar(impuestoSobreRenta.total());
        return new EstadoResultados(
                desde,
                hasta,
                nivel,
                ingresos,
                costosGastos,
                utilidadAntesImpuesto,
                impuestoSobreRenta,
                utilidadEjercicio,
                LEYENDA);
    }
}
