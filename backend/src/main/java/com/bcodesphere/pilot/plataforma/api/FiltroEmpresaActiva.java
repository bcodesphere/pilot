package com.bcodesphere.pilot.plataforma.api;

import com.bcodesphere.pilot.compartido.ClavesMdc;
import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.aplicacion.ExigirAppInstalada;
import com.bcodesphere.pilot.plataforma.aplicacion.ResolverIdentidad;
import com.bcodesphere.pilot.plataforma.aplicacion.ValidarMembresia;
import com.bcodesphere.pilot.plataforma.dominio.DatosIdentidad;
import com.bcodesphere.pilot.plataforma.dominio.Rol;
import com.bcodesphere.pilot.plataforma.dominio.Usuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.UrlPathHelper;

/**
 * Establece la identidad y la empresa activa de cada petición autenticada (CLAUDE.md 4.5, ADR-026, ADR-028).
 * Corre en la cadena de seguridad justo DESPUÉS de validar el token y antes de la autorización, porque el rol sale de
 * la membresía y se publica como autoridad de Spring Security.
 *
 * <ol>
 *   <li>Resuelve al usuario del token (alta automática en el primer inicio de sesión) y lo publica en el MDC.
 *   <li>Si llega {@code X-Empresa-Id}: valida el UUID (400 PLT-001) y la membresía (403 PLT-003), comprueba que la app
 *       de la ruta esté instalada (403 PLT-004) y ejecuta el resto de la petición con la empresa en el contexto.
 *   <li>Si no llega, la petición sigue sin empresa (las operaciones que la exigen la piden con un header obligatorio),
 *       salvo en las rutas de una app del catálogo, que se rechazan antes del controlador (422 PLT-002).
 * </ol>
 *
 * <p>No es un bean: se construye dentro de la cadena de seguridad para que Spring Boot no lo registre además como
 * filtro del contenedor (correría dos veces y fuera del orden de seguridad).
 */
final class FiltroEmpresaActiva extends OncePerRequestFilter {

    /** Header con la empresa activa (CLAUDE.md 4.5). */
    static final String HEADER_EMPRESA = "X-Empresa-Id";

    /** Raíz de la API versionada. */
    private static final String RAIZ_API = "/api/v1";

    /** UUID en su forma canónica; {@code UUID.fromString} es más permisivo y aceptaría entradas raras. */
    private static final Pattern UUID_CANONICO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ResolverIdentidad identidad;
    private final ValidarMembresia membresia;
    private final ExigirAppInstalada aplicaciones;
    private final HandlerExceptionResolver resolvedor;

    /**
     * Crea el filtro.
     *
     * @param identidad caso de uso que resuelve (y da de alta) al usuario
     * @param membresia caso de uso que valida la membresía y devuelve el rol
     * @param aplicaciones caso de uso que exige la app instalada
     * @param resolvedor resolvedor de excepciones de MVC, para responder errores en el mismo formato que la API
     */
    FiltroEmpresaActiva(
            ResolverIdentidad identidad,
            ValidarMembresia membresia,
            ExigirAppInstalada aplicaciones,
            HandlerExceptionResolver resolvedor) {
        this.identidad = identidad;
        this.membresia = membresia;
        this.aplicaciones = aplicaciones;
        this.resolvedor = resolvedor;
    }

    /** Solo se aplica a la API versionada; el resto (p. ej. {@code /actuator/health}) no tiene usuario ni empresa. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = rutaNormalizada(request);
        return !(ruta.equals(RAIZ_API) || ruta.startsWith(RAIZ_API + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        // 1. Sin token válido (anónimo) no hay identidad que resolver: la autorización lo rechazará con 401
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (!(actual instanceof JwtAuthenticationToken token)) {
            cadena.doFilter(request, response);
            return;
        }

        // 2. Identidad, membresía y app instalada. Los errores se responden aquí con Problem Details porque el
        //    manejador global no alcanza a los filtros; el resolvedor de MVC sí aplica el @RestControllerAdvice
        Contexto contexto;
        try {
            contexto = resolverContexto(request, token);
        } catch (RuntimeException e) {
            if (resolvedor.resolveException(request, response, null, e) == null) {
                throw e;
            }
            return;
        }

        // 3. Publica la autenticación con el rol de la membresía y el usuario en los logs del resto de la petición
        SecurityContextHolder.getContext().setAuthentication(contexto.autenticacion());
        MDC.put(ClavesMdc.USUARIO_ID, contexto.usuarioId().toString());
        try {
            if (contexto.empresaId() == null) {
                cadena.doFilter(request, response);
            } else {
                MDC.put(ClavesMdc.EMPRESA_ID, contexto.empresaId().toString());
                continuarConEmpresa(contexto, request, response, cadena);
            }
        } finally {
            // 4. Los hilos del servidor se reutilizan: nada de esta petición queda en el MDC
            MDC.remove(ClavesMdc.USUARIO_ID);
            MDC.remove(ClavesMdc.EMPRESA_ID);
        }
    }

    /** Resultado de resolver la petición: autenticación final, usuario y empresa (nula si no se pidió). */
    private record Contexto(Authentication autenticacion, UUID usuarioId, UUID empresaId) {}

