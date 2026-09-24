package com.bcodesphere.pilot.compartido;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Base de los errores de negocio. Lleva el código público (p. ej. {@code CON-005}), el estado HTTP sugerido
 * y el detalle; la capa api la traduce a Problem Details (CLAUDE.md 8.3 y 8.4).
 */
public abstract class ExcepcionDominio extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Formato del código según el contrato: prefijo de módulo y tres dígitos. */
    private static final Pattern FORMATO_CODIGO = Pattern.compile("^(PLT|CON|INT)-\\d{3}$");

    private final String codigo;
    private final int estadoHttp;
    private final Dinero diferencia;

    /**
     * Crea el error sin diferencia monetaria.
     *
     * @param codigo código de negocio, por ejemplo {@code CON-001}
     * @param estadoHttp estado HTTP sugerido
     * @param detalle explicación para el cliente; sin datos internos
     */
    protected ExcepcionDominio(String codigo, int estadoHttp, String detalle) {
        this(codigo, estadoHttp, detalle, null);
    }

    /**
     * Crea el error con la diferencia que lo provocó (descuadres de asientos, cobros que no cuadran).
     *
     * @param diferencia diferencia exacta, o nulo si no aplica
     */
    protected ExcepcionDominio(String codigo, int estadoHttp, String detalle, Dinero diferencia) {
        super(detalle);
        // 1. No se lanza nada aquí (SpotBugs CT_CONSTRUCTOR_THROW): el formato del código se comprueba
        //    en codigoValido() y el manejador global responde PLT-500 si no cumple
        this.codigo = codigo;
        this.estadoHttp = estadoHttp;
        this.diferencia = diferencia;
    }

    /** Indica si el código cumple el formato del contrato ({@code PLT|CON|INT-NNN}). */
    public boolean codigoValido() {
        return codigo != null && FORMATO_CODIGO.matcher(codigo).matches();
    }

    /** Código de negocio, por ejemplo {@code CON-005}. */
    public String codigo() {
        return codigo;
    }

    /** Estado HTTP sugerido para la respuesta. */
    public int estadoHttp() {
        return estadoHttp;
    }

    /** Diferencia monetaria asociada, si el error la tiene. */
    public Optional<Dinero> diferencia() {
        return Optional.ofNullable(diferencia);
    }
}
