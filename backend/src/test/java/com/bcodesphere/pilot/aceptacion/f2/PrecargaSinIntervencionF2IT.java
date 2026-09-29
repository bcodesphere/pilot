package com.bcodesphere.pilot.aceptacion.f2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Aceptación de F2, criterio 1 (primera parte) de {@code docs/plan-de-trabajo.md}: «al instalar Contabilidad, la
 * empresa tiene catálogo, configuración y reglas precargados sin intervención manual». Los valores esperados se leen
 * de las plantillas globales ({@code plantilla_cuenta}, {@code plantilla_regla_contabilizacion} y
 * {@code plantilla_configuracion_contable}, ADR-034 y ADR-035), no de números fijos: si el contador valida otro
 * catálogo en una migración nueva, la prueba sigue siendo válida.
 */
class PrecargaSinIntervencionF2IT extends BaseAceptacionF2IT {

    /** Cuenta de la plantilla: código, nombre y naturaleza. */
    private record CuentaPlantilla(String codigo, String nombre, String naturaleza) {}

    /** Regla de la plantilla: clave, código de cuenta (nulo si es inactiva) y estado. */
    private record ReglaPlantilla(String tipo, String categoria, String codigo, String cuentaCodigo, boolean activa) {}

    /**
     * Instalar con una sola llamada deja catálogo, configuración y reglas listos. Fuente: plantillas globales
     * (ADR-035) y CLAUDE.md 10.2, 11.3 y 12.5.
     */
    @Test
    void unaSolaInstalacionDejaCatalogoConfiguracionYReglasListos() throws Exception {
        Sesion s = nuevaSesion();

        // 1. La única llamada de la persona: instalar (201). Todo lo demás son lecturas
        instalarContabilidad(s).andExpect(status().isCreated());

        // 2. Las tres lecturas se comparan contra las plantillas globales
        comprobarCatalogo(s);
        comprobarConfiguracion(s);
        comprobarReglas(s);

        // 3. Una sola fila de auditoría PRECARGAR para la empresa
        assertThat(contar("SELECT count(*) FROM auditoria WHERE empresa_id = ? AND accion = 'PRECARGAR'", s.empresa()))
                .isEqualTo(1);
    }

    /** Catálogo: mismas cuentas, nombres, naturalezas, padres enlazados y {@code aceptaMovimientos} que la plantilla. */
    private void comprobarCatalogo(Sesion s) throws Exception {
        List<CuentaPlantilla> plantilla = duenio.sql("SELECT codigo, nombre, naturaleza FROM plantilla_cuenta")
                .query((rs, n) -> new CuentaPlantilla(rs.getString(1), rs.getString(2), rs.getString(3)))
                .list();
        assertThat(plantilla).isNotEmpty();
        List<Map<String, Object>> cuentas = leer(get(s, "/contabilidad/cuentas").andExpect(status().isOk()), "$");
        assertThat(cuentas).hasSameSizeAs(plantilla);

        Map<String, Map<String, Object>> porCodigo = new HashMap<>();
        Map<String, String> codigoPorId = new HashMap<>();
        for (Map<String, Object> c : cuentas) {
            porCodigo.put((String) c.get("codigo"), c);
            codigoPorId.put((String) c.get("id"), (String) c.get("codigo"));
        }
        for (CuentaPlantilla p : plantilla) {
            Map<String, Object> c = porCodigo.get(p.codigo());
            assertThat(c).as("cuenta %s precargada", p.codigo()).isNotNull();
            assertThat(c.get("nombre")).isEqualTo(p.nombre());
            assertThat(c.get("naturaleza")).isEqualTo(p.naturaleza());

            // 3. Padre enlazado: el prefijo del nivel anterior (1, 2, 4 o 6 dígitos); las clases no tienen padre
            String padreEsperado = p.codigo().length() == 1 ? null : codigoPadre(p.codigo());
            String padreReal = c.get("cuentaPadreId") == null ? null : codigoPorId.get((String) c.get("cuentaPadreId"));
            assertThat(padreReal).as("padre de %s", p.codigo()).isEqualTo(padreEsperado);

            // 4. Acepta movimientos exactamente si ninguna otra cuenta de la plantilla la tiene como prefijo
            boolean tieneHijas = plantilla.stream()
                    .anyMatch(o -> !o.codigo().equals(p.codigo()) && o.codigo().startsWith(p.codigo()));
            assertThat(c.get("aceptaMovimientos"))
                    .as("aceptaMovimientos de %s", p.codigo())
                    .isEqualTo(!tieneHijas);
        }
    }

