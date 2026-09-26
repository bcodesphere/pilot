package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import org.junit.jupiter.api.Test;

/** Regla de negocio (ADR-032): el nombre del espacio se guarda sin espacios en los extremos y no puede ser vacío. */
class NombreEspacioTest {

    /** Los espacios de los extremos se quitan; los interiores se conservan. */
    @Test
    void quitaLosEspaciosDeLosExtremos() {
        assertThat(NombreEspacio.normalizar("  Mi  espacio \t")).isEqualTo("Mi  espacio");
    }

    /** Un nombre solo de espacios (o nulo) es inválido: PLT-002 con el campo nombre. */
    @Test
    void unNombreVacioSeRechazaConPlt002() {
        assertThatThrownBy(() -> NombreEspacio.normalizar("   "))
                .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                    assertThat(e.codigo()).isEqualTo("PLT-002");
                    assertThat(e.errores()).extracting("campo").containsExactly("nombre");
                });
        assertThatThrownBy(() -> NombreEspacio.normalizar(null)).isInstanceOf(ExcepcionValidacion.class);
    }
}
