package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link VigenciaClave}: una clave revocada o vencida no autentica (plan F1: revocada o vencida = 401). */
class VigenciaClaveTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");

    /** Regla: sin revocación ni vencimiento, o con vencimiento futuro, la clave está vigente. */
    @Test
    void unaClaveSinRevocarYSinVencerEstaVigente() {
        assertThat(VigenciaClave.estaVigente(null, null, AHORA)).isTrue();
        assertThat(VigenciaClave.estaVigente(null, AHORA.plusSeconds(1), AHORA)).isTrue();
    }

    /** Regla: una revocación anula la clave aunque no haya vencido. */
    @Test
    void unaClaveRevocadaNoEstaVigente() {
        assertThat(VigenciaClave.estaVigente(AHORA.minusSeconds(60), null, AHORA))
                .isFalse();
        assertThat(VigenciaClave.estaVigente(AHORA.minusSeconds(60), AHORA.plusSeconds(600), AHORA))
                .isFalse();
    }

    /** Regla (expira_en &lt;= ahora): el instante exacto de expiración y los posteriores ya no sirven. */
    @Test
    void unaClaveVencidaNoEstaVigenteDesdeElInstanteDeExpiracion() {
        assertThat(VigenciaClave.estaVigente(null, AHORA, AHORA)).isFalse();
        assertThat(VigenciaClave.estaVigente(null, AHORA.minusSeconds(1), AHORA))
                .isFalse();
    }
}
