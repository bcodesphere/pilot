package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.compartido.ExcepcionDominio;

/**
 * Errores de idempotencia fuera del webhook de n8n, con los códigos del catálogo de CLAUDE.md 8.4
 * (el webhook usa {@code INT-005}, {@code INT-008} e {@code INT-009}). El manejador global los traduce a Problem Details.
 */
public final class ExcepcionIdempotencia extends ExcepcionDominio {

    private static final long serialVersionUID = 1L;

    private ExcepcionIdempotencia(String codigo, int estadoHttp, String detalle) {
        super(codigo, estadoHttp, detalle);
    }

    /**
     * La clave ya se usó con otro cuerpo.
     *
     * @return error 422 {@code PLT-005}
     */
    public static ExcepcionIdempotencia claveReutilizada() {
        return new ExcepcionIdempotencia(
                "PLT-005",
                422,
                "La Idempotency-Key ya se usó con otro cuerpo; use una clave nueva para una petición distinta");
    }

    /**
     * Falta la clave.
     *
     * @return error 428 {@code PLT-006}
     */
    public static ExcepcionIdempotencia claveAusente() {
        return new ExcepcionIdempotencia("PLT-006", 428, "Falta el header Idempotency-Key");
    }

    /**
     * Otra petición con la misma clave está en proceso.
     *
     * @return error 409 {@code PLT-008}
     */
    public static ExcepcionIdempotencia peticionEnProceso() {
        return new ExcepcionIdempotencia(
                "PLT-008",
                409,
                "Otra petición con la misma Idempotency-Key está en proceso; reintente en unos segundos");
    }
}
