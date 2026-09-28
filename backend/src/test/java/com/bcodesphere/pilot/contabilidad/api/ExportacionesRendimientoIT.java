package com.bcodesphere.pilot.contabilidad.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Prueba de rendimiento de la exportación del Libro Diario (F4-04, ADR-038): un año de 10 000 asientos (20 000
 * líneas) se exporta a CSV y XLSX sin cargar todo el archivo formateado en memoria de golpe (escritura en flujo y
 * SXSSF). La siembra es SQL directo como dueño, igual que {@link ReportesRendimientoIT}.
 */
class ExportacionesRendimientoIT extends BaseContabilidadIT {

    private static final int CANTIDAD_ASIENTOS = 10_000;

    /** Reutiliza la siembra masiva de {@link ReportesRendimientoIT} vía SQL directo como dueño. */
    private void sembrarAnioDeCarga(UUID empresa, UUID caja, UUID ventas) {
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

        // En producción los asientos se insertan uno a uno a lo largo del tiempo y autovacuum mantiene las
        // estadísticas al día; esta siembra masiva las deja desactualizadas de golpe, lo que puede hacer que el
        // planificador de PostgreSQL elija un plan muy malo para el JOIN de la exportación. ANALYZE reproduce el
        // estado real (CLAUDE.md 16.3: autovacuum activo en producción).
        duenio.sql("ANALYZE asiento, asiento_linea, cuenta_contable").update();
    }

    /**
     * Criterio de rendimiento (ADR-038, F4-04): el Libro Diario de un año de 10 000 asientos se exporta a CSV y a
     * XLSX en menos de 10 s cada uno (generoso frente al objetivo de 2 s del JSON, porque además formatea 20 000
     * filas), sin agotar memoria. El tiempo medido queda en el mensaje de la aserción para el reporte de entrega.
     */
    @Test
    void elLibroDiarioDeDiezMilAsientosSeExportaACsvYXlsxSinAgotarMemoria() throws Exception {
        Sesion s = sesionConContabilidad();
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        sembrarAnioDeCarga(s.empresa(), caja, ventas);
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isEqualTo(CANTIDAD_ASIENTOS);

        String ruta = "/contabilidad/asientos/exportacion?desde=2026-01-01&hasta=2026-12-31&formato=";

        long inicioCsv = System.nanoTime();
        byte[] csv = get(s, ruta + "csv")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        long msCsv = (System.nanoTime() - inicioCsv) / 1_000_000;

        long inicioXlsx = System.nanoTime();
        byte[] xlsx = get(s, ruta + "xlsx")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        long msXlsx = (System.nanoTime() - inicioXlsx) / 1_000_000;

        assertThat(csv.length).as("bytes del CSV").isGreaterThan(0);
        assertThat(xlsx.length).as("bytes del XLSX").isGreaterThan(0);
        assertThat(msCsv)
                .as("CSV de 10 000 asientos (ms), medido: " + msCsv + " ms")
                .isLessThan(10_000);
        assertThat(msXlsx)
                .as("XLSX de 10 000 asientos (ms), medido: " + msXlsx + " ms")
                .isLessThan(10_000);
    }
}
