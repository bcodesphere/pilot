package com.bcodesphere.pilot.plataforma.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Regla de negocio (ADR-030): el estado de una app depende de su edición y de si la empresa la instaló. */
class EstadoAplicacionEmpresaTest {

    /** Una app instalada se muestra INSTALADA sin importar su edición. */
    @Test
    void unaAppInstaladaEsInstalada() {
        assertThat(EstadoAplicacionEmpresa.calcular(EdicionAplicacion.COMUNITARIA, true))
                .isEqualTo(EstadoAplicacionEmpresa.INSTALADA);
    }

    /** Una comunitaria sin instalar está DISPONIBLE. */
    @Test
    void unaComunitariaSinInstalarEstaDisponible() {
        assertThat(EstadoAplicacionEmpresa.calcular(EdicionAplicacion.COMUNITARIA, false))
                .isEqualTo(EstadoAplicacionEmpresa.DISPONIBLE);
    }

    /** Una Enterprise sin instalar está BLOQUEADA_ENTERPRISE (ADR-030). */
    @Test
    void unaEnterpriseSinInstalarEstaBloqueada() {
        assertThat(EstadoAplicacionEmpresa.calcular(EdicionAplicacion.ENTERPRISE, false))
                .isEqualTo(EstadoAplicacionEmpresa.BLOQUEADA_ENTERPRISE);
    }

    /** El valor de la base de datos se convierte a la edición; uno desconocido queda vacío. */
    @Test
    void laEdicionSeConvierteDesdeElCodigoDeLaBase() {
        assertThat(EdicionAplicacion.deCodigo("ENTERPRISE")).contains(EdicionAplicacion.ENTERPRISE);
        assertThat(EdicionAplicacion.deCodigo("otra")).isEmpty();
    }
}
