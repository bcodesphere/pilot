package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de integración de la configuración contable y las reglas de contabilización por la API (F2-03). Fuente:
 * CLAUDE.md 11.3, 12.5 y 14.2, ADR-020 y ADR-035, y el criterio de F2 «cambiar el modo de precio queda en la
 * auditoría».
 */
class ConfiguracionYReglasIT extends BaseContabilidadIT {

    private static String configuracion(String modo, UUID debito, UUID credito) {
        return "{\"modoPrecioDefecto\":\"" + modo + "\",\"cuentaIvaDebitoId\":\"" + debito
                + "\",\"cuentaIvaCreditoId\":\"" + credito + "\"}";
    }

    private static String regla(UUID cuenta, boolean activa) {
        return "{\"cuentaId\":" + (cuenta == null ? "null" : "\"" + cuenta + "\"") + ",\"activa\":" + activa + "}";
    }

    private UUID reglaId(Sesion s, String codigo) throws Exception {
        java.util.List<String> ids =
                leer(get(s, "/contabilidad/reglas-contabilizacion"), "$[?(@.codigo=='" + codigo + "')].id");
        return UUID.fromString(ids.get(0));
    }

    // ---------------------------------------------------------------------------------------- configuración

    /** Regla (CLAUDE.md 11.3): la configuración inicial sale con su ETag y el resumen de las dos cuentas de IVA. */
    @Test
    void laConfiguracionSeLeeConEtag() throws Exception {
        Sesion s = sesionConContabilidad();

        get(s, "/contabilidad/configuracion")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.modoPrecioDefecto").value("CON_IVA"))
                .andExpect(jsonPath("$.cuentaIvaDebito.id")
                        .value(cuentaId(s.empresa(), "21020101").toString()))
                .andExpect(jsonPath("$.cuentaIvaCredito.id")
                        .value(cuentaId(s.empresa(), "11040101").toString()));
    }

    /** Regla (F2): pasar a SIN_IVA da 200, sube la versión y queda auditado con el modo anterior y el nuevo. */
    @Test
    void cambiarElModoDePrecioQuedaAuditado() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID debito = cuentaId(s.empresa(), "21020101");
        UUID credito = cuentaId(s.empresa(), "11040101");

        put(s, "/contabilidad/configuracion", "\"0\"", configuracion("SIN_IVA", debito, credito))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.modoPrecioDefecto").value("SIN_IVA"));

