package com.bcodesphere.pilot.plataforma.api;

import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra los interceptores HTTP del módulo plataforma y el prefijo {@code /api/v1} de los controladores que
 * implementan interfaces generadas desde el contrato.
 */
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

    /**
     * Los paths del contrato son relativos al servidor ({@code servers: /api/v1}) y el generador no incluye ese
     * prefijo en las interfaces; se agrega a todo controlador que implemente una interfaz del paquete del contrato.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurador) {
        configurador.addPathPrefix(
                "/api/v1",
                clase -> Arrays.stream(clase.getInterfaces())
                        .anyMatch(i -> i.getPackageName().equals("com.bcodesphere.pilot.compartido.api.contrato")));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        // El interceptor decide por anotación, así que se registra para todas las rutas
        registro.addInterceptor(interceptorIdempotencia);
    }
}
