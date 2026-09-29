package com.bcodesphere.pilot.aceptacion.f4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Criterio 1 de F4 (plan de trabajo): "con el conjunto de datos dorado, cada reporte coincide al centavo con el
 * resultado validado por contador". Recorre {@code casos/estados/conjunto-dorado-f4.json}: registra sus asientos y
 * su reversión por la API (nunca por SQL directo, para ejercitar la expansión de IVA y la mayorización reales,
 * ADR-018), y compara cada valor de la Balanza, el Libro Mayor, los dos estados financieros, el resumen de IVA y el
 * Libro Diario de marzo de 2026 contra los valores calculados a mano en el {@code fuente} del caso.
 *
 * <p>Los valores esperados fueron verificados por tres caminos independientes antes de fijarse en el JSON (ver el
 * campo {@code calculo} de cada rubro): suma por cuenta, suma por período, y la ecuación contable
 * Activo = Pasivo + Patrimonio + resultados anteriores + utilidad del ejercicio (ADR-016). Esta prueba, además,
 * afirma en código que los totales que el conjunto quiere distinguir son efectivamente distintos entre sí (p. ej.
 * el total Debe de la balanza no es igual al total de saldos deudores), como pide el criterio 1.
 */
class ConjuntoDoradoF4IT extends BaseAceptacionF4IT {

    @Test
    void elConjuntoDoradoCoincideAlCentavoEnTodosLosReportes() throws Exception {
        ConjuntoDorado conjunto = sembrarConjuntoDorado();
        Sesion s = conjunto.sesion();
        DocumentContext doc = conjunto.doc();
        Map<String, Object> esperado = doc.read("$.esperado");

        verificarBalanza(s, doc, esperado);
        verificarMayor(s, doc, esperado);
        verificarEstadoResultados(s, esperado);
        verificarEstadoSituacionFinanciera(s, esperado);
        verificarResumenIva(s, esperado);
        verificarLibroDiario(s, esperado);
    }

    // -------------------------------------------------------------------------------------------------- balanza

    @SuppressWarnings("unchecked")
    private void verificarBalanza(Sesion s, DocumentContext doc, Map<String, Object> esperado) throws Exception {
        Map<String, Object> b = (Map<String, Object>) esperado.get("balanza");
        String rango = "desde=" + b.get("desde") + "&hasta=" + b.get("hasta");

        ResultActions r = get(s, "/contabilidad/balanza?" + rango + "&nivel=5").andExpect(status().isOk());
        String cuerpo = r.andReturn().getResponse().getContentAsString();
        DocumentContext json = JsonPath.parse(cuerpo);

        // Se lee el array completo una sola vez y se filtra en Java: encadenar un predicado [?(...)] con [0] y
        // más ruta detrás en una sola llamada a JsonPath.read(...) puede devolver un JSONArray en vez de un
        // valor escalar (la ruta deja de ser "definida" en cuanto incluye un filtro), así que se evita esa forma.
        List<Map<String, Object>> todasLasFilas = json.read("$.filas");
        List<Map<String, Object>> filasEsperadas = (List<Map<String, Object>>) b.get("filas");
        for (Map<String, Object> filaEsperada : filasEsperadas) {
            verificarFilaDeBalanza(
                    buscarFilaDeDetalle(todasLasFilas, (String) filaEsperada.get("cuentaCodigo")), filaEsperada);
        }

        String totalDebe = json.read("$.totalDebe");
        String totalHaber = json.read("$.totalHaber");
        String totalDeudores = json.read("$.totalSaldosDeudores");
        String totalAcreedores = json.read("$.totalSaldosAcreedores");
        assertThat(totalDebe).isEqualTo((String) b.get("totalDebe"));
        assertThat(totalHaber).isEqualTo((String) b.get("totalHaber"));
        assertThat(totalDeudores).isEqualTo((String) b.get("totalSaldosDeudores"));
        assertThat(totalAcreedores).isEqualTo((String) b.get("totalSaldosAcreedores"));
        assertThat((Boolean) json.read("$.cuadra")).isEqualTo(b.get("cuadra"));
        // El criterio 1 exige que los totales que el conjunto distingue sean realmente distintos entre sí
        assertThat(totalDebe)
                .as("totalDebe != totalSaldosDeudores (corrección 1, F4-04)")
                .isNotEqualTo(totalDeudores);

        // ADR-038 §6: los totales no cambian con el nivel pedido
        ResultActions r2 = get(s, "/contabilidad/balanza?" + rango + "&nivel=2").andExpect(status().isOk());
        DocumentContext json2 = JsonPath.parse(r2.andReturn().getResponse().getContentAsString());
        assertThat((String) json2.read("$.totalDebe")).isEqualTo(totalDebe);
        assertThat((String) json2.read("$.totalHaber")).isEqualTo(totalHaber);
        assertThat((String) json2.read("$.totalSaldosDeudores")).isEqualTo(totalDeudores);
        assertThat((String) json2.read("$.totalSaldosAcreedores")).isEqualTo(totalAcreedores);
    }

