package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bcodesphere.pilot.contabilidad.aplicacion.PrecargaContable;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Prueba de integración del fallo de la precarga (F2-03). Fuente: plan de trabajo F2 («si la precarga falla, la
 * instalación se revierte») y ADR-030 punto 5 (el evento se publica dentro de la transacción de la instalación).
 */
class PrecargaContabilidadFallidaIT extends BaseContabilidadIT {

    /** Puerto simulado: fuerza el fallo de la precarga sin tocar la base de datos. */
    @MockitoBean
    private PrecargaContable precarga;

    /**
     * Si la precarga lanza una excepción, la instalación entera se revierte: sin fila en {@code empresa_aplicacion},
     * sin cuentas y sin auditoría de instalación; la respuesta no es 201.
     */
    @Test
    void siLaPrecargaFallaLaInstalacionSeRevierte() throws Exception {
        when(precarga.precargar()).thenThrow(new IllegalStateException("fallo simulado de la precarga"));
        Sesion s = nuevaSesion();

        int estado = mvc.perform(MockMvcRequestBuilders.post("/api/v1/aplicaciones/contabilidad/instalacion")
                        .with(token(s.sub()))
                        .header("X-Empresa-Id", s.empresa().toString()))
                .andReturn()
                .getResponse()
                .getStatus();

        // 1. Error interno (PLT-500), nunca 201 ni 200
        assertThat(estado).isEqualTo(500);
        // 2. Nada quedó: ni la instalación, ni cuentas, ni la auditoría de la instalación
        assertThat(contar("SELECT count(*) FROM empresa_aplicacion WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'empresa_aplicacion'",
                        s.empresa()))
                .isZero();
    }
}
