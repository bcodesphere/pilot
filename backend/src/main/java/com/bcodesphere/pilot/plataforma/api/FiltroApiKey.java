package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.aplicacion.AutenticarApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica las peticiones de integraciones con una API key {@code Authorization: Bearer pk_xxxx.secreto}
 * (CLAUDE.md 12.1 y 14.2, ADR-026). Solo actúa en la cadena de seguridad propia de {@code /api/v1/integraciones/**}.
 *
 * <ol>
 *   <li>Sin credencial, con formato inválido, prefijo inexistente, secreto incorrecto, clave revocada o vencida: 401
 *       {@code PLT-009} con la misma respuesta genérica en todos los casos (no revela cuál falló).
 *   <li>Si es válida: publica una autenticación con {@code ROLE_INTEGRACION} y una autoridad por alcance
 *       ({@code SCOPE_integracion:operaciones}). {@code ROLE_INTEGRACION} no está en la jerarquía
 *       {@code admin_empresa > contador > auditor}, así que una API key nunca satisface {@code hasRole('AUDITOR')}.
 *   <li>Ejecuta el resto de la petición con la empresa de la clave y el usuario técnico {@code api_key:<id>}; la empresa
 *       nunca viene de un header del cliente, solo de la clave.
 *   <li>Registra el último uso como mucho una vez por minuto; si falla, no rechaza la petición.
 * </ol>
 *
 * <p>Logs: el prefijo puede aparecer, el secreto jamás. No es un bean: se construye dentro de la cadena de seguridad
 * para que Spring Boot no lo registre además como filtro del contenedor.
 */
final class FiltroApiKey extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(FiltroApiKey.class);

    /** Esquema del header {@code Authorization}, con el espacio; se compara sin distinguir mayúsculas (RFC 6750). */
    private static final String ESQUEMA_BEARER = "Bearer ";

    /** Rol técnico de las API keys (CLAUDE.md 14.2); fuera de la jerarquía de roles de usuario. */
    private static final String ROL_INTEGRACION = "ROLE_INTEGRACION";

    private final AutenticarApiKey autenticacion;
    private final AuthenticationEntryPoint puntoEntrada;

    /**
     * Crea el filtro.
     *
     * @param autenticacion caso de uso que verifica la API key
     * @param puntoEntrada respuesta 401 en Problem Details (el mismo de la cadena de usuarios)
     */
    FiltroApiKey(AutenticarApiKey autenticacion, AuthenticationEntryPoint puntoEntrada) {
        this.autenticacion = autenticacion;
        this.puntoEntrada = puntoEntrada;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        // 1. Credencial del header: sin ella o con otro esquema (p. ej. Basic) es un 401 genérico
        String credencial = credencial(request);
        Optional<AutenticarApiKey.Autenticada> clave =
                credencial == null ? Optional.empty() : autenticacion.autenticar(credencial);
        if (clave.isEmpty()) {
            // Sin motivo en el mensaje ni en el log: no se distingue prefijo inexistente de secreto incorrecto
            LOG.warn("Petición de integración rechazada por credencial inválida");
            SecurityContextHolder.clearContext();
            puntoEntrada.commence(request, response, new BadCredentialsException("API key inválida"));
            return;
        }
        AutenticarApiKey.Autenticada autenticada = clave.get();

        // 2. Autoridades: el rol técnico y un SCOPE_ por cada alcance de la clave
        List<GrantedAuthority> autoridades = new java.util.ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(ROL_INTEGRACION));
        autenticada.alcances().forEach(a -> autoridades.add(new SimpleGrantedAuthority(a.autoridad())));
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        autenticada.usuarioContexto(), null, autoridades));

        // 3. El resto de la petición corre con la empresa de la clave; el MDC lleva empresa y clave (nunca el secreto)
        MDC.put(ClavesMdc.EMPRESA_ID, autenticada.empresaId().toString());
        MDC.put(ClavesMdc.USUARIO_ID, autenticada.usuarioContexto());
        try {
            continuarConEmpresa(autenticada, request, response, cadena);
        } finally {
            // 4. Los hilos del servidor se reutilizan: nada de esta petición queda en el MDC
            MDC.remove(ClavesMdc.EMPRESA_ID);
            MDC.remove(ClavesMdc.USUARIO_ID);
        }
    }

    /** Extrae el valor tras {@code Bearer }, o nulo si el header falta o usa otro esquema. */
    private static String credencial(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, ESQUEMA_BEARER, 0, ESQUEMA_BEARER.length())) {
            return null;
        }
        return header.substring(ESQUEMA_BEARER.length()).strip();
    }

    /** Ejecuta el resto de la cadena con la empresa y el usuario técnico de la clave en el contexto (para RLS). */
    private void continuarConEmpresa(
            AutenticarApiKey.Autenticada clave,
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain cadena)
            throws ServletException, IOException {
        try {
            ContextoEmpresa.ejecutarCon(new EmpresaId(clave.empresaId()), clave.usuarioContexto(), (Runnable) () -> {
                // Último uso: como mucho una escritura por minuto; un fallo aquí no debe rechazar una clave válida
                registrarUso(clave);
                try {
                    cadena.doFilter(request, response);
                } catch (ServletException | IOException e) {
                    // Runnable no admite excepciones comprobadas: se envuelven y se relanzan abajo
                    throw new ErrorEnCadena(e);
                }
            });
        } catch (ErrorEnCadena e) {
            if (e.getCause() instanceof ServletException se) {
                throw se;
            }
            throw (IOException) e.getCause();
        }
    }

    /** Registra el último uso sin propagar errores: es información de apoyo, no parte de la autenticación. */
    private void registrarUso(AutenticarApiKey.Autenticada clave) {
        try {
            autenticacion.registrarUso(clave.id());
        } catch (RuntimeException e) {
            LOG.warn("No se pudo registrar el último uso de la API key {}", clave.id());
        }
    }

    /** Envoltorio interno para atravesar la lambda de {@link ContextoEmpresa#ejecutarCon}. */
    private static final class ErrorEnCadena extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ErrorEnCadena(Exception causa) {
            super(causa);
        }
    }
}
