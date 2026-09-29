package com.bcodesphere.pilot.aceptacion.f4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Criterios 2, 3 y 4 de F4 (plan de trabajo), sobre el conjunto dorado de {@link ConjuntoDoradoF4IT}:
 *
 * <ul>
 *   <li>criterio 2 — "Balanza: total saldos deudores = total saldos acreedores", verificado en los cinco niveles
 *       del catálogo, y los cuatro totales no cambian con el nivel (ADR-038 §6);
 *   <li>criterio 3 — "el Estado de Situación Financiera cuadra; ante un descuadre forzado en la base de pruebas
 *       aparece la alerta con la diferencia exacta", con un monto distinto del que ya usa {@code ReportesIT}
 *       (50.00) para no repetir el mismo caso de prueba, y {@code cantidadCuentasRevisadas} igual a las 21
 *       combinaciones cuenta/año/mes calculadas a mano en {@code conjunto-dorado-f4.json};
 *   <li>criterio 4 — "Utilidad del Estado de Resultados = utilidad mostrada en el Balance para el mismo período",
 *       para el período completo del conjunto (1/1 al 31/3) y además para un corte a mitad de mes (20/3), donde el
 *       Estado de Resultados también corta a mitad de mes.
 * </ul>
 */
class BalanzaYEstadosF4IT extends BaseAceptacionF4IT {

    // ------------------------------------------------------------------------------------------------- criterio 2

    /** Criterio 2: la Balanza del conjunto dorado cuadra en los cinco niveles y sus totales no cambian con el nivel. */
    @Test
    void laBalanzaDelConjuntoDoradoCuadraEnLosCincoNiveles() throws Exception {
        ConjuntoDorado conjunto = sembrarConjuntoDorado();
        Sesion s = conjunto.sesion();
        String rango = "desde=2026-03-01&hasta=2026-03-31";

        String totalDebeReferencia = null;
        String totalHaberReferencia = null;
        String totalDeudoresReferencia = null;
        String totalAcreedoresReferencia = null;
        for (int nivel = 1; nivel <= 5; nivel++) {
            var r = get(s, "/contabilidad/balanza?" + rango + "&nivel=" + nivel).andExpect(status().isOk());
            String totalDebe = leer(r, "$.totalDebe");
            String totalHaber = leer(r, "$.totalHaber");
            String totalDeudores = leer(r, "$.totalSaldosDeudores");
            String totalAcreedores = leer(r, "$.totalSaldosAcreedores");
            assertThat(totalDebe).as("nivel %d: Σ Debe = Σ Haber", nivel).isEqualTo(totalHaber);
            assertThat(totalDeudores)
                    .as("nivel %d: Σ saldos deudores = Σ saldos acreedores", nivel)
                    .isEqualTo(totalAcreedores);
            assertThat((Boolean) leer(r, "$.cuadra"))
                    .as("nivel %d cuadra", nivel)
                    .isTrue();
            if (nivel == 1) {
                totalDebeReferencia = totalDebe;
                totalHaberReferencia = totalHaber;
                totalDeudoresReferencia = totalDeudores;
                totalAcreedoresReferencia = totalAcreedores;
            } else {
                // ADR-038 §6: los totales salen solo del detalle, sin importar el nivel pedido
                assertThat(totalDebe)
                        .as("nivel %d: mismo totalDebe que nivel 1", nivel)
                        .isEqualTo(totalDebeReferencia);
                assertThat(totalHaber)
                        .as("nivel %d: mismo totalHaber que nivel 1", nivel)
                        .isEqualTo(totalHaberReferencia);
                assertThat(totalDeudores)
                        .as("nivel %d: mismo totalSaldosDeudores que nivel 1", nivel)
                        .isEqualTo(totalDeudoresReferencia);
                assertThat(totalAcreedores)
                        .as("nivel %d: mismo totalSaldosAcreedores que nivel 1", nivel)
                        .isEqualTo(totalAcreedoresReferencia);
            }
        }
        // Valores exactos del conjunto dorado (fuente: conjunto-dorado-f4.json)
        assertThat(totalDebeReferencia).isEqualTo("2878.98");
        assertThat(totalDeudoresReferencia).isEqualTo("4045.00");
    }

    // ------------------------------------------------------------------------------------------------- criterio 3

