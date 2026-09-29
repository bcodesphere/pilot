package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de integración del catálogo de cuentas por la API (F2-03). Fuente: CLAUDE.md 10.2 y 13, ADR-035 y el
 * criterio de F2 del plan de trabajo. Los valores del catálogo base son los del PDF de la U. Católica (ADR-044,
 * tarea CAT): 455 cuentas, 333 de detalle. Las pruebas de {@code CON-011} y {@code CON-012} con movimientos reales están
 * en {@code LibroDiarioIT} (F3-03, ADR-035 decisión 5).
 */
class CatalogoCuentasIT extends BaseContabilidadIT {

    private static String nueva(String codigo, String nombre) {
        return "{\"codigo\":\"" + codigo + "\",\"nombre\":\"" + nombre + "\"}";
    }

    // --------------------------------------------------------------------------------------------- lectura

    /** Regla (ADR-035 punto 3): el catálogo sale completo y sin paginar, con sus filtros opcionales. */
    @Test
    void elCatalogoSeListaCompletoYConFiltros() throws Exception {
        Sesion s = sesionConContabilidad();

        get(s, "/contabilidad/cuentas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(455));
        get(s, "/contabilidad/cuentas?soloDetalle=true")
                .andExpect(jsonPath("$.length()").value(333));
        // La búsqueda por prefijo de código y por nombre, sin distinguir mayúsculas
        get(s, "/contabilidad/cuentas?busqueda=1101010")
                .andExpect(jsonPath("$[*].codigo").value(org.hamcrest.Matchers.hasItem("11010101")));
        get(s, "/contabilidad/cuentas?busqueda=CAJA GENERAL")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("11010101"));
        // Un comodín del cliente se busca literalmente: "%" no devuelve todo el catálogo
        get(s, "/contabilidad/cuentas?busqueda=%25")
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Regla: la cuenta trae clase, nivel, padre, naturaleza, si acepta movimientos y su versión como ETag. */
    @Test
    void unaCuentaSeObtieneConSuEtag() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");

        get(s, "/contabilidad/cuentas/" + caja)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.codigo").value("11010101"))
                .andExpect(jsonPath("$.clase").value(1))
                .andExpect(jsonPath("$.nivel").value(5))
                .andExpect(jsonPath("$.naturaleza").value("DEUDORA"))
                .andExpect(jsonPath("$.aceptaMovimientos").value(true))
                .andExpect(jsonPath("$.cuentaPadreId")
                        .value(cuentaId(s.empresa(), "110101").toString()));
    }

    // ---------------------------------------------------------------------------------------------- alta

