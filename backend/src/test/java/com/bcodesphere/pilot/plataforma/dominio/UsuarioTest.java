package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas del consentimiento vigente y de la detección de cambios de perfil (ADR-028). */
class UsuarioTest {

    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-02-01T00:00:00Z");

    private static Usuario con(Instant aceptada, Instant retirada) {
        return new Usuario(
                UUID.randomUUID(),
                "sub",
                "a@b.sv",
                "Ana",
                new TelefonoSalvadoreno("+50370001234"),
                false,
                aceptada,
                retirada);
    }

    /** Caso: nunca aceptó → no vigente; aceptó y nunca retiró → vigente. */
    @Test
    void sinRetiroVigenteSoloSiAcepto() {
        assertThat(con(null, null).recomendacionesVigentes()).isFalse();
        assertThat(con(T1, null).recomendacionesVigentes()).isTrue();
    }

    /** Caso: retiró después de aceptar → no vigente; aceptó de nuevo después del retiro → vigente. */
    @Test
    void vigenteSoloSiLaAceptacionEsPosteriorAlRetiro() {
        assertThat(con(T1, T2).recomendacionesVigentes()).isFalse();
        assertThat(con(T2, T1).recomendacionesVigentes()).isTrue();
        assertThat(con(T1, T1).recomendacionesVigentes()).isFalse();
    }

    /** Caso: solo cambian correo, nombre o teléfono; el consentimiento no cuenta como cambio de perfil. */
    @Test
    void detectaCambiosDePerfil() {
        Usuario usuario = con(null, null);
        DatosIdentidad igual =
                new DatosIdentidad("sub", "a@b.sv", "Ana", new TelefonoSalvadoreno("+50370001234"), true);
        DatosIdentidad otroNombre = new DatosIdentidad("sub", "a@b.sv", "Ana M.", igual.telefono(), false);

        assertThat(usuario.difiereDe(igual)).isFalse();
        assertThat(usuario.difiereDe(otroNombre)).isTrue();
    }
}
