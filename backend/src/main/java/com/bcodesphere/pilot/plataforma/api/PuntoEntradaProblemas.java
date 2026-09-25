package com.bcodesphere.pilot.plataforma.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Traduce los rechazos de la cadena de seguridad (401 y 403) al mismo formato de error del resto de la API. Los filtros
 * de seguridad corren antes del {@code DispatcherServlet}, donde el manejador global no llega; por eso se delega en el
 * resolvedor de excepciones de MVC, que sí aplica el {@code @RestControllerAdvice} y responde Problem Details
 * {@code PLT-009} o {@code PLT-010} con {@code X-Request-Id}, sin detalles internos (CLAUDE.md 8.4).
 */
final class PuntoEntradaProblemas implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolvedor;

    /**
     * Crea el punto de entrada.
     *
     * @param resolvedor resolvedor de excepciones de Spring MVC (bean {@code handlerExceptionResolver})
     */
    PuntoEntradaProblemas(HandlerExceptionResolver resolvedor) {
        this.resolvedor = resolvedor;
    }

    /** Sin credencial o con una inválida: 401 PLT-009, con el desafío {@code WWW-Authenticate} de RFC 6750. */
    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) {
        response.setHeader("WWW-Authenticate", "Bearer");
        resolvedor.resolveException(request, response, null, authException);
    }

    /** Autenticado pero sin rol suficiente: 403 PLT-010. */
    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) {
        resolvedor.resolveException(request, response, null, accessDeniedException);
    }
}