    /** Regla (CLAUDE.md 10.2): la hija de 110101 (que ya tiene hijas) es hoja y su padre sigue sin aceptar movimientos. */
    @Test
    void crearUnaCuentaDeDetalleBajoUnPadreExistenteDa201ConEtag() throws Exception {
        Sesion s = sesionConContabilidad();

        post(s, "/contabilidad/cuentas", nueva("11010104", "Caja de ventas"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.codigo").value("11010104"))
                .andExpect(jsonPath("$.nivel").value(5))
                .andExpect(jsonPath("$.aceptaMovimientos").value(true))
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.naturaleza").value("DEUDORA"))
                .andExpect(jsonPath("$.cuentaPadreId")
                        .value(cuentaId(s.empresa(), "110101").toString()));

        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND codigo = '110101'"
                                + " AND acepta_movimientos",
                        s.empresa()))
                .isZero();
        // Auditada como CREAR, sin valor anterior
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad = 'cuenta_contable'"
                                + " AND accion = 'CREAR' AND valor_anterior IS NULL",
                        s.empresa()))
                .isEqualTo(1);
    }

    /**
     * Regla (ADR-035, F2-03): una subcuenta hoja (110102 bajo 1101) acepta movimientos; al crear su primera hija
     * (11010201) deja de aceptarlos; una cuenta de 8 dígitos no puede tener hijas (código de 10 dígitos = CON-015).
     */
    @Test
    void unaSubcuentaHojaDejaDeAceptarMovimientosAlRecibirSuPrimeraHija() throws Exception {
        Sesion s = sesionConContabilidad();

        post(s, "/contabilidad/cuentas", nueva("110906", "Efectivo en tránsito"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aceptaMovimientos").value(true));

        post(s, "/contabilidad/cuentas", nueva("11090601", "Remesas en camino"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aceptaMovimientos").value(true));

        get(s, "/contabilidad/cuentas/" + cuentaId(s.empresa(), "110906"))
                .andExpect(jsonPath("$.aceptaMovimientos").value(false))
                // El padre cambió, así que su versión subió
                .andExpect(header().string("ETag", "\"1\""));

        // Una cuenta de detalle (8 dígitos) no puede tener hijas: no existe un nivel de 10 dígitos; el contrato
        // (máximo 8 caracteres) lo corta antes del dominio con 422 PLT-002
        post(s, "/contabilidad/cuentas", nueva("1109060101", "Imposible"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"));
    }

    /**
     * Regla (CON-016): si la hoja 110102 la usa una regla activa, crearle una hija la dejaría sin aceptar movimientos
     * y se rechaza.
     */
    @Test
    void crearUnaHijaBajoUnaHojaUsadaPorUnaReglaActivaDaCon016() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID hoja = UUID.fromString(leer(
                post(s, "/contabilidad/cuentas", nueva("110906", "Efectivo en tránsito"))
                        .andExpect(status().isCreated()),
                "$.id"));
        // La regla OTRO nace inactiva y sin cuenta: se activa con la hoja
        java.util.List<String> ids = leer(get(s, "/contabilidad/reglas-contabilizacion"), "$[?(@.codigo=='OTRO')].id");
        UUID otro = UUID.fromString(ids.get(0));
        put(
                        s,
                        "/contabilidad/reglas-contabilizacion/" + otro,
                        "\"0\"",
                        "{\"cuentaId\":\"" + hoja + "\",\"activa\":true}")
                .andExpect(status().isOk());

        post(s, "/contabilidad/cuentas", nueva("11090601", "Remesas en camino"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-016"));
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND codigo = '11090601'",
                        s.empresa()))
                .isZero();
    }

    /** Reglas del alta: clase 6 (CON-010), longitud 3 (CON-015), padre inexistente (CON-015) y duplicado (CON-014). */
    @Test
    void lasAltasInvalidasSeRechazanConSuCodigo() throws Exception {
        Sesion s = sesionConContabilidad();

        post(s, "/contabilidad/cuentas", nueva("6101", "Cuenta liquidadora"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-010"));
        post(s, "/contabilidad/cuentas", nueva("110", "Longitud tres"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));
        // Padre 110199 no existe en el catálogo base (ADR-044)
        post(s, "/contabilidad/cuentas", nueva("11019999", "Sin padre"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));
        post(s, "/contabilidad/cuentas", nueva("11010101", "Duplicada"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CON-014"));

        // Ninguna de las cuatro dejó filas
        assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(455);
    }

    /** Regla: un padre inactivo no admite hijas (CON-015, «existente y activa»). */
    @Test
    void unPadreInactivoNoAdmiteHijas() throws Exception {
        Sesion s = sesionConContabilidad();
        // 110301 (Estimación para cuentas incobrables) no lo usa ninguna regla ni la configuración, pero es padre
        // potencial: se desactiva a mano
        duenio.sql("UPDATE cuenta_contable SET activa = false WHERE empresa_id = ? AND codigo = '110301'")
                .params(s.empresa())
                .update();

        post(s, "/contabilidad/cuentas", nueva("11030102", "Inventario nuevo"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));
    }

    // ------------------------------------------------------------------------------------- actualización

    /** Regla (CLAUDE.md 8.3, PLT-015): sin {@code If-Match} la edición se rechaza con 428. */
    @Test
    void editarSinIfMatchDa428() throws Exception {
        Sesion s = sesionConContabilidad();

        patch(s, "/contabilidad/cuentas/" + cuentaId(s.empresa(), "11010102"), null, "{\"nombre\":\"Caja menor\"}")
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.codigo").value("PLT-015"));
    }

    /** Regla (PLT-016): con una versión que ya no es la actual la edición se rechaza con 412 y no cambia nada. */
    @Test
    void editarConUnaVersionVieja412() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID id = cuentaId(s.empresa(), "11010102");

        patch(s, "/contabilidad/cuentas/" + id, "\"7\"", "{\"nombre\":\"Caja menor\"}")
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.codigo").value("PLT-016"));
        // Formato inválido (sin comillas) también es 412
        patch(s, "/contabilidad/cuentas/" + id, "0", "{\"nombre\":\"Caja menor\"}")
                .andExpect(status().isPreconditionFailed());
        assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE id = ? AND nombre = 'Caja Chica'", id))
                .isEqualTo(1);
    }

    /** Regla: renombrar da 200 con la versión nueva y deja auditoría con el nombre anterior y el nuevo. */
    @Test
    void renombrarUnaCuentaDa200YQuedaAuditado() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID id = cuentaId(s.empresa(), "11010102");

        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"nombre\":\"  Caja menor  \"}")
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.nombre").value("Caja menor"))
                .andExpect(jsonPath("$.version").value(1));

        String anterior = duenio.sql("SELECT valor_anterior::text FROM auditoria WHERE empresa_id = ?"
                        + " AND entidad_id = ? AND accion = 'ACTUALIZAR'")
                .params(s.empresa(), id.toString())
                .query(String.class)
                .single();
        String nuevo = duenio.sql("SELECT valor_nuevo::text FROM auditoria WHERE empresa_id = ?"
                        + " AND entidad_id = ? AND accion = 'ACTUALIZAR'")
                .params(s.empresa(), id.toString())
                .query(String.class)
                .single();
        assertThat(anterior).contains("Caja Chica");
        assertThat(nuevo).contains("Caja menor");
        // Reintentar con la versión vieja ya es 412
        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"nombre\":\"Otra\"}")
                .andExpect(status().isPreconditionFailed());
    }

    /**
     * Regla (F2-03, decisión conservadora): el código solo puede cambiar dentro del mismo nivel y padre (110101…);
     * otro nivel o un código repetido se rechaza.
     */
    @Test
    void elCodigoSoloCambiaDentroDelMismoNivelYPadre() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID id = UUID.fromString(leer(
                post(s, "/contabilidad/cuentas", nueva("11010104", "Caja de ventas"))
                        .andExpect(status().isCreated()),
                "$.id"));

        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"codigo\":\"11010105\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("11010105"));
        // Otro nivel: 6 dígitos
        patch(s, "/contabilidad/cuentas/" + id, "\"1\"", "{\"codigo\":\"110109\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));
        // Otro padre: 110201
        patch(s, "/contabilidad/cuentas/" + id, "\"1\"", "{\"codigo\":\"11020104\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));
        // Código repetido (11010101 ya existe)
        patch(s, "/contabilidad/cuentas/" + id, "\"1\"", "{\"codigo\":\"11010101\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CON-014"));
        // Clase inválida
        patch(s, "/contabilidad/cuentas/" + id, "\"1\"", "{\"codigo\":\"61010105\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-010"));
    }

    /** Regla (CON-016): 11010101 la usa la regla EFECTIVO, así que no se puede desactivar. */
    @Test
    void desactivarUnaCuentaUsadaPorUnaReglaActivaDaCon016() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");

        patch(s, "/contabilidad/cuentas/" + caja, "\"0\"", "{\"activa\":false}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-016"));
        // También 21020101 (Proveedores nacionales, ADR-044): la usan varias reglas guiadas activas (pago a
        // crédito y contrapartida de PAGO_PROVEEDOR)
        patch(s, "/contabilidad/cuentas/" + cuentaId(s.empresa(), "21020101"), "\"0\"", "{\"activa\":false}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-016"));
    }

    /** Regla: una cuenta sin uso ni saldo se desactiva, sale con {@code soloActivas} y se puede reactivar. */
    @Test
    void unaCuentaSinUsoSeDesactivaYSeReactiva() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID id = cuentaId(s.empresa(), "11010102");

        patch(s, "/contabilidad/cuentas/" + id, "\"0\"", "{\"activa\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        get(s, "/contabilidad/cuentas?soloActivas=true")
                .andExpect(jsonPath("$.length()").value(454));
        patch(s, "/contabilidad/cuentas/" + id, "\"1\"", "{\"activa\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    /** Regla: un nombre en blanco es un error de validación (422 PLT-002) y no cambia la cuenta. */
    @Test
    void unNombreEnBlancoDa422() throws Exception {
        Sesion s = sesionConContabilidad();

        patch(s, "/contabilidad/cuentas/" + cuentaId(s.empresa(), "11010102"), "\"0\"", "{\"nombre\":\"   \"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("PLT-002"))
                .andExpect(jsonPath("$.errores[0].campo").value("nombre"));
        post(s, "/contabilidad/cuentas", nueva("11010104", " ")).andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------------------------------- roles

    /** Regla (CLAUDE.md 14.2): el auditor lee (200) y no escribe (403 PLT-010); el contador escribe (201). */
    @Test
    void elAuditorLeeYNoEscribeYElContadorEscribe() throws Exception {
        Sesion admin = sesionConContabilidad();
        Sesion auditor = sembrarMiembro(admin, "auditor");
        Sesion contador = sembrarMiembro(admin, "contador");
        UUID id = cuentaId(admin.empresa(), "11010102");

        get(auditor, "/contabilidad/cuentas").andExpect(status().isOk());
        get(auditor, "/contabilidad/cuentas/" + id).andExpect(status().isOk());
        post(auditor, "/contabilidad/cuentas", nueva("11010104", "Caja de ventas"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));
        patch(auditor, "/contabilidad/cuentas/" + id, "\"0\"", "{\"nombre\":\"X\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PLT-010"));

        post(contador, "/contabilidad/cuentas", nueva("11010104", "Caja de ventas"))
                .andExpect(status().isCreated());
    }

    /** Regla (ADR-002): editar la cuenta de otra empresa es un 404 PLT-017, sin revelar que existe. */
    @Test
    void editarLaCuentaDeOtraEmpresaDa404() throws Exception {
        Sesion a = sesionConContabilidad();
        Sesion b = sesionConContabilidad();

        patch(a, "/contabilidad/cuentas/" + cuentaId(b.empresa(), "11010102"), "\"0\"", "{\"nombre\":\"Robada\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PLT-017"));
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND nombre = 'Robada'", b.empresa()))
                .isZero();
    }
}
