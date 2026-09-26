package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de las reglas del catálogo con un doble del puerto de movimientos. Fuente: CLAUDE.md 10.2 y ADR-035
 * (CON-011, CON-012, CON-015 y CON-016). La prueba con movimientos reales queda para F3 (ADR-035, decisión 5).
 */
class ReglasCatalogoTest {

    /** Doble del puerto: movimientos y saldos configurables por cuenta. */
    private static final class MovimientosDoble implements ConsultaMovimientosCuenta {
        final Map<UUID, BigDecimal> saldos = new HashMap<>();
        final Map<UUID, Boolean> conMovimientos = new HashMap<>();

        @Override
        public boolean tieneMovimientos(UUID cuentaId) {
            return conMovimientos.getOrDefault(cuentaId, false);
        }

        @Override
        public BigDecimal saldo(UUID cuentaId) {
            return saldos.getOrDefault(cuentaId, new BigDecimal("0.00"));
        }
    }

    private final MovimientosDoble movimientos = new MovimientosDoble();

    private static Cuenta cuenta(String codigo, boolean acepta, boolean activa) {
        return new Cuenta(
                UUID.randomUUID(),
                CodigoCuenta.de(codigo),
                "Cuenta " + codigo,
                null,
                NaturalezaCuenta.DEUDORA,
                acepta,
                activa,
                0L);
    }

    private static void esperaCodigo(Runnable accion, String codigo) {
        assertThatThrownBy(accion::run)
                .isInstanceOfSatisfying(
                        ExcepcionContabilidad.class, e -> assertThat(e.codigo()).isEqualTo(codigo));
    }

    // ------------------------------------------------------------------------------------------------ alta

    /** Una clase (nivel 1) no tiene padre: el alta procede sin más. */
    @Test
    void unaClaseSeCreaSinPadre() {
        assertThatCode(() -> ReglasCatalogo.validarAlta(CodigoCuenta.de("1"), null, false, movimientos))
                .doesNotThrowAnyException();
    }

    /** Alta válida: padre activo, con el prefijo esperado, sin movimientos y fuera de uso. */
    @Test
    void unaHijaBajoUnPadreActivoSeAcepta() {
        Cuenta padre = cuenta("110101", false, true);
        assertThatCode(() -> ReglasCatalogo.validarAlta(CodigoCuenta.de("11010104"), padre, false, movimientos))
                .doesNotThrowAnyException();
    }

    /** CON-015: el padre no existe (nulo), está inactivo o su código no es el prefijo esperado. */
    @Test
    void unPadreInexistenteInactivoOQueNoEsPrefijoDaCon015() {
        CodigoCuenta hija = CodigoCuenta.de("11010104");
        esperaCodigo(() -> ReglasCatalogo.validarAlta(hija, null, false, movimientos), "CON-015");
        esperaCodigo(
                () -> ReglasCatalogo.validarAlta(hija, cuenta("110101", false, false), false, movimientos), "CON-015");
        // 110201 no es el prefijo de 11010104 (que espera 110101)
        esperaCodigo(
                () -> ReglasCatalogo.validarAlta(hija, cuenta("110201", false, true), false, movimientos), "CON-015");
    }

    /** CON-011: no se crean hijas bajo una cuenta con movimientos. */
    @Test
    void unPadreConMovimientosDaCon011() {
        Cuenta padre = cuenta("110102", true, true);
        movimientos.conMovimientos.put(padre.id(), true);
        esperaCodigo(
                () -> ReglasCatalogo.validarAlta(CodigoCuenta.de("11010201"), padre, false, movimientos), "CON-011");
    }