    /** Configuración: modo y cuentas de IVA de la plantilla, comparadas por código de cuenta. */
    private void comprobarConfiguracion(Sesion s) throws Exception {
        // 1. Modo y cuentas de IVA de la plantilla, comparadas por código de cuenta
        Map<String, Object> cfg = duenio.sql("SELECT modo_precio_defecto, cuenta_iva_debito_codigo,"
                        + " cuenta_iva_credito_codigo FROM plantilla_configuracion_contable")
                .query((rs, n) -> Map.<String, Object>of(
                        "modo", rs.getString(1), "debito", rs.getString(2), "credito", rs.getString(3)))
                .single();
        get(s, "/contabilidad/configuracion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoPrecioDefecto").value(cfg.get("modo")))
                .andExpect(jsonPath("$.cuentaIvaDebito.codigo").value(cfg.get("debito")))
                .andExpect(jsonPath("$.cuentaIvaCredito.codigo").value(cfg.get("credito")));
    }

    /** Reglas: una por regla de la plantilla, con la cuenta de su código; las inactivas sin cuenta (ADR-035). */
    private void comprobarReglas(Sesion s) throws Exception {
        // 1. Una por regla de la plantilla, con la cuenta de su código; las inactivas sin cuenta (ADR-035)
        List<ReglaPlantilla> reglasPlantilla = duenio.sql("SELECT tipo_operacion, categoria, codigo, cuenta_codigo,"
                        + " activa FROM plantilla_regla_contabilizacion")
                .query((rs, n) -> new ReglaPlantilla(
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)))
                .list();
        assertThat(reglasPlantilla).isNotEmpty();
        List<Map<String, Object>> reglas =
                leer(get(s, "/contabilidad/reglas-contabilizacion").andExpect(status().isOk()), "$");
        assertThat(reglas).hasSameSizeAs(reglasPlantilla);
        for (ReglaPlantilla p : reglasPlantilla) {
            Map<String, Object> r = reglas.stream()
                    .filter(x -> p.tipo().equals(x.get("tipoOperacion"))
                            && p.categoria().equals(x.get("categoria"))
                            && p.codigo().equals(x.get("codigo")))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("falta la regla " + p));
            assertThat(r.get("activa")).as("activa de %s", p.codigo()).isEqualTo(p.activa());
            if (p.cuentaCodigo() == null) {
                assertThat(r.get("cuenta")).as("cuenta de %s", p.codigo()).isNull();
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> cuenta = (Map<String, Object>) r.get("cuenta");
                assertThat(cuenta.get("codigo")).as("cuenta de %s", p.codigo()).isEqualTo(p.cuentaCodigo());
            }
        }
        // 2. ADR-035: la regla OTRO queda inactiva y sin cuenta hasta que la persona la configure
        assertThat(reglas.stream()
                        .filter(r -> "OTRO".equals(r.get("codigo")))
                        .allMatch(r -> Boolean.FALSE.equals(r.get("activa")) && r.get("cuenta") == null))
                .isTrue();

        // 8. Una sola fila de auditoría PRECARGAR para la empresa
        assertThat(contar("SELECT count(*) FROM auditoria WHERE empresa_id = ? AND accion = 'PRECARGAR'", s.empresa()))
                .isEqualTo(1);
    }

    /** Código del padre: el prefijo del nivel anterior según la longitud (2 → 1, 4 → 2, 6 → 4, 8 → 6 dígitos). */
    private static String codigoPadre(String codigo) {
        return switch (codigo.length()) {
            case 2 -> codigo.substring(0, 1);
            case 4 -> codigo.substring(0, 2);
            case 6 -> codigo.substring(0, 4);
            case 8 -> codigo.substring(0, 6);
            default -> throw new IllegalArgumentException("Longitud de código inesperada: " + codigo);
        };
    }
}