    /**
     * Criterio 3: con el conjunto dorado sin alterar, el Estado de Situación Financiera cuadra y el diagnóstico
     * revisa exactamente las 21 combinaciones cuenta/año/mes calculadas a mano (corrección F4-07). Luego, una
     * alteración directa de {@code saldo_cuenta_mensual} con un monto distinto del que usa
     * {@code contabilidad.api.ReportesIT} (50.00) produce la alerta con la diferencia exacta, y el diagnóstico la
     * señala; el dato se restaura en el {@code finally} para no dejar la base inconsistente.
     */
    @Test
    void elEstadoDeSituacionFinancieraCuadraYAlertaAnteUnDescuadreForzado() throws Exception {
        ConjuntoDorado conjunto = sembrarConjuntoDorado();
        Sesion s = conjunto.sesion();

        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-03-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobacion.cuadra").value(true))
                .andExpect(jsonPath("$.comprobacion.diferencia").value("0.00"));

        get(s, "/contabilidad/diagnostico/mayorizacion")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consistente").value(true))
                .andExpect(jsonPath("$.cantidadCuentasRevisadas").value(21))
                .andExpect(jsonPath("$.diferencias").isEmpty());

        // Alteración directa como dueño (sin RLS): 77.77, un monto distinto del 50.00 de ReportesIT. Se altera
        // FEBRERO (no marzo): para un corte del 31/3, marzo es el "mes parcial" y se calcula directo de
        // asiento_linea (CLAUDE.md §10.3), así que alterar su saldo_cuenta_mensual no cambiaría nada; febrero, en
        // cambio, es un "mes completo anterior" y sí se lee de saldo_cuenta_mensual (mismo patrón que
        // contabilidad.api.ReportesIT, que altera enero para un corte de febrero).
        var caja = cuentaId(s.empresa(), "11010101");
        int filas = duenio.sql("UPDATE saldo_cuenta_mensual SET total_debe = total_debe + 77.77"
                        + " WHERE empresa_id = ? AND cuenta_id = ? AND anio = 2026 AND mes = 2")
                .params(s.empresa(), caja)
                .update();
        assertThat(filas).isEqualTo(1);
        try {
            get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-03-31")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.comprobacion.cuadra").value(false))
                    .andExpect(jsonPath("$.comprobacion.diferencia").value("77.77"));

            get(s, "/contabilidad/diagnostico/mayorizacion")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.consistente").value(false))
                    .andExpect(jsonPath("$.diferencias[0].anio").value(2026))
                    .andExpect(jsonPath("$.diferencias[0].mes").value(2))
                    .andExpect(jsonPath("$.diferencias[0].saldoDebe").value("77.77")) // 0.00 (febrero, Caja) + 77.77
                    .andExpect(jsonPath("$.diferencias[0].lineasDebe").value("0.00"));
        } finally {
            duenio.sql("UPDATE saldo_cuenta_mensual SET total_debe = total_debe - 77.77"
                            + " WHERE empresa_id = ? AND cuenta_id = ? AND anio = 2026 AND mes = 2")
                    .params(s.empresa(), caja)
                    .update();
        }

        // Restaurado: vuelve a cuadrar
        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=2026-03-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comprobacion.cuadra").value(true));
    }

    // ------------------------------------------------------------------------------------------------- criterio 4

    /**
     * Criterio 4: la utilidad del Estado de Resultados es igual a la del Estado de Situación Financiera para el
     * mismo período (siempre 1/1 del año del corte a la fecha de corte, CLAUDE.md §10.4), tanto a fin de mes como a
     * mitad de mes. No se compara contra un valor calculado a mano aparte: es la misma identidad contable que
     * ambos reportes comparten por construcción (EstadoSituacionFinanciera.generar, ADR-037 punto 4).
     */
    @Test
    void laUtilidadDelEstadoDeResultadosCoincideConLaDelBalanceAFinDeMesYAMitadDeMes() throws Exception {
        ConjuntoDorado conjunto = sembrarConjuntoDorado();
        Sesion s = conjunto.sesion();

        verificarUtilidadesCoinciden(s, LocalDate.of(2026, 3, 31));
        verificarUtilidadesCoinciden(s, LocalDate.of(2026, 3, 20));
    }

    private void verificarUtilidadesCoinciden(Sesion s, LocalDate corte) throws Exception {
        var er = get(s, "/contabilidad/estados/resultados?desde=2026-01-01&hasta=" + corte)
                .andExpect(status().isOk());
        String utilidadEr = leer(er, "$.utilidadEjercicio");

        var esf = get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=" + corte)
                .andExpect(status().isOk());
        String utilidadEsf = leer(esf, "$.utilidadEjercicio");

        assertThat(utilidadEr).as("utilidad ER == utilidad ESF al %s", corte).isEqualTo(utilidadEsf);
        assertThat((Boolean) leer(esf, "$.comprobacion.cuadra"))
                .as("cuadra al %s", corte)
                .isTrue();
    }
}
