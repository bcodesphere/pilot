package com.bcodesphere.pilot.contabilidad.dominio;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.ExcepcionDominio;

/**
 * Errores de negocio del catálogo de cuentas, la configuración contable y el Libro Diario, con los códigos {@code CON-}
 * de CLAUDE.md 10.1 y 10.2, ADR-035 y ADR-036. El código y el estado HTTP viajan en la excepción y el manejador global los traduce a
 * Problem Details sin conocer este tipo (CLAUDE.md 8.3 y 8.4). Los mensajes no revelan datos de otras empresas.
 */
public final class ExcepcionContabilidad extends ExcepcionDominio {

    private static final long serialVersionUID = 1L;

    private ExcepcionContabilidad(String codigo, int estadoHttp, String detalle) {
        super(codigo, estadoHttp, detalle);
    }

    private ExcepcionContabilidad(String codigo, int estadoHttp, String detalle, Dinero diferencia) {
        super(codigo, estadoHttp, detalle, diferencia);
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

    // ---------------------------------------------------------------------------- Libro Diario (CLAUDE.md 10.1)

    /**
     * El asiento tiene menos de dos líneas.
     *
     * @return error 422 {@code CON-001}
     */
    public static ExcepcionContabilidad pocasLineas() {
        return new ExcepcionContabilidad("CON-001", 422, "El asiento debe tener al menos dos líneas");
    }

    /**
     * Una línea tiene Debe y Haber a la vez, o ninguno de los dos.
     *
     * @param numeroLinea posición de la línea capturada, desde 1
     * @return error 422 {@code CON-002}
     */
    public static ExcepcionContabilidad lineaConAmbosLados(int numeroLinea) {
        return new ExcepcionContabilidad(
                "CON-002",
                422,
                "La línea " + numeroLinea + " debe llevar solo Debe o solo Haber, con un monto mayor que cero");
    }

    /**
     * Un monto es negativo, no es decimal o tiene más de 2 decimales.
     *
     * @param numeroLinea posición de la línea capturada, desde 1
     * @param lado {@code Debe} o {@code Haber}
     * @return error 422 {@code CON-003}
     */
    public static ExcepcionContabilidad montoInvalido(int numeroLinea, String lado) {
        return new ExcepcionContabilidad(
                "CON-003",
                422,
                "El " + lado + " de la línea " + numeroLinea + " debe ser un monto no negativo con máximo 2 decimales");
    }

    /**
     * Los totales del asiento son cero.
     *
     * @return error 422 {@code CON-004}
     */
    public static ExcepcionContabilidad totalesEnCero() {
        return new ExcepcionContabilidad("CON-004", 422, "Los totales del asiento deben ser mayores que cero");
    }

    /**
     * Σ Debe distinto de Σ Haber.
     *
     * @param diferencia Σ Debe − Σ Haber, exacta
     * @return error 422 {@code CON-005} con la diferencia
     */
    public static ExcepcionContabilidad descuadrado(Dinero diferencia) {
        return new ExcepcionContabilidad(
                "CON-005", 422, "El asiento no cuadra: la diferencia entre Debe y Haber es " + diferencia, diferencia);
    }

    /**
     * La fecha contable falta o es posterior a hoy en hora de El Salvador.
     *
     * @return error 422 {@code CON-007}
     */
    public static ExcepcionContabilidad fechaInvalida() {
        return new ExcepcionContabilidad(
                "CON-007", 422, "La fecha es obligatoria y no puede ser posterior a hoy (hora de El Salvador)");
    }

    /**
     * Reversión de un asiento que ya está revertido.
     *
     * @return error 409 {@code CON-008}
     */
    public static ExcepcionContabilidad yaRevertido() {
        return new ExcepcionContabilidad("CON-008", 409, "El asiento ya está revertido");
    }

    /**
     * Reversión de un asiento que es a su vez una reversión.
     *
     * @return error 409 {@code CON-009}
     */
    public static ExcepcionContabilidad reversionNoRevertible() {
        return new ExcepcionContabilidad("CON-009", 409, "Una reversión no se puede revertir");
    }

    /**
     * "Lleva IVA" sobre una de las cuentas de IVA de la configuración.
     *
     * @param numeroLinea posición de la línea capturada, desde 1
     * @return error 422 {@code CON-013}
     */
    public static ExcepcionContabilidad ivaSobreCuentaDeIva(int numeroLinea) {
        return new ExcepcionContabilidad(
                "CON-013",
                422,
                "La línea " + numeroLinea + " no puede llevar IVA porque su cuenta es una cuenta de IVA");
    }

    /**
     * No hay tasa de IVA vigente en la fecha del asiento.
     *
     * @return error 422 {@code CON-017}
     */
    public static ExcepcionContabilidad sinTasaVigente() {
        return new ExcepcionContabilidad("CON-017", 422, "No hay una tasa de IVA vigente en la fecha del asiento");
    }

    /**
     * La fecha de una reversión es anterior a la del asiento original.
     *
     * @return error 422 {@code CON-018}
     */
    public static ExcepcionContabilidad fechaAnteriorAlOriginal() {
        return new ExcepcionContabilidad(
                "CON-018", 422, "La fecha de la reversión no puede ser anterior a la del asiento original");
    }
}
