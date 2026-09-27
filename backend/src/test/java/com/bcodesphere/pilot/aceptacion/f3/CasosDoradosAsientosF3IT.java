package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Casos dorados de asientos e IVA de la fase F3 (CLAUDE.md §15, puntos 1 y 2: "Asientos válidos e inválidos para cada
 * código CON-001 a CON-013" y "IVA: los cuatro casos de 11.1 y los dos ejemplos de 11.2"). Recorre los archivos JSON
 * de {@code casos/asientos/} y {@code casos/iva/} (cada uno con su {@code fuente}) y, por la API:
 *
 * <ul>
 *   <li>los casos válidos responden 201 y, como dueño, las líneas guardadas (cuenta, lado, monto, {@code
 *       origen_linea} y el enlace {@code linea_base_id} de las de IVA) son exactamente las del caso;
 *   <li>los casos inválidos responden con el HTTP y el código del caso, y no queda nada guardado (ni asiento, ni
 *       líneas, ni saldos, ni correlativo);
 *   <li>para los casos de {@code casos/iva/}, la vista previa (ADR-036) devuelve las mismas líneas que se guardan.
 * </ul>
 *
 * <p>CON-004 no tiene caso de API (una línea válida ya tiene un monto mayor que cero, como documenta
 * {@code LibroDiarioIT.losRechazosDeFormaYPartidaDobleDan422YNoGuardanNada}); su caso dorado vive en
 * {@code ReglasAsientoTest} (dominio). CON-008, CON-009 y CON-018 son de la reversión y viven en
 * {@code casos/reversiones/}, recorridos por {@link CasosDoradosReversionF3IT}.
 */
class CasosDoradosAsientosF3IT extends BaseAceptacionF3IT {

    private static final ZoneId ZONA_SV = ZoneId.of("America/El_Salvador");

    /** Un argumento por archivo JSON de {@code casos/asientos/} y {@code casos/iva/}: carpeta y nombre del archivo. */
    static Stream<Arguments> casos() throws IOException {
        List<Arguments> args = new ArrayList<>();
        for (String carpeta : List.of("asientos", "iva")) {
            for (var recurso :
                    new PathMatchingResourcePatternResolver().getResources("classpath:casos/" + carpeta + "/*.json")) {
                args.add(Arguments.of(carpeta, recurso.getFilename()));
            }
        }
        return args.stream();
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("casos")
    void aplicaElCasoDorado(String carpeta, String archivo) throws Exception {
        Map<String, Object> raiz = leerCaso(carpeta, archivo);
        Sesion s = sesionConContabilidad();
        List<Map<String, Object>> lineasEntrada = (List<Map<String, Object>>) raiz.get("lineas");
        String cuerpo = construirCuerpo(s, raiz, lineasEntrada);

        if ("INVALIDO".equals(raiz.get("resultado"))) {
            int http = ((Number) raiz.get("httpEsperado")).intValue();
            String codigo = (String) raiz.get("codigoEsperado");
            ResultActions r = postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                    .andExpect(status().is(http))
                    .andExpect(jsonPath("$.codigo").value(codigo));
            if (raiz.get("diferenciaEsperada") != null) {
                r.andExpect(jsonPath("$.diferencia").value((String) raiz.get("diferenciaEsperada")));
            }
            nadaGuardado(s);
            return;
        }

        // Caso válido: se guarda y las líneas persistidas coinciden exactamente con lo esperado
        List<Map<String, Object>> esperadas = (List<Map<String, Object>>) raiz.get("lineasEsperadas");
        String id = leer(
                postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                        .andExpect(status().isCreated()),
                "$.id");
        verificarLineasGuardadas(s, UUID.fromString(id), esperadas);

        // Los casos de IVA verifican además que la vista previa expande igual que lo que se guardó (ADR-036)
        if ("iva".equals(carpeta)) {
            verificarVistaPrevia(s, cuerpo, esperadas);
        }
    }

    // ------------------------------------------------------------------------------------- lectura del caso

    /** Lee un archivo JSON de {@code casos/<carpeta>/<archivo>} como un mapa genérico. */
    private static Map<String, Object> leerCaso(String carpeta, String archivo) throws IOException {
        String contenido =
                new ClassPathResource("casos/" + carpeta + "/" + archivo).getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(contenido).read("$", Map.class);
    }

    /** Resuelve "HOY", "HOY+N" y "HOY-N" contra la fecha de hoy en hora de El Salvador; cualquier otro valor se usa tal cual. */
    private static String resolverFecha(String marcador) {
        if (marcador == null) {
            return null;
        }
        LocalDate hoy = LocalDate.now(ZONA_SV);
        if ("HOY".equals(marcador)) {
            return hoy.toString();
        }
        if (marcador.startsWith("HOY+")) {
            return hoy.plusDays(Long.parseLong(marcador.substring(4))).toString();
        }
        if (marcador.startsWith("HOY-")) {
            return hoy.minusDays(Long.parseLong(marcador.substring(4))).toString();
        }
        return marcador;
    }

    /** Resuelve los marcadores {@code IVA_DEBITO}/{@code IVA_CREDITO} contra la configuración de la empresa; cualquier otro código se usa tal cual. */
    private String resolverCodigo(Sesion s, String codigoOMarcador) throws Exception {
        return switch (codigoOMarcador) {
            case "IVA_DEBITO" -> leer(get(s, "/contabilidad/configuracion"), "$.cuentaIvaDebito.codigo");
            case "IVA_CREDITO" -> leer(get(s, "/contabilidad/configuracion"), "$.cuentaIvaCredito.codigo");
            default -> codigoOMarcador;
        };
    }

    // --------------------------------------------------------------------------------- construcción del cuerpo

    /** Línea de entrada en JSON; los montos van como cadena (ADR-013). */
    private static String lineaJson(UUID cuenta, String debe, String haber, boolean llevaIva) {
        return "{\"cuentaId\":\"" + cuenta + "\",\"debe\":\"" + debe + "\",\"haber\":\"" + haber + "\",\"llevaIva\":"
                + llevaIva + "}";
    }

    @SuppressWarnings("unchecked")
    private String construirCuerpo(Sesion s, Map<String, Object> raiz, List<Map<String, Object>> lineasEntrada)
            throws Exception {
        String fecha = resolverFecha((String) raiz.get("fecha"));
        String concepto = raiz.containsKey("concepto") ? (String) raiz.get("concepto") : "Caso dorado F3";
        String modo = (String) raiz.get("modoPrecio");

        List<String> partes = new ArrayList<>();
        if (raiz.get("lineasGeneradasCantidad") != null) {
            // Caso PLT-002 de más de 200 líneas: se generan alternando dos cuentas; no necesitan cuadrar porque
            // el límite de forma (maxItems) se rechaza antes de llegar a la regla de negocio
            int cantidad = ((Number) raiz.get("lineasGeneradasCantidad")).intValue();
            UUID a = cuentaId(s.empresa(), (String) raiz.get("lineasGeneradasCuentaA"));
            UUID b = cuentaId(s.empresa(), (String) raiz.get("lineasGeneradasCuentaB"));
            String monto = (String) raiz.get("lineasGeneradasMonto");
            for (int i = 0; i < cantidad; i++) {
                boolean debeA = i % 2 == 0;
                partes.add(lineaJson(debeA ? a : b, debeA ? monto : "0", debeA ? "0" : monto, false));
            }
        } else {
            for (Map<String, Object> l : lineasEntrada) {
                UUID cuenta = cuentaId(s.empresa(), resolverCodigo(s, (String) l.get("cuentaCodigo")));
                partes.add(lineaJson(
                        cuenta,
                        (String) l.get("debe"),
                        (String) l.get("haber"),
                        Boolean.TRUE.equals(l.get("llevaIva"))));
            }
        }

        return "{\"fecha\":\"" + fecha + "\",\"concepto\":\"" + concepto + "\""
                + (modo == null ? "" : ",\"modoPrecio\":\"" + modo + "\"")
                + ",\"lineas\":[" + String.join(",", partes) + "]}";
    }

    // --------------------------------------------------------------------------------------------- verificación

    /** Fila de {@code asiento_linea} tal como quedó guardada, con el código de su cuenta y el número de línea de su base. */
    private record FilaLineaDb(
            String codigo,
            BigDecimal debe,
            BigDecimal haber,
            String origenLinea,
            int numeroLinea,
            Integer baseNumeroLinea) {}

    /** Líneas persistidas del asiento, en orden, con la cuenta ya resuelta a su código y el número de línea de su base (si es de IVA). */
    private List<FilaLineaDb> lineasPersistidas(UUID asientoId) {
        return duenio.sql("""
                        SELECT cc.codigo AS codigo, al.debe AS debe, al.haber AS haber, al.origen_linea AS origen_linea,
                               al.numero_linea AS numero_linea, lb.numero_linea AS base_numero_linea
                          FROM asiento_linea al
                          JOIN cuenta_contable cc ON cc.id = al.cuenta_id
                          LEFT JOIN asiento_linea lb ON lb.id = al.linea_base_id
                         WHERE al.asiento_id = ?
                         ORDER BY al.numero_linea
                        """)
                .param(asientoId)
                .query((rs, i) -> new FilaLineaDb(
                        rs.getString("codigo"),
                        rs.getBigDecimal("debe"),
                        rs.getBigDecimal("haber"),
                        rs.getString("origen_linea"),
                        rs.getInt("numero_linea"),
                        rs.getObject("base_numero_linea") == null ? null : rs.getInt("base_numero_linea")))
                .list();
    }

    /** Compara, como dueño, las líneas realmente guardadas contra las esperadas del caso (cuenta, lados y origen; el
     * enlace de una línea IVA_CALCULADO se verifica contra la línea inmediatamente anterior, su base). */
    private void verificarLineasGuardadas(Sesion s, UUID asientoId, List<Map<String, Object>> esperadas)
            throws Exception {
        List<FilaLineaDb> reales = lineasPersistidas(asientoId);
        assertThat(reales).as("cantidad de líneas guardadas").hasSize(esperadas.size());

        for (int i = 0; i < esperadas.size(); i++) {
            Map<String, Object> e = esperadas.get(i);
            FilaLineaDb real = reales.get(i);
            String codigoEsperado = resolverCodigo(s, (String) e.get("cuentaCodigo"));

            assertThat(real.codigo()).as("línea %d cuenta", i + 1).isEqualTo(codigoEsperado);
            assertThat(real.debe()).as("línea %d debe", i + 1).isEqualByComparingTo((String) e.get("debe"));
            assertThat(real.haber()).as("línea %d haber", i + 1).isEqualByComparingTo((String) e.get("haber"));
            assertThat(real.origenLinea()).as("línea %d origen", i + 1).isEqualTo((String) e.get("origenLinea"));

            if ("IVA_CALCULADO".equals(e.get("origenLinea"))) {
                assertThat(real.baseNumeroLinea())
                        .as("línea %d enlazada a su línea base", i + 1)
                        .isEqualTo(reales.get(i - 1).numeroLinea());
            } else {
                assertThat(real.baseNumeroLinea())
                        .as("línea %d sin base", i + 1)
                        .isNull();
            }
        }
    }

    /** La vista previa del cuerpo produce las mismas líneas (cuenta, lados, origen y el número de línea de entrada
     * de cada línea de IVA) que las que se guardaron, sin escribir nada (ADR-036). */
    private void verificarVistaPrevia(Sesion s, String cuerpo, List<Map<String, Object>> esperadas) throws Exception {
        ResultActions r = post(s, "/contabilidad/asientos/vista-previa", cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuadra").value(true))
                .andExpect(jsonPath("$.lineas.length()").value(esperadas.size()));

        for (int i = 0; i < esperadas.size(); i++) {
            Map<String, Object> e = esperadas.get(i);
            String codigoEsperado = resolverCodigo(s, (String) e.get("cuentaCodigo"));
            r.andExpect(jsonPath("$.lineas[" + i + "].cuenta.codigo").value(codigoEsperado))
                    .andExpect(jsonPath("$.lineas[" + i + "].debe").value((String) e.get("debe")))
                    .andExpect(jsonPath("$.lineas[" + i + "].haber").value((String) e.get("haber")))
                    .andExpect(jsonPath("$.lineas[" + i + "].origenLinea").value((String) e.get("origenLinea")));
            Object numeroLineaOrigen = e.get("numeroLineaOrigen");
            if (numeroLineaOrigen != null) {
                r.andExpect(jsonPath("$.lineas[" + i + "].numeroLineaOrigen")
                        .value(((Number) numeroLineaOrigen).intValue()));
            } else {
                r.andExpect(jsonPath("$.lineas[" + i + "].numeroLineaOrigen").doesNotExist());
            }
        }
    }

    /** Verifica que un rechazo no dejó rastro: ni asiento, ni líneas, ni saldos, ni correlativo. */
    private void nadaGuardado(Sesion s) {
        assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM asiento_linea WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM saldo_cuenta_mensual WHERE empresa_id = ?", s.empresa()))
                .isZero();
        assertThat(contar("SELECT count(*) FROM correlativo_asiento WHERE empresa_id = ?", s.empresa()))
                .isZero();
    }
}
