package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.plataforma.ExcepcionIdempotencia;
import com.bcodesphere.pilot.plataforma.RequiereIdempotencia;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Exige el header {@code Idempotency-Key} en los endpoints marcados con {@link RequiereIdempotencia}: si falta
 * responde 428 {@code PLT-006} (CLAUDE.md 8.4). La excepción la traduce el manejador global a Problem Details.
 */
@Component
class InterceptorIdempotencia implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1. Solo aplica a endpoints de controlador que pidan idempotencia (en el método o en la clase)
        if (handler instanceof HandlerMethod metodo && exigeIdempotencia(metodo)) {
            // 2. Ausente o en blanco: 428 antes de ejecutar el controlador
            String clave = request.getHeader(RequiereIdempotencia.HEADER);
            if (clave == null || clave.isBlank()) {
                throw ExcepcionIdempotencia.claveAusente();
            }
        }
        return true;
    }

    /** Busca la anotación en el método y, si no está, en el controlador. */
    private static boolean exigeIdempotencia(HandlerMethod metodo) {
        return AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), RequiereIdempotencia.class)
                || AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), RequiereIdempotencia.class);
    }
}
