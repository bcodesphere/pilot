package com.bcodesphere.pilot.plataforma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.GeneradorId;
import org.junit.jupiter.api.Test;

/** Pruebas del contexto de empresa por hilo (CLAUDE.md 4.5). */
class ContextoEmpresaTest {

    private static final EmpresaId EMPRESA = new EmpresaId(GeneradorId.nuevo());

    /** Caso: sin contexto, pedir la empresa falla con un error claro y el usuario es "sistema". */
    @Test
    void sinContextoLaEmpresaEsRequeridaYElUsuarioEsSistema() {
        assertThatThrownBy(ContextoEmpresa::empresaRequerida)
                .isInstanceOf(ContextoEmpresaAusenteException.class)
                .hasMessageContaining("empresa activa");
        assertThat(ContextoEmpresa.usuarioOSistema()).isEqualTo(ContextoEmpresa.USUARIO_SISTEMA);
        assertThat(ContextoEmpresa.hayEmpresa()).isFalse();
    }

    /** Caso: dentro de ejecutarCon se ven empresa y usuario; al salir el contexto queda limpio. */
    @Test
    void ejecutarConEstableceYLuegoLimpia() {
        String resultado = ContextoEmpresa.ejecutarCon(EMPRESA, "u-1", () -> {
            assertThat(ContextoEmpresa.empresaRequerida()).isEqualTo(EMPRESA);
            return ContextoEmpresa.usuarioOSistema();
        });
        assertThat(resultado).isEqualTo("u-1");
        assertThat(ContextoEmpresa.hayEmpresa()).isFalse();
    }

    /** Caso: si la acción lanza una excepción, el contexto igual se limpia (finally). */
    @Test
    void ejecutarConLimpiaAunqueLaAccionFalle() {
        assertThatThrownBy(() -> ContextoEmpresa.ejecutarCon(EMPRESA, "u-1", () -> {
                    throw new IllegalStateException("falla");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ContextoEmpresa.hayEmpresa()).isFalse();
    }

    /** Caso: un usuario nulo se registra como "sistema". */
    @Test
    void usuarioNuloEsSistema() {
        ContextoEmpresa.ejecutarCon(EMPRESA, null, (Runnable)
                () -> assertThat(ContextoEmpresa.usuarioOSistema()).isEqualTo(ContextoEmpresa.USUARIO_SISTEMA));
    }

    /** Caso: un contexto anidado restaura el exterior al terminar. */
    @Test
    void ejecutarConAnidadoRestauraElContextoExterior() {
        EmpresaId otra = new EmpresaId(GeneradorId.nuevo());
        ContextoEmpresa.ejecutarCon(EMPRESA, "externo", (Runnable) () -> {
            ContextoEmpresa.ejecutarCon(otra, "interno", (Runnable)
                    () -> assertThat(ContextoEmpresa.empresaRequerida()).isEqualTo(otra));
            assertThat(ContextoEmpresa.empresaRequerida()).isEqualTo(EMPRESA);
            assertThat(ContextoEmpresa.usuarioOSistema()).isEqualTo("externo");
        });
    }

    /** Caso: una empresa nula se rechaza sin dejar contexto a medias. */
    @Test
    void empresaNulaSeRechaza() {
        assertThatThrownBy(() -> ContextoEmpresa.ejecutarCon(null, "u", (Runnable) () -> {}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ContextoEmpresa.hayEmpresa()).isFalse();
    }

    /**
     * Caso (ADR-026): dentro de ejecutarSinEmpresa no hay empresa, pero el modo es explícito: hayEmpresa es falso,
     * empresaRequerida SIGUE lanzando error y el usuario se conserva.
     */
    @Test
    void modoSinEmpresaNoTieneEmpresaPeroEsExplicito() {
        ContextoEmpresa.ejecutarSinEmpresa("u-sin", (Runnable) () -> {
            assertThat(ContextoEmpresa.enModoSinEmpresa()).isTrue();
            assertThat(ContextoEmpresa.hayEmpresa()).isFalse();
            assertThat(ContextoEmpresa.usuarioOSistema()).isEqualTo("u-sin");
            assertThatThrownBy(ContextoEmpresa::empresaRequerida).isInstanceOf(ContextoEmpresaAusenteException.class);
        });
        assertThat(ContextoEmpresa.enModoSinEmpresa()).isFalse();
    }

    /** Caso: el modo sin empresa se restaura al salir, incluso con error, y un contexto con empresa lo reemplaza y lo devuelve. */
    @Test
    void modoSinEmpresaSeRestauraYSeAnidaConEmpresa() {
        assertThatThrownBy(() -> ContextoEmpresa.ejecutarSinEmpresa("u", () -> {
                    throw new IllegalStateException("falla");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ContextoEmpresa.enModoSinEmpresa()).isFalse();

        ContextoEmpresa.ejecutarSinEmpresa("u", (Runnable) () -> {
            ContextoEmpresa.ejecutarCon(EMPRESA, "u", (Runnable) () -> {
                assertThat(ContextoEmpresa.hayEmpresa()).isTrue();
                assertThat(ContextoEmpresa.enModoSinEmpresa()).isFalse();
            });
            assertThat(ContextoEmpresa.enModoSinEmpresa()).isTrue();
        });
    }

    /** Caso: un usuario nulo en modo sin empresa se registra como "sistema" (aún no se conoce al usuario). */
    @Test
    void modoSinEmpresaConUsuarioNuloEsSistema() {
        ContextoEmpresa.ejecutarSinEmpresa(null, (Runnable)
                () -> assertThat(ContextoEmpresa.usuarioOSistema()).isEqualTo(ContextoEmpresa.USUARIO_SISTEMA));
    }
}
