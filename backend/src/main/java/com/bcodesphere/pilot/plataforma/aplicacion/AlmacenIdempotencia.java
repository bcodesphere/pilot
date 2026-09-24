package com.bcodesphere.pilot.plataforma.aplicacion;

import com.bcodesphere.pilot.compartido.EmpresaId;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import java.util.Optional;

/**
 * Puerto de salida: persistencia de las respuestas por clave de idempotencia. Todas las operaciones corren en la
 * transacción del llamador (CLAUDE.md 4.1, transacción única por comando).
 */
public interface AlmacenIdempotencia {

    /**
     * Respuesta guardada junto con el hash del cuerpo con que se creó.
     *
     * @param hashSolicitud SHA-256 (hex) del cuerpo original
     * @param respuesta respuesta original
     */
    record RegistroGuardado(String hashSolicitud, RespuestaIdempotente respuesta) {}

    /**
     * Toma un bloqueo hasta el fin de la transacción sobre (empresa, clave): la segunda petición con la misma clave
     * espera aquí a que la primera termine, y después encuentra su respuesta guardada.
     *
     * @param empresaId empresa dueña de la clave
     * @param clave valor de {@code Idempotency-Key}
     */
    void bloquear(EmpresaId empresaId, String clave);

    /**
     * Busca la respuesta guardada de una clave.
     *
     * @param empresaId empresa dueña de la clave
     * @param clave valor de {@code Idempotency-Key}
     * @return el registro, o vacío si la clave es nueva
     */
    Optional<RegistroGuardado> buscar(EmpresaId empresaId, String clave);

    /**
     * Guarda la respuesta de una clave nueva.
     *
     * @param empresaId empresa dueña de la clave
     * @param clave valor de {@code Idempotency-Key}
     * @param hashSolicitud SHA-256 (hex) del cuerpo
     * @param respuesta respuesta a guardar
     * @return la respuesta tal como quedó guardada (la que devolverán las repeticiones), o vacío si la clave ya
     *     existía (choque de clave primaria), sin abortar la transacción
     */
    Optional<RespuestaIdempotente> guardar(
            EmpresaId empresaId, String clave, String hashSolicitud, RespuestaIdempotente respuesta);
}
