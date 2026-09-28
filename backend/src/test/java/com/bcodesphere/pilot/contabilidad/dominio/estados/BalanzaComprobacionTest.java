package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.estados.CatalogoDePrueba.Catalogo;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link BalanzaComprobacion}: saldo inicial, movimientos y saldo final por cuenta, con los totales calculados solo
 * desde el detalle (ADR-038 §6). Los valores esperados se calculan a mano en el comentario de cada prueba.
 */
class BalanzaComprobacionTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 3, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 3, 31);

    /**
     * Caja (Deudora): saldo inicial 1,000.00, Debe 500.00, Haber 200.00 → saldo final 1,300.00 (Deudor). Proveedores
     * (Acreedora): saldo inicial -1,000.00 (Acreedor 1,000.00), Debe 200.00, Haber 500.00 → saldo final -1,300.00
     * (Acreedor 1,300.00). Σ Debe = Σ Haber = 700.00 y Σ saldos deudores = Σ saldos acreedores = 1,300.00: cuadra.
     * El grupo "11" (código de nivel 2) agrega la única cuenta de detalle que tiene debajo (Caja) sin duplicar el
     * total, que sigue saliendo solo del detalle.
     */
    @Test
    void totalesCuadranYSalenSoloDelDetalle() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        Cuenta caja = catalogo.detalle("11010101");
        Cuenta proveedores = catalogo.detalle("21010101");
        Map<UUID, NetoCuenta> saldoInicial = Map.of(
                caja.id(), new NetoCuenta(Dinero.de("1000.00"), Dinero.CERO),
                proveedores.id(), new NetoCuenta(Dinero.CERO, Dinero.de("1000.00")));
        Map<UUID, NetoCuenta> movimientos = Map.of(
                caja.id(), new NetoCuenta(Dinero.de("500.00"), Dinero.de("200.00")),
                proveedores.id(), new NetoCuenta(Dinero.de("200.00"), Dinero.de("500.00")));

        BalanzaComprobacion b =
                BalanzaComprobacion.generar(catalogo.todas(), saldoInicial, movimientos, DESDE, HASTA, 5);

        assertThat(b.totalDebe()).isEqualTo(Dinero.de("700.00"));
        assertThat(b.totalHaber()).isEqualTo(Dinero.de("700.00"));
        assertThat(b.totalSaldosDeudores()).isEqualTo(Dinero.de("1300.00"));
        assertThat(b.totalSaldosAcreedores()).isEqualTo(Dinero.de("1300.00"));
        assertThat(b.cuadra()).isTrue();

        // La fila de Caja (nivel 5) y su grupo "11" (nivel 2) muestran el mismo saldo final, porque Caja es la
        // única cuenta de detalle bajo "11"; el total de la balanza no se duplicó por incluir ambas filas.
        var filaCaja = b.filas().stream()
                .filter(f -> f.cuenta().codigo().valor().equals("11010101"))
                .findFirst()
                .orElseThrow();
        var filaGrupo = b.filas().stream()
                .filter(f -> f.cuenta().codigo().valor().equals("11"))
                .findFirst()
                .orElseThrow();
        assertThat(filaCaja.esDetalle()).isTrue();
        assertThat(filaGrupo.esDetalle()).isFalse();
        assertThat(filaGrupo.saldoFinal().monto())
                .isEqualTo(filaCaja.saldoFinal().monto());
        assertThat(filaCaja.saldoFinal().monto()).isEqualTo(Dinero.de("1300.00"));
    }

    /** Una cuenta sin saldo inicial ni movimiento no genera fila (ADR-038 §6: "con saldo o movimiento"). */
    @Test
    void cuentaSinSaldoNiMovimientoNoApareceComoFila() {
        Catalogo catalogo = CatalogoDePrueba.construir();

        BalanzaComprobacion b = BalanzaComprobacion.generar(catalogo.todas(), Map.of(), Map.of(), DESDE, HASTA, 5);

        assertThat(b.filas()).isEmpty();
        assertThat(b.totalDebe()).isEqualTo(Dinero.CERO);
        assertThat(b.cuadra()).isTrue();
    }

    /** Una cuenta deudora (Caja) con saldo final Acreedor dispara la alerta de {@code contrarioNaturaleza}. */
    @Test
    void saldoContrarioALaNaturalezaSeMarcaEnLaFila() {
        Catalogo catalogo = CatalogoDePrueba.construir();
        Cuenta caja = catalogo.detalle("11010101");
        Map<UUID, NetoCuenta> saldoInicial = Map.of(caja.id(), new NetoCuenta(Dinero.CERO, Dinero.de("50.00")));

        BalanzaComprobacion b = BalanzaComprobacion.generar(catalogo.todas(), saldoInicial, Map.of(), DESDE, HASTA, 5);

        var filaCaja = b.filas().stream()
                .filter(f -> f.cuenta().codigo().valor().equals("11010101"))
                .findFirst()
                .orElseThrow();
        assertThat(filaCaja.saldoInicial().lado()).isEqualTo(LadoSaldo.ACREEDOR);
        assertThat(filaCaja.saldoInicial().contrarioNaturaleza()).isTrue();
    }
}
