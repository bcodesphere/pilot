package com.bcodesphere.pilot.compartido.api;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Cuerpo {@code application/problem+json}: exactamente los campos del esquema {@code ProblemDetails}
 * del contrato (RFC 9457 + extensiones de Pilot).
 *
 * @param type URI del tipo de problema ({@code about:blank} si no hay uno específico)
 * @param title resumen corto en español
 * @param status estado HTTP
 * @param detail explicación de esta ocurrencia, sin datos internos
 * @param instance ruta de la petición
 * @param codigo código de negocio con prefijo de módulo
 * @param errores errores por campo, solo en validaciones
 * @param diferencia diferencia monetaria (se serializa como cadena de 2 decimales)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemaDto(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String codigo,
        List<ErrorCampo> errores,
        Dinero diferencia) {

    /** Copia defensiva e inmutable de la lista de errores (puede ser nula si no aplica). */
    public ProblemaDto {
        errores = errores == null ? null : List.copyOf(errores);
    }
}