        String anterior = duenio.sql("SELECT valor_anterior::text FROM auditoria WHERE empresa_id = ?"
                        + " AND entidad = 'configuracion_contable'")
                .params(s.empresa())
                .query(String.class)
                .single();
        String nuevo = duenio.sql("SELECT valor_nuevo::text FROM auditoria WHERE empresa_id = ?"
                        + " AND entidad = 'configuracion_contable'")
                .params(s.empresa())
                .query(String.class)
                .single();
        assertThat(anterior).contains("CON_IVA");
        assertThat(nuevo).contains("SIN_IVA");
    }

    /** Regla: un PUT idéntico a lo guardado no escribe ni sube la versión, y la concurrencia optimista se respeta. */
    @Test
    void unPutSinCambiosNoSubeLaVersionYSinIfMatchDa428() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID debito = cuentaId(s.empresa(), "21020101");
        UUID credito = cuentaId(s.empresa(), "11040101");

        put(s, "/contabilidad/configuracion", "\"0\"", configuracion("CON_IVA", debito, credito))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""));
        put(s, "/contabilidad/configuracion", null, configuracion("SIN_IVA", debito, credito))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.codigo").value("PLT-015"));
        put(s, "/contabilidad/configuracion", "\"3\"", configuracion("SIN_IVA", debito, credito))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.codigo").value("PLT-016"));
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'configuracion_contable'",
                        s.empresa()))
                .isZero();
    }

    /** Regla (CON-006): una cuenta que no es de detalle, está inactiva o es de otra empresa no sirve para el IVA. */
    @Test
    void lasCuentasDeIvaDebenSerDeDetalleActivasYDeLaEmpresa() throws Exception {
        Sesion s = sesionConContabilidad();
        Sesion otra = sesionConContabilidad();
        UUID debito = cuentaId(s.empresa(), "21020101");
        UUID credito = cuentaId(s.empresa(), "11040101");

        // 1. Cuenta padre (110401 no acepta movimientos)
        put(
                        s,
                        "/contabilidad/configuracion",
                        "\"0\"",
                        configuracion("CON_IVA", debito, cuentaId(s.empresa(), "110401")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaIvaCreditoId"));
        // 2. Cuenta inactiva (se desactiva a mano: 11040102 no la usa nada)
        duenio.sql("UPDATE cuenta_contable SET activa = false WHERE empresa_id = ? AND codigo = '11040102'")
                .params(s.empresa())
                .update();
        put(
                        s,
                        "/contabilidad/configuracion",
                        "\"0\"",
                        configuracion("CON_IVA", debito, cuentaId(s.empresa(), "11040102")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaIvaCreditoId"));
        // 3. Cuenta de otra empresa: se comporta como inexistente
        put(
                        s,
                        "/contabilidad/configuracion",
                        "\"0\"",
                        configuracion("CON_IVA", cuentaId(otra.empresa(), "21020101"), credito))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaIvaDebitoId"));
        // 4. Cuenta inexistente
        put(s, "/contabilidad/configuracion", "\"0\"", configuracion("CON_IVA", UUID.randomUUID(), credito))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaIvaDebitoId"));
    }

    /** Regla (F2-04): si las dos cuentas de IVA fallan, CON-006 lleva ambos campos para marcar los dos selectores. */
    @Test
    void siFallanLasDosCuentasDeIvaElErrorTraeAmbosCampos() throws Exception {
        Sesion s = sesionConContabilidad();

        put(s, "/contabilidad/configuracion", "\"0\"", configuracion("CON_IVA", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores.length()").value(2))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaIvaDebitoId"))
                .andExpect(jsonPath("$.errores[1].campo").value("cuentaIvaCreditoId"));
    }

    /** Regla (CLAUDE.md 14.2): el auditor lee la configuración y no la cambia (403 PLT-010); el contador sí. */
    @Test
    void laConfiguracionSoloLaEscribeUnContador() throws Exception {
        Sesion admin = sesionConContabilidad();
        Sesion auditor = sembrarMiembro(admin, "auditor");
        Sesion contador = sembrarMiembro(admin, "contador");
        String cuerpo =
                configuracion("SIN_IVA", cuentaId(admin.empresa(), "21020101"), cuentaId(admin.empresa(), "11040101"));

        get(auditor, "/contabilidad/configuracion").andExpect(status().isOk());
        put(auditor, "/contabilidad/configuracion", "\"0\"", cuerpo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        put(contador, "/contabilidad/configuracion", "\"0\"", cuerpo).andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------------------------- reglas

    /** Regla (ADR-035): 9 reglas precargadas; el filtro por tipo de operación devuelve las mismas 9. */
    @Test
    void lasReglasPrecargadasSeListan() throws Exception {
        Sesion s = sesionConContabilidad();

        get(s, "/contabilidad/reglas-contabilizacion")
                .andExpect(jsonPath("$.length()").value(9));
        get(s, "/contabilidad/reglas-contabilizacion?tipoOperacion=CIERRE_INGRESOS_DIARIO")
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].version").value(0));
    }

    /** Regla (ADR-035): OTRO se activa asignándole una cuenta de detalle; la versión sube y queda auditado. */
    @Test
    void activarOtroConUnaCuentaDa200() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID otro = reglaId(s, "OTRO");
        UUID banco = cuentaId(s.empresa(), "11010103");

        put(s, "/contabilidad/reglas-contabilizacion/" + otro, "\"0\"", regla(banco, true))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.cuenta.codigo").value("11010103"));
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'regla_contabilizacion'"
                                + " AND entidad_id = ?",
                        s.empresa(),
                        otro.toString()))
                .isEqualTo(1);
    }

    /** Regla (ADR-035): activar sin cuenta es 422 PLT-002 con el campo {@code cuentaId}; no cambia nada. */
    @Test
    void activarSinCuentaDa422ConElCampoCuentaId() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID otro = reglaId(s, "OTRO");

        put(s, "/contabilidad/reglas-contabilizacion/" + otro, "\"0\"", regla(null, true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaId"));
        assertThat(contar(
                        "SELECT count(*) FROM regla_contabilizacion WHERE id = ? AND NOT activa AND version = 0", otro))
                .isEqualTo(1);
    }

    /** Regla (CON-006): la cuenta de otra empresa, una cuenta padre o una inactiva no sirven para una regla. */
    @Test
    void laCuentaDeUnaReglaDebeSerDeDetalleActivaYDeLaEmpresa() throws Exception {
        Sesion s = sesionConContabilidad();
        Sesion otra = sesionConContabilidad();
        UUID efectivo = reglaId(s, "EFECTIVO");

        // 1. Cuenta de otra empresa
        put(
                        s,
                        "/contabilidad/reglas-contabilizacion/" + efectivo,
                        "\"0\"",
                        regla(cuentaId(otra.empresa(), "11010101"), true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaId"));
        // 2. Cuenta que no es de detalle
        put(
                        s,
                        "/contabilidad/reglas-contabilizacion/" + efectivo,
                        "\"0\"",
                        regla(cuentaId(s.empresa(), "110101"), true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaId"));
        // 3. Cuenta inactiva
        duenio.sql("UPDATE cuenta_contable SET activa = false WHERE empresa_id = ? AND codigo = '11010102'")
                .params(s.empresa())
                .update();
        put(
                        s,
                        "/contabilidad/reglas-contabilizacion/" + efectivo,
                        "\"0\"",
                        regla(cuentaId(s.empresa(), "11010102"), true))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-006"))
                .andExpect(jsonPath("$.errores[0].campo").value("cuentaId"));
    }

    /** Regla: desactivar una regla con cuenta es válido; la regla conserva su cuenta y la cuenta queda libre (CON-016). */
    @Test
    void desactivarUnaReglaLiberaSuCuentaParaDesactivarla() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID efectivo = reglaId(s, "EFECTIVO");
        UUID caja = cuentaId(s.empresa(), "11010101");

        put(s, "/contabilidad/reglas-contabilizacion/" + efectivo, "\"0\"", regla(caja, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        // Con la regla inactiva la caja ya no está en uso: se puede desactivar
        patch(s, "/contabilidad/cuentas/" + caja, "\"0\"", "{\"activa\":false}").andExpect(status().isOk());
    }

    /** Regla (PLT-015 y PLT-016): la edición de una regla exige If-Match con la versión actual. */
    @Test
    void editarUnaReglaExigeIfMatch() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID efectivo = reglaId(s, "EFECTIVO");
        UUID banco = cuentaId(s.empresa(), "11010103");

        put(s, "/contabilidad/reglas-contabilizacion/" + efectivo, null, regla(banco, true))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.codigo").value("PLT-015"));
        put(s, "/contabilidad/reglas-contabilizacion/" + efectivo, "\"9\"", regla(banco, true))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.codigo").value("PLT-016"));
    }

    /** Regla (CLAUDE.md 14.2): el auditor lee las reglas (200) y no las escribe (403 PLT-010); el contador sí. */
    @Test
    void lasReglasSoloLasEscribeUnContador() throws Exception {
        Sesion admin = sesionConContabilidad();
        Sesion auditor = sembrarMiembro(admin, "auditor");
        Sesion contador = sembrarMiembro(admin, "contador");
        UUID otro = reglaId(admin, "OTRO");
        String cuerpo = regla(cuentaId(admin.empresa(), "11010103"), true);

        get(auditor, "/contabilidad/reglas-contabilizacion").andExpect(status().isOk());
        put(auditor, "/contabilidad/reglas-contabilizacion/" + otro, "\"0\"", cuerpo)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        put(contador, "/contabilidad/reglas-contabilizacion/" + otro, "\"0\"", cuerpo)
                .andExpect(status().isOk());
    }

    /** Regla (ADR-002): editar la regla de otra empresa es 404 PLT-017. */
    @Test
    void editarLaReglaDeOtraEmpresaDa404() throws Exception {
        Sesion a = sesionConContabilidad();
        Sesion b = sesionConContabilidad();
        UUID reglaDeB = reglaId(b, "EFECTIVO");

        put(
                        a,
                        "/contabilidad/reglas-contabilizacion/" + reglaDeB,
                        "\"0\"",
                        regla(cuentaId(a.empresa(), "11010103"), true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
    }
}
