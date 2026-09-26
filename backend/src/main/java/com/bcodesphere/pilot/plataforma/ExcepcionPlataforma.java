package com.bcodesphere.pilot.plataforma;

import com.bcodesphere.pilot.compartido.ExcepcionDominio;

/**
 * Errores del núcleo por identidad, empresa activa y apps, con los códigos {@code PLT-} del catálogo de CLAUDE.md 8.4.
 * Los lanzan los casos de uso y el filtro de empresa activa; el manejador global los traduce a Problem Details.
 * Los mensajes son genéricos a propósito: no revelan si un usuario o una empresa existe (CLAUDE.md 8.4).
 */
public final class ExcepcionPlataforma extends ExcepcionDominio {

    private static final long serialVersionUID = 1L;

    private ExcepcionPlataforma(String codigo, int estadoHttp, String detalle) {
        super(codigo, estadoHttp, detalle);
    }

    /**
     * El header {@code X-Empresa-Id} no es un UUID.
     *
     * @return error 400 {@code PLT-001}
     */
    public static ExcepcionPlataforma empresaMalFormada() {
        return new ExcepcionPlataforma("PLT-001", 400, "El header X-Empresa-Id debe ser un UUID válido");
    }

    /**
     * El usuario no tiene una membresía activa en la empresa indicada (o la empresa no existe).
     *
     * @return error 403 {@code PLT-003}
     */
    public static ExcepcionPlataforma sinMembresia() {
        return new ExcepcionPlataforma("PLT-003", 403, "No tiene acceso a la empresa indicada");
    }

    /**
     * La app de la ruta no está instalada en la empresa activa (ADR-021, ADR-030).
     *
     * @return error 403 {@code PLT-004}
     */
    public static ExcepcionPlataforma appNoInstalada() {
        return new ExcepcionPlataforma("PLT-004", 403, "La aplicación no está instalada en la empresa activa");
    }

    /**
     * Falta la credencial o el token no sirve para identificar al usuario (por ejemplo, correo sin verificar).
     *
     * @param detalle explicación segura para el cliente
     * @return error 401 {@code PLT-009}
     */
    public static ExcepcionPlataforma noAutenticado(String detalle) {
        return new ExcepcionPlataforma("PLT-009", 401, detalle);
    }

    /**
     * El usuario está bloqueado o su rol no alcanza para la operación.
     *
     * @param detalle explicación segura para el cliente
     * @return error 403 {@code PLT-010}
     */
    public static ExcepcionPlataforma sinPermiso(String detalle) {
        return new ExcepcionPlataforma("PLT-010", 403, detalle);
    }

    /**
     * La app es de la edición Enterprise y no se puede instalar en la versión abierta (ADR-030).
     *
     * @return error 403 {@code PLT-011}
     */
    public static ExcepcionPlataforma appEnterprise() {
        return new ExcepcionPlataforma(
                "PLT-011", 403, "La aplicación es de la edición Enterprise y no está disponible");
    }

    /**
     * {@code If-Match} está mal formado o no coincide con la versión actual del recurso (concurrencia optimista).
     *
     * @return error 412 {@code PLT-016}
     */
    public static ExcepcionPlataforma versionNoCoincide() {
        return new ExcepcionPlataforma(
                "PLT-016", 412, "El recurso cambió o el header If-Match no coincide con su versión");
    }

    /**
     * El recurso no existe o no pertenece a la empresa activa; no distingue ambos casos para no revelar otras empresas.
     *
     * @return error 404 {@code PLT-017}
     */
    public static ExcepcionPlataforma noEncontrado() {
        return new ExcepcionPlataforma("PLT-017", 404, "El recurso no existe o no pertenece a la empresa activa");
    }
}