    /** Compara una fila real de la balanza (ya deserializada) contra la fila esperada del conjunto dorado. */
    @SuppressWarnings("unchecked")
    private static void verificarFilaDeBalanza(Map<String, Object> filaReal, Map<String, Object> filaEsperada) {
        String codigo = (String) filaEsperada.get("cuentaCodigo");
        Map<String, Object> saldoInicial = (Map<String, Object>) filaReal.get("saldoInicial");
        Map<String, Object> saldoFinal = (Map<String, Object>) filaReal.get("saldoFinal");
        assertThat((String) saldoInicial.get("monto"))
                .as("balanza %s saldoInicial.monto", codigo)
                .isEqualTo(filaEsperada.get("saldoInicialMonto"));
        assertThat((String) saldoInicial.get("lado"))
                .as("balanza %s saldoInicial.lado", codigo)
                .isEqualTo(filaEsperada.get("saldoInicialLado"));
        assertThat((String) filaReal.get("debe")).as("balanza %s debe", codigo).isEqualTo(filaEsperada.get("debe"));
        assertThat((String) filaReal.get("haber"))
                .as("balanza %s haber", codigo)
                .isEqualTo(filaEsperada.get("haber"));
        assertThat((String) saldoFinal.get("monto"))
                .as("balanza %s saldoFinal.monto", codigo)
                .isEqualTo(filaEsperada.get("saldoFinalMonto"));
        assertThat((String) saldoFinal.get("lado"))
                .as("balanza %s saldoFinal.lado", codigo)
                .isEqualTo(filaEsperada.get("saldoFinalLado"));
        assertThat((Boolean) saldoFinal.get("contrarioNaturaleza"))
                .as("balanza %s contrarioNaturaleza", codigo)
                .isEqualTo(filaEsperada.get("contrarioNaturaleza"));
    }

