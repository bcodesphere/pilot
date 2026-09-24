package com.bcodesphere.pilot.plataforma.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra los interceptores HTTP del módulo plataforma. */
@Configuration(proxyBeanMethods = false)
class ConfiguracionWebPlataforma implements WebMvcConfigurer {

    private final InterceptorIdempotencia interceptorIdempotencia;

    /**
     * Crea la configuración.
     *
     * @param interceptorIdempotencia interceptor que exige {@code Idempotency-Key}
     */
    ConfiguracionWebPlataforma(InterceptorIdempotencia interceptorIdempotencia) {
        this.interceptorIdempotencia = interceptorIdempotencia;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // El interceptor decide por anotación, así que se registra para todas las rutas
        registro.addInterceptor(interceptorIdempotencia);
    }
}
