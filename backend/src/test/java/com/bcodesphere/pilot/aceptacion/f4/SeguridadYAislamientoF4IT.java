package com.bcodesphere.pilot.aceptacion.f4;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Criterio 9 de F4-06 (CLAUDE.md §14.2 y §15): seguridad y aislamiento de los seis reportes y sus exportaciones.
 *
 * <ul>
 *   <li>el rol {@code auditor} lee los seis reportes y sus exportaciones; el diagnóstico de mayorización exige
 *       {@code contador} (403 {@code PLT-010} para el auditor);
 *   <li>una API key (alcance de integración, nunca de usuario) recibe 401 en todas las rutas de reportes;
 *   <li>la empresa B no ve datos de la empresa A en ningún reporte: balanza vacía, y consultar por el
 *       {@code cuentaId} de A responde 404 {@code PLT-017};
 *   <li>sin Contabilidad instalada en la empresa, cualquier ruta de reportes responde 403 {@code PLT-004}.
 * </ul>
 */
class SeguridadYAislamientoF4IT extends BaseAceptacionF4IT {

    private static final String RANGO = "desde=2026-03-01&hasta=2026-03-31";

    /** Las seis rutas de reporte (JSON) más el diagnóstico, y sus seis rutas de exportación en un formato cada una. */
    private List<String> rutasDeReporte(UUID cuentaId) {
        return List.of(
                "/contabilidad/balanza?" + RANGO,
                "/contabilidad/estados/resultados?" + RANGO,
                "/contabilidad/estados/situacion-financiera?fechaCorte=2026-03-31",
                "/contabilidad/reportes/iva?anio=2026&mes=3",
                "/contabilidad/mayor?cuentaId=" + cuentaId + "&" + RANGO,
                "/contabilidad/asientos?" + RANGO,
                "/contabilidad/balanza/exportacion?formato=csv&" + RANGO,
                "/contabilidad/estados/resultados/exportacion?formato=csv&" + RANGO,
                "/contabilidad/estados/situacion-financiera/exportacion?formato=csv&fechaCorte=2026-03-31",
                "/contabilidad/reportes/iva/exportacion?formato=csv&anio=2026&mes=3",
                "/contabilidad/mayor/exportacion?formato=csv&cuentaId=" + cuentaId + "&" + RANGO,
                "/contabilidad/asientos/exportacion?formato=csv&" + RANGO);
    }

    // ------------------------------------------------------------------------------------------- rol auditor

    /** El auditor lee los seis reportes y sus exportaciones, pero el diagnóstico exige el rol contador. */
    @Test
    void elAuditorLeeLosSeisReportesYExportacionesPeroNoElDiagnostico() throws Exception {
        Sesion contador = sembrarConjuntoDorado().sesion();
        Sesion auditor = sembrarMiembro(contador, "auditor");
        UUID caja = cuentaId(contador.empresa(), "11010101");

        for (String ruta : rutasDeReporte(caja)) {
            get(auditor, ruta).andExpect(status().isOk());
        }

        get(auditor, "/contabilidad/diagnostico/mayorizacion")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        get(contador, "/contabilidad/diagnostico/mayorizacion").andExpect(status().isOk());
    }

    // --------------------------------------------------------------------------------------------- api key

    /** Crea una API key con el único alcance de 1.0 (integración) y devuelve su secreto completo. */
    private String nuevaApiKeySecreto(Sesion s) throws Exception {
        return leer(
                post(
                        s,
                        "/api-keys",
                        "{\"nombre\":\"Clave de prueba F4-06\",\"alcances\":[\"integracion:operaciones\"],"
                                + "\"expiraEn\":null}"),
                "$.secreto");
    }

    /** Una API key (credencial de integración) no autentica ninguna ruta de reportes: 401 en todas. */
    @Test
    void unaApiKeyRecibe401EnTodasLasRutasDeReportes() throws Exception {
        Sesion s = sembrarConjuntoDorado().sesion();
        UUID caja = cuentaId(s.empresa(), "11010101");
        String secreto = nuevaApiKeySecreto(s);

        for (String ruta : rutasDeReporte(caja)) {
            mvc.perform(MockMvcRequestBuilders.get("/api/v1" + ruta)
                            .header("Authorization", "Bearer " + secreto)
                            .header("X-Empresa-Id", s.empresa().toString()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("PLT-009"));
        }
    }

    // ----------------------------------------------------------------------------------------- aislamiento A/B

    /** La empresa B no ve los reportes de la empresa A: balanza vacía y 404 PLT-017 al usar el cuentaId de A. */
    @Test
    void laEmpresaBNoVeLosReportesNiLasCuentasDeLaEmpresaA() throws Exception {
        Sesion a = sembrarConjuntoDorado().sesion();
        Sesion b = sesionConContabilidad();
        UUID cuentaDeA = cuentaId(a.empresa(), "11010101");

        get(b, "/contabilidad/balanza?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filas").isEmpty())
                .andExpect(jsonPath("$.totalDebe").value("0.00"));
        get(b, "/contabilidad/estados/resultados?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresos.total").value("0.00"));
        get(b, "/contabilidad/reportes/iva?anio=2026&mes=3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ivaDebito.total").value("0.00"));
        get(b, "/contabilidad/asientos?" + RANGO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos").isEmpty());

        get(b, "/contabilidad/mayor?cuentaId=" + cuentaDeA + "&" + RANGO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        get(b, "/contabilidad/mayor/exportacion?formato=csv&cuentaId=" + cuentaDeA + "&" + RANGO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
    }

    // --------------------------------------------------------------------------------------- app no instalada

    /** Sin Contabilidad instalada en la empresa, toda ruta de reportes responde 403 PLT-004. */
    @Test
    void sinContabilidadInstaladaTodaRutaDeReportesResponde403() throws Exception {
        Sesion sinApp = nuevaSesion();
        UUID cuentaCualquiera = UUID.randomUUID();

        for (String ruta : rutasDeReporte(cuentaCualquiera)) {
            get(sinApp, ruta)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-004"));
        }
        get(sinApp, "/contabilidad/diagnostico/mayorizacion")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));
    }
}
