package com.bcodesphere.pilot.contabilidad.dominio.reglas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pruebas del estado de una regla. Fuente: ADR-035 punto 2 (una regla activa siempre tiene cuenta, 422 PLT-002). */
class ReglaContabilizacionTest {

    /** Activar sin cuenta es un error de validación sobre el campo cuentaId. */
    @Test
    void activarSinCuentaDa422ConElCampoCuentaId() {
        assertThatThrownBy(() -> ReglaContabilizacion.validarEstado(true, null))
                .isInstanceOfSatisfying(ExcepcionValidacion.class, e -> {
                    assertThat(e.codigo()).isEqualTo("PLT-002");
                    assertThat(e.estadoHttp()).isEqualTo(422);
                    assertThat(e.errores()).hasSize(1);
                    assertThat(e.errores().get(0).campo()).isEqualTo("cuentaId");
                });
    }

    /** Activa con cuenta, inactiva con cuenta e inactiva sin cuenta son estados válidos. */
    @Test
    void losDemasEstadosSonValidos() {
        assertThatCode(() -> ReglaContabilizacion.validarEstado(true, UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReglaContabilizacion.validarEstado(false, UUID.randomUUID()))
                .doesNotThrowAnyException();
        assertThatCode(() -> ReglaContabilizacion.validarEstado(false, null)).doesNotThrowAnyException();
    }
}
