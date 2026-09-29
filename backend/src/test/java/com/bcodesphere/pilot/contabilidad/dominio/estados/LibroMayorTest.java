package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.estados.CatalogoDePrueba.Catalogo;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link LibroMayor}: saldo inicial, saldo acumulado línea a línea y saldo final, incluida una cuenta padre cuyos
 * movimientos vienen de más de una cuenta de detalle (ADR-038 §5). Los valores esperados se calculan a mano en el
 * comentario de cada prueba.
 */
class LibroMayorTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 1, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 1, 31);

    /**
     * Cuenta padre "Efectivo y equivalentes" (1101, deudora) con movimientos de dos cuentas de detalle (Caja
     * general y Caja chica), ya mezclados y ordenados por fecha. Saldo inicial 200.00; línea 1 (05/01, Caja general,
     * Debe 500.00) → 700.00; línea 2 (10/01, Caja chica, Debe 50.00) → 750.00; línea 3 (20/01, Caja general, Haber
     * 100.00) → 650.00. Totales: Debe 550.00, Haber 100.00; saldo final 200.00 + 550.00 − 100.00 = 650.00.
     */
    @Test
    void acumulaElSaldoLineaALineaMezclandoCuentasDeDetalleDeUnPadre() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        var cajaGeneral = catalogo.detalle("11010101");
        var cajaChica = catalogo.detalle("11010102");
        var padre = catalogo.detalle("1101");
        List<MovimientoLinea> lineas = List.of(
                new MovimientoLinea(
                        LocalDate.of(2026, 1, 5),
                        UUID.randomUUID(),
                        2026,
                        1L,
                        "Depósito inicial",
                        null,
                        cajaGeneral,
                        Dinero.de("500.00"),
                        Dinero.CERO),
                new MovimientoLinea(
                        LocalDate.of(2026, 1, 10),
                        UUID.randomUUID(),
                        2026,
                        2L,
                        "Fondo de caja chica",
                        null,
                        cajaChica,
                        Dinero.de("50.00"),
                        Dinero.CERO),
                new MovimientoLinea(
                        LocalDate.of(2026, 1, 20),
                        UUID.randomUUID(),
                        2026,
                        3L,
                        "Pago de gasto",
                        null,
                        cajaGeneral,
                        Dinero.CERO,
                        Dinero.de("100.00")));

        LibroMayor mayor = LibroMayor.generar(padre, Dinero.de("200.00"), lineas, DESDE, HASTA);

        assertThat(mayor.saldoInicial().monto()).isEqualTo(Dinero.de("200.00"));
        assertThat(mayor.movimientos()).hasSize(3);
        assertThat(mayor.movimientos().get(0).saldo().monto()).isEqualTo(Dinero.de("700.00"));
        assertThat(mayor.movimientos().get(1).saldo().monto()).isEqualTo(Dinero.de("750.00"));
        assertThat(mayor.movimientos().get(2).saldo().monto()).isEqualTo(Dinero.de("650.00"));
        assertThat(mayor.totalDebe()).isEqualTo(Dinero.de("550.00"));
        assertThat(mayor.totalHaber()).isEqualTo(Dinero.de("100.00"));
        assertThat(mayor.saldoFinal().monto()).isEqualTo(Dinero.de("650.00"));
        assertThat(mayor.saldoFinal().lado()).isEqualTo(LadoSaldo.DEUDOR);
        // Cada línea conserva su propia cuenta de detalle (ADR-038 §5)
        assertThat(mayor.movimientos().get(1).linea().cuenta()).isEqualTo(cajaChica);
    }

    /** Sin líneas, el saldo final es igual al inicial y los totales quedan en cero. */
    @Test
    void sinLineasElSaldoFinalEsIgualAlInicial() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        var caja = catalogo.detalle("11010101");

        LibroMayor mayor = LibroMayor.generar(caja, Dinero.de("300.00"), List.of(), DESDE, HASTA);

        assertThat(mayor.movimientos()).isEmpty();
        assertThat(mayor.totalDebe()).isEqualTo(Dinero.CERO);
        assertThat(mayor.totalHaber()).isEqualTo(Dinero.CERO);
        assertThat(mayor.saldoFinal()).isEqualTo(mayor.saldoInicial());
    }
}
