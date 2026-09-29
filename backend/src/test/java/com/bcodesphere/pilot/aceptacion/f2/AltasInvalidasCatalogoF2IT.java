package com.bcodesphere.pilot.aceptacion.f2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Aceptación de F2, criterio 2: «no se puede crear una cuenta con código {@code 6…}, con un padre que no sea prefijo
 * de su código, ni un código duplicado». Fuente: CLAUDE.md 10.2 (tabla de códigos {@code CON-010}, {@code CON-014} y
 * {@code CON-015}) y ADR-035. Cada rechazo se comprueba por la API y en la base: no queda ninguna fila nueva.
 */
class AltasInvalidasCatalogoF2IT extends BaseAceptacionF2IT {

    private static String cuerpo(String codigo) {
        return "{\"codigo\":\"" + codigo + "\",\"nombre\":\"Cuenta de prueba " + codigo + "\"}";
    }

    private int cuentasDe(Sesion s) {
        return contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ?", s.empresa());
    }

    /** Un código de la clase 6 (fuera del catálogo, CLAUDE.md 10.2) se rechaza con 422 {@code CON-010}, sea cual sea su longitud. */
    @Test
    void unCodigoDeLaClase6SeRechazaConCon010() throws Exception {
        Sesion s = sesionConContabilidad();
        int antes = cuentasDe(s);

        for (String codigo : new String[] {"6", "61", "61010101"}) {
            post(s, "/contabilidad/cuentas", cuerpo(codigo))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.codigo").value("CON-010"));
        }

        assertThat(cuentasDe(s)).isEqualTo(antes);
    }

    /**
     * Un padre que no existe (8 dígitos cuya cuenta de 6 no está en el catálogo) o una longitud no válida (3 dígitos)
     * se rechazan con 422 {@code CON-015}. El código sin padre se elige de la plantilla y se comprueba ausente.
     */
    @Test
    void unPadreInexistenteOUnaLongitudInvalidaSeRechazanConCon015() throws Exception {
        Sesion s = sesionConContabilidad();
        int antes = cuentasDe(s);

        // 1. Una cuenta de 4 dígitos existente a la que le falta la subcuenta "99": el padre de 6 dígitos no existe
        String cuenta = duenio.sql("SELECT p.codigo FROM plantilla_cuenta p WHERE p.nivel = 3 AND NOT EXISTS"
                        + " (SELECT 1 FROM plantilla_cuenta h WHERE h.codigo = p.codigo || '99')"
                        + " ORDER BY p.codigo LIMIT 1")
                .query(String.class)
                .single();
        String sinPadre = cuenta + "9901";
        assertThat(contar("SELECT count(*) FROM plantilla_cuenta WHERE codigo LIKE ?", cuenta + "99%"))
                .as("el padre elegido no debe existir en la plantilla")
                .isZero();

        post(s, "/contabilidad/cuentas", cuerpo(sinPadre))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));
        // 2. Longitud de 3 dígitos: no es ningún nivel del catálogo (1, 2, 4, 6 u 8)
        post(s, "/contabilidad/cuentas", cuerpo("110"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("CON-015"));

        assertThat(cuentasDe(s)).isEqualTo(antes);
    }

    /**
     * Un código que ya existe (de la precarga, o creado un instante antes) se rechaza con 409 {@code CON-014}; la
     * unicidad es por empresa, así que otra empresa sí puede crear el mismo código.
     */
    @Test
    void unCodigoDuplicadoSeRechazaConCon014PeroSoloDentroDeLaEmpresa() throws Exception {
        Sesion s = sesionConContabilidad();
        Sesion otra = sesionConContabilidad();
        String nuevo = "11010104";
        assertThat(contar("SELECT count(*) FROM plantilla_cuenta WHERE codigo = ?", nuevo))
                .as("el código nuevo no debe estar en la plantilla")
                .isZero();
        int antes = cuentasDe(s);

        // 1. Uno de la precarga
        String existente = duenio.sql("SELECT codigo FROM plantilla_cuenta WHERE nivel = 5 ORDER BY codigo LIMIT 1")
                .query(String.class)
                .single();
        post(s, "/contabilidad/cuentas", cuerpo(existente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CON-014"));
        assertThat(cuentasDe(s)).isEqualTo(antes);

        // 2. Uno nuevo: la primera alta pasa y la segunda del mismo código choca
        post(s, "/contabilidad/cuentas", cuerpo(nuevo)).andExpect(status().isCreated());
        post(s, "/contabilidad/cuentas", cuerpo(nuevo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CON-014"));
        assertThat(cuentasDe(s)).isEqualTo(antes + 1);

        // 3. Otra empresa puede crear el mismo código
        post(otra, "/contabilidad/cuentas", cuerpo(nuevo)).andExpect(status().isCreated());
        assertThat(contar(
                        "SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?",
                        otra.empresa(),
                        nuevo))
                .isEqualTo(1);
    }
}
