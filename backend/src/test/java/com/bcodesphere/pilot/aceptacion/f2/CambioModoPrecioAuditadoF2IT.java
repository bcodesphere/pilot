package com.bcodesphere.pilot.aceptacion.f2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aceptación de F2, criterio 4: «cambiar el modo de precio queda en la auditoría con valor anterior y nuevo».
 * Fuente: CLAUDE.md 1.1.11 (auditar toda mutación) y 11.3 («todo cambio de configuración queda auditado»). La
 * entidad, la acción y los campos salen del caso de uso {@code GestionarConfiguracionContable} y de la tabla
 * {@code auditoria} (V3): {@code entidad = 'configuracion_contable'}, {@code accion = 'ACTUALIZAR'} y el JSONB con
 * {@code modoPrecioDefecto}.
 */
class CambioModoPrecioAuditadoF2IT extends BaseAceptacionF2IT {

    private static String cuerpo(String modo, UUID debito, UUID credito) {
        return "{\"modoPrecioDefecto\":\"" + modo + "\",\"cuentaIvaDebitoId\":\"" + debito
                + "\",\"cuentaIvaCreditoId\":\"" + credito + "\"}";
    }

    /** Filas de auditoría de la configuración de la empresa, de la más antigua a la más reciente. */
    private List<Map<String, Object>> filas(UUID empresa) {
        return duenio.sql("SELECT valor_anterior->>'modoPrecioDefecto' AS anterior,"
                        + " valor_nuevo->>'modoPrecioDefecto' AS nuevo, usuario_id, trace_id"
                        + " FROM auditoria WHERE empresa_id = ? AND entidad = 'configuracion_contable'"
                        + " AND accion = 'ACTUALIZAR' ORDER BY creado_en, id")
                .params(empresa)
                .query()
                .listOfRows();
    }

    /** Pasar de CON_IVA a SIN_IVA y volver deja dos filas con los valores anterior y nuevo invertidos. */
    @Test
    void elCambioDeModoDePrecioYSuRetornoQuedanAuditados() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID debito = cuentaId(s.empresa(), "21020101");
        UUID credito = cuentaId(s.empresa(), "11040101");

        // 1. CON_IVA (precarga) → SIN_IVA con If-Match de la versión 0
        put(s, "/contabilidad/configuracion", "\"0\"", cuerpo("SIN_IVA", debito, credito))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoPrecioDefecto").value("SIN_IVA"));

        List<Map<String, Object>> tras1 = filas(s.empresa());
        assertThat(tras1).hasSize(1);
        assertThat(tras1.get(0).get("anterior")).isEqualTo("CON_IVA");
        assertThat(tras1.get(0).get("nuevo")).isEqualTo("SIN_IVA");
        // 2. Quién y correlación: el usuario de la sesión y un traceId no vacío
        assertThat(tras1.get(0).get("usuario_id")).isEqualTo(s.usuario().toString());
        assertThat((String) tras1.get(0).get("trace_id")).isNotBlank();

        // 3. Vuelta a CON_IVA con la versión nueva (1): segunda fila con los valores invertidos
        put(s, "/contabilidad/configuracion", "\"1\"", cuerpo("CON_IVA", debito, credito))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoPrecioDefecto").value("CON_IVA"));

        List<Map<String, Object>> tras2 = filas(s.empresa());
        assertThat(tras2).hasSize(2);
        assertThat(tras2.get(1).get("anterior")).isEqualTo("SIN_IVA");
        assertThat(tras2.get(1).get("nuevo")).isEqualTo("CON_IVA");
        assertThat(tras2.get(1).get("usuario_id")).isEqualTo(s.usuario().toString());
    }
}