    /** Busca, en las filas de la balanza ya deserializadas, la fila de detalle cuya cuenta tiene el código dado. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buscarFilaDeDetalle(List<Map<String, Object>> filas, String codigo) {
        return filas.stream()
                .filter(f -> Boolean.TRUE.equals(f.get("esDetalle")))
                .filter(f -> codigo.equals(((Map<String, Object>) f.get("cuenta")).get("codigo")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No se encontró la fila de detalle de la cuenta " + codigo));
    }

    // ---------------------------------------------------------------------------------------------------- mayor

    @SuppressWarnings("unchecked")
    private void verificarMayor(Sesion s, DocumentContext doc, Map<String, Object> esperado) throws Exception {
        Map<String, Object> m = (Map<String, Object>) esperado.get("mayor");
        String rango = "desde=" + m.get("desde") + "&hasta=" + m.get("hasta");
        Map<String, Object> detalle = (Map<String, Object>) m.get("detalle");
        Map<String, Object> padre = (Map<String, Object>) m.get("padre");

        verificarUnMayor(s, rango, (String) detalle.get("cuentaCodigo"), detalle);
        verificarUnMayor(s, rango, (String) padre.get("cuentaCodigo"), padre);
    }

    private void verificarUnMayor(Sesion s, String rango, String codigoCuenta, Map<String, Object> esperado)
            throws Exception {
        var id = cuentaId(s.empresa(), codigoCuenta);
        get(s, "/contabilidad/mayor?cuentaId=" + id + "&" + rango)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial.monto").value(esperado.get("saldoInicialMonto")))
                .andExpect(jsonPath("$.saldoInicial.lado").value(esperado.get("saldoInicialLado")))
                .andExpect(jsonPath("$.movimientos.length()")
                        .value(((Number) esperado.get("cantidadMovimientos")).intValue()))
                .andExpect(jsonPath("$.totalDebe").value(esperado.get("totalDebe")))
                .andExpect(jsonPath("$.totalHaber").value(esperado.get("totalHaber")))
                .andExpect(jsonPath("$.saldoFinal.monto").value(esperado.get("saldoFinalMonto")))
                .andExpect(jsonPath("$.saldoFinal.lado").value(esperado.get("saldoFinalLado")));
    }

    // ------------------------------------------------------------------------------------------ estado resultados

    @SuppressWarnings("unchecked")
    private void verificarEstadoResultados(Sesion s, Map<String, Object> esperado) throws Exception {
        Map<String, Object> er = (Map<String, Object>) esperado.get("estadoResultados");
        get(s, "/contabilidad/estados/resultados?desde=" + er.get("desde") + "&hasta=" + er.get("hasta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresos.total").value(er.get("ingresos")))
                .andExpect(jsonPath("$.costosGastos.total").value(er.get("costosGastos")))
                .andExpect(jsonPath("$.utilidadAntesImpuesto").value(er.get("utilidadAntesImpuesto")))
                .andExpect(jsonPath("$.impuestoSobreRenta.total").value(er.get("impuestoSobreRenta")))
                .andExpect(jsonPath("$.utilidadEjercicio").value(er.get("utilidadEjercicio")));
    }

    // --------------------------------------------------------------------------------- estado situación financiera

    @SuppressWarnings("unchecked")
    private void verificarEstadoSituacionFinanciera(Sesion s, Map<String, Object> esperado) throws Exception {
        Map<String, Object> esf = (Map<String, Object>) esperado.get("estadoSituacionFinanciera");
        get(s, "/contabilidad/estados/situacion-financiera?fechaCorte=" + esf.get("fechaCorte"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo.total").value(esf.get("activo")))
                .andExpect(jsonPath("$.pasivo.total").value(esf.get("pasivo")))
                .andExpect(jsonPath("$.patrimonio.total").value(esf.get("patrimonio")))
                .andExpect(
                        jsonPath("$.resultadosEjerciciosAnteriores").value(esf.get("resultadosEjerciciosAnteriores")))
                .andExpect(jsonPath("$.utilidadEjercicio").value(esf.get("utilidadEjercicio")))
                .andExpect(jsonPath("$.totalPasivoPatrimonio").value(esf.get("totalPasivoPatrimonio")))
                .andExpect(jsonPath("$.comprobacion.cuadra").value(esf.get("cuadra")))
                .andExpect(jsonPath("$.comprobacion.diferencia").value(esf.get("diferencia")));
    }

    // ------------------------------------------------------------------------------------------------- resumen iva

    @SuppressWarnings("unchecked")
    private void verificarResumenIva(Sesion s, Map<String, Object> esperado) throws Exception {
        Map<String, Object> iva = (Map<String, Object>) esperado.get("resumenIva");
        get(s, "/contabilidad/reportes/iva?anio=" + iva.get("anio") + "&mes=" + iva.get("mes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ivaDebito.manual").value(iva.get("ivaDebitoManual")))
                .andExpect(jsonPath("$.ivaDebito.reversion").value(iva.get("ivaDebitoReversion")))
                .andExpect(jsonPath("$.ivaDebito.n8n").value(iva.get("ivaDebitoN8n")))
                .andExpect(jsonPath("$.ivaDebito.total").value(iva.get("ivaDebitoTotal")))
                .andExpect(jsonPath("$.ivaCredito.manual").value(iva.get("ivaCreditoManual")))
                .andExpect(jsonPath("$.ivaCredito.reversion").value(iva.get("ivaCreditoReversion")))
                .andExpect(jsonPath("$.ivaCredito.n8n").value(iva.get("ivaCreditoN8n")))
                .andExpect(jsonPath("$.ivaCredito.total").value(iva.get("ivaCreditoTotal")))
                .andExpect(jsonPath("$.diferenciaEstimada").value(iva.get("diferenciaEstimada")));
    }

    // ----------------------------------------------------------------------------------------------- libro diario

    @SuppressWarnings("unchecked")
    private void verificarLibroDiario(Sesion s, Map<String, Object> esperado) throws Exception {
        Map<String, Object> ld = (Map<String, Object>) esperado.get("libroDiario");
        ResultActions r = get(s, "/contabilidad/asientos?desde=" + ld.get("desde") + "&hasta=" + ld.get("hasta"))
                .andExpect(status().isOk());
        String cuerpo = r.andReturn().getResponse().getContentAsString();
        DocumentContext json = JsonPath.parse(cuerpo);
        List<Map<String, Object>> elementos = json.read("$.elementos");
        assertThat(elementos)
                .as("cantidad de asientos de marzo")
                .hasSize(((Number) ld.get("cantidadAsientos")).intValue());

        java.math.BigDecimal totalDebe = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalHaber = java.math.BigDecimal.ZERO;
        for (Map<String, Object> a : elementos) {
            totalDebe = totalDebe.add(new java.math.BigDecimal((String) a.get("totalDebe")));
            totalHaber = totalHaber.add(new java.math.BigDecimal((String) a.get("totalHaber")));
        }
        assertThat(totalDebe).as("suma de totalDebe del Libro Diario de marzo").isEqualByComparingTo((String)
                ld.get("totalDebe"));
        assertThat(totalHaber)
                .as("suma de totalHaber del Libro Diario de marzo")
                .isEqualByComparingTo((String) ld.get("totalHaber"));
    }

    /** Atajo local a {@code MockMvcResultMatchers.jsonPath}, para no repetir el import estático en cada método. */
    private static org.springframework.test.web.servlet.result.JsonPathResultMatchers jsonPath(String path) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(path);
    }
}
