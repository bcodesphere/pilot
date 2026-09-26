package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link ReglasNuevaClave}: nombre normalizado, alcances sin repetidos y vencimiento futuro, todos como
 * 422 {@code PLT-002} con el campo culpable (CLAUDE.md 8.4, plan F1-06).
 */
class ReglasNuevaClaveTest {

    private static final Instant AHORA = Instant.parse("2026-09-25T12:00:00Z");
    private static final String ALCANCE = "integracion:operaciones";

    /** Regla: el nombre se guarda sin espacios en los extremos y la expiración nula significa «no vence». */
    @Test
    void unaClaveValidaSeNormaliza() {
        var datos = ReglasNuevaClave.validar("  n8n producción  ", List.of(ALCANCE), null, AHORA);

        assertThat(datos.nombre()).isEqualTo("n8n producción");
        assertThat(datos.alcances()).containsExactly(AlcanceClave.INTEGRACION_OPERACIONES);
        assertThat(datos.expiraEn()).isNull();
    }

    /** Regla: un nombre que solo tiene espacios queda vacío y se rechaza en el campo nombre. */
    @Test
    void unNombreVacioSeRechaza() {
        assertThatThrownBy(() -> ReglasNuevaClave.validar("   ", List.of(ALCANCE), null, AHORA))
                .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                    assertThat(e.codigo()).isEqualTo("PLT-002");
                    assertThat(e.errores()).extracting("campo").containsExactly("nombre");
                });
    }

    /** Regla: los alcances repetidos se rechazan en el campo alcances (el contrato no usa uniqueItems a propósito). */
    @Test
    void losAlcancesRepetidosSeRechazan() {
        assertThatThrownBy(() -> ReglasNuevaClave.validar("n8n", List.of(ALCANCE, ALCANCE), null, AHORA))
                .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                    assertThat(e.codigo()).isEqualTo("PLT-002");
                    assertThat(e.errores()).extracting("campo").containsExactly("alcances");
                });
    }

    /** Regla: un alcance desconocido o una lista vacía no dan una clave sin permisos, se rechazan. */
    @Test
    void unAlcanceDesconocidoOVacioSeRechaza() {
        assertThatThrownBy(() -> ReglasNuevaClave.validar("n8n", List.of("otro:alcance"), null, AHORA))
                .isInstanceOf(ExcepcionValidacion.class);
        assertThatThrownBy(() -> ReglasNuevaClave.validar("n8n", List.of(), null, AHORA))
                .isInstanceOf(ExcepcionValidacion.class);
    }

    /** Regla: el vencimiento debe ser futuro; el instante actual exacto ya cuenta como vencido. */
    @Test
    void unVencimientoPasadoOIgualAAhoraSeRechaza() {
        assertThatThrownBy(() -> ReglasNuevaClave.validar("n8n", List.of(ALCANCE), AHORA.minusSeconds(1), AHORA))
                .isInstanceOfSatisfying(
                        ExcepcionValidacion.class,
                        e -> assertThat(e.errores()).extracting("campo").containsExactly("expiraEn"));
        assertThatThrownBy(() -> ReglasNuevaClave.validar("n8n", List.of(ALCANCE), AHORA, AHORA))
                .isInstanceOf(ExcepcionValidacion.class);
        assertThat(ReglasNuevaClave.validar("n8n", List.of(ALCANCE), AHORA.plusSeconds(1), AHORA)
                        .expiraEn())
                .isEqualTo(AHORA.plusSeconds(1));
    }

    /** Regla: se informan todos los campos inválidos a la vez, no solo el primero. */
    @Test
    void seInformanTodosLosCamposInvalidosJuntos() {
        assertThatThrownBy(() -> ReglasNuevaClave.validar(" ", List.of(ALCANCE, ALCANCE), AHORA.minusSeconds(5), AHORA))
                .isInstanceOfSatisfying(
                        ExcepcionValidacion.class,
                        e -> assertThat(e.errores())
                                .extracting("campo")
                                .containsExactly("nombre", "alcances", "expiraEn"));
    }
}
