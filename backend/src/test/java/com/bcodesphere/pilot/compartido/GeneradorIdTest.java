package com.bcodesphere.pilot.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link GeneradorId} y {@link EmpresaId} (ADR-010). */
class GeneradorIdTest {

    /** Caso: versión 7, variante 2 y milisegundos en los 48 bits altos (RFC 9562). */
    @Test
    void generaUuidV7ConTimestamp() {
        UUID id = GeneradorId.generar(1_700_000_000_000L, new Random(1));
        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(1_700_000_000_000L);
    }

    /** Caso: ids generados en instantes crecientes ordenan igual que el tiempo (índices B-tree ordenados). */
    @Test
    void losIdsCrecenConElTiempo() {
        String antes = GeneradorId.generar(1_000L, new Random(1)).toString();
        String despues = GeneradorId.generar(2_000L, new Random(2)).toString();
        assertThat(antes).isLessThan(despues);
        assertThat(GeneradorId.nuevo()).isNotEqualTo(GeneradorId.nuevo());
    }

    /** Caso: EmpresaId envuelve el UUID y rechaza nulos. */
    @Test
    void empresaIdEnvuelveUuid() {
        UUID uuid = GeneradorId.nuevo();
        assertThat(EmpresaId.de(uuid.toString()).valor()).isEqualTo(uuid);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EmpresaId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
