package com.bcodesphere.pilot.aceptacion.f2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Aceptación de F2, criterio 1 (segunda parte): «si la precarga falla, la instalación se revierte». A diferencia de
 * {@code PrecargaContabilidadFallidaIT} (que simula el puerto con un mock), aquí el fallo es real y ocurre en
 * PostgreSQL a mitad de la precarga: un trigger {@code BEFORE INSERT} sobre {@code regla_contabilizacion} (el último
 * paso, ADR-035) lanza una excepción solo para la empresa de la prueba, cuando ya se copiaron las cuentas y la
 * configuración. Si la precarga corriera fuera de la transacción de la instalación, esos pasos previos y la propia
 * instalación quedarían guardados; la prueba lo detecta (ver la mutación en el reporte de F2-05).
 */
class PrecargaFallidaEnPostgresF2IT extends BaseAceptacionF2IT {

    /** Nombres únicos por ejecución: el trigger y su función viven en el esquema compartido de la base de pruebas. */
    private final String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private final String funcion = "f2_05_fallo_precarga_" + sufijo;
    private final String disparador = "trg_f2_05_fallo_precarga_" + sufijo;

    /** Elimina siempre el trigger y su función, también si la prueba falla a mitad, para no afectar a otras clases. */
    @AfterEach
    void limpiarTrigger() {
        duenio.sql("DROP TRIGGER IF EXISTS " + disparador + " ON regla_contabilizacion")
                .update();
        duenio.sql("DROP FUNCTION IF EXISTS " + funcion + "()").update();
    }

    /**
     * Un fallo real de PostgreSQL en el último paso de la precarga deja la empresa exactamente como antes de instalar
     * (sin filas en ninguna tabla ni auditoría) y una nueva instalación, ya sin el fallo, funciona sin intervención.
     */
    @Test
    void unFalloRealDePostgresAMitadDeLaPrecargaRevierteLaInstalacionYSePuedeReintentar() throws Exception {
        Sesion s = nuevaSesion();

        // 1. Trigger que falla solo para la empresa de esta sesión (el UUID es propio, sin riesgo de inyección)
        duenio.sql("CREATE FUNCTION " + funcion + "() RETURNS trigger AS $$ BEGIN"
                        + " IF NEW.empresa_id = '" + s.empresa() + "'::uuid THEN"
                        + " RAISE EXCEPTION 'fallo simulado F2-05 en la precarga'; END IF;"
                        + " RETURN NEW; END; $$ LANGUAGE plpgsql")
                .update();
        duenio.sql("CREATE TRIGGER " + disparador + " BEFORE INSERT ON regla_contabilizacion"
                        + " FOR EACH ROW EXECUTE FUNCTION " + funcion + "()")
                .update();

        // 2. La creación de la empresa personal ya dejó filas de auditoría propias de F1: se cuentan antes de instalar
        int auditoriaAntes = contar("SELECT count(*) FROM auditoria WHERE empresa_id = ?", s.empresa());

        // 3. Instalar: el fallo llega como error interno (500 PLT-500), nunca 201
        instalarContabilidad(s)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("PLT-500"));

        // 4. Como dueño: nada de esa empresa quedó guardado, ni siquiera lo que la precarga alcanzó a insertar
        for (String tabla :
                new String[] {"empresa_aplicacion", "cuenta_contable", "configuracion_contable", "regla_contabilizacion"
                }) {
            assertThat(contar("SELECT count(*) FROM " + tabla + " WHERE empresa_id = ?", s.empresa()))
                    .as("filas de %s tras el fallo", tabla)
                    .isZero();
        }

        // 4b. Auditoría: ni la instalación ni PRECARGAR, y ninguna fila nueva respecto de antes de instalar
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND accion IN ('INSTALAR', 'PRECARGAR')",
                        s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM auditoria WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(auditoriaAntes);

        // 5. El catálogo de apps sigue mostrando Contabilidad como disponible
        get(s, "/aplicaciones")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo == 'contabilidad')].estado").value("DISPONIBLE"));

        // 6. Sin el fallo, reinstalar funciona sin intervención manual y deja la precarga completa
        limpiarTrigger();
        instalarContabilidad(s).andExpect(status().isCreated());
        int cuentasPlantilla = contar("SELECT count(*) FROM plantilla_cuenta");
        int reglasPlantilla = contar("SELECT count(*) FROM plantilla_regla_contabilizacion");
        assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(cuentasPlantilla);
        assertThat(contar("SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(reglasPlantilla);
        assertThat(contar("SELECT count(*) FROM configuracion_contable WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(1);
        assertThat(contar(
                        "SELECT count(*) FROM auditoria WHERE empresa_id = ? AND accion IN ('INSTALAR', 'PRECARGAR')",
                        s.empresa()))
                .isEqualTo(2);
    }
}
