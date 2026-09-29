package com.bcodesphere.pilot.aceptacion.f2;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aceptación de F2 para el rol {@code auditor} (CLAUDE.md 14.2: solo lectura; 13: rol mínimo por ruta). Un auditor
 * sembrado en la empresa de otra sesión lee catálogo, configuración y reglas (200) y no puede escribir en ninguno de
 * los tres: 403 {@code PLT-010}.
 */
class RolAuditorF2IT extends BaseAceptacionF2IT {

    /** El auditor lee los tres recursos y cualquier escritura sobre ellos se rechaza con 403 {@code PLT-010}. */
    @Test
    void elAuditorLeeCatalogoConfiguracionYReglasPeroNoEscribe() throws Exception {
        Sesion admin = sesionConContabilidad();
        Sesion auditor = sembrarMiembro(admin, "auditor");
        UUID cuenta = cuentaId(admin.empresa(), "11010102");
        UUID debito = cuentaId(admin.empresa(), "21020101");
        UUID credito = cuentaId(admin.empresa(), "11040101");
        List<String> idsRegla =
                leer(get(admin, "/contabilidad/reglas-contabilizacion"), "$[?(@.codigo=='EFECTIVO')].id");
        UUID regla = UUID.fromString(idsRegla.get(0));

        // 1. Lecturas: 200
        get(auditor, "/contabilidad/cuentas").andExpect(status().isOk());
        get(auditor, "/contabilidad/cuentas/" + cuenta).andExpect(status().isOk());
        get(auditor, "/contabilidad/configuracion").andExpect(status().isOk());
        get(auditor, "/contabilidad/reglas-contabilizacion").andExpect(status().isOk());

        // 2. Escrituras: 403 PLT-010 en cada recurso
        post(auditor, "/contabilidad/cuentas", "{\"codigo\":\"11010104\",\"nombre\":\"Caja de ventas\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        patch(auditor, "/contabilidad/cuentas/" + cuenta, "\"0\"", "{\"nombre\":\"Otro nombre\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        put(
                        auditor,
                        "/contabilidad/configuracion",
                        "\"0\"",
                        "{\"modoPrecioDefecto\":\"SIN_IVA\",\"cuentaIvaDebitoId\":\"" + debito
                                + "\",\"cuentaIvaCreditoId\":\"" + credito + "\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        put(
                        auditor,
                        "/contabilidad/reglas-contabilizacion/" + regla,
                        "\"0\"",
                        "{\"cuentaId\":\"" + cuenta + "\",\"activa\":true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
    }
}
