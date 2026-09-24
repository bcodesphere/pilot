package com.bcodesphere.pilot.plataforma;

/**
 * Respuesta de una operación idempotente: estado HTTP y cuerpo JSON, listos para guardar o devolver.
 *
 * <p>El cuerpo se guarda en una columna JSONB, así que PostgreSQL lo normaliza (orden de claves, espacios):
 * una repetición devuelve un JSON equivalente, no necesariamente idéntico byte a byte.
 *
 * @param estadoHttp código HTTP de la respuesta
 * @param cuerpoJson cuerpo como texto JSON válido, o nulo si no hay cuerpo
 * @param repetida {@code true} si es la respuesta guardada de una petición anterior (header
 *     {@code Idempotency-Replayed: true})
 */
public record RespuestaIdempotente(int estadoHttp, String cuerpoJson, boolean repetida) {

    /**
     * Crea la respuesta de una ejecución nueva (no repetida).
     *
     * @param estadoHttp código HTTP
     * @param cuerpoJson cuerpo como texto JSON válido, o nulo
     * @return respuesta con {@code repetida = false}
     */
    public static RespuestaIdempotente de(int estadoHttp, String cuerpoJson) {
        return new RespuestaIdempotente(estadoHttp, cuerpoJson, false);
    }

    /**
     * Copia esta respuesta marcándola como repetida.
     *
     * @return respuesta con {@code repetida = true}
     */
    public RespuestaIdempotente comoRepetida() {
        return new RespuestaIdempotente(estadoHttp, cuerpoJson, true);
    }
}
