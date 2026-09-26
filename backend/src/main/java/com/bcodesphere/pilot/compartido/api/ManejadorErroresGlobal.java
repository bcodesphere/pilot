package com.bcodesphere.pilot.compartido.api;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.EnmascaradorDatosPersonales;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionDominio;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce toda excepción a Problem Details (RFC 9457, CLAUDE.md 8.4). Nunca expone trazas, mensajes internos
 * ni nombres de clases: el error completo va solo al log, con el {@code traceId} del MDC.
 */
@RestControllerAdvice
public class ManejadorErroresGlobal {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresGlobal.class);

    /** Tipo de problema genérico (RFC 9457). */
    private static final String TIPO = "about:blank";

    /** Errores de negocio propios del dominio. */
    @ExceptionHandler(ExcepcionDominio.class)
    public ResponseEntity<ProblemaDto> dominio(ExcepcionDominio ex, HttpServletRequest req) {
        // 0. Un código mal formado es un error de programación: se trata como fallo interno
        if (!ex.codigoValido()) {
            return inesperado(ex, req);
        }
        // 1. Los de validación agregan la lista por campo; el resto solo el código y la diferencia si la hay
        List<ErrorCampo> errores = ex instanceof ExcepcionValidacion v ? v.errores() : null;
        Dinero diferencia = ex.diferencia().orElse(null);
        LOG.warn("Error de dominio {}: {}", ex.codigo(), EnmascaradorDatosPersonales.enmascarar(ex.getMessage()));
        return responder(new ProblemaDto(
                TIPO,
                titulo(ex.estadoHttp()),
                ex.estadoHttp(),
                ex.getMessage(),
                req.getRequestURI(),
                ex.codigo(),
                errores,
                diferencia));
    }

    /** Bean Validation sobre el cuerpo ({@code @Valid}). PLT-002 (CLAUDE.md 8.4). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemaDto> cuerpoInvalido(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorCampo(e.getField(), mensaje(e.getDefaultMessage())))
                .toList();
        return validacion(errores, req);
    }

    /** Bean Validation sobre parámetros y variables de ruta (Spring 6.1+). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemaDto> parametrosInvalidos(
            HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorCampo(
                                r.getMethodParameter().getParameterName(), mensaje(e.getDefaultMessage()))))
                .toList();
        return validacion(errores, req);
    }

    /** Bean Validation lanzada como {@link ConstraintViolationException}. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemaDto> violaciones(ConstraintViolationException ex, HttpServletRequest req) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorCampo(v.getPropertyPath().toString(), mensaje(v.getMessage())))
                .toList();
        return validacion(errores, req);
    }

    /** JSON ilegible o con tipos incorrectos (incluye un monto enviado como número): 400 PLT-001. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemaDto> jsonIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        // El mensaje de Jackson puede nombrar clases: solo va al log
        LOG.warn("Cuerpo de la petición ilegible", ex);
        return responder(new ProblemaDto(
                TIPO,
                titulo(400),
                400,
                "El cuerpo de la petición no es un JSON válido",
                req.getRequestURI(),
                "PLT-001",
                null,
                null));
    }

    /**
     * Header obligatorio ausente. {@code If-Match} falta en una edición con concurrencia optimista: 428 PLT-015;
     * {@code Idempotency-Key}: 428 PLT-006; cualquier otro: 422 PLT-002 con el nombre del header en {@code errores}
     * (CLAUDE.md 8.4).
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemaDto> headerAusente(MissingRequestHeaderException ex, HttpServletRequest req) {
        String header = ex.getHeaderName();
        // 1. Los dos headers de precondición tienen su propio código y estado 428
        if ("If-Match".equalsIgnoreCase(header)) {
            LOG.warn("Falta el header If-Match");
            return responder(precondicion("PLT-015", "Falta el header If-Match", req));
        }
        if ("Idempotency-Key".equalsIgnoreCase(header)) {
            LOG.warn("Falta el header Idempotency-Key");
            return responder(precondicion("PLT-006", "Falta el header Idempotency-Key", req));
        }
        // 2. Cualquier otro header obligatorio es un error de validación de la petición
        return validacion(List.of(new ErrorCampo(header, "El header es obligatorio")), req);
    }

    /** Parámetro de consulta obligatorio ausente: 422 PLT-002 con el nombre del parámetro en {@code errores}. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemaDto> parametroAusente(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return validacion(List.of(new ErrorCampo(ex.getParameterName(), "El parámetro es obligatorio")), req);
    }

    /** Parámetro o variable de ruta con un tipo inválido (p. ej. un UUID mal formado): 400 PLT-001. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemaDto> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        // El detalle de Spring nombra clases internas: solo el nombre del parámetro va en el mensaje
        LOG.warn("Parámetro {} con un tipo inválido", ex.getName());
        return responder(new ProblemaDto(
                TIPO,
                titulo(400),
                400,
                "El parámetro '" + ex.getName() + "' tiene un formato inválido",
                req.getRequestURI(),
                "PLT-001",
                null,
                null));
    }

    /**
     * Falta la credencial o es inválida: 401 PLT-009. Lo usa el punto de entrada de la seguridad (que delega en el
     * resolvedor de excepciones de MVC) para que el 401 tenga el mismo formato que cualquier otro error.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemaDto> noAutenticado(AuthenticationException ex, HttpServletRequest req) {
        LOG.warn("Petición no autenticada");
        return responder(new ProblemaDto(
                TIPO,
                titulo(401),
                401,
                "Falta la credencial o es inválida, está vencida o fue revocada",
                req.getRequestURI(),
                "PLT-009",
                null,
                null));
    }

    /**
     * Rol o alcance insuficiente: 403 PLT-010. Cubre tanto el rechazo del filtro de seguridad como la
     * {@link AccessDeniedException} de la seguridad de métodos, que sin este manejador terminaría en 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemaDto> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        LOG.warn("Acceso denegado por rol o alcance insuficiente");
        return responder(new ProblemaDto(
                TIPO,
                titulo(403),
                403,
                "No tiene permiso para realizar esta operación",
                req.getRequestURI(),
                "PLT-010",
                null,
                null));
    }

    /** Cualquier otra excepción: 500 PLT-500 sin detalle interno; el error completo queda en el log. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemaDto> inesperado(Exception ex, HttpServletRequest req) {
        // 1. Errores propios de Spring MVC (404, 405, 415...) conservan su estado en lugar de volverse 500.
        //    PLT-007 (CLAUDE.md 8.4)
        if (ex instanceof ErrorResponse er) {
            int estado = er.getStatusCode().value();
            LOG.warn("Error HTTP {}", estado);
            return responder(new ProblemaDto(
                    TIPO, titulo(estado), estado, titulo(estado), req.getRequestURI(), "PLT-007", null, null));
        }
        // 2. Cualquier otro caso es un fallo interno
        LOG.error("Error inesperado", ex);
        return responder(new ProblemaDto(
                TIPO,
                titulo(500),
                500,
                "Ocurrió un error interno. Intente de nuevo más tarde.",
                req.getRequestURI(),
                "PLT-500",
                null,
                null));
    }

    /** Arma la respuesta 422 PLT-002 de validación con la lista por campo. */
    private ResponseEntity<ProblemaDto> validacion(List<ErrorCampo> errores, HttpServletRequest req) {
        LOG.warn("Validación fallida en {} campo(s)", errores.size());
        return responder(new ProblemaDto(
                TIPO,
                titulo(422),
                422,
                "La solicitud contiene datos inválidos",
                req.getRequestURI(),
                "PLT-002",
                errores,
                null));
    }

    /** Arma un problema 428 (precondición requerida) con su código. */
    private static ProblemaDto precondicion(String codigo, String detalle, HttpServletRequest req) {
        return new ProblemaDto(TIPO, titulo(428), 428, detalle, req.getRequestURI(), codigo, null, null);
    }

    /** Envuelve el problema con el estado y el media type {@code application/problem+json}. */
    private static ResponseEntity<ProblemaDto> responder(ProblemaDto problema) {
        return ResponseEntity.status(problema.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }

    /** Título corto en español según el estado HTTP. */
    private static String titulo(int estado) {
        return switch (estado) {
            case 400 -> "Solicitud incorrecta";
            case 401 -> "No autenticado";
            case 403 -> "Acceso denegado";
            case 404 -> "No encontrado";
            case 405 -> "Método no permitido";
            case 409 -> "Conflicto";
            case 412 -> "Precondición fallida";
            case 415 -> "Tipo de contenido no soportado";
            case 422 -> "Solicitud no procesable";
            case 428 -> "Precondición requerida";
            case 429 -> "Demasiadas peticiones";
            default ->
                estado >= 500 ? "Error interno" : HttpStatus.valueOf(estado).getReasonPhrase();
        };
    }

    /** Evita mensajes nulos en los errores por campo. */
    private static String mensaje(String texto) {
        return texto == null ? "Valor inválido" : texto;
    }
}
