package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Casos dorados de la partida doble y de la fecha (CLAUDE.md 10.1, tabla de códigos; ejemplo de 8.2): un asiento válido
 * y uno inválido por cada código {@code CON-001}, {@code CON-004}, {@code CON-005} y {@code CON-007}.
 */
class ReglasAsientoTest {

    private static final ResumenCuenta CAJA = new ResumenCuenta(UUID.randomUUID(), "11010101", "Caja general");
    private static final ResumenCuenta VENTAS = new ResumenCuenta(UUID.randomUUID(), "51010101", "Ventas gravadas");

    private static LineaExpandida linea(int n, String debe, String haber) {
        return new LineaExpandida(
                n, n == 1 ? CAJA : VENTAS, null, Dinero.de(debe), Dinero.de(haber), OrigenLinea.USUARIO, null);
    }

    private static void esperaCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run)
                .isInstanceOfSatisfying(
                        ExcepcionContabilidad.class, e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    /** Un asiento de dos líneas que cuadra es válido (caso válido de CON-001, CON-004 y CON-005). */
    @Test
    void aceptaUnAsientoQueCuadra() {
        assertThatCode(() ->
                        ReglasAsiento.validarPartidaDoble(List.of(linea(1, "100.00", "0"), linea(2, "0", "100.00"))))
                .doesNotThrowAnyException();
    }

    /** CON-001: una sola línea, o ninguna, no forma un asiento. */
    @Test
    void rechazaMenosDeDosLineas() {
        esperaCodigo(() -> ReglasAsiento.validarPartidaDoble(List.of(linea(1, "100.00", "0"))), "CON-001");
        esperaCodigo(() -> ReglasAsiento.validarPartidaDoble(List.of()), "CON-001");
    }

    /** CON-004: dos líneas en cero no representan ninguna operación. */
    @Test
    void rechazaTotalesEnCero() {
        esperaCodigo(
                () -> ReglasAsiento.validarPartidaDoble(List.of(linea(1, "0", "0"), linea(2, "0", "0"))), "CON-004");
    }

    /** CON-005: el descuadre informa la diferencia exacta (Debe − Haber), con signo. */
    @Test
    void rechazaUnDescuadreConLaDiferenciaExacta() {
        assertThatThrownBy(() ->
                        ReglasAsiento.validarPartidaDoble(List.of(linea(1, "100.00", "0"), linea(2, "0", "99.99"))))
                .isInstanceOfSatisfying(ExcepcionContabilidad.class, e -> {
                    assertThat(e.codigo()).isEqualTo("CON-005");
                    assertThat(e.estadoHttp()).isEqualTo(422);
                    assertThat(e.diferencia()).contains(Dinero.de("0.01"));
                });
        assertThatThrownBy(() ->
                        ReglasAsiento.validarPartidaDoble(List.of(linea(1, "50.00", "0"), linea(2, "0", "80.50"))))
                .isInstanceOfSatisfying(
                        ExcepcionContabilidad.class,
                        e -> assertThat(e.diferencia()).contains(Dinero.de("-30.50")));
    }

    /** CON-001 sobre lo capturado: dos líneas expandidas desde una sola línea con IVA no bastan (formulario, 10.1). */
    @Test
    void exigeDosLineasCapturadasAunqueLaExpansionDeMas() {
        AsientoExpandido e = new AsientoExpandido(
                LocalDate.of(2026, 9, 1),
                "x",
                null,
                null,
                List.of(linea(1, "100.00", "0"), linea(2, "0", "100.00")),
                1);

        esperaCodigo(() -> ReglasAsiento.validar(e), "CON-001");
    }

    /** CON-007: la fecha de hoy y las pasadas valen; una futura o la ausencia de fecha no. */
    @Test
    void validaLaFechaContraHoy() {
        LocalDate hoy = LocalDate.of(2026, 9, 26);

        assertThatCode(() -> ReglasAsiento.validarFecha(hoy, hoy)).doesNotThrowAnyException();
        assertThatCode(() -> ReglasAsiento.validarFecha(hoy.minusYears(1), hoy)).doesNotThrowAnyException();
        esperaCodigo(() -> ReglasAsiento.validarFecha(hoy.plusDays(1), hoy), "CON-007");
        esperaCodigo(() -> ReglasAsiento.validarFecha(null, hoy), "CON-007");
    }

    /** Los totales y la bandera cuadra de la vista previa salen de las líneas expandidas (ADR-036). */
    @Test
    void laVistaPreviaCalculaTotalesDiferenciaYCuadra() {
        AsientoExpandido descuadrado = new AsientoExpandido(
                LocalDate.of(2026, 9, 1), "x", null, null, List.of(linea(1, "100.00", "0"), linea(2, "0", "60.00")), 2);
        AsientoExpandido cuadrado = new AsientoExpandido(
                LocalDate.of(2026, 9, 1),
                "x",
                null,
                null,
                List.of(linea(1, "100.00", "0"), linea(2, "0", "100.00")),
                2);

        assertThat(descuadrado.totalDebe()).isEqualTo(Dinero.de("100.00"));
        assertThat(descuadrado.totalHaber()).isEqualTo(Dinero.de("60.00"));
        assertThat(descuadrado.diferencia()).isEqualTo(Dinero.de("40.00"));
        assertThat(descuadrado.cuadra()).isFalse();
        assertThat(cuadrado.cuadra()).isTrue();
    }
}
