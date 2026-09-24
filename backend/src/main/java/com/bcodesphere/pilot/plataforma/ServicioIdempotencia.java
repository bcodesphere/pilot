package com.bcodesphere.pilot.plataforma;

import java.util.Set;
import java.util.function.Supplier;

/**
 * Evita duplicados cuando un cliente o n8n reintenta una petición (CLAUDE.md 12.6).
 * Siempre corre dentro de la transacción del llamador (exige una activa) y usa la empresa del
 * {@link ContextoEmpresa}: la misma clave en dos empresas no colisiona.
 */
public interface ServicioIdempotencia {

    /**
     * Ejecuta la operación una sola vez por clave y cuerpo, guardando la respuesta si es 2xx.
     *
     * @param clave valor del header {@code Idempotency-Key}
     * @param cuerpoSolicitud cuerpo de la petición tal como llegó; su SHA-256 detecta el reuso de la clave
     * @param operacion trabajo a ejecutar solo si la clave es nueva
     * @return la respuesta nueva, o la guardada marcada como repetida
     * @throws com.bcodesphere.pilot.compartido.ExcepcionDominio 422 {@code PLT-005} si la clave se usó con otro
     *     cuerpo, o 409 {@code PLT-008} si otra petición con la misma clave choca al guardar
     */
    RespuestaIdempotente ejecutar(String clave, String cuerpoSolicitud, Supplier<RespuestaIdempotente> operacion);

    /**
     * Igual que {@link #ejecutar(String, String, Supplier)}, pero además guarda las respuestas cuyo estado esté en
     * {@code estadosAdicionales} (p. ej. 409 {@code INT-004} del webhook, CLAUDE.md 12.6 punto 5).
     * Los demás estados no 2xx no se guardan, para permitir reintentar tras corregir.
     * Cuando la respuesta se guarda, la primera ejecución devuelve la misma forma que devolverán las repeticiones
     * (PostgreSQL normaliza el JSONB), así que ambas son idénticas.
     *
     * @param clave valor del header {@code Idempotency-Key}
     * @param cuerpoSolicitud cuerpo de la petición tal como llegó
     * @param estadosAdicionales estados HTTP no 2xx que también se guardan
     * @param operacion trabajo a ejecutar solo si la clave es nueva
     * @return la respuesta nueva, o la guardada marcada como repetida
     */
    RespuestaIdempotente ejecutar(
            String clave,
            String cuerpoSolicitud,
            Set<Integer> estadosAdicionales,
            Supplier<RespuestaIdempotente> operacion);
}
