package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas de {@link CursorApiKey}: cursor opaco de la paginación (CLAUDE.md 13); un cursor alterado es 422. */
class CursorApiKeyTest {

    /** Regla: codificar y decodificar conserva el instante a microsegundos (la precisión de PostgreSQL) y el id. */
    @Test
    void elCursorSeConservaAlDecodificar() {
        Instant instante = Instant.parse("2026-09-25T12:34:56.123456Z");
        UUID id = UUID.randomUUID();

        CursorApiKey decodificado = CursorApiKey.decodificar(new CursorApiKey(instante, id).codificar());

        assertThat(decodificado.creadoEn()).isEqualTo(instante);
        assertThat(decodificado.id()).isEqualTo(id);
    }

    /** Regla: texto inventado, sin separador, con número o UUID inválidos se rechaza con PLT-002 en el campo cursor. */
    @Test
    void unCursorAlteradoSeRechaza() {
        for (String cursor : new String[] {"basura!", "AAAA", "bm8tZXMtY3Vyc29y", ""}) {
            assertThatThrownBy(() -> CursorApiKey.decodificar(cursor))
                    .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                        assertThat(e.codigo()).isEqualTo("PLT-002");
                        assertThat(e.errores()).extracting("campo").containsExactly("cursor");
                    });
        }
    }
}
