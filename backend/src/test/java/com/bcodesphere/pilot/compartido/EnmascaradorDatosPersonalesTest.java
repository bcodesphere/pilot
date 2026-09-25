package com.bcodesphere.pilot.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pruebas del enmascarador. Los formatos de DUI y NIT son [VERIFICAR]; se prueban con y sin guiones. */
class EnmascaradorDatosPersonalesTest {

    /** Caso: DUI con y sin guion conserva solo los últimos 2 dígitos. */
    @Test
    void enmascaraDui() {
        assertThat(EnmascaradorDatosPersonales.enmascarar("DUI 01234567-8 ok")).isEqualTo("DUI *******7-8 ok");
        assertThat(EnmascaradorDatosPersonales.enmascarar("DUI 012345678")).isEqualTo("DUI *******78");
    }

    /** Caso: NIT (14 dígitos) con y sin guiones; no se parte en un DUI y un teléfono. */
    @Test
    void enmascaraNit() {
        assertThat(EnmascaradorDatosPersonales.enmascarar("NIT 0614-050505-101-3"))
                .isEqualTo("NIT ****-******-**1-3");
        assertThat(EnmascaradorDatosPersonales.enmascarar("NIT 06140505051013")).isEqualTo("NIT ************13");
    }

    /** Caso: correo oculto salvo los últimos 2 caracteres de la parte local; el dominio se conserva. */
    @Test
    void enmascaraCorreo() {
        assertThat(EnmascaradorDatosPersonales.enmascarar("usuario juan.perez@example.com creó"))
                .isEqualTo("usuario ********ez@example.com creó");
    }

    /** Caso: teléfono salvadoreño de 8 dígitos, con guion o con prefijo +503. */
    @Test
    void enmascaraTelefono() {
        assertThat(EnmascaradorDatosPersonales.enmascarar("tel 7123-4567")).isEqualTo("tel ****-**67");
        String conPrefijo = EnmascaradorDatosPersonales.enmascarar("tel +503 7123 4567");
        assertThat(conPrefijo).endsWith("67").doesNotContain("7123").doesNotContain("503");
    }

    /**
     * Caso F1-04: el teléfono en el formato en que se GUARDA (+503 y 8 dígitos) y el que llega del token (8 dígitos)
     * quedan enmascarados, sin que se filtren los dígitos originales.
     */
    @Test
    void enmascaraTelefonoGuardadoYDelToken() {
        String guardado = EnmascaradorDatosPersonales.enmascarar("tel +50370001234 ok");
        assertThat(guardado)
                .endsWith("34 ok")
                .doesNotContain("7000")
                .doesNotContain("503")
                .doesNotContain("70001234");

        String deToken = EnmascaradorDatosPersonales.enmascarar("tel 70001234 ok");
        assertThat(deToken).isEqualTo("tel ******34 ok");
    }

    /** Caso: texto sin datos personales queda igual y nulo se tolera. */
    @Test
    void noAlteraTextoLimpio() {
        assertThat(EnmascaradorDatosPersonales.enmascarar("Asiento 1523 por 1180.00"))
                .isEqualTo("Asiento 1523 por 1180.00");
        assertThat(EnmascaradorDatosPersonales.enmascarar(null)).isNull();
    }
}
