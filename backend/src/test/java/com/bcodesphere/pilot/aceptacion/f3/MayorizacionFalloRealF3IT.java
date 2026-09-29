package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Criterio F3 "Mayorización" (docs/plan-de-trabajo.md F3, CLAUDE.md §4.1 y §10.3, ADR-018): "los saldos cambian en la
 * misma transacción del asiento; si el guardado falla, ningún saldo cambia". {@code MayorizacionAtomicaIT} ya lo
 * prueba con un doble (una excepción lanzada en el serializador de la respuesta); aquí el fallo es real y ocurre en
 * PostgreSQL, con el mismo patrón que {@code PrecargaFallidaEnPostgresF2IT}: un trigger {@code BEFORE INSERT} sobre
 * {@code auditoria} que falla solo para la empresa de la prueba. Según {@code GuardarAsiento.persistir}, la auditoría
 * se escribe después de insertar la cabecera, las líneas y de mayorizar, así que el fallo ocurre exactamente después
 * del último paso que debe revertirse.
 */
class MayorizacionFalloRealF3IT extends BaseAceptacionF3IT {

    /** Nombres únicos por ejecución: el trigger y su función viven en el esquema compartido de la base de pruebas. */
    private final String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    private final String funcion = "f3_05_fallo_mayorizacion_" + sufijo;
    private final String disparador = "trg_f3_05_fallo_mayorizacion_" + sufijo;

    /** Elimina siempre el trigger y su función, también si la prueba falla a mitad, para no afectar a otras clases. */
    @AfterEach
    void limpiarTrigger() {
        duenio.sql("DROP TRIGGER IF EXISTS " + disparador + " ON auditoria").update();
        duenio.sql("DROP FUNCTION IF EXISTS " + funcion + "()").update();
    }

    /**
     * Un fallo real de PostgreSQL justo después de mayorizar (al auditar) deja la empresa exactamente como antes de
     * registrar: ni cabecera, ni líneas, ni saldos, ni correlativo, ni respuesta de idempotencia guardada. Reintentar
     * sin el fallo funciona sin intervención manual.
     */
    @Test
    void unFalloRealDePostgresAlAuditarRevierteElAsientoCompletoYSePuedeReintentar() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        String cuerpo = "{\"fecha\":\"2026-01-15\",\"concepto\":\"Asiento que falla al auditar\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"100.00\",\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"100.00\",\"llevaIva\":false}]}";

        // 1. Trigger que falla solo para la empresa de esta sesión (el UUID es propio, sin riesgo de inyección)
        duenio.sql("CREATE FUNCTION " + funcion + "() RETURNS trigger AS $$ BEGIN"
                        + " IF NEW.empresa_id = '" + s.empresa() + "'::uuid THEN"
                        + " RAISE EXCEPTION 'fallo simulado F3-05 al auditar'; END IF;"
                        + " RETURN NEW; END; $$ LANGUAGE plpgsql")
                .update();
        duenio.sql("CREATE TRIGGER " + disparador + " BEFORE INSERT ON auditoria" + " FOR EACH ROW EXECUTE FUNCTION "
                        + funcion + "()")
                .update();

        // 2. La instalación de Contabilidad ya dejó auditoría propia (precarga): se cuenta antes de intentar el asiento
        int auditoriaAntes = contar("SELECT count(*) FROM auditoria WHERE empresa_id = ?", s.empresa());

        // 3. Registrar: el fallo llega como error interno (500 PLT-500), nunca 201
        postConClave(s, "/contabilidad/asientos", "clave-fallida", cuerpo)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("PLT-500"));

        // 4. Como dueño: cero filas nuevas en asiento, líneas, saldos, correlativo, idempotencia y auditoría
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .as("asiento")
                .isZero();
        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE empresa_id = ?", s.empresa()))
                .as("asiento_linea")
                .isZero();
        assertThat(contar("SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ?", s.empresa()))
                .as("saldo_cuenta_mensual")
                .isZero();
        assertThat(contar("SELECT count(*) FROM correlativo_asiento WHERE empresa_id = ?", s.empresa()))
                .as("correlativo_asiento")
                .isZero();
        assertThat(contar(
                        "SELECT count(*) FROM idempotencia WHERE empresa_id = ? AND clave = 'clave-fallida'",
                        s.empresa()))
                .as("idempotencia de la clave fallida")
                .isZero();
        assertThat(contar("SELECT count(*) FROM auditoria WHERE empresa_id = ?", s.empresa()))
                .as("auditoría: ninguna fila nueva respecto de antes del intento")
                .isEqualTo(auditoriaAntes);

        // 5. Sin el fallo, reintentar con la misma clave funciona sin intervención manual (la clave no se consumió)
        limpiarTrigger();
        postConClave(s, "/contabilidad/asientos", "clave-fallida", cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1));
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(1);
    }
}
