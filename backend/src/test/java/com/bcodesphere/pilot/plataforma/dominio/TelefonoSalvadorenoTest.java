package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pruebas del objeto de valor de teléfono (ADR-028; formatos del realm en infra/keycloak/README.md). */
class TelefonoSalvadorenoTest {

    /** Caso: 8 dígitos sin prefijo se guardan con +503 (el realm los acepta así). */
    @Test
    void ochoDigitosSeNormalizanConPrefijo() {
        assertThat(TelefonoSalvadoreno.de("70001234")).contains(new TelefonoSalvadoreno("+50370001234"));
    }

    /** Caso: con el prefijo ya puesto queda igual, y los espacios en los extremos se ignoran. */
    @Test
    void conPrefijoQuedaIgual() {
        assertThat(TelefonoSalvadoreno.de(" +50370001234 ")).contains(new TelefonoSalvadoreno("+50370001234"));
    }

    /** Caso: números extranjeros, con letras, con separadores o de otro largo se rechazan (solo El Salvador). */
    @Test
    void formasInvalidasSeRechazan() {
        assertThat(TelefonoSalvadoreno.de(null)).isEmpty();
        assertThat(TelefonoSalvadoreno.de("")).isEmpty();
        assertThat(TelefonoSalvadoreno.de("+1 305 555 0100")).isEmpty();
        assertThat(TelefonoSalvadoreno.de("+50270001234")).isEmpty();
        assertThat(TelefonoSalvadoreno.de("7000-1234")).isEmpty();
        assertThat(TelefonoSalvadoreno.de("7000123")).isEmpty();
        assertThat(TelefonoSalvadoreno.de("700012345")).isEmpty();
        assertThat(TelefonoSalvadoreno.de("7000123a")).isEmpty();
    }

    /** Caso: el constructor solo admite la forma ya normalizada (la que exige la restricción de la base de datos). */
    @Test
    void elConstructorExigeLaFormaNormalizada() {
        assertThatThrownBy(() -> new TelefonoSalvadoreno("70001234")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TelefonoSalvadoreno(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
