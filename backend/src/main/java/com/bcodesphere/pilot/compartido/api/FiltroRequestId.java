package com.bcodesphere.pilot.compartido.api;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Genera o propaga {@code X-Request-Id}, lo devuelve en la respuesta y lo deja en el MDC como {@code traceId}
 * para que todos los logs de la petición lo lleven (CLAUDE.md 1.1.12 y 16.4). Corre primero en la cadena.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroRequestId extends OncePerRequestFilter {

    /** Nombre del header de correlación (CLAUDE.md 12.2). */
    public static final String HEADER = "X-Request-Id";

    /** Solo se acepta un id corto y sin caracteres de control: evita inyectar líneas falsas en los logs. */
    private static final Pattern VALIDO = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        // 1. Respeta el id del cliente si es seguro; si falta o es sospechoso genera uno nuevo
        String recibido = request.getHeader(HEADER);
        String id = recibido != null && VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        // 2. Lo devuelve en la respuesta antes de procesar, así también sale en los errores
        // Seguro: id es el valor recibido solo si cumple VALIDO (sin CR/LF); si no, es un UUID generado.
        // SpotBugs no entiende esa validación: ver la exclusión acotada en config/spotbugs-exclude.xml.
        response.setHeader(HEADER, id);
        MDC.put(ClavesMdc.TRACE_ID, id);
        try {
            cadena.doFilter(request, response);
        } finally {
            // 3. Limpia el MDC: los hilos del servidor se reutilizan
            MDC.remove(ClavesMdc.TRACE_ID);
        }
    }
}
