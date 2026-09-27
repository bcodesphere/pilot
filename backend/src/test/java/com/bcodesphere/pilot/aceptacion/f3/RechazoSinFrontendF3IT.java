package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Criterio F3 "Frontend" (parte del servidor, docs/plan-de-trabajo.md F3): el backend rechaza un asiento inválido
 * incluso si algo evita el formulario del Libro Diario y llama a la API directamente con un cuerpo que el frontend
 * nunca produciría (montos como número JSON en vez de cadena, ADR-013). {@link com.bcodesphere.pilot.contabilidad.api.LibroDiarioIT}
 * ya prueba CON-002, CON-003 y CON-005 con montos como cadena (lo que el frontend sí envía); esta clase cubre lo que
 * solo se alcanza saltándose la forma que arma {@code decimal.js}.
 */
class RechazoSinFrontendF3IT extends BaseAceptacionF3IT {

    /** Un monto como número JSON (no cadena) es un cuerpo ilegible para el DTO: 400 PLT-001, no una validación de negocio. */
    @Test
    void unMontoComoNumeroJsonEs400PltUno() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        // debe va como número JSON (100.00 sin comillas) en vez de cadena decimal (ADR-013)
        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Bypass del frontend\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":100.00,\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"100.00\",\"llevaIva\":false}]}";

        postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PLT-001"));

        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }

    /** Un monto negativo enviado directo a la API (sin pasar por el formulario) sigue siendo 422 CON-003, no 500 ni 201. */
    @Test
    void unMontoNegativoSinPasarPorElFormularioEsCon003() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Bypass del frontend\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"-50.00\",\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"50.00\",\"llevaIva\":false}]}";

        postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-003"));

        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }

    /** Tres decimales enviados directo a la API es 422 CON-003: la regla de negocio, no la forma, es quien lo rechaza
     * (ADR-036: MontoEntrada es permisivo a propósito). */
    @Test
    void tresDecimalesSinPasarPorElFormularioEsCon003() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Bypass del frontend\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"10.123\",\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"10.123\",\"llevaIva\":false}]}";

        postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-003"));

        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }

    /** Debe y Haber a la vez en una línea, enviado directo a la API, es 422 CON-002. */
    @Test
    void debeYHaberALaVezSinPasarPorElFormularioEsCon002() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");

        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Bypass del frontend\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"10.00\",\"haber\":\"10.00\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"10.00\",\"llevaIva\":false}]}";

        postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-002"));

        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }
}
