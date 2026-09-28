package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Prueba de rendimiento de F4 (plan de trabajo): con 10 000 asientos cuadrados en un año, la Balanza y los dos
 * estados financieros del año deben responder en menos de 2 s. La siembra es SQL directo como dueño (sin pasar por
 * la API ni por la idempotencia, que no son el objeto de esta prueba), pero deja {@code saldo_cuenta_mensual}
 * exactamente consistente con {@code asiento_linea}, igual que dejaría la mayorización real (ADR-018).
 */
class ReportesRendimientoIT extends BaseContabilidadIT {

    private static final int CANTIDAD_ASIENTOS = 10_000;
    private static final long LIMITE_MS = 2_000;

    /**
     * Siembra {@link #CANTIDAD_ASIENTOS} asientos Caja/Ventas gravadas de 100.00, repartidos en los 360 primeros
     * días de 2026, y agrega {@code saldo_cuenta_mensual} desde las líneas insertadas.
     */
    private void sembrarAnioDeCarga(UUID empresa, UUID caja, UUID ventas) {
        // El asiento y sus dos líneas se insertan en UNA sola sentencia (tres CTE encadenados): los triggers
        // diferidos de partida doble (trg_partida_doble y trg_partida_doble_cabecera, V13) se evalúan al terminar
        // la sentencia, así que la cabecera y sus líneas deben quedar juntas en la misma transacción implícita.
        duenio.sql("WITH nuevos AS ("
                        + "  INSERT INTO asiento (id, empresa_id, anio, numero, fecha, concepto, estado, origen_tipo,"
                        + "                        total_debe, total_haber, creado_por)"
                        + "  SELECT gen_random_uuid(), :empresa, 2026, gs,"
                        + "         DATE '2026-01-01' + ((gs % 360) || ' days')::interval,"
                        + "         'Carga de rendimiento ' || gs, 'CONTABILIZADO', 'MANUAL', 100.00, 100.00, 'sistema'"
                        + "  FROM generate_series(1, :cantidad) AS gs"
                        + "  RETURNING id, fecha"
                        + "), lineas_debe AS ("
                        + "  INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea, fecha, cuenta_id,"
                        + "                             debe, haber, origen_linea)"
                        + "  SELECT gen_random_uuid(), :empresa, n.id, 1, n.fecha, :caja, 100.00, 0, 'USUARIO'"
                        + "  FROM nuevos n"
                        + "  RETURNING 1"
                        + "), lineas_haber AS ("
                        + "  INSERT INTO asiento_linea (id, empresa_id, asiento_id, numero_linea, fecha, cuenta_id,"
                        + "                             debe, haber, origen_linea)"
                        + "  SELECT gen_random_uuid(), :empresa, n.id, 2, n.fecha, :ventas, 0, 100.00, 'USUARIO'"
                        + "  FROM nuevos n"
                        + "  RETURNING 1"
                        + ")"
                        + " SELECT (SELECT count(*) FROM lineas_debe) + (SELECT count(*) FROM lineas_haber)")
                .param("empresa", empresa)
                .param("cantidad", CANTIDAD_ASIENTOS)
                .param("caja", caja)
                .param("ventas", ventas)
                .query(Integer.class)
                .single();

        duenio.sql("INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes, total_debe, total_haber)"
                        + " SELECT :empresa, cuenta_id, EXTRACT(YEAR FROM fecha)::smallint,"
                        + "        EXTRACT(MONTH FROM fecha)::smallint, SUM(debe), SUM(haber)"
                        + " FROM asiento_linea WHERE empresa_id = :empresa AND fecha >= DATE '2026-01-01'"
                        + "   AND fecha <= DATE '2026-12-31'"
                        + " GROUP BY cuenta_id, EXTRACT(YEAR FROM fecha), EXTRACT(MONTH FROM fecha)")
                .param("empresa", empresa)
                .update();
    }

    /** Mide la petición más lenta de 5 (tras una de calentamiento) y la compara contra el límite del plan. */
    private long peorLatenciaMs(Sesion s, String ruta) throws Exception {
        get(s, ruta).andExpect(status().isOk());
        long peor = 0;
        for (int i = 0; i < 5; i++) {
            long inicio = System.nanoTime();
            get(s, ruta).andExpect(status().isOk());
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            peor = Math.max(peor, ms);
        }
        return peor;
    }

    /**
     * Criterio de rendimiento del plan de trabajo F4: año de 10 000 asientos, Balanza y estados en menos de 2 s.
     */
    @Test
    void balanzaYEstadosDelAnioResponenEnMenosDeDosSegundosConDiezMilAsientos() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        sembrarAnioDeCarga(s.empresa(), caja, ventas);
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(CANTIDAD_ASIENTOS);

        long balanza = peorLatenciaMs(s, "/contabilidad/balanza?desde=2026-01-01&hasta=2026-12-31");
        long resultados = peorLatenciaMs(s, "/contabilidad/estados/resultados?desde=2026-01-01&hasta=2026-12-31");
        long situacion = peorLatenciaMs(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-12-31");

        assertThat(balanza).as("balanza (ms)").isLessThan(LIMITE_MS);
        assertThat(resultados).as("estado de resultados (ms)").isLessThan(LIMITE_MS);
        assertThat(situacion).as("estado de situación financiera (ms)").isLessThan(LIMITE_MS);
    }
}
