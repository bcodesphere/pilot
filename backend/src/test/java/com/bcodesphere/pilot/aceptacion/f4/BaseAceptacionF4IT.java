package com.bcodesphere.pilot.aceptacion.f4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bcodesphere.pilot.compartido.api.FiltroRequestId;
import com.bcodesphere.pilot.plataforma.BasePlataformaIT;
import com.bcodesphere.pilot.soporte.PostgresContenedor;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base de las pruebas de aceptación de la fase F4 (F4-06): contexto completo de Spring contra PostgreSQL real, con
 * la aplicación conectada como {@code pilot_app} y las cadenas de seguridad reales. Sigue el mismo patrón que
 * {@code aceptacion.f3.BaseAceptacionF3IT}: se escribe aquí en vez de extenderla porque es package-private de otro
 * paquete. Agrega, sobre esa base, los ayudantes de reportes y exportaciones que {@code contabilidad.api.ReportesIT}
 * y {@code contabilidad.api.ExportacionesIT} ya usan, para no repetirlos por reflexión ni por herencia cruzada de
 * paquetes. Cada clase hija crea sus propios usuarios y empresas (UUID propios por sesión), así que las clases son
 * independientes entre sí.
 */
abstract class BaseAceptacionF4IT extends BasePlataformaIT {

    /** Usuario con su empresa activa, del que salen el token y el encabezado {@code X-Empresa-Id}. */
    record Sesion(String sub, UUID usuario, UUID empresa) {}

    @Autowired
    private WebApplicationContext contexto;

    /** MockMvc con el filtro de X-Request-Id y las cadenas de seguridad reales. */
    protected MockMvc mvc;

    /** Cliente JDBC como dueño de la base: siembra y verifica sin RLS. */
    protected final JdbcClient duenio = JdbcClient.create(PostgresContenedor.dataSourceDuenio());