    /** CON-016: al crear la hija, un padre hoja usado por la configuración o una regla activa dejaría de ser de detalle. */
    @Test
    void unPadreHojaEnUsoDaCon016PeroUnPadreQueYaNoEsHojaNo() {
        Cuenta hoja = cuenta("110102", true, true);
        esperaCodigo(() -> ReglasCatalogo.validarAlta(CodigoCuenta.de("11010201"), hoja, true, movimientos), "CON-016");

        // Un padre que ya no acepta movimientos no cambia de estado, así que el uso no importa
        Cuenta yaPadre = cuenta("110101", false, true);
        assertThatCode(() -> ReglasCatalogo.validarAlta(CodigoCuenta.de("11010104"), yaPadre, true, movimientos))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------------------------ cambio de código

    /** Cambio válido: misma longitud, mismo padre (110101), sin hijas y sin movimientos. */
    @Test
    void cambiarElCodigoConElMismoNivelYPadreSeAcepta() {
        Cuenta actual = cuenta("11010104", true, true);
        assertThatCode(() ->
                        ReglasCatalogo.validarCambioCodigo(actual, CodigoCuenta.de("11010109"), false, movimientos))
                .doesNotThrowAnyException();
    }

    /** CON-015: otra longitud o otro padre reordenaría el árbol (decisión conservadora de F2-03). */
    @Test
    void cambiarDeNivelOdePadreDaCon015() {
        Cuenta actual = cuenta("11010104", true, true);
        // Otro nivel (6 dígitos)
        esperaCodigo(
                () -> ReglasCatalogo.validarCambioCodigo(actual, CodigoCuenta.de("110109"), false, movimientos),
                "CON-015");
        // Mismo nivel pero otro padre (110201)
        esperaCodigo(
                () -> ReglasCatalogo.validarCambioCodigo(actual, CodigoCuenta.de("11020104"), false, movimientos),
                "CON-015");
    }

    /** CON-015: con hijas, el cambio arrastraría el prefijo de todas ellas. */
    @Test
    void cambiarElCodigoDeUnaCuentaConHijasDaCon015() {
        Cuenta actual = cuenta("110102", false, true);
        esperaCodigo(
                () -> ReglasCatalogo.validarCambioCodigo(actual, CodigoCuenta.de("110103"), true, movimientos),
                "CON-015");
    }

    /** CON-011: con movimientos no se cambia el código (ADR-035, decisión 5: probado con el doble del puerto). */
    @Test
    void cambiarElCodigoConMovimientosDaCon011() {
        Cuenta actual = cuenta("11010104", true, true);
        movimientos.conMovimientos.put(actual.id(), true);
        esperaCodigo(
                () -> ReglasCatalogo.validarCambioCodigo(actual, CodigoCuenta.de("11010109"), false, movimientos),
                "CON-011");
    }

    // ------------------------------------------------------------------------------------- desactivación

    /** Una cuenta sin saldo y fuera de uso se desactiva sin error. */
    @Test
    void desactivarUnaCuentaSinSaldoNiUsoSeAcepta() {
        assertThatCode(() -> ReglasCatalogo.validarDesactivacion(cuenta("11010104", true, true), false, movimientos))
                .doesNotThrowAnyException();
    }

    /** CON-012: un saldo distinto de cero impide desactivar; el saldo 0.00 (escala 2) no lo impide. */
    @Test
    void desactivarConSaldoDaCon012() {
        Cuenta actual = cuenta("11010104", true, true);
        movimientos.saldos.put(actual.id(), new BigDecimal("0.01"));
        esperaCodigo(() -> ReglasCatalogo.validarDesactivacion(actual, false, movimientos), "CON-012");

        movimientos.saldos.put(actual.id(), new BigDecimal("-25.50"));
        esperaCodigo(() -> ReglasCatalogo.validarDesactivacion(actual, false, movimientos), "CON-012");

        movimientos.saldos.put(actual.id(), new BigDecimal("0.00"));
        assertThatCode(() -> ReglasCatalogo.validarDesactivacion(actual, false, movimientos))
                .doesNotThrowAnyException();
    }

    /** CON-016: en uso por la configuración o una regla activa no se desactiva. */
    @Test
    void desactivarUnaCuentaEnUsoDaCon016() {
        esperaCodigo(
                () -> ReglasCatalogo.validarDesactivacion(cuenta("11010101", true, true), true, movimientos),
                "CON-016");
    }

    // ---------------------------------------------------------------------------------- cuenta imputable

    /** CON-006: nula (no existe o es de otra empresa), inactiva o que no es de detalle se rechaza, con el campo. */
    @Test
    void soloUnaCuentaActivaYDeDetalleEsImputable() {
        assertThatCode(() -> ReglasCatalogo.exigirImputable(cuenta("11010101", true, true), "cuentaId"))
                .doesNotThrowAnyException();
        for (Cuenta mala : new Cuenta[] {null, cuenta("11010101", true, false), cuenta("110101", false, true)}) {
            assertThatThrownBy(() -> ReglasCatalogo.exigirImputable(mala, "cuentaId"))
                    .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                        assertThat(e.codigo()).isEqualTo("CON-006");
                        assertThat(e.estadoHttp()).isEqualTo(422);
                        assertThat(e.errores()).extracting(ErrorCampo::campo).containsExactly("cuentaId");
                    });
        }
    }

    /** Si fallan varias cuentas, CON-006 lleva una entrada por campo (configuración: débito y crédito). */
    @Test
    void variasCuentasInvalidasDanUnaEntradaPorCampo() {
        var errores = new java.util.ArrayList<ErrorCampo>();
        ReglasCatalogo.comprobarImputable(null, "cuentaIvaDebitoId").ifPresent(errores::add);
        ReglasCatalogo.comprobarImputable(cuenta("11010101", true, true), "x").ifPresent(errores::add);
        ReglasCatalogo.comprobarImputable(null, "cuentaIvaCreditoId").ifPresent(errores::add);

        assertThatThrownBy(() -> ReglasCatalogo.exigirImputables(errores))
                .isInstanceOfSatisfying(
                        ExcepcionValidacion.class,
                        e -> assertThat(e.errores())
                                .extracting(ErrorCampo::campo)
                                .containsExactly("cuentaIvaDebitoId", "cuentaIvaCreditoId"));
        assertThatCode(() -> ReglasCatalogo.exigirImputables(java.util.List.of()))
                .doesNotThrowAnyException();
    }
}
