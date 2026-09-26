package com.bcodesphere.pilot.contabilidad.dominio;

import com.bcodesphere.pilot.compartido.ExcepcionDominio;

/**
 * Errores de negocio del catálogo de cuentas y de la configuración contable, con los códigos {@code CON-} de
 * CLAUDE.md 10.2 y ADR-035. El código y el estado HTTP viajan en la excepción y el manejador global los traduce a
 * Problem Details sin conocer este tipo (CLAUDE.md 8.3 y 8.4). Los mensajes no revelan datos de otras empresas.
 */
public final class ExcepcionContabilidad extends ExcepcionDominio {

    private static final long serialVersionUID = 1L;

    private ExcepcionContabilidad(String codigo, int estadoHttp, String detalle) {
        super(codigo, estadoHttp, detalle);
    }

    /**
     * El primer dígito del código no es una clase de 1 a 5 (la clase 6 es del cierre anual, fuera de alcance).
     *
     * @return error 422 {@code CON-010}
     */
    public static ExcepcionContabilidad claseInvalida() {
        return new ExcepcionContabilidad("CON-010", 422, "El primer dígito del código debe ser una clase de 1 a 5");
    }

    /**
     * Una cuenta con movimientos no puede cambiar de código ni recibir hijas.
     *
     * @param detalle qué operación se intentó
     * @return error 422 {@code CON-011}
     */
    public static ExcepcionContabilidad conMovimientos(String detalle) {
        return new ExcepcionContabilidad("CON-011", 422, detalle);
    }

    /**
     * Una cuenta con saldo distinto de cero no puede desactivarse.
     *
     * @return error 422 {@code CON-012}
     */
    public static ExcepcionContabilidad conSaldo() {
        return new ExcepcionContabilidad(
                "CON-012", 422, "No se puede desactivar una cuenta con saldo distinto de cero");
    }

    /**
     * El código ya existe en el catálogo de la empresa.
     *
     * @return error 409 {@code CON-014}
     */
    public static ExcepcionContabilidad codigoDuplicado() {
        return new ExcepcionContabilidad("CON-014", 409, "Ya existe una cuenta con ese código en el catálogo");
    }

    /**
     * Longitud de código no válida, o sin cuenta padre existente y activa cuyo código sea su prefijo.
     *
     * @param detalle explicación segura para el cliente
     * @return error 422 {@code CON-015}
     */
    public static ExcepcionContabilidad codigoNoValido(String detalle) {
        return new ExcepcionContabilidad("CON-015", 422, detalle);
    }

    /**
     * La cuenta está en uso por la configuración contable o por una regla activa: no se puede desactivar ni dejar de
     * ser de detalle.
     *
     * @return error 422 {@code CON-016}
     */
    public static ExcepcionContabilidad cuentaEnUso() {
        return new ExcepcionContabilidad(
                "CON-016", 422, "La cuenta está en uso por la configuración contable o por una regla activa");
    }
}
