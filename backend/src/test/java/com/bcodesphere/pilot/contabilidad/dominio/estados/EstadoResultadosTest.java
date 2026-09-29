package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.estados.CatalogoDePrueba.Catalogo;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link EstadoResultados}: ingresos, costos y gastos sin el grupo 44, utilidad antes de impuesto, impuesto sobre la
 * renta aparte y utilidad del ejercicio (CLAUDE.md 10.4, ADR-037 punto 4). Los valores esperados se calculan a mano
 * en el comentario de cada prueba.
 */
class EstadoResultadosTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 1, 31);

    /**
     * Ventas gravadas 1,000.00 + exentas 50.00 = ingresos 1,050.00; gasto de administración 300.00 (sin el grupo 44);
     * utilidad antes de impuesto = 1,050.00 - 300.00 = 750.00; impuesto sobre la renta 100.00 (grupo 44, aparte);
     * utilidad del ejercicio = 750.00 - 100.00 = 650.00.
     */
    @Test
    void separaElImpuestoDeLosCostosYCalculaLasDosUtilidades() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        Map<UUID, NetoCuenta> movimientos = Map.of(
                catalogo.detalle("51010101").id(), new NetoCuenta(Dinero.CERO, Dinero.de("1000.00")),
                catalogo.detalle("51010102").id(), new NetoCuenta(Dinero.CERO, Dinero.de("50.00")),
                catalogo.detalle("42020101").id(), new NetoCuenta(Dinero.de("300.00"), Dinero.CERO),
                catalogo.detalle("44010101").id(), new NetoCuenta(Dinero.de("100.00"), Dinero.CERO));

        EstadoResultados e = EstadoResultados.generar(catalogo.todas(), movimientos, DESDE, HASTA, 5, false);

        assertThat(e.ingresos().total()).isEqualTo(Dinero.de("1050.00"));
        assertThat(e.costosGastos().total()).isEqualTo(Dinero.de("300.00"));
        assertThat(e.utilidadAntesImpuesto()).isEqualTo(Dinero.de("750.00"));
        assertThat(e.impuestoSobreRenta().total()).isEqualTo(Dinero.de("100.00"));
        assertThat(e.utilidadEjercicio()).isEqualTo(Dinero.de("650.00"));
        // El grupo 44 nunca aparece dentro de costosGastos (ADR-037 punto 4)
        assertThat(e.costosGastos().filas())
                .noneMatch(f -> f.cuenta().codigo().valor().startsWith("44"));
        assertThat(e.leyenda()).isEqualTo(EstadoResultados.LEYENDA);
    }

    /** Sin movimiento alguno, ambos rubros quedan en cero y la utilidad también, sin lanzar error. */
    @Test
    void sinMovimientoTodoQuedaEnCero() {
        Catalogo catalogo = CatalogoDePrueba.construir();

        EstadoResultados e = EstadoResultados.generar(catalogo.todas(), Map.of(), DESDE, HASTA, 5, false);

        assertThat(e.ingresos().total()).isEqualTo(Dinero.CERO);
        assertThat(e.costosGastos().total()).isEqualTo(Dinero.CERO);
        assertThat(e.utilidadEjercicio()).isEqualTo(Dinero.CERO);
        assertThat(e.ingresos().filas()).isEmpty();
    }

    /** Con nivel 1 no hay filas (el nivel 1 es el propio rubro), pero el total sigue calculado desde el detalle. */
    @Test
    void nivelUnoNoTraeFilasPeroSiElTotal() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        Map<UUID, NetoCuenta> movimientos =
                Map.of(catalogo.detalle("51010101").id(), new NetoCuenta(Dinero.CERO, Dinero.de("1000.00")));

        EstadoResultados e = EstadoResultados.generar(catalogo.todas(), movimientos, DESDE, HASTA, 1, false);

        assertThat(e.ingresos().filas()).isEmpty();
        assertThat(e.ingresos().total()).isEqualTo(Dinero.de("1000.00"));
    }
}