    /** Construye el MockMvc antes de cada prueba. */
    @BeforeEach
    void prepararMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .addFilters(contexto.getBean(FiltroRequestId.class))
                .apply(springSecurity())
                .build();
    }

    /** JWT simulado con los claims del realm {@code pilot} (correo verificado, nombre y teléfono). */
    protected static RequestPostProcessor token(String sub) {
        return jwt().jwt(j -> {
            j.subject(sub);
            j.claim("email", sub.replace("sub-", "u") + "@prueba.sv");
            j.claim("name", "Nombre de " + sub);
            j.claim("email_verified", true);
            j.claim("telefono", "70001234");
        });
    }

    /** Primer inicio de sesión de un usuario nuevo: crea su empresa personal con rol admin_empresa. */
    protected Sesion nuevaSesion() throws Exception {
        String sub = "sub-" + UUID.randomUUID();
        String cuerpo = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me").with(token(sub)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Sesion(
                sub,
                UUID.fromString(JsonPath.read(cuerpo, "$.id")),
                UUID.fromString(JsonPath.read(cuerpo, "$.membresias[0].empresaId")));
    }

    /** Sesión nueva con Contabilidad ya instalada (exige 201; dispara la precarga posterior a V15). */
    protected Sesion sesionConContabilidad() throws Exception {
        Sesion s = nuevaSesion();
        mvc.perform(con(MockMvcRequestBuilders.post("/api/v1/aplicaciones/contabilidad/instalacion"), s))
                .andExpect(status().isCreated());
        return s;
    }

    /** Siembra (como dueño) un segundo usuario con el rol dado en la empresa de {@code duenioEmpresa}. */
    protected Sesion sembrarMiembro(Sesion duenioEmpresa, String rol) throws Exception {
        Sesion otro = nuevaSesion();
        duenio.sql("INSERT INTO empresa_usuario (empresa_id, usuario_id, rol, estado) VALUES (?, ?, ?, 'ACTIVA')")
                .params(duenioEmpresa.empresa(), otro.usuario(), rol)
                .update();
        return new Sesion(otro.sub(), otro.usuario(), duenioEmpresa.empresa());
    }

    /** Id de una cuenta de la empresa por su código (como dueño, sin RLS). */
    protected UUID cuentaId(UUID empresa, String codigo) {
        return duenio.sql("SELECT id FROM cuenta_contable WHERE empresa_id = ? AND codigo = ?")
                .params(empresa, codigo)
                .query(UUID.class)
                .single();
    }

    /** Cuenta filas con SQL como dueño. */
    protected int contar(String sql, Object... parametros) {
        return duenio.sql(sql).params(parametros).query(Integer.class).single();
    }

    // ---------------------------------------------------------------------------------- construcción de asientos

    /** Línea de entrada en JSON; los montos van como cadena (ADR-013). */
    static String linea(UUID cuenta, String debe, String haber, boolean llevaIva) {
        return "{\"cuentaId\":\"" + cuenta + "\",\"debe\":\"" + debe + "\",\"haber\":\"" + haber + "\",\"llevaIva\":"
                + llevaIva + "}";
    }

    /** Cuerpo de un asiento manual con las líneas dadas y modo de precio opcional. */
    static String asiento(String fecha, String concepto, String modo, String... lineas) {
        return "{\"fecha\":\"" + fecha + "\",\"concepto\":\"" + escapar(concepto) + "\""
                + (modo == null ? "" : ",\"modoPrecio\":\"" + modo + "\"") + ",\"lineas\":["
                + String.join(",", lineas) + "]}";
    }

    /** Escapa comillas de un texto para incrustarlo en JSON construido a mano. */
    private static String escapar(String texto) {
        return texto.replace("\"", "\\\"");
    }

    // ---------------------------------------------------------------------------------- peticiones

    /** Agrega el token y la empresa activa de la sesión. */
    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder b, Sesion s) {
        return b.with(token(s.sub())).header("X-Empresa-Id", s.empresa().toString());
    }

    /** {@code GET} de una ruta bajo {@code /api/v1} con la empresa activa de la sesión. */
    protected ResultActions get(Sesion s, String ruta) throws Exception {
        return mvc.perform(con(MockMvcRequestBuilders.get("/api/v1" + ruta), s));
    }

    /** {@code POST} con cuerpo JSON, sin {@code Idempotency-Key}. */
    protected ResultActions post(Sesion s, String ruta, String json) throws Exception {
        return mvc.perform(con(MockMvcRequestBuilders.post("/api/v1" + ruta), s)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** {@code POST} con {@code Idempotency-Key} (registro y reversión de asientos); la clave nula omite el header. */
    protected ResultActions postConClave(Sesion s, String ruta, String clave, String json) throws Exception {
        MockHttpServletRequestBuilder b = con(MockMvcRequestBuilders.post("/api/v1" + ruta), s);
        if (clave != null) {
            b.header("Idempotency-Key", clave);
        }
        return mvc.perform(b.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Registra un asiento con la clave dada y devuelve su id. */
    protected String registrar(Sesion s, String clave, String cuerpo) throws Exception {
        return leer(postConClave(s, "/contabilidad/asientos", clave, cuerpo).andExpect(status().isCreated()), "$.id");
    }

    /** Revierte un asiento en la fecha dada y devuelve el id de la reversión. */
    protected String revertir(Sesion s, String asientoId, String clave, String fecha) throws Exception {
        return leer(
                postConClave(
                                s,
                                "/contabilidad/asientos/" + asientoId + "/reversion",
                                clave,
                                "{\"fecha\":\"" + fecha + "\"}")
                        .andExpect(status().isCreated()),
                "$.id");
    }

    /** Lee un valor del cuerpo de una respuesta con JsonPath. */
    protected static <T> T leer(ResultActions r, String jsonPath) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), jsonPath);
    }

    // ---------------------------------------------------------------------------------- conjunto dorado (F4-06)

    /** Sesión con el conjunto dorado ya sembrado, el documento JSON leído y el id de cada asiento por su referencia. */
    protected record ConjuntoDorado(Sesion sesion, DocumentContext doc, Map<String, String> idsPorRef) {

        /** Atajo a {@code esperado.<seccion>} del conjunto, como mapa. */
        @SuppressWarnings("unchecked")
        Map<String, Object> esperado(String seccion) {
            return doc.read("$.esperado." + seccion);
        }
    }

    /** Lee {@code casos/estados/conjunto-dorado-f4.json} como un documento JsonPath navegable. */
    protected static DocumentContext leerConjuntoDorado() throws Exception {
        String contenido = new org.springframework.core.io.ClassPathResource("casos/estados/conjunto-dorado-f4.json")
                .getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(contenido);
    }

    /**
     * Siembra el conjunto dorado de F4 por la API (nunca por SQL directo, para ejercitar la expansión de IVA y la
     * mayorización reales, ADR-018): una empresa nueva con Contabilidad instalada, los 15 asientos de
     * {@code conjunto-dorado-f4.json} registrados en el orden del archivo y su única reversión aplicada.
     */
    @SuppressWarnings("unchecked")
    protected ConjuntoDorado sembrarConjuntoDorado() throws Exception {
        DocumentContext doc = leerConjuntoDorado();
        Sesion s = sesionConContabilidad();
        List<Map<String, Object>> asientos = doc.read("$.asientos");
        Map<String, String> idsPorRef = new HashMap<>();
        for (Map<String, Object> a : asientos) {
            String ref = (String) a.get("ref");
            String fecha = (String) a.get("fecha");
            String concepto = (String) a.get("concepto");
            String modo = (String) a.get("modoPrecio");
            List<Map<String, Object>> lineasEntrada = (List<Map<String, Object>>) a.get("lineas");
            List<String> partesLinea = lineasEntrada.stream()
                    .map(l -> linea(
                            cuentaId(s.empresa(), (String) l.get("cuentaCodigo")),
                            (String) l.get("debe"),
                            (String) l.get("haber"),
                            Boolean.TRUE.equals(l.get("llevaIva"))))
                    .toList();
            String cuerpo = asiento(fecha, concepto, modo, partesLinea.toArray(String[]::new));
            idsPorRef.put(ref, registrar(s, "cd-" + ref, cuerpo));
        }
        List<Map<String, Object>> reversiones = doc.read("$.reversiones");
        for (Map<String, Object> r : reversiones) {
            String ref = (String) r.get("ref");
            String deRef = (String) r.get("deAsientoRef");
            String fecha = (String) r.get("fecha");
            idsPorRef.put(ref, revertir(s, idsPorRef.get(deRef), "cd-" + ref, fecha));
        }
        return new ConjuntoDorado(s, doc, idsPorRef);
    }

    // ---------------------------------------------------------------------------------- exportaciones (ADR-038)

    /** Ejecuta la exportación, confirma el estado y el tipo de contenido, y devuelve los bytes del archivo. */
    protected byte[] exportar(Sesion s, String ruta, String tipoContenidoEsperado) throws Exception {
        ResultActions r = get(s, ruta)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", tipoContenidoEsperado));
        return r.andReturn().getResponse().getContentAsByteArray();
    }

    /** Extrae el texto de un PDF con PDFBox, para verificar su contenido sin depender del layout exacto. */
    protected static String extraerTexto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    /** Compara una columna de una fila del CSV, identificada por su etiqueta, contra el valor esperado. */
    protected static void verificarCsv(String textoCsv, String etiqueta, int columna, String esperado) {
        assertThat(filaCsv(textoCsv, etiqueta)[columna])
                .as("CSV: columna %d de '%s'", columna, etiqueta)
                .isEqualTo(esperado);
    }

    /** Divide el CSV en líneas (RFC 4180 con CRLF) y devuelve los campos de la que empieza con la etiqueta dada. */
    protected static String[] filaCsv(String textoCsv, String etiqueta) {
        for (String linea : textoCsv.split("\r\n")) {
            String[] campos = linea.split(",", -1);
            if (campos.length > 0 && etiqueta.equals(campos[0])) {
                return campos;
            }
        }
        throw new AssertionError("No se encontró la fila '" + etiqueta + "' en el CSV exportado");
    }

    /**
     * Lee el total (última columna) de la fila de valores del desglose de IVA de una sección, dos líneas después
     * de su encabezado (la línea del encabezado de sección, luego "Manual,N8N,Reversión,Total", luego los valores).
     */
    protected static String totalDesgloseCsv(String textoCsv, String etiquetaSeccion) {
        String[] lineas = textoCsv.split("\r\n");
        for (int i = 0; i < lineas.length; i++) {
            if (lineas[i].startsWith(etiquetaSeccion + ",")) {
                return lineas[i + 2].split(",", -1)[3];
            }
        }
        throw new AssertionError("No se encontró la sección '" + etiquetaSeccion + "' en el CSV exportado");
    }

    /** Compara una celda numérica del XLSX, identificada por la etiqueta de su fila, contra el valor esperado. */
    protected static void verificarXlsx(byte[] xlsx, String etiqueta, int columna, String esperado) throws IOException {
        assertThat(celdaNumericaDeFila(xlsx, etiqueta, columna))
                .as("XLSX: columna %d de '%s'", columna, etiqueta)
                .isEqualTo(Double.parseDouble(esperado));
    }

    /** Busca la fila cuya primera celda sea el texto dado y devuelve el valor numérico de la columna pedida. */
    protected static double celdaNumericaDeFila(byte[] xlsx, String textoPrimeraCelda, int columna) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            for (Row fila : hoja) {
                Cell primera = fila.getCell(0);
                if (primera != null
                        && primera.getCellType() == CellType.STRING
                        && textoPrimeraCelda.equals(primera.getStringCellValue())) {
                    return fila.getCell(columna).getNumericCellValue();
                }
            }
            throw new AssertionError("No se encontró la fila '" + textoPrimeraCelda + "' en la hoja exportada");
        }
    }

    /**
     * Busca la fila cuya primera celda sea la etiqueta dada (p. ej. "IVA débito fiscal") y devuelve el valor
     * numérico de la columna pedida, {@code filasDespues} filas más abajo (la fila de valores del desglose de IVA,
     * dos filas después de su encabezado de sección).
     */
    protected static double celdaTrasEtiqueta(byte[] xlsx, String etiqueta, int filasDespues, int columna)
            throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            XSSFSheet hoja = libro.getSheetAt(0);
            for (Row fila : hoja) {
                Cell primera = fila.getCell(0);
                if (primera != null
                        && primera.getCellType() == CellType.STRING
                        && etiqueta.equals(primera.getStringCellValue())) {
                    Row destino = hoja.getRow(fila.getRowNum() + filasDespues);
                    return destino.getCell(columna).getNumericCellValue();
                }
            }
            throw new AssertionError("No se encontró la etiqueta '" + etiqueta + "' en la hoja exportada");
        }
    }

    /** Confirma que el valor esperado aparece cerca de su etiqueta en el texto extraído del PDF (ver ExportacionesIT). */
    protected static void verificarPdf(String textoPdf, String etiqueta, String esperado) {
        String patron = Pattern.quote(etiqueta) + "(?s).{0,25}?" + Pattern.quote(esperado);
        assertThat(Pattern.compile(patron).matcher(textoPdf).find())
                .as("PDF: '%s' cerca de '%s'", etiqueta, esperado)
                .isTrue();
    }
}
