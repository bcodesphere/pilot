package com.bcodesphere.pilot.compartido;

import java.util.List;

/** Error de validación (HTTP 422) con la lista de campos inválidos. */
public class ExcepcionValidacion extends ExcepcionDominio {

    private static final long serialVersionUID = 1L;

    /** Lista inmutable de errores por campo; ErrorCampo es un record inmutable. */
    private final transient List<ErrorCampo> errores;

    /**
     * Crea el error de validación.
     *
     * @param codigo código de negocio
     * @param detalle explicación general
     * @param errores errores por campo (se copia defensivamente)
     */
    public ExcepcionValidacion(String codigo, String detalle, List<ErrorCampo> errores) {
        super(codigo, 422, detalle);
        this.errores = List.copyOf(errores);
    }

    /** Errores por campo; nunca nulo. */
    public List<ErrorCampo> errores() {
        return errores;
    }
}