    /** Resuelve usuario, empresa, rol y app instalada; lanza el error de negocio que corresponda. */
    private Contexto resolverContexto(HttpServletRequest request, JwtAuthenticationToken token) {
        // 1. Usuario del token: alta automática o sincronización; un usuario bloqueado se rechaza aquí (PLT-010)
        Usuario usuario =
                identidad.resolver(DatosIdentidad.desdeClaims(token.getToken().getClaims()));
        List<GrantedAuthority> autoridades = new ArrayList<>(token.getAuthorities());

        // 2. Sin X-Empresa-Id la petición sigue sin empresa (y sin rol de empresa)
        String header = request.getHeader(HEADER_EMPRESA);
        if (header == null) {
            // 2.1 Defensa en profundidad: una ruta de app del catálogo sin X-Empresa-Id se rechaza aquí con la misma
            //     respuesta que daría el controlador (422 PLT-002); así ninguna ruta de app queda sin empresa
            String appSinEmpresa = primerSegmentoDeApi(request);
            if (appSinEmpresa != null && aplicaciones.esRutaDeApp(appSinEmpresa, usuario.id())) {
                throw new ExcepcionValidacion(
                        "PLT-002",
                        "La solicitud contiene datos inválidos",
                        List.of(new ErrorCampo(HEADER_EMPRESA, "El header es obligatorio")));
            }
            return new Contexto(autenticacion(token, autoridades, usuario.id()), usuario.id(), null);
        }

        // 3. Con X-Empresa-Id: formato, membresía activa y app instalada (PLT-001, PLT-003, PLT-004)
        if (!UUID_CANONICO.matcher(header.strip()).matches()) {
            throw ExcepcionPlataforma.empresaMalFormada();
        }
        UUID empresaId = UUID.fromString(header.strip());
        Rol rol = membresia.rolEnEmpresa(usuario.id(), empresaId);
        String app = primerSegmentoDeApi(request);
        if (app != null) {
            aplicaciones.exigir(app, empresaId, usuario.id());
        }

        // 4. El rol de la membresía se publica como autoridad; la jerarquía admin > contador > auditor la aplica Spring
        autoridades.add(new SimpleGrantedAuthority(rol.autoridad()));
        return new Contexto(autenticacion(token, autoridades, usuario.id()), usuario.id(), empresaId);
    }

    /** Copia la autenticación del token con las autoridades finales; su nombre pasa a ser el UUID del usuario. */
    private static Authentication autenticacion(
            JwtAuthenticationToken token, List<GrantedAuthority> autoridades, UUID usuarioId) {
        return new JwtAuthenticationToken(token.getToken(), autoridades, usuarioId.toString());
    }

    /** Ejecuta el resto de la cadena con la empresa y el usuario en el contexto (para Row-Level Security). */
    private static void continuarConEmpresa(
            Contexto contexto, HttpServletRequest request, HttpServletResponse response, FilterChain cadena)
            throws ServletException, IOException {
        try {
            ContextoEmpresa.ejecutarCon(
                    new EmpresaId(contexto.empresaId()), contexto.usuarioId().toString(), (Runnable) () -> {
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

    /** Envoltorio interno para atravesar la lambda de {@link ContextoEmpresa#ejecutarCon}. */
    private static final class ErrorEnCadena extends RuntimeException {

        private static final long serialVersionUID = 1L;

        ErrorEnCadena(Exception causa) {
            super(causa);
        }
    }

    /**
     * Primer segmento de la ruta después de {@code /api/v1/}, calculado sobre la ruta que Spring MVC usará para
     * enrutar (decodificada y sin parámetros de ruta), para que variantes como {@code /%63ontabilidad} o
     * {@code /contabilidad;x=1} no eludan el control de la app instalada.
     */
    private static String primerSegmentoDeApi(HttpServletRequest request) {
        String ruta = rutaNormalizada(request);
        if (!ruta.startsWith(RAIZ_API + "/")) {
            return null;
        }
        String resto = ruta.substring(RAIZ_API.length() + 1);
        int fin = resto.indexOf('/');
        String segmento = fin < 0 ? resto : resto.substring(0, fin);
        return segmento.isEmpty() ? null : segmento;
    }

    /** Ruta dentro de la aplicación, decodificada y normalizada como lo hace Spring MVC. */
    private static String rutaNormalizada(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
