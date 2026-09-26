package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regla (CLAUDE.md 8.3, PLT-016): el ETag es la versión entre comillas dobles; todo lo demás es un If-Match inválido. */
class VersionEtagTest {

    /** La versión se formatea y se lee de vuelta sin pérdida. */
    @Test
    void formateaYParseaLaVersion() {
        assertThat(VersionEtag.formatear(3)).isEqualTo("\"3\"");
        assertThat(VersionEtag.parsear("\"3\"")).isEqualTo(3L);
    }

    /** Sin comillas, débil, comodín, lista, negativo o texto: 412 PLT-016. */
    @ParameterizedTest
    @ValueSource(strings = {"3", "W/\"3\"", "*", "\"3\", \"4\"", "\"-1\"", "\"abc\"", "\"\"", ""})
    void unIfMatchMalFormadoDa412(String valor) {
        assertThatThrownBy(() -> VersionEtag.parsear(valor)).isInstanceOfSatisfying(ExcepcionPlataforma.class, e -> {
            assertThat(e.codigo()).isEqualTo("PLT-016");
            assertThat(e.estadoHttp()).isEqualTo(412);
        });
    }
}
