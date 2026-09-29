package com.bcodesphere.pilot.aceptacion.f3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

/**
 * Casos dorados de la reversión (parte de CLAUDE.md §15, punto 1: "Asientos válidos e inválidos para cada código
 * CON-001 a CON-013"; CON-008, CON-009 y CON-018 son de la reversión). Recorre {@code casos/reversiones/} y, para
 * cada escenario, registra un asiento simple, intenta la reversión inválida que describe el caso y comprueba el HTTP,
 * el código y que no queda nada nuevo guardado. CON-007 sobre la reversión ya lo cubre
 * {@code LibroDiarioIT.losRechazosDeLaReversionNoGuardanNada} por completo, así que no se repite aquí.
 */
class CasosDoradosReversionF3IT extends BaseAceptacionF3IT {

    private static final String FECHA_ORIGINAL = "2026-03-05";
    private static final String FECHA_REVERSION_VALIDA = "2026-03-10";
    private static final String FECHA_ANTERIOR_AL_ORIGINAL = "2026-02-01";

    static Stream<Arguments> casos() throws IOException {
        List<Arguments> args = new ArrayList<>();
        for (var recurso :
                new PathMatchingResourcePatternResolver().getResources("classpath:casos/reversiones/*.json")) {
            args.add(Arguments.of(recurso.getFilename()));
        }
        return args.stream();
    }

    /** Línea simple Caja/Ventas por el monto, sin IVA. */
    private String asientoSimple(Sesion s, String fecha, String monto) {
        UUID caja = cuentaId(s.empresa(), "11010101");
        UUID ventas = cuentaId(s.empresa(), "51010101");
        return "{\"fecha\":\"" + fecha + "\",\"concepto\":\"Caso dorado de reversión\",\"lineas\":["
                + "{\"cuentaId\":\"" + caja + "\",\"debe\":\"" + monto + "\",\"haber\":\"0\",\"llevaIva\":false},"
                + "{\"cuentaId\":\"" + ventas + "\",\"debe\":\"0\",\"haber\":\"" + monto + "\",\"llevaIva\":false}]}";
    }

    private String registrar(Sesion s, String cuerpo) throws Exception {
        return leer(
                postConClave(s, "/contabilidad/asientos", "k-" + UUID.randomUUID(), cuerpo)
                        .andExpect(status().isCreated()),
                "$.id");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("casos")
    void aplicaElCasoDoradoDeReversion(String archivo) throws Exception {
        Map<String, Object> raiz = leerCaso(archivo);
        String escenario = (String) raiz.get("escenario");
        int http = ((Number) raiz.get("httpEsperado")).intValue();
        String codigo = (String) raiz.get("codigoEsperado");

        Sesion s = sesionConContabilidad();
        String original = registrar(s, asientoSimple(s, FECHA_ORIGINAL, "100.00"));
        String rutaReversion = "/contabilidad/asientos/" + original + "/reversion";

        switch (escenario) {
            case "DOBLE_REVERSION" -> {
                postConClave(s, rutaReversion, "primera", "{\"fecha\":\"" + FECHA_REVERSION_VALIDA + "\"}")
                        .andExpect(status().isCreated());
                int asientosAntes = contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa());
                postConClave(s, rutaReversion, "segunda", "{\"fecha\":\"" + FECHA_REVERSION_VALIDA + "\"}")
                        .andExpect(status().is(http))
                        .andExpect(jsonPath("$.codigo").value(codigo));
                assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                        .as("la segunda reversión no guarda nada nuevo")
                        .isEqualTo(asientosAntes);
            }
            case "REVERTIR_REVERSION" -> {
                String reversion = leer(
                        postConClave(s, rutaReversion, "r1", "{\"fecha\":\"" + FECHA_REVERSION_VALIDA + "\"}")
                                .andExpect(status().isCreated()),
                        "$.id");
                int asientosAntes = contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa());
                postConClave(s, "/contabilidad/asientos/" + reversion + "/reversion", "r2", "")
                        .andExpect(status().is(http))
                        .andExpect(jsonPath("$.codigo").value(codigo));
                assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                        .as("revertir la reversión no guarda nada nuevo")
                        .isEqualTo(asientosAntes);
            }
            case "FECHA_ANTERIOR" -> {
                int asientosAntes = contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa());
                postConClave(s, rutaReversion, "r1", "{\"fecha\":\"" + FECHA_ANTERIOR_AL_ORIGINAL + "\"}")
                        .andExpect(status().is(http))
                        .andExpect(jsonPath("$.codigo").value(codigo));
                assertThat(contar("SELECT count(*) FROM asiento WHERE empresa_id = ?", s.empresa()))
                        .as("la fecha anterior no guarda nada nuevo")
                        .isEqualTo(asientosAntes);
            }
            default -> throw new IllegalStateException("Escenario de reversión desconocido: " + escenario);
        }
    }

    private static Map<String, Object> leerCaso(String archivo) throws IOException {
        String contenido =
                new ClassPathResource("casos/reversiones/" + archivo).getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(contenido).read("$", Map.class);
    }
}
