package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Pruebas de integración de la precarga al instalar Contabilidad (F2-03). Fuente: plan de trabajo F2, ADR-030 y
 * ADR-035, y el catálogo base de V10 + V15 (117 + 36 = 153 cuentas, 56 + 18 = 74 de detalle, 9 reglas; ADR-037). El
 * fallo de la precarga tiene su propia clase porque necesita un contexto con un bean simulado.
 */
class PrecargaContabilidadIT extends BaseContabilidadIT {

    /**
     * Regla (ADR-035, decisión 1): al instalar, la empresa recibe el catálogo base completo, 153 cuentas de las que
     * 74 aceptan movimientos (solo las hojas), con los padres enlazados por prefijo.
     */
    @Test
    void alInstalarLaEmpresaRecibeElCatalogoConLosPadresEnlazados() throws Exception {
        Sesion s = sesionConContabilidad();

        // 1. Catálogo: 153 cuentas, 74 aceptan movimientos
        assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(153);
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND acepta_movimientos",
                        s.empresa()))
                .isEqualTo(74);
        // 2. Padres enlazados: toda cuenta que no es clase tiene padre y su código es prefijo del suyo
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND nivel > 1"
                                + " AND cuenta_padre_id IS NULL",
                        s.empresa()))
                .isZero();
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable h JOIN cuenta_contable p ON p.id = h.cuenta_padre_id"
                                + " WHERE h.empresa_id = ? AND NOT starts_with(h.codigo, p.codigo)",
                        s.empresa()))
                .isZero();
        // 3. Una cuenta acepta movimientos si y solo si no tiene hijas
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable c WHERE c.empresa_id = ? AND c.acepta_movimientos ="
                                + " EXISTS (SELECT 1 FROM cuenta_contable h WHERE h.cuenta_padre_id = c.id)",
                        s.empresa()))
                .isZero();
    }

    /**
     * Regla (ADR-035, decisión 1): la configuración precargada es CON_IVA con IVA débito 21020101 e IVA crédito
     * 11040101, vista por la API.
     */
    @Test
    void alInstalarLaEmpresaRecibeLaConfiguracionPorDefecto() throws Exception {
        Sesion s = sesionConContabilidad();

        get(s, "/contabilidad/configuracion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoPrecioDefecto").value("CON_IVA"))
                .andExpect(jsonPath("$.cuentaIvaDebito.codigo").value("21020101"))
                .andExpect(jsonPath("$.cuentaIvaCredito.codigo").value("11040101"))
                .andExpect(jsonPath("$.version").value(0));
    }

    /**
     * Regla (ADR-035, decisión 2): 9 reglas, con OTRO inactiva y sin cuenta y las otras 8 activas con cuenta; y una
     * sola fila de auditoría de la precarga con los totales.
     */
    @Test
    void alInstalarLaEmpresaRecibeLasReglasYSeAuditaUnaVez() throws Exception {
        Sesion s = sesionConContabilidad();

        // 1. Reglas vistas por la API
        get(s, "/contabilidad/reglas-contabilizacion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[?(@.codigo == 'OTRO')].activa").value(false))
                .andExpect(
                        jsonPath("$[?(@.codigo == 'EFECTIVO')].cuenta.codigo").value("11010101"));
        // 2. OTRO inactiva y sin cuenta; las demás activas con cuenta
        assertThat(contar(
                        "SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ? AND codigo = 'OTRO'"
                                + " AND NOT activa AND cuenta_id IS NULL",
                        s.empresa()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ? AND activa"
                                + " AND cuenta_id IS NOT NULL",
                        s.empresa()))
                .isEqualTo(8);
        // 3. Una sola fila de auditoría de la precarga, con los totales
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'contabilidad'"
                                + " AND accion = 'PRECARGAR'",
                        s.empresa()))
                .isEqualTo(1);
        String valorNuevo = duenio.sql(
                        "SELECT valor_nuevo::text FROM auditoria WHERE empresa_id = ?" + " AND accion = 'PRECARGAR'")
                .params(s.empresa())
                .query(String.class)
                .single();
        assertThat(valorNuevo)
                .contains("\"cuentas\": 153")
                .contains("\"cuentasDetalle\": 74")
                .contains("\"reglas\": 9");
    }

    /** Regla (ADR-002): dos empresas que instalan tienen catálogos separados y ninguna ve las cuentas de la otra. */
    @Test
    void otraEmpresaNoVeNadaDeLaPrecargaAjena() throws Exception {
        Sesion a = sesionConContabilidad();
        Sesion b = sesionConContabilidad();

        List<String> idsA = leer(get(a, "/contabilidad/cuentas").andExpect(status().isOk()), "$[*].id");
        List<String> idsB = leer(get(b, "/contabilidad/cuentas").andExpect(status().isOk()), "$[*].id");

        assertThat(idsA).hasSize(153);
        assertThat(idsB).hasSize(153);
        Set<String> comunes = new HashSet<>(idsA);
        comunes.retainAll(idsB);
        assertThat(comunes).isEmpty();
        // Una cuenta de B pedida por A es inexistente (404 PLT-017), no prohibida
        get(a, "/contabilidad/cuentas/" + cuentaId(b.empresa(), "11010101"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
    }

    /** Regla (ADR-030): instalar dos veces no repite la precarga (la segunda es 200 sin efectos). */
    @Test
    void reinstalarNoDuplicaLaPrecarga() throws Exception {
        Sesion s = sesionConContabilidad();
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/aplicaciones/contabilidad/instalacion")
                        .with(token(s.sub()))
                        .header("X-Empresa-Id", s.empresa().toString()))
                .andExpect(status().isOk());

        assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(153);
        assertThat(contar("SELECT count(*) FROM auditoria WHERE empresa_id = ? AND accion = 'PRECARGAR'", s.empresa()))
                .isEqualTo(1);
    }

    /** Regla (CLAUDE.md 4.4, PLT-004): sin instalar la app, las rutas contables responden 403 PLT-004. */
    @Test
    void sinInstalarContabilidadLasRutasDanPlt004() throws Exception {
        Sesion s = nuevaSesion();

        get(s, "/contabilidad/cuentas")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));
        get(s, "/contabilidad/configuracion")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));
        get(s, "/contabilidad/reglas-contabilizacion")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-004"));
    }
}
