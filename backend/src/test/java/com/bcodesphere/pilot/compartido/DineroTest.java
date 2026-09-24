package com.bcodesphere.pilot.compartido;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link Dinero}: escala 2, HALF_UP y patrón del contrato (ADR-013, ADR-022). */
class DineroTest {

    /** Caso: la suma decimal es exacta (0.1 + 0.2 no da 0.30000000000000004 como en double). */
    @Test
    void sumaDecimalExacta() {
        assertThat(Dinero.de("0.1").sumar(Dinero.de("0.2")).toString()).isEqualTo("0.30");
    }

    /** Caso: entradas fuera del patrón MontoConSigno se rechazan, sin redondear en silencio. */
    @Test
    void rechazaEntradasInvalidas() {
        assertThatThrownBy(() -> Dinero.de("1.005")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.de("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.de("1e3")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.de(" 1.00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.de(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Dinero.de("123456789012345678")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Caso: los negativos son válidos (saldos y diferencias con signo). */
    @Test
    void aceptaNegativos() {
        assertThat(Dinero.de("-10.5").toString()).isEqualTo("-10.50");
    }

    /** Caso: redondear usa HALF_UP, es decir, se aleja de cero en la mitad también con negativos. */
    @Test
    void redondeaHalfUp() {
        assertThat(Dinero.redondear(new BigDecimal("1.005")).toString()).isEqualTo("1.01");
        assertThat(Dinero.redondear(new BigDecimal("-1.005")).toString()).isEqualTo("-1.01");
    }

    /** Caso: 100.0 y 100.00 son el mismo monto (equals y hashCode por valor). */
    @Test
    void igualdadIgnoraLaEscala() {
        Dinero a = new Dinero(new BigDecimal("100.0"));
        Dinero b = Dinero.de("100.00");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    /** Caso: resta, signo y comparación. */
    @Test
    void restaSignoYComparacion() {
        Dinero dif = Dinero.de("1.00").restar(Dinero.de("2.50"));
        assertThat(dif.toString()).isEqualTo("-1.50");
        assertThat(dif.esPositivo()).isFalse();
        assertThat(dif.esCero()).isFalse();
        assertThat(Dinero.CERO.esCero()).isTrue();
        assertThat(Dinero.de("3").comparar(Dinero.de("2.99"))).isPositive();
    }

    /** Caso: el constructor directo tampoco redondea en silencio. */
    @Test
    void constructorRechazaMasDeDosDecimales() {
        assertThatThrownBy(() -> new Dinero(new BigDecimal("0.001"))).isInstanceOf(IllegalArgumentException.class);
    }
}
