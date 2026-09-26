package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link ClaveApi}: formato {@code pk_xxxxxxxx.secreto} de CLAUDE.md 9.2 y tarea F1-06 (prefijo de 8
 * caracteres {@code [a-z0-9]}, secreto de 32 bytes en Base64URL sin relleno y sin punto).
 */
class ClaveApiTest {

    /** Regla: la clave generada cumple el formato exigido y su secreto tiene 43 caracteres (32 bytes en Base64URL). */
    @Test
    void laClaveGeneradaTieneElFormatoDePrefijoYSecreto() {
        ClaveApi.Generada clave = ClaveApi.generar();

        assertThat(clave.prefijo()).matches("^pk_[a-z0-9]{8}$");
        assertThat(clave.secreto()).matches("^[A-Za-z0-9_-]{43}$").doesNotContain(".");
        assertThat(clave.completa()).isEqualTo(clave.prefijo() + "." + clave.secreto());
    }

    /** Regla: la aleatoriedad no repite prefijos ni secretos en una muestra grande (colisión ~ imposible). */
    @Test
    void cienClavesGeneradasNoRepitenPrefijoNiSecreto() {
        Set<String> prefijos = new HashSet<>();
        Set<String> secretos = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ClaveApi.Generada clave = ClaveApi.generar();
            prefijos.add(clave.prefijo());
            secretos.add(clave.secreto());
        }
        assertThat(prefijos).hasSize(100);
        assertThat(secretos).hasSize(100);
    }

    /** Regla: una clave completa generada se interpreta de vuelta en el mismo prefijo y secreto. */
    @Test
    void unaClaveCompletaSeInterpretaEnPrefijoYSecreto() {
        ClaveApi.Generada clave = ClaveApi.generar();

        assertThat(ClaveApi.interpretar(clave.completa()))
                .contains(new ClaveApi.Presentada(clave.prefijo(), clave.secreto()));
    }

    /** Regla: cualquier otro formato (nulo, sin punto, prefijo corto o en mayúsculas, secreto corto) no se acepta. */
    @Test
    void losFormatosInvalidosNoSeInterpretan() {
        String secreto = "A".repeat(43);
        assertThat(ClaveApi.interpretar(null)).isEmpty();
        assertThat(ClaveApi.interpretar("")).isEmpty();
        assertThat(ClaveApi.interpretar("pk_abcd1234" + secreto)).isEmpty();
        assertThat(ClaveApi.interpretar("pk_abcd123." + secreto)).isEmpty();
        assertThat(ClaveApi.interpretar("pk_ABCD1234." + secreto)).isEmpty();
        assertThat(ClaveApi.interpretar("xx_abcd1234." + secreto)).isEmpty();
        assertThat(ClaveApi.interpretar("pk_abcd1234.corto")).isEmpty();
        assertThat(ClaveApi.interpretar("pk_abcd1234." + secreto + ".extra")).isEmpty();
    }

    /** Regla (CLAUDE.md 14.1): el secreto nunca aparece en el texto de los objetos que lo llevan. */
    @Test
    void elSecretoNoApareceEnToString() {
        ClaveApi.Generada clave = ClaveApi.generar();
        ClaveApi.Presentada presentada = new ClaveApi.Presentada(clave.prefijo(), clave.secreto());

        assertThat(clave.toString()).doesNotContain(clave.secreto()).contains(clave.prefijo());
        assertThat(presentada.toString()).doesNotContain(clave.secreto());
    }
}
