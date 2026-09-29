package com.bcodesphere.pilot.contabilidad.dominio.estados;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;
import org.junit.jupiter.api.Test;

/**
 * {@link Saldo}: signo, lado y alerta de {@code contrarioNaturaleza} (CLAUDE.md 10.3). Los valores esperados se
 * calculan a mano en cada prueba.
 */
class SaldoTest {

    /** Debe − Haber = 100.00 (positivo) en una cuenta deudora: Deudor, sin contradicción. */
    @Test
    void debeMayorQueHaberEsDeudorSinContradiccion() {
        Saldo s = Saldo.de(Dinero.de("100.00"), NaturalezaCuenta.DEUDORA);

        assertThat(s.lado()).isEqualTo(LadoSaldo.DEUDOR);
        assertThat(s.monto()).isEqualTo(Dinero.de("100.00"));
        assertThat(s.contrarioNaturaleza()).isFalse();
    }

    /** Debe − Haber = -50.00 en una cuenta acreedora: Acreedor, sin contradicción; el monto se presenta positivo. */
    @Test
    void haberMayorQueDebeEsAcreedorSinContradiccion() {
        Saldo s = Saldo.de(Dinero.de("-50.00"), NaturalezaCuenta.ACREEDORA);

        assertThat(s.lado()).isEqualTo(LadoSaldo.ACREEDOR);
        assertThat(s.monto()).isEqualTo(Dinero.de("50.00"));
        assertThat(s.contrarioNaturaleza()).isFalse();
    }

    /** Una cuenta deudora (p. ej. Caja) con saldo Acreedor es la alerta de CLAUDE.md 10.3. */
    @Test
    void saldoAcreedorEnCuentaDeudoraEsContrario() {
        Saldo s = Saldo.de(Dinero.de("-10.00"), NaturalezaCuenta.DEUDORA);

        assertThat(s.lado()).isEqualTo(LadoSaldo.ACREEDOR);
        assertThat(s.contrarioNaturaleza()).isTrue();
    }

    /** Una cuenta acreedora con saldo Deudor también es contraria. */
    @Test
    void saldoDeudorEnCuentaAcreedoraEsContrario() {
        Saldo s = Saldo.de(Dinero.de("10.00"), NaturalezaCuenta.ACREEDORA);

        assertThat(s.lado()).isEqualTo(LadoSaldo.DEUDOR);
        assertThat(s.contrarioNaturaleza()).isTrue();
    }

    /** Cero nunca es "contrario": no hay lado que contradiga la naturaleza. */
    @Test
    void saldoEnCeroNuncaEsContrario() {
        Saldo deudora = Saldo.de(Dinero.CERO, NaturalezaCuenta.DEUDORA);
        Saldo acreedora = Saldo.de(Dinero.CERO, NaturalezaCuenta.ACREEDORA);

        assertThat(deudora.lado()).isEqualTo(LadoSaldo.CERO);
        assertThat(deudora.contrarioNaturaleza()).isFalse();
        assertThat(acreedora.lado()).isEqualTo(LadoSaldo.CERO);
        assertThat(acreedora.contrarioNaturaleza()).isFalse();
        assertThat(deudora.monto()).isEqualTo(Dinero.CERO);
    }
}
