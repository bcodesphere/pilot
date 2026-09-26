package com.bcodesphere.pilot.compartido;

/**
 * Error 412 {@code PLT-016}: el header {@code If-Match} está mal formado o no coincide con la versión actual del
 * recurso (concurrencia optimista, CLAUDE.md 8.3 y 8.4). Vive en {@code compartido} porque {@link VersionEtag} también
 * está allí y lo lanzan los casos de uso de plataforma y de contabilidad; es la única fábrica de PLT-016.
 */
public final class ExcepcionVersionNoCoincide extends ExcepcionDominio {

    private static final long serialVersionUID = 1L;

    /** Crea el error con el mensaje genérico del catálogo de errores. */
    public ExcepcionVersionNoCoincide() {
        super("PLT-016", 412, "El recurso cambió o el header If-Match no coincide con su versión");
    }
}
