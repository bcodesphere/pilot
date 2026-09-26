package com.bcodesphere.pilot.aceptacion.f1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aceptación F1, criterio 1 del plan: "encuentra su empresa personal ya creada" tras el primer inicio de sesión
 * (docs/plan-de-trabajo.md F1; CLAUDE.md 2.1; ADR-029). El primer {@code GET /me} con un {@code sub} nuevo da de alta
 * al usuario, a su empresa PERSONAL con su nombre y a su membresía {@code admin_empresa}; un segundo {@code GET /me}
 * no duplica nada; el alta del usuario queda en {@code auditoria_global} (ADR-025).
 */
class PrimerInicioSesionIT extends BaseAceptacionF1IT {

    /**
     * Fuente del valor esperado: ADR-029 (empresa personal automática con el usuario como admin_empresa) y F1-04
     * (nombre de la empresa = nombre del usuario). Se verifica la respuesta y las tres tablas con SQL como dueño.
     */
    @Test
    void elPrimerInicioDeSesionCreaUsuarioEmpresaPersonalYMembresiaAdmin() throws Exception {
        String sub = nuevoSub();
        // Antes: el usuario no existe
        assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                .isZero();

        String me = iniciarSesion(sub);
        UUID usuario = usuarioDe(me);
        UUID empresa = empresaDe(me);

        // 1. La respuesta trae una sola membresía: la empresa PERSONAL con el nombre del usuario y rol admin_empresa
        mvc.perform(get("/api/v1/me").with(token(sub)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membresias.length()").value(1))
                .andExpect(jsonPath("$.membresias[0].tipoEmpresa").value("PERSONAL"))
                .andExpect(jsonPath("$.membresias[0].nombreEmpresa").value("Nombre de " + sub))
                .andExpect(jsonPath("$.membresias[0].rol").value("admin_empresa"));

        // 2. En la base: usuario con su sub, empresa PERSONAL de su propiedad con su nombre y membresía ACTIVA
        assertThat(contar("SELECT count(*) FROM usuario WHERE id = ? AND sub_keycloak = ?", usuario, sub))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM empresa WHERE id = ? AND tipo = 'PERSONAL' AND propietario_id = ?"
                                + " AND nombre = ? AND nit IS NULL",
                        empresa,
                        usuario,
                        "Nombre de " + sub))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM empresa_usuario WHERE empresa_id = ? AND usuario_id = ?"
                                + " AND rol = 'admin_empresa' AND estado = 'ACTIVA'",
                        empresa,
                        usuario))
                .isEqualTo(1);
    }

    /** Fuente: F1-04 (alta idempotente). Un segundo {@code GET /me} devuelve lo mismo y no crea filas nuevas. */
    @Test
    void unSegundoInicioDeSesionNoDuplicaNada() throws Exception {
        String sub = nuevoSub();
        String primero = iniciarSesion(sub);
        UUID usuario = usuarioDe(primero);

        String segundo = iniciarSesion(sub);

        // 1. Mismo usuario y misma empresa
        assertThat(usuarioDe(segundo)).isEqualTo(usuario);
        assertThat(empresaDe(segundo)).isEqualTo(empresaDe(primero));
        // 2. Una sola fila de cada cosa
        assertThat(contar("SELECT count(*) FROM usuario WHERE sub_keycloak = ?", sub))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa WHERE propietario_id = ?", usuario))
                .isEqualTo(1);
        assertThat(contar("SELECT count(*) FROM empresa_usuario WHERE usuario_id = ?", usuario))
                .isEqualTo(1);
    }

    /**
     * Fuente: ADR-025 y CLAUDE.md 1.1.11 (toda mutación se audita). El alta del usuario queda en auditoria_global con
     * acción CREAR y su id; el segundo inicio de sesión no agrega otra fila. La empresa y la membresía se auditan en
     * {@code auditoria} (con empresa).
     */
    @Test
    void elAltaDelUsuarioQuedaEnLaAuditoriaGlobalUnaSolaVez() throws Exception {
        String sub = nuevoSub();
        String me = iniciarSesion(sub);
        UUID usuario = usuarioDe(me);
        UUID empresa = empresaDe(me);
        iniciarSesion(sub); // segundo inicio: no debe auditar otra alta

        assertThat(contar(
                        "SELECT count(*) FROM auditoria_global WHERE entidad = 'usuario' AND entidad_id = ?"
                                + " AND accion = 'CREAR'",
                        usuario.toString()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria_global WHERE entidad = 'usuario' AND entidad_id = ?",
                        usuario.toString()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND entidad IN ('empresa', 'empresa_usuario')"
                                + " AND accion = 'CREAR'",
                        empresa))
                .isEqualTo(2);
    }
}
