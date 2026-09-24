package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionIdempotencia;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import com.bcodesphere.pilot.plataforma.ServicioIdempotencia;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ServicioIdempotencia}. Exige una transacción activa (MANDATORY): el bloqueo, la
 * ejecución de la operación y el guardado de la respuesta deben confirmarse o revertirse juntos (CLAUDE.md 12.6).
 */
@Service
public class ServicioIdempotenciaImpl implements ServicioIdempotencia {

    /** Largo máximo de la clave, igual que la columna {@code idempotencia.clave}. */
    private static final int LARGO_MAXIMO_CLAVE = 100;

    private final AlmacenIdempotencia almacen;

    /**
     * Crea el servicio.
     *
     * @param almacen persistencia de las respuestas guardadas
     */
    public ServicioIdempotenciaImpl(AlmacenIdempotencia almacen) {
        this.almacen = almacen;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RespuestaIdempotente ejecutar(
            String clave, String cuerpoSolicitud, Supplier<RespuestaIdempotente> operacion) {
        return ejecutar(clave, cuerpoSolicitud, Set.of(), operacion);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RespuestaIdempotente ejecutar(
            String clave,
            String cuerpoSolicitud,
            Set<Integer> estadosAdicionales,
            Supplier<RespuestaIdempotente> operacion) {
        // 1. Empresa del contexto (falla si no hay) y clave válida
        EmpresaId empresaId = ContextoEmpresa.empresaRequerida();
        validarClave(clave);
        String hash = sha256(cuerpoSolicitud);

        // 2. Serializa las peticiones con la misma clave: la segunda espera aquí y luego ve la respuesta guardada
        almacen.bloquear(empresaId, clave);

        // 3. Clave conocida: misma solicitud devuelve lo guardado; otro cuerpo es un error del cliente
        Optional<AlmacenIdempotencia.RegistroGuardado> guardado = almacen.buscar(empresaId, clave);
        if (guardado.isPresent()) {
            if (!guardado.get().hashSolicitud().equals(hash)) {
                throw ExcepcionIdempotencia.claveReutilizada();
            }
            return guardado.get().respuesta().comoRepetida();
        }

        // 4. Clave nueva: ejecuta la operación una sola vez
        RespuestaIdempotente respuesta = operacion.get();
        if (respuesta == null) {
            throw new IllegalStateException("La operación idempotente no devolvió respuesta");
        }

        // 5. Solo se guardan las 2xx y los estados pedidos: un 4xx de validación debe poder reintentarse corregido
        if (!seGuarda(respuesta.estadoHttp(), estadosAdicionales)) {
            return respuesta;
        }
        // 6. Devuelve la respuesta tal como quedó guardada; si hubo choque de clave (no debería ocurrir con el
        //    bloqueo) se pide reintentar en vez de fallar con 500
        return almacen.guardar(empresaId, clave, hash, respuesta).orElseThrow(ExcepcionIdempotencia::peticionEnProceso);
    }

    /** Una respuesta se guarda si es 2xx o si su estado está en la lista explícita. */
    private static boolean seGuarda(int estadoHttp, Set<Integer> estadosAdicionales) {
        return (estadoHttp >= 200 && estadoHttp < 300) || estadosAdicionales.contains(estadoHttp);
    }

    /** Rechaza una clave ausente (428 PLT-006) o más larga que la columna (422 PLT-002). */
    private static void validarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            throw ExcepcionIdempotencia.claveAusente();
        }
        if (clave.length() > LARGO_MAXIMO_CLAVE) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("Idempotency-Key", "Máximo " + LARGO_MAXIMO_CLAVE + " caracteres")));
        }
    }

    /** SHA-256 en hexadecimal del cuerpo (vacío si es nulo), en UTF-8. */
    private static String sha256(String cuerpo) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((cuerpo == null ? "" : cuerpo).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si falta, el entorno está roto
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
