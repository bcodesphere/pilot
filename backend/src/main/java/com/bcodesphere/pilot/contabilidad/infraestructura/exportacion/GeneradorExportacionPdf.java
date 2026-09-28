package com.bcodesphere.pilot.contabilidad.infraestructura.exportacion;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.aplicacion.GeneradorExportacion;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.estados.BalanzaComprobacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.LibroMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva;
import com.bcodesphere.pilot.contabilidad.dominio.exportacion.FormatoArchivo;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Exportación de reportes a PDF con Thymeleaf y OpenHTMLtoPDF (ADR-038 "Implementación de las exportaciones"):
 * tamaño carta, título, período, fecha y hora de generación en hora de El Salvador, número de página y, en los
 * estados financieros, la leyenda de estado de gestión (ADR-037). El motor de plantillas es propio, sin
 * {@code spring-boot-starter-thymeleaf}, porque ese starter resolvería vistas de Spring MVC (CLAUDE.md 4.3). No
 * calcula nada: cada método recibe el mismo objeto de dominio que ya entregó el reporte JSON y lo vuelca en su
 * plantilla ({@code plantillas/pdf/*.html}) con {@code th:text}, nunca {@code th:utext}, para que cualquier dato de
 * negocio quede escapado.
 */
@Component
public class GeneradorExportacionPdf implements GeneradorExportacion {

    /** Zona horaria de negocio para la fecha y hora de generación (CLAUDE.md, encabezado). */
    private static final ZoneId ZONA_EL_SALVADOR = ZoneId.of("America/El_Salvador");

    /** Formato de la fecha y hora de generación mostrada en el PDF. */
    private static final DateTimeFormatter FORMATO_GENERADO_EN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.of("es", "SV"));

    /** Motor de Thymeleaf propio, con las plantillas de {@code src/main/resources/plantillas/pdf/}. */
    private final TemplateEngine motor;

    /** Crea el generador con su motor de plantillas Thymeleaf (sin Spring MVC, CLAUDE.md 4.3). */
    public GeneradorExportacionPdf() {
        ClassLoaderTemplateResolver resolvedor = new ClassLoaderTemplateResolver();
        resolvedor.setPrefix("plantillas/pdf/");
        resolvedor.setSuffix(".html");
        resolvedor.setTemplateMode(TemplateMode.HTML);
        resolvedor.setCharacterEncoding("UTF-8");
        this.motor = new TemplateEngine();
        this.motor.setTemplateResolver(resolvedor);
    }

    @Override
    public FormatoArchivo formato() {
        return FormatoArchivo.PDF;
    }

    @Override
    public String extension() {
        return "pdf";
    }

    @Override
    public String tipoContenido() {
        return "application/pdf";
    }

    @Override
    public byte[] libroDiario(List<Asiento> asientos, LocalDate desde, LocalDate hasta) {
        Dinero totalDebe = Dinero.CERO;
        Dinero totalHaber = Dinero.CERO;
        for (Asiento a : asientos) {
            totalDebe = totalDebe.sumar(a.totalDebe());
            totalHaber = totalHaber.sumar(a.totalHaber());
        }
        Context c = contexto("Libro Diario", "Del " + desde + " al " + hasta);
        c.setVariable("asientos", asientos);
        c.setVariable("totalDebe", totalDebe);
        c.setVariable("totalHaber", totalHaber);
        return generar("libro-diario", c);
    }

    @Override
    public byte[] libroMayor(LibroMayor m) {
        Context c = contexto("Libro Mayor", "Del " + m.desde() + " al " + m.hasta());
        c.setVariable("mayor", m);
        return generar("libro-mayor", c);
    }

    @Override
    public byte[] balanza(BalanzaComprobacion b) {
        Context c = contexto("Balanza de Comprobación", "Del " + b.desde() + " al " + b.hasta());
        c.setVariable("balanza", b);
        return generar("balanza", c);
    }

    @Override
    public byte[] estadoResultados(EstadoResultados e) {
        Context c = contexto("Estado de Resultados", "Del " + e.desde() + " al " + e.hasta());
        c.setVariable("estado", e);
        return generar("estado-resultados", c);
    }

    @Override
    public byte[] estadoSituacionFinanciera(EstadoSituacionFinanciera e) {
        Context c = contexto("Estado de Situación Financiera", "Al corte del " + e.fechaCorte());
        c.setVariable("estado", e);
        return generar("estado-situacion-financiera", c);
    }

    @Override
    public byte[] resumenIva(ResumenIva r) {
        Context c = contexto("Resumen de IVA", String.format(Locale.ROOT, "%04d-%02d", r.anio(), r.mes()));
        c.setVariable("resumen", r);
        return generar("resumen-iva", c);
    }

    /** Contexto común a toda plantilla: título, período y la fecha y hora de generación en hora de El Salvador. */
    private static Context contexto(String titulo, String periodo) {
        Context c = new Context();
        c.setVariable("titulo", titulo);
        c.setVariable("periodo", periodo);
        c.setVariable("generadoEn", ZonedDateTime.now(ZONA_EL_SALVADOR).format(FORMATO_GENERADO_EN));
        return c;
    }

    /** Procesa la plantilla a HTML y lo convierte a PDF; cualquier fallo de E/S se relanza sin exponer detalles. */
    private byte[] generar(String plantilla, Context contexto) {
        String html = motor.process(plantilla, contexto);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // useFastMode() no muta el builder existente: hay que quedarse con el que devuelve (SpotBugs RV_*)
            builder = builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(salida);
            builder.run();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }
}
