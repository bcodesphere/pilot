package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Casos dorados de la reversión (CLAUDE.md 10.1, ADR-019, ADR-036): intercambio de lados, enlaces de IVA, y los
 * rechazos {@code CON-007}, {@code CON-008}, {@code CON-009} y {@code CON-018}.
 */
class ReglasReversionTest {

    private static final LocalDate FECHA_ORIGINAL = LocalDate.of(2026, 9, 10);
    private static final LocalDate HOY = LocalDate.of(2026, 9, 26);
    private static final Instant AHORA = Instant.parse("2026-09-26T12:00:00Z");

    private static final ResumenCuenta CAJA = new ResumenCuenta(UUID.randomUUID(), "11010101", "Caja general");
    private static final ResumenCuenta VENTAS = new ResumenCuenta(UUID.randomUUID(), "51010101", "Ventas");
    private static final ResumenCuenta IVA = new ResumenCuenta(UUID.randomUUID(), "21060101", "IVA débito");

    /** Asiento CON_IVA de 11.2: Caja 113 D / Ventas 100 H / IVA 13 H (la línea de IVA enlazada a Ventas). */
    private static Asiento original(EstadoAsiento estado, OrigenAsiento origen) {
        UUID idVentas = UUID.randomUUID();
        return new Asiento(
                UUID.randomUUID(),
                2026,
                15,
                FECHA_ORIGINAL,
                "Venta",
                estado,
                origen,
                null,
                ModoPrecio.CON_IVA,
                null,
                null,
                Dinero.de("113.00"),
                Dinero.de("113.00"),
                AHORA,
                List.of(
                        new LineaAsiento(
                                UUID.randomUUID(),
                                1,
                                CAJA,
                                "Cobro",
                                Dinero.de("113.00"),
                                Dinero.CERO,
                                OrigenLinea.USUARIO,
                                null),
                        new LineaAsiento(
                                idVentas, 2, VENTAS, null, Dinero.CERO, Dinero.de("100.00"), OrigenLinea.USUARIO, null),
                        new LineaAsiento(
                                UUID.randomUUID(),
                                3,
                                IVA,
                                null,
                                Dinero.CERO,
                                Dinero.de("13.00"),
                                OrigenLinea.IVA_CALCULADO,
                                idVentas)));
    }

    private static void esperaCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run)
                .isInstanceOfSatisfying(
                        ExcepcionContabilidad.class, e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    /** La reversión intercambia Debe y Haber, conserva cuentas y descripciones y enlaza las líneas de IVA. */
    @Test
    void armaElContraAsientoConLosLadosIntercambiados() {
        Asiento o = original(EstadoAsiento.CONTABILIZADO, OrigenAsiento.MANUAL);

        Asiento r = ReglasReversion.armar(o, HOY, 3, AHORA);

        assertThat(r.origenTipo()).isEqualTo(OrigenAsiento.REVERSION);
        assertThat(r.estado()).isEqualTo(EstadoAsiento.CONTABILIZADO);
        assertThat(r.asientoRevertidoId()).isEqualTo(o.id());
        assertThat(r.concepto()).isEqualTo("Reversión del asiento N.º 15/2026");
        assertThat(r.fecha()).isEqualTo(HOY);
        assertThat(r.numero()).isEqualTo(3);
        assertThat(r.lineas()).hasSize(3);
        assertThat(r.lineas().get(0).haber()).isEqualTo(Dinero.de("113.00"));
        assertThat(r.lineas().get(0).debe()).isEqualTo(Dinero.CERO);
        assertThat(r.lineas().get(0).descripcion()).isEqualTo("Cobro");
        assertThat(r.lineas().get(1).debe()).isEqualTo(Dinero.de("100.00"));
        assertThat(r.lineas().get(2).debe()).isEqualTo(Dinero.de("13.00"));
        assertThat(r.lineas().get(2).origenLinea()).isEqualTo(OrigenLinea.IVA_CALCULADO);
        // El enlace apunta a la línea base NUEVA (no a la del original) y los ids son todos distintos
        assertThat(r.lineas().get(2).lineaBaseId()).isEqualTo(r.lineas().get(1).id());
        assertThat(r.lineas().get(1).id()).isNotEqualTo(o.lineas().get(1).id());
        assertThat(r.totalDebe()).isEqualTo(Dinero.de("113.00"));
        assertThat(r.totalHaber()).isEqualTo(Dinero.de("113.00"));
    }

    /** Un asiento vigente se puede revertir el mismo día del original, y en una fecha posterior o de hoy. */
    @Test
    void aceptaFechasDesdeLaDelOriginalHastaHoy() {
        Asiento o = original(EstadoAsiento.CONTABILIZADO, OrigenAsiento.MANUAL);

        assertThatCode(() -> ReglasReversion.validar(o, FECHA_ORIGINAL, HOY)).doesNotThrowAnyException();
        assertThatCode(() -> ReglasReversion.validar(o, HOY, HOY)).doesNotThrowAnyException();
    }

    /** CON-008: un asiento ya revertido no se revierte otra vez. */
    @Test
    void rechazaUnAsientoYaRevertido() {
        esperaCodigo(
                () -> ReglasReversion.validar(original(EstadoAsiento.REVERTIDO, OrigenAsiento.MANUAL), HOY, HOY),
                "CON-008");
    }

    /** CON-009: una reversión no se revierte (aunque estuviera vigente). */
    @Test
    void rechazaRevertirUnaReversion() {
        esperaCodigo(
                () -> ReglasReversion.validar(original(EstadoAsiento.CONTABILIZADO, OrigenAsiento.REVERSION), HOY, HOY),
                "CON-009");
    }

    /** CON-007: la fecha de la reversión no puede ser futura. */
    @Test
    void rechazaUnaFechaFutura() {
        esperaCodigo(
                () -> ReglasReversion.validar(
                        original(EstadoAsiento.CONTABILIZADO, OrigenAsiento.MANUAL), HOY.plusDays(1), HOY),
                "CON-007");
    }

    /** CON-018: la fecha no puede ser anterior a la del asiento original. */
    @Test
    void rechazaUnaFechaAnteriorALaDelOriginal() {
        esperaCodigo(
                () -> ReglasReversion.validar(
                        original(EstadoAsiento.CONTABILIZADO, OrigenAsiento.MANUAL), FECHA_ORIGINAL.minusDays(1), HOY),
                "CON-018");
    }
}
