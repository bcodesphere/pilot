package com.bcodesphere.pilot.contabilidad.dominio.estados;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado de Situación Financiera de gestión a una fecha de corte (CLAUDE.md 10.4, ADR-016, ADR-037): Activo, Pasivo,
 * Patrimonio, los resultados de ejercicios anteriores no cerrados y la utilidad del ejercicio en curso, con la
 * comprobación Activo = Pasivo + Patrimonio + resultados anteriores + utilidad del ejercicio. Como 1.0 no tiene
 * cierre contable, las clases 4 y 5 nunca se trasladan al Patrimonio (ADR-016): por eso se calculan aparte y se
 * suman a la ecuación.
 *
 * @param fechaCorte fecha de corte del estado
 * @param nivel nivel de la jerarquía pedido
 * @param activo rubro de la clase 1
 * @param pasivo rubro de la clase 2
 * @param patrimonio rubro de la clase 3
 * @param resultadosEjerciciosAnteriores utilidad (5 − 4) acumulada desde el primer movimiento hasta el 31/12 del
 *     año anterior al corte
 * @param utilidadEjercicio utilidad (5 − 4) del 1/1 del año del corte a la fecha de corte, después de impuesto
 *     (ADR-037 punto 4: usa la misma utilidad después de impuesto del Estado de Resultados, porque restar el grupo
 *     44 aparte y luego sumarlo de vuelta a la clase 4 completa da el mismo resultado que usar la clase 4 entera)
 * @param totalPasivoPatrimonio Pasivo + Patrimonio + resultados anteriores + utilidad del ejercicio
 * @param comprobacion Activo comparado contra {@code totalPasivoPatrimonio}
 * @param leyenda leyenda de estado de gestión (ADR-037 punto 3)
 */
public record EstadoSituacionFinanciera(
        LocalDate fechaCorte,
        int nivel,
        RubroEstado activo,
        RubroEstado pasivo,
        RubroEstado patrimonio,
        Dinero resultadosEjerciciosAnteriores,
        Dinero utilidadEjercicio,
        Dinero totalPasivoPatrimonio,
        ComprobacionSituacionFinanciera comprobacion,
        String leyenda) {

    /**
     * Genera el Estado de Situación Financiera a la fecha de corte.
     *
     * @param catalogoCompleto todas las cuentas de la empresa
     * @param acumuladoAFechaCorte neto acumulado de cada cuenta desde el primer movimiento hasta la fecha de corte
     *     (CLAUDE.md 10.3: meses completos de {@code saldo_cuenta_mensual} más el mes parcial)
     * @param acumuladoAFinAnioAnterior el mismo acumulado, pero hasta el 31/12 del año anterior al corte
     * @param fechaCorte fecha de corte
     * @param nivel nivel de la jerarquía pedido (1 a 5)
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @return el estado calculado, con su comprobación
     */
    public static EstadoSituacionFinanciera generar(
            List<Cuenta> catalogoCompleto,
            Map<UUID, NetoCuenta> acumuladoAFechaCorte,
            Map<UUID, NetoCuenta> acumuladoAFinAnioAnterior,
            LocalDate fechaCorte,
            int nivel,
            boolean incluirCeros) {
        // 1. Activo, Pasivo y Patrimonio: saldo acumulado a la fecha de corte, cada uno de su propia clase
        RubroEstado activo = RubroEstado.construir(
                1,
                "ACTIVO",
                catalogoCompleto,
                FiltroDetalle.seleccionar(
                        catalogoCompleto, acumuladoAFechaCorte, c -> c.codigo().clase() == 1),
                nivel,
                incluirCeros);
        RubroEstado pasivo = RubroEstado.construir(
                2,
                "PASIVO",
                catalogoCompleto,
                FiltroDetalle.seleccionar(
                        catalogoCompleto, acumuladoAFechaCorte, c -> c.codigo().clase() == 2),
                nivel,
                incluirCeros);
        RubroEstado patrimonio = RubroEstado.construir(
                3,
                "PATRIMONIO",
                catalogoCompleto,
                FiltroDetalle.seleccionar(
                        catalogoCompleto, acumuladoAFechaCorte, c -> c.codigo().clase() == 3),
                nivel,
                incluirCeros);
        // 2. Resultados de ejercicios anteriores: utilidad (5 - 4) acumulada hasta el cierre del año anterior
        Dinero resultadosAnteriores = totalClase(catalogoCompleto, acumuladoAFinAnioAnterior, 5)
                .restar(totalClase(catalogoCompleto, acumuladoAFinAnioAnterior, 4));
        // 3. Utilidad del ejercicio: la diferencia entre lo acumulado a la fecha de corte y lo acumulado al cierre
        //    del año anterior, para las clases 5 y 4 (equivale a la utilidad después de impuesto del período)
        Dinero utilidadEjercicio = totalClase(catalogoCompleto, acumuladoAFechaCorte, 5)
                .restar(totalClase(catalogoCompleto, acumuladoAFechaCorte, 4))
                .restar(resultadosAnteriores);
        Dinero totalPasivoPatrimonio = pasivo.total()
                .sumar(patrimonio.total())
                .sumar(resultadosAnteriores)
                .sumar(utilidadEjercicio);
        ComprobacionSituacionFinanciera comprobacion =
                ComprobacionSituacionFinanciera.de(activo.total(), totalPasivoPatrimonio);
        return new EstadoSituacionFinanciera(
                fechaCorte,
                nivel,
                activo,
                pasivo,
                patrimonio,
                resultadosAnteriores,
                utilidadEjercicio,
                totalPasivoPatrimonio,
                comprobacion,
                EstadoResultados.LEYENDA);
    }

    /** Total en positivo (según la naturaleza de la clase) de todas las cuentas de detalle de una clase. */
    private static Dinero totalClase(List<Cuenta> catalogoCompleto, Map<UUID, NetoCuenta> movimientos, int clase) {
        Dinero total = Dinero.CERO;
        for (Cuenta c : catalogoCompleto) {
            if (c.aceptaMovimientos() && c.codigo().clase() == clase) {
                total = total.sumar(
                        NaturalezaClase.enPositivo(clase, movimientos.getOrDefault(c.id(), NetoCuenta.CERO)));
            }
        }
        return total;
    }
}
