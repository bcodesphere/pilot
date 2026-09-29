package com.bcodesphere.pilot.aceptacion.f1;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Aceptación F1, criterio 4 del plan: "un X-Empresa-Id sin membresía devuelve 403 PLT-003" (docs/plan-de-trabajo.md
 * F1; CLAUDE.md 4.5 y 8.4). Se prueba con la empresa de otro usuario, con un UUID que no existe y con una membresía
 * INACTIVA, en dos rutas distintas (catálogo de apps y API keys), para que no dependa de una sola.
 */
class EmpresaActivaSinMembresiaIT extends BaseAceptacionF1IT {

    /** Rutas de usuario sobre las que se comprueba el rechazo. */
    private static final String[] RUTAS = {"/aplicaciones", "/api-keys"};

    /** Fuente: criterio 4 del plan. V pide la empresa A (de U) sin ser miembro: 403 PLT-003. */
    @Test
    void laEmpresaDeOtroUsuarioDevuelve403Plt003() throws Exception {
        UUID empresaDeOtro = empresaDe(iniciarSesion(nuevoSub()));
        String sub = nuevoSub();
        iniciarSesion(sub);

        for (String ruta : RUTAS) {
            consultar(sub, empresaDeOtro, ruta)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-003"));
        }
    }

    /** Fuente: ValidarMembresia (F1-04): un UUID sin empresa se trata igual que uno ajeno, sin revelar la diferencia. */
    @Test
    void unaEmpresaInexistenteDevuelve403Plt003() throws Exception {
        String sub = nuevoSub();
        iniciarSesion(sub);

        for (String ruta : RUTAS) {
            consultar(sub, UUID.randomUUID(), ruta)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-003"));
        }
    }

    /**
     * Fuente: CLAUDE.md 9.2 (membresía ACTIVA o INACTIVA). Con la membresía en INACTIVA el usuario pierde el acceso a la
     * empresa: 403 PLT-003; al reactivarla vuelve a verla (demuestra que el rechazo se debe al estado y no a otra causa).
     */
    @Test
    void unaMembresiaInactivaDevuelve403Plt003() throws Exception {
        String sub = nuevoSub();
        String me = iniciarSesion(sub);
        UUID empresa = empresaDe(me);
        UUID usuario = usuarioDe(me);

        // 1. Con la membresía activa ve la empresa
        consultar(sub, empresa, "/aplicaciones").andExpect(status().isOk());

        // 2. Se inactiva (siembra como dueño): 403 PLT-003
        duenio.sql("UPDATE empresa_usuario SET estado = 'INACTIVA' WHERE empresa_id = ? AND usuario_id = ?")
                .params(empresa, usuario)
                .update();
        for (String ruta : RUTAS) {
            consultar(sub, empresa, ruta)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.codigo").value("PLT-003"));
        }

        // 3. Se reactiva: vuelve el acceso
        duenio.sql("UPDATE empresa_usuario SET estado = 'ACTIVA' WHERE empresa_id = ? AND usuario_id = ?")
                .params(empresa, usuario)
                .update();
        consultar(sub, empresa, "/aplicaciones").andExpect(status().isOk());
    }
}
