package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.estados.CatalogoDePrueba.Catalogo;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link EstadoSituacionFinanciera}: Activo, Pasivo, Patrimonio, resultados de ejercicios anteriores no cerrados y
 * utilidad del ejercicio, con la comprobación Activo = Pasivo + Patrimonio + resultados anteriores + utilidad del
 * ejercicio (ADR-016, ADR-037). Los valores esperados se calculan a mano en el comentario de cada prueba.
 */
class EstadoSituacionFinancieraTest {

    private static final LocalDate FECHA_CORTE = LocalDate.of(2026, 6, 30);

    /**
     * Resultados de ejercicios anteriores (al 31/12/2025): ingresos 5,000.00 − gastos 3,000.00 (2,800 + 200 del
     * grupo 44) = 2,000.00. Utilidad del ejercicio: acumulado a la fecha de corte menos lo acumulado al cierre
     * anterior: ingresos (6,200.00 − 5,000.00 = 1,200.00) − gastos (3,500.00 − 3,000.00 = 500.00) = 700.00. Activo
     * 50,000.00 = Pasivo 20,000.00 + Patrimonio 27,300.00 + 2,000.00 + 700.00 = 50,000.00: cuadra.
     */
    @Test
    void formulaDeSituacionFinancieraCuadraConResultadosAnterioresYUtilidadDelEjercicio() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        Map<UUID, NetoCuenta> aFinAnioAnterior = Map.of(
                catalogo.detalle("51010101").id(), new NetoCuenta(Dinero.CERO, Dinero.de("5000.00")),
                catalogo.detalle("42020101").id(), new NetoCuenta(Dinero.de("2800.00"), Dinero.CERO),
                catalogo.detalle("44010101").id(), new NetoCuenta(Dinero.de("200.00"), Dinero.CERO));
        Map<UUID, NetoCuenta> aFechaCorte = new HashMap<>();
        aFechaCorte.put(catalogo.detalle("51010101").id(), new NetoCuenta(Dinero.CERO, Dinero.de("6200.00")));
        aFechaCorte.put(catalogo.detalle("42020101").id(), new NetoCuenta(Dinero.de("3200.00"), Dinero.CERO));
        aFechaCorte.put(catalogo.detalle("44010101").id(), new NetoCuenta(Dinero.de("300.00"), Dinero.CERO));
        aFechaCorte.put(catalogo.detalle("11010101").id(), new NetoCuenta(Dinero.de("50000.00"), Dinero.CERO));
        aFechaCorte.put(catalogo.detalle("21010101").id(), new NetoCuenta(Dinero.CERO, Dinero.de("20000.00")));
        aFechaCorte.put(catalogo.detalle("31010101").id(), new NetoCuenta(Dinero.CERO, Dinero.de("27300.00")));

        EstadoSituacionFinanciera e = EstadoSituacionFinanciera.generar(
                catalogo.todas(), aFechaCorte, aFinAnioAnterior, FECHA_CORTE, 5, false);

        assertThat(e.activo().total()).isEqualTo(Dinero.de("50000.00"));
        assertThat(e.pasivo().total()).isEqualTo(Dinero.de("20000.00"));
        assertThat(e.patrimonio().total()).isEqualTo(Dinero.de("27300.00"));
        assertThat(e.resultadosEjerciciosAnteriores()).isEqualTo(Dinero.de("2000.00"));
        assertThat(e.utilidadEjercicio()).isEqualTo(Dinero.de("700.00"));
        assertThat(e.totalPasivoPatrimonio()).isEqualTo(Dinero.de("50000.00"));
        assertThat(e.comprobacion().cuadra()).isTrue();
        assertThat(e.comprobacion().diferencia()).isEqualTo(Dinero.CERO);
        assertThat(e.leyenda()).isEqualTo(EstadoResultados.LEYENDA);
    }

    /**
     * Mismo escenario que el anterior pero con el Activo alterado en 100.00 (simula una alteración directa de
     * {@code saldo_cuenta_mensual}, sección "Alerta de descuadre" del plan F4): la comprobación debe fallar con la
     * diferencia exacta.
     */
    @Test
    void alertaConLaDiferenciaExactaCuandoNoCuadra() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        Map<UUID, NetoCuenta> aFinAnioAnterior = Map.of();
        Map<UUID, NetoCuenta> aFechaCorte =
                Map.of(catalogo.detalle("11010101").id(), new NetoCuenta(Dinero.de("100.00"), Dinero.CERO));

        EstadoSituacionFinanciera e = EstadoSituacionFinanciera.generar(
                catalogo.todas(), aFechaCorte, aFinAnioAnterior, FECHA_CORTE, 5, false);

        // Activo 100.00 sin nada del otro lado: no cuadra, la diferencia es exactamente 100.00
        assertThat(e.comprobacion().cuadra()).isFalse();
        assertThat(e.comprobacion().diferencia()).isEqualTo(Dinero.de("100.00"));
    }
}
