package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.plataforma.aplicacion.AutenticarApiKey;
import com.bcodesphere.pilot.plataforma.aplicacion.ExigirAppInstalada;
import com.bcodesphere.pilot.plataforma.aplicacion.ResolverIdentidad;
import com.bcodesphere.pilot.plataforma.aplicacion.ValidarMembresia;
import com.bcodesphere.pilot.plataforma.infraestructura.ValidadoresJwt;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Seguridad HTTP de Pilot (CLAUDE.md 14, ADR-008): API sin sesión, con CORS para el frontend y roles por empresa con
 * jerarquía. Hay dos cadenas de filtros excluyentes: la de integraciones ({@code /api/v1/integraciones/**}), que solo
 * acepta API keys, y la general, que solo acepta el token de acceso de Keycloak (bearer JWT). Así una API key nunca
 * pasa por la cadena de usuarios (no es un JWT válido: 401) ni un JWT por la de integraciones (401).
 *
 * <p>Sin estado y sin CSRF: al usarse un bearer token en el header {@code Authorization} y no cookies, un sitio
 * externo no puede hacer que el navegador envíe la credencial, así que el ataque CSRF no aplica.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class ConfiguracionSeguridad {

    /** Headers que el frontend puede enviar (CLAUDE.md 4.5, 8.4 y 12.2). */
    private static final List<String> HEADERS_PERMITIDOS =
            List.of("Authorization", "Content-Type", "X-Empresa-Id", "If-Match", "Idempotency-Key", "X-Request-Id");

    /** Headers de respuesta que el frontend puede leer. */
    private static final List<String> HEADERS_EXPUESTOS = List.of("ETag", "X-Request-Id", "Idempotency-Replayed");

    /**
     * Cadena de las integraciones: solo API keys (CLAUDE.md 12.1, ADR-026). Va ANTES de la general ({@code @Order(1)})
     * porque Spring aplica la primera cadena cuyo {@code securityMatcher} coincide.
     *
     * @param http constructor de la cadena
     * @param autenticacion caso de uso que verifica la API key
     * @param resolvedor resolvedor de excepciones de MVC, para responder 401 y 403 en Problem Details
     * @return la cadena aplicada solo a {@code /api/v1/integraciones/**}
     * @throws Exception si la configuración falla
     */
    @Bean
    @Order(1)
    SecurityFilterChain cadenaDeIntegraciones(
            HttpSecurity http,
            AutenticarApiKey autenticacion,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolvedor)
            throws Exception {
        PuntoEntradaProblemas problemas = new PuntoEntradaProblemas(resolvedor);
        http
                // 1. Solo las rutas de integraciones; el resto lo atiende la cadena de usuarios
                .securityMatcher("/api/v1/integraciones/**")
                // 2. Sin sesión ni CSRF (bearer en el header) y sin CORS: n8n llama de servidor a servidor, no desde un
                //    navegador, así que ningún origen web queda autorizado
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 3. Toda ruta exige una API key válida; el alcance lo exige cada operación
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(problemas).accessDeniedHandler(problemas))
                // 4. La API key se autentica justo antes de la autorización (sin oauth2ResourceServer: un JWT aquí es
                // 401)
                .addFilterBefore(new FiltroApiKey(autenticacion, problemas), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Cadena de filtros de seguridad de los usuarios (resto de la API), con el token de Keycloak.
     *
     * @param http constructor de la cadena
     * @param identidad caso de uso de identidad (alta y sincronización)
     * @param membresia caso de uso de validación de membresía
     * @param aplicaciones caso de uso de app instalada
     * @param resolvedor resolvedor de excepciones de MVC, para responder 401 y 403 en Problem Details
     * @return la cadena aplicada a todas las peticiones
     * @throws Exception si la configuración falla
     */
    @Bean
    @Order(2)
    SecurityFilterChain cadenaDeSeguridad(
            HttpSecurity http,
            ResolverIdentidad identidad,
            ValidarMembresia membresia,
            ExigirAppInstalada aplicaciones,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolvedor)
            throws Exception {
        PuntoEntradaProblemas problemas = new PuntoEntradaProblemas(resolvedor);
        http
                // 1. Bearer token en el header: sin sesión y sin CSRF (ver la nota de la clase)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                // 2. Solo la salud del servicio es pública; todo lo demás, incluido /api/v1/**, exige autenticación
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // 3. Recurso protegido por JWT; los rechazos salen como Problem Details PLT-009 y PLT-010
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemas)
                        .accessDeniedHandler(problemas))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemas).accessDeniedHandler(problemas))
                // 4. Identidad y empresa activa, justo después de validar el token
                .addFilterAfter(
                        new FiltroEmpresaActiva(identidad, membresia, aplicaciones, resolvedor),
                        BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Decodificador del JWT: valida emisor, vigencia y audiencia, y obtiene la configuración del emisor en el primer
     * uso (no al arrancar), así la aplicación arranca aunque Keycloak no esté levantado.
     *
     * @param emisor URL del emisor OIDC ({@code PILOT_OIDC_ISSUER})
     * @param audiencia audiencia exigida ({@code pilot-api})
     * @return decodificador perezoso
     */
    @Bean
    JwtDecoder decodificadorJwt(
            @Value("${pilot.seguridad.emisor}") String emisor,
            @Value("${pilot.seguridad.audiencia}") String audiencia) {
        return ValidadoresJwt.decodificador(emisor, audiencia);
    }

    /**
     * Jerarquía de roles de CLAUDE.md 14.2: {@code admin_empresa} puede todo lo de {@code contador}, y este todo lo de
     * {@code auditor}.
     *
     * @return la jerarquía usada por la seguridad de métodos
     */
    @Bean
    static RoleHierarchy jerarquiaDeRoles() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN_EMPRESA")
                .implies("CONTADOR")
                .role("CONTADOR")
                .implies("AUDITOR")
                .build();
    }

    /**
     * Manejador de expresiones de la seguridad de métodos ({@code @PreAuthorize}) con la jerarquía de roles, para que
     * {@code hasRole('AUDITOR')} también admita a un contador o a un administrador.
     *
     * @param jerarquia jerarquía de roles de la aplicación
     * @return manejador con la jerarquía aplicada
     */
    @Bean
    static MethodSecurityExpressionHandler manejadorExpresionesDeMetodo(RoleHierarchy jerarquia) {
        DefaultMethodSecurityExpressionHandler manejador = new DefaultMethodSecurityExpressionHandler();
        manejador.setRoleHierarchy(jerarquia);
        return manejador;
    }

    /**
     * CORS para los orígenes de {@code pilot.cors.origenes} (en dev, el servidor de Vite). Sin orígenes configurados
     * ningún sitio externo puede llamar a la API desde un navegador.
     *
     * @param origenes lista de orígenes permitidos, separada por comas
     * @return la configuración aplicada a {@code /api/**}
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${pilot.cors.origenes:}") List<String> origenes) {
        CorsConfiguration configuracion = new CorsConfiguration();
        configuracion.setAllowedOrigins(origenes);
        configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracion.setAllowedHeaders(HEADERS_PERMITIDOS);
        configuracion.setExposedHeaders(HEADERS_EXPUESTOS);
        // El token viaja en Authorization, no en cookies: no hace falta permitir credenciales del navegador
        configuracion.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", configuracion);
        return fuente;
    }
}
