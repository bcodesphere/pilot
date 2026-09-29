package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.api.contrato.FormatoExportacion;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Convierte el parámetro de consulta {@code formato} (ADR-038) a su enum del contrato. El valor JSON de
 * {@link FormatoExportacion} es minúscula ({@code "pdf"}, {@code "xlsx"}, {@code "csv"}), distinto del nombre de la
 * constante Java; sin este conversor, Spring MVC intentaría {@code Enum.valueOf} (sensible a mayúsculas) sobre los
 * parámetros de consulta y rechazaría todo valor válido del contrato con 400. Spring Boot registra automáticamente
 * cualquier bean {@link Converter} en el {@code ConversionService} usado para enlazar {@code @RequestParam}.
 */
@Component
class ConversorFormatoExportacion implements Converter<String, FormatoExportacion> {

    @Override
    public FormatoExportacion convert(String source) {
        return FormatoExportacion.fromValue(source);
    }
}
