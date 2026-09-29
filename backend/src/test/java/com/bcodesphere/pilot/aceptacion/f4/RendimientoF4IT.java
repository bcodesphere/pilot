package com.bcodesphere.pilot.aceptacion.f4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Criterio 7 de F4 (plan de trabajo): "un reporte anual de 10,000 asientos responde en menos de 2 segundos (p95)".
 * Siembra 10 000 asientos cuadrados de un año con SQL directo como dueño (misma técnica que
 * {@code contabilidad.api.ReportesRendimientoIT}, sin repetir la clase: dos CTE encadenados para el asiento y sus
 * dos líneas, más el agregado de {@code saldo_cuenta_mensual} desde las líneas insertadas, consistente con la
 * mayorización real, ADR-018), y mide, tras 3 ejecuciones de calentamiento, el p95 de <strong>20</strong>
 * ejecuciones de la Balanza anual, el Estado de Situación Financiera al 31/12, el Estado de Resultados anual, el
 * Libro Mayor anual de Caja y el Libro Diario del año. También mide, solo de forma informativa (sin afirmar nada),
 * la exportación PDF y XLSX del Libro Diario anual.
 */
class RendimientoF4IT extends BaseAceptacionF4IT {

    private static final int CANTIDAD_ASIENTOS = 10_000;
    private static final long LIMITE_P95_MS = 2_000;
    private static final int CALENTAMIENTO = 3;
    private static final int MEDICIONES = 20;

    /**
     * Siembra {@link #CANTIDAD_ASIENTOS} asientos Caja/Ventas gravadas de 100.00, repartidos en los 360 primeros
     * días de 2026, y agrega {@code saldo_cuenta_mensual} desde las líneas insertadas (misma técnica que
     * {@code ReportesRendimientoIT}).
     */
    private void sembrarAnioDeCarga(UUID empresa, UUID caja, UUID ventas) {
        duenio.sql("WITH nuevos AS ("
                        + "  INSERT INTO asiento (id, empresa_id, anio, numero, fecha, concepto, estado, origen_tipo,"
                        + "                        total_debe, total_haber, creado_por)"
                        + "  SELECT gen_random_uuid(), :empresa, 2026, gs,"
                        + "         DATE '2026-01-01' + ((gs % 360) || ' days')::interval,"
                        + "         'Carga de rendimiento F4-06 ' || gs, 'CONTABILIZADO', 'MANUAL', 100.00, 100.00,"
                        + "         'sistema'"
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
        // Deja las estadísticas del planificador al día, como haría autovacuum en producción (CLAUDE.md §16.3);
        // una siembra masiva de golpe las deja desactualizadas y puede elegir un plan muy distinto del real.
        duenio.sql("ANALYZE asiento, asiento_linea, saldo_cuenta_mensual, cuenta_contable")
                .update();
    }

    /** p50, p95 y máximo (ms) de una serie ya ordenada ascendentemente. */
    private record Percentiles(long p50, long p95, long max) {
        static Percentiles de(List<Long> tiemposMs) {
            List<Long> ordenados = new ArrayList<>(tiemposMs);
            Collections.sort(ordenados);
            int n = ordenados.size();
            long p50 = ordenados.get((int) Math.ceil(0.50 * n) - 1);
            long p95 = ordenados.get((int) Math.ceil(0.95 * n) - 1);
            long max = ordenados.get(n - 1);
            return new Percentiles(p50, p95, max);
        }
    }

    /** Ejecuta {@link #CALENTAMIENTO} peticiones descartadas y luego {@link #MEDICIONES} medidas, en milisegundos. */
    private List<Long> medirTiempos(Sesion s, String ruta) throws Exception {
        for (int i = 0; i < CALENTAMIENTO; i++) {
            get(s, ruta).andExpect(status().isOk());
        }
        List<Long> tiempos = new ArrayList<>(MEDICIONES);
        for (int i = 0; i < MEDICIONES; i++) {
            long inicio = System.nanoTime();
            get(s, ruta).andExpect(status().isOk());
            tiempos.add((System.nanoTime() - inicio) / 1_000_000);
        }
        return tiempos;
    }

    /** Mide, afirma el p95 contra el límite y deja el resultado (p50/p95/máx) en el mensaje de la aserción y en stdout. */
    private void medirYAfirmar(String nombre, Sesion s, String ruta) throws Exception {
        Percentiles p = Percentiles.de(medirTiempos(s, ruta));
        String resumen = String.format(
                "%s: p50=%d ms, p95=%d ms, máx=%d ms (límite p95 < %d ms, %d mediciones tras %d de calentamiento)",
                nombre, p.p50(), p.p95(), p.max(), LIMITE_P95_MS, MEDICIONES, CALENTAMIENTO);
        System.out.println("[RendimientoF4IT] " + resumen);
        assertThat(p.p95()).as(resumen).isLessThan(LIMITE_P95_MS);
    }

    /**
     * Criterio de rendimiento del plan de trabajo F4: con 10 000 asientos, el p95 (20 mediciones tras 3 de
     * calentamiento) de la Balanza, el Estado de Situación Financiera, el Estado de Resultados, el Libro Mayor de
     * Caja y el Libro Diario, todos del año 2026, es menor que 2 s.
     */
    @Test
    void losCincoReportesDelAnioResponenConP95MenorADosSegundosConDiezMilAsientos() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        sembrarAnioDeCarga(s.empresa(), caja, ventas);
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(CANTIDAD_ASIENTOS);

        medirYAfirmar("Balanza 2026", s, "/contabilidad/balanza?desde=2026-01-01&hasta=2026-12-31");
        medirYAfirmar(
                "Estado de Situación Financiera al 31/12/2026",
                s,
                "/contabilidad/estados/situacion-financiera?fechaCorte=2026-12-31");
        medirYAfirmar(
                "Estado de Resultados 2026", s, "/contabilidad/estados/resultados?desde=2026-01-01&hasta=2026-12-31");
        medirYAfirmar(
                "Libro Mayor de Caja 2026",
                s,
                "/contabilidad/mayor?cuentaId=" + caja + "&desde=2026-01-01&hasta=2026-12-31");
        medirYAfirmar("Libro Diario 2026", s, "/contabilidad/asientos?desde=2026-01-01&hasta=2026-12-31&limite=200");

        // Informativo (sin afirmar nada): tiempo de exportar el Libro Diario anual completo a PDF y a XLSX
        long inicioPdf = System.nanoTime();
        get(s, "/contabilidad/asientos/exportacion?formato=pdf&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk());
        long msPdf = (System.nanoTime() - inicioPdf) / 1_000_000;
        long inicioXlsx = System.nanoTime();
        get(s, "/contabilidad/asientos/exportacion?formato=xlsx&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(status().isOk());
        long msXlsx = (System.nanoTime() - inicioXlsx) / 1_000_000;
        System.out.println("[RendimientoF4IT] Exportación Libro Diario 2026 (informativo, sin límite afirmado): PDF="
                + msPdf + " ms, XLSX=" + msXlsx + " ms");
    }
}
