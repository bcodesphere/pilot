package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.CodigoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import com.bcodesphere.pilot.contabilidad.dominio.iva.ConsultaTasaIva;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Casos dorados de la expansión del IVA y de las validaciones que impiden expandir (CLAUDE.md 10.1 y 11.2): los dos
 * ejemplos de 11.2, {@code CON-002}, {@code CON-003}, {@code CON-006}, {@code CON-013} y {@code CON-017}.
 */
class ExpansorAsientoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 26);

    private final Map<UUID, Cuenta> cuentas = new HashMap<>();
    private final Cuenta caja = cuenta("11010101", "Caja general", NaturalezaCuenta.DEUDORA, true, true);
    private final Cuenta ventas = cuenta("51010101", "Ventas gravadas", NaturalezaCuenta.ACREEDORA, true, true);
    private final Cuenta compras = cuenta("41010101", "Compras", NaturalezaCuenta.DEUDORA, true, true);
    private final Cuenta ivaDebito = cuenta("21060101", "IVA débito fiscal", NaturalezaCuenta.ACREEDORA, true, true);
    private final Cuenta ivaCredito = cuenta("11060101", "IVA crédito fiscal", NaturalezaCuenta.DEUDORA, true, true);

    /** Configuración con modo CON_IVA por defecto y las dos cuentas de IVA. */
    private final ConfiguracionContable config =
            new ConfiguracionContable(ModoPrecio.CON_IVA, ivaDebito.resumen(), ivaCredito.resumen(), 0L);

    /** Doble de la tasa: 13 % vigente en cualquier fecha (dato de prueba; producción la lee de tasa_impuesto). */
    private final ConsultaTasaIva tasaVigente = fecha -> Optional.of(new BigDecimal("0.1300"));

    private Cuenta cuenta(String codigo, String nombre, NaturalezaCuenta naturaleza, boolean acepta, boolean activa) {
        Cuenta c = new Cuenta(UUID.randomUUID(), CodigoCuenta.de(codigo), nombre, null, naturaleza, acepta, activa, 0L);
        cuentas.put(c.id(), c);
        return c;
    }

    private static LineaSolicitud linea(Cuenta c, String debe, String haber, boolean llevaIva) {
        return new LineaSolicitud(c.id(), null, debe, haber, llevaIva);
    }

    private AsientoExpandido expandir(ModoPrecio modo, LineaSolicitud... lineas) {
        return ExpansorAsiento.expandir(
                new SolicitudAsiento(HOY, "Prueba", modo, List.of(lineas)), HOY, cuentas, config, tasaVigente);
    }

    private static void esperaCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run)
                .isInstanceOfSatisfying(
                        ExcepcionContabilidad.class, e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    // ------------------------------------------------------------------------------- ejemplos de CLAUDE.md 11.2

    /** Ejemplo CON_IVA de 11.2: Caja 113.00 / Ventas 113.00 con IVA → Ventas 100.00 + IVA débito 13.00 al Haber. */
    @Test
    void expandeElEjemploConIvaDeVentas() {
        AsientoExpandido e =
                expandir(ModoPrecio.CON_IVA, linea(caja, "113.00", "0", false), linea(ventas, "0", "113.00", true));

        assertThat(e.lineas()).hasSize(3);
        assertThat(e.lineas().get(0).cuenta().codigo()).isEqualTo("11010101");
        assertThat(e.lineas().get(0).debe()).isEqualTo(Dinero.de("113.00"));
        assertThat(e.lineas().get(1).cuenta().codigo()).isEqualTo("51010101");
        assertThat(e.lineas().get(1).haber()).isEqualTo(Dinero.de("100.00"));
        assertThat(e.lineas().get(1).origenLinea()).isEqualTo(OrigenLinea.USUARIO);
        LineaExpandida iva = e.lineas().get(2);
        assertThat(iva.cuenta().codigo()).isEqualTo("21060101");
        assertThat(iva.haber()).isEqualTo(Dinero.de("13.00"));
        assertThat(iva.origenLinea()).isEqualTo(OrigenLinea.IVA_CALCULADO);
        assertThat(iva.numeroLineaOrigen()).isEqualTo(2);
        assertThat(e.modoPrecio()).isEqualTo(ModoPrecio.CON_IVA);
        assertThat(e.tasaIva()).isEqualByComparingTo("0.13");
        assertThat(e.cuadra()).isTrue();
    }

    /** Ejemplo SIN_IVA de 11.2: Compras 100.00 con IVA / Caja 113.00 → Compras 100.00 + IVA crédito 13.00 al Debe. */
    @Test
    void expandeElEjemploSinIvaDeCompras() {
        AsientoExpandido e =
                expandir(ModoPrecio.SIN_IVA, linea(compras, "100.00", "0", true), linea(caja, "0", "113.00", false));

        assertThat(e.lineas()).hasSize(3);
        assertThat(e.lineas().get(0).debe()).isEqualTo(Dinero.de("100.00"));
        LineaExpandida iva = e.lineas().get(1);
        assertThat(iva.cuenta().codigo()).isEqualTo("11060101");
        assertThat(iva.debe()).isEqualTo(Dinero.de("13.00"));
        assertThat(iva.origenLinea()).isEqualTo(OrigenLinea.IVA_CALCULADO);
        assertThat(iva.numeroLineaOrigen()).isEqualTo(1);
        assertThat(e.lineas().get(2).numeroLinea()).isEqualTo(3);
        assertThat(e.cuadra()).isTrue();
        assertThat(e.modoPrecio()).isEqualTo(ModoPrecio.SIN_IVA);
    }

    /** Sin líneas con IVA no se informa modo ni tasa (ADR-036) y no se consulta la tasa. */
    @Test
    void sinIvaNoGuardaModoNiConsultaLaTasa() {
        ConsultaTasaIva sinTasa = fecha -> {
            throw new AssertionError("No debe consultarse la tasa si ninguna línea lleva IVA");
        };
        AsientoExpandido e = ExpansorAsiento.expandir(
                new SolicitudAsiento(
                        HOY,
                        "x",
                        ModoPrecio.SIN_IVA,
                        List.of(linea(caja, "10.00", "0", false), linea(ventas, "0", "10.00", false))),
                HOY,
                cuentas,
                config,
                sinTasa);

        assertThat(e.modoPrecio()).isNull();
        assertThat(e.tasaIva()).isNull();
        assertThat(e.lineas()).hasSize(2);
    }

    /** Sin modo en el cuerpo se usa el de la configuración (CON_IVA en esta prueba). */
    @Test
    void usaElModoDeLaConfiguracionSiFalta() {
        AsientoExpandido e = expandir(null, linea(caja, "113.00", "0", false), linea(ventas, "0", "113.00", true));

        assertThat(e.modoPrecio()).isEqualTo(ModoPrecio.CON_IVA);
        assertThat(e.lineas().get(1).haber()).isEqualTo(Dinero.de("100.00"));
    }

    /** Un IVA que redondea a cero no genera línea de IVA (una línea en cero viola el CHECK de un solo lado). */
    @Test
    void omiteLaLineaDeIvaCuandoRedondeaACero() {
        AsientoExpandido e =
                expandir(ModoPrecio.CON_IVA, linea(caja, "0.01", "0", false), linea(ventas, "0", "0.01", true));

        assertThat(e.lineas()).hasSize(2);
        assertThat(e.cuadra()).isTrue();
    }

    // -------------------------------------------------------------------------------------------- CON-013

    /** CON-013: «lleva IVA» sobre cualquiera de las dos cuentas de IVA se rechaza. */
    @Test
    void rechazaIvaSobreLasCuentasDeIva() {
        esperaCodigo(
                () -> expandir(null, linea(ivaDebito, "0", "10.00", true), linea(caja, "10.00", "0", false)),
                "CON-013");
        esperaCodigo(
                () -> expandir(null, linea(ivaCredito, "10.00", "0", true), linea(caja, "0", "10.00", false)),
                "CON-013");
    }

    /** Las cuentas de IVA sin la casilla sí se pueden usar (registro manual del IVA, sección 11.2). */
    @Test
    void aceptaLasCuentasDeIvaSinLaCasilla() {
        AsientoExpandido e = expandir(null, linea(ivaCredito, "13.00", "0", false), linea(caja, "0", "13.00", false));

        assertThat(e.cuadra()).isTrue();
    }

    // -------------------------------------------------------------------------------------------- CON-017

    /** CON-017: sin tasa vigente a la fecha, una línea con IVA no se puede expandir. */
    @Test
    void rechazaCuandoNoHayTasaVigente() {
        ConsultaTasaIva sinTasa = fecha -> Optional.empty();

        assertThatThrownBy(() -> ExpansorAsiento.expandir(
                        new SolicitudAsiento(
                                HOY,
                                "x",
                                null,
                                List.of(linea(caja, "113.00", "0", false), linea(ventas, "0", "113.00", true))),
                        HOY,
                        cuentas,
                        config,
                        sinTasa))
                .isInstanceOfSatisfying(ExcepcionContabilidad.class, e -> {
                    assertThat(e.codigo()).isEqualTo("CON-017");
                    assertThat(e.estadoHttp()).isEqualTo(422);
                });
    }

    // -------------------------------------------------------------------------------------------- CON-002

    /** CON-002: Debe y Haber a la vez, o ninguno de los dos. */
    @Test
    void rechazaUnaLineaConAmbosLadosOConNinguno() {
        esperaCodigo(
                () -> expandir(null, linea(caja, "10.00", "10.00", false), linea(ventas, "0", "10.00", false)),
                "CON-002");
        esperaCodigo(
                () -> expandir(null, linea(caja, "0", "0.00", false), linea(ventas, "0", "10.00", false)), "CON-002");
    }

    // -------------------------------------------------------------------------------------------- CON-003

    /** CON-003: negativo, más de 2 decimales escritos, notación científica o texto no son montos válidos. */
    @ParameterizedTest
    @ValueSource(strings = {"-1.00", "10.001", "10.500", "1e3", "abc", "", " 5", "5,00"})
    void rechazaMontosInvalidos(String monto) {
        esperaCodigo(
                () -> expandir(null, linea(caja, monto, "0", false), linea(ventas, "0", "10.00", false)), "CON-003");
        esperaCodigo(
                () -> expandir(null, linea(caja, "10.00", "0", false), linea(ventas, "0", monto, false)), "CON-003");
    }

    /** Montos válidos: enteros, con uno o dos decimales y con ceros a la izquierda. */
    @ParameterizedTest
    @ValueSource(strings = {"10", "10.5", "10.50", "0.01", "007.10", "99999999999999999.99"})
    void aceptaMontosValidos(String monto) {
        AsientoExpandido e = expandir(null, linea(caja, monto, "0", false), linea(ventas, "0", monto, false));

        assertThat(e.cuadra()).isTrue();
        assertThat(e.lineas().get(0).debe().valor().scale()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------------------------- CON-006

    /** CON-006: cuenta inexistente, de otra empresa, inactiva o padre; cada línea mala se informa por su campo. */
    @Test
    void rechazaCuentasQueNoSirvenConUnErrorPorLinea() {
        Cuenta inactiva = cuenta("11010199", "Inactiva", NaturalezaCuenta.DEUDORA, true, false);
        Cuenta padre = cuenta("1101", "Efectivo", NaturalezaCuenta.DEUDORA, false, true);
        LineaSolicitud desconocida = new LineaSolicitud(UUID.randomUUID(), null, "1.00", "0", false);

        assertThatThrownBy(() -> expandir(
                        null,
                        linea(inactiva, "1.00", "0", false),
                        linea(padre, "0", "1.00", false),
                        desconocida,
                        linea(caja, "0", "1.00", false)))
                .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                    assertThat(e.codigo()).isEqualTo("CON-006");
                    assertThat(e.errores())
                            .extracting(err -> err.campo())
                            .containsExactly("lineas[0].cuentaId", "lineas[1].cuentaId", "lineas[2].cuentaId");
                });
    }

    // -------------------------------------------------------------------------------------------- CON-007

    /** CON-007: una fecha posterior a hoy o ausente no se puede expandir. */
    @Test
    void rechazaFechasFuturasOAusentes() {
        List<LineaSolicitud> lineas = List.of(linea(caja, "1.00", "0", false), linea(ventas, "0", "1.00", false));

        esperaCodigo(
                () -> ExpansorAsiento.expandir(
                        new SolicitudAsiento(HOY.plusDays(1), "x", null, lineas), HOY, cuentas, config, tasaVigente),
                "CON-007");
        esperaCodigo(
                () -> ExpansorAsiento.expandir(
                        new SolicitudAsiento(null, "x", null, lineas), HOY, cuentas, config, tasaVigente),
                "CON-007");
    }

    // ------------------------------------------------------------------------------------------ Asiento.manual

    /** Al guardar, la línea de IVA queda enlazada por id a su línea base y la cabecera lleva los totales expandidos. */
    @Test
    void elAsientoGuardadoEnlazaLaLineaDeIvaConSuBase() {
        AsientoExpandido e =
                expandir(ModoPrecio.SIN_IVA, linea(compras, "100.00", "0", true), linea(caja, "0", "113.00", false));

        Asiento a = Asiento.manual(e, 7, java.time.Instant.parse("2026-09-26T12:00:00Z"));

        assertThat(a.numero()).isEqualTo(7);
        assertThat(a.anio()).isEqualTo(2026);
        assertThat(a.estado()).isEqualTo(EstadoAsiento.CONTABILIZADO);
        assertThat(a.origenTipo()).isEqualTo(OrigenAsiento.MANUAL);
        assertThat(a.totalDebe()).isEqualTo(Dinero.de("113.00"));
        assertThat(a.totalHaber()).isEqualTo(Dinero.de("113.00"));
        assertThat(a.modoPrecio()).isEqualTo(ModoPrecio.SIN_IVA);
        assertThat(a.lineas().get(1).lineaBaseId()).isEqualTo(a.lineas().get(0).id());
        assertThat(a.lineas().get(0).lineaBaseId()).isNull();
        assertThat(a.numeroVisible()).isEqualTo("7/2026");
    }
}
