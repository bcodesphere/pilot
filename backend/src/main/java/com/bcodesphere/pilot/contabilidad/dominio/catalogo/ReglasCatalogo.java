package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import java.util.List;
import java.util.Optional;

/**
 * Reglas del catálogo de cuentas como funciones puras (CLAUDE.md 10.2, ADR-035). No leen la base de datos: reciben
 * los hechos ya consultados (padre, uso, hijas) y el puerto de movimientos, y lanzan {@link ExcepcionContabilidad} si
 * la operación no procede. Así se prueban sin infraestructura.
 */
public final class ReglasCatalogo {

    /** Mensaje de CON-006, común al detalle y a cada entrada de {@code errores}. */
    private static final String MENSAJE_CUENTA_NO_IMPUTABLE =
            "La cuenta debe existir en la empresa, estar activa y ser de detalle";

    private ReglasCatalogo() {}

    /**
     * Valida el alta de una cuenta. Una clase (nivel 1) no tiene padre; las demás exigen un padre existente, activo y
     * cuyo código sea el prefijo esperado, sin movimientos. Si el padre es hoja (acepta movimientos) dejará de serlo,
     * y eso no puede ocurrir si la configuración o una regla activa lo usa.
     *
     * @param codigo código de la cuenta nueva
     * @param padre cuenta con el código del padre esperado, o {@code null} si no existe en la empresa
     * @param padreEnUso {@code true} si la configuración contable o una regla activa usa al padre
     * @param movimientos puerto de movimientos
     * @throws ExcepcionContabilidad {@code CON-015} si el padre no existe, está inactivo o no es el prefijo esperado;
     *     {@code CON-011} si el padre tiene movimientos; {@code CON-016} si el padre está en uso
     */
    public static void validarAlta(
            CodigoCuenta codigo, Cuenta padre, boolean padreEnUso, ConsultaMovimientosCuenta movimientos) {
        // 1. Una clase no tiene padre: nada más que validar (el código duplicado lo detecta el caso de uso)
        if (codigo.nivel() == 1) {
            return;
        }
        // 2. El padre debe existir, estar activo y ser exactamente el prefijo del nivel anterior (CON-015)
        CodigoCuenta esperado = codigo.codigoPadreEsperado().orElseThrow();
        if (padre == null || !padre.activa() || !padre.codigo().equals(esperado)) {
            throw ExcepcionContabilidad.codigoNoValido(
                    "La cuenta padre " + esperado.valor() + " debe existir y estar activa");
        }
        // 3. Una cuenta con movimientos no puede recibir hijas: sus movimientos quedarían en una cuenta que no acepta
        //    asientos (CON-011)
        if (movimientos.tieneMovimientos(padre.id())) {
            throw ExcepcionContabilidad.conMovimientos(
                    "No se pueden crear cuentas hijas bajo una cuenta con movimientos");
        }
        // 4. Al crear la hija, el padre hoja deja de aceptar movimientos: no puede estar en uso (CON-016)
        if (padre.aceptaMovimientos() && padreEnUso) {
            throw ExcepcionContabilidad.cuentaEnUso();
        }
    }

    /**
     * Valida el cambio de código de una cuenta con la regla más conservadora: el código nuevo debe tener la misma
     * longitud y el mismo padre que el actual (así el árbol nunca se reordena), la cuenta no puede tener hijas y no
     * puede tener movimientos.
     *
     * @param actual cuenta actual
     * @param nuevo código nuevo, distinto del actual
     * @param tieneHijas {@code true} si la cuenta tiene cuentas hijas
     * @param movimientos puerto de movimientos
     * @throws ExcepcionContabilidad {@code CON-015} si cambia de nivel o de padre, o tiene hijas; {@code CON-011} si
     *     tiene movimientos
     */
    public static void validarCambioCodigo(
            Cuenta actual, CodigoCuenta nuevo, boolean tieneHijas, ConsultaMovimientosCuenta movimientos) {
        // 1. Misma longitud (mismo nivel) y mismo padre: el prefijo del padre es igual (CON-015)
        boolean mismoNivel = nuevo.nivel() == actual.codigo().nivel();
        boolean mismoPadre = nuevo.codigoPadreEsperado().equals(actual.codigo().codigoPadreEsperado());
        if (!mismoNivel || !mismoPadre) {
            throw ExcepcionContabilidad.codigoNoValido(
                    "El código nuevo debe tener la misma longitud y el mismo padre que el actual");
        }
        // 2. Con hijas, el cambio arrastraría el prefijo de todas ellas (CON-015)
        if (tieneHijas) {
            throw ExcepcionContabilidad.codigoNoValido("No se puede cambiar el código de una cuenta con cuentas hijas");
        }
        // 3. Con movimientos, los asientos históricos apuntarían a otro código (CON-011)
        if (movimientos.tieneMovimientos(actual.id())) {
            throw ExcepcionContabilidad.conMovimientos("No se puede cambiar el código de una cuenta con movimientos");
        }
    }

    /**
     * Valida la desactivación de una cuenta activa.
     *
     * @param actual cuenta que se desactiva
     * @param enUso {@code true} si la configuración contable o una regla activa la usa
     * @param movimientos puerto de movimientos
     * @throws ExcepcionContabilidad {@code CON-012} si tiene saldo distinto de cero; {@code CON-016} si está en uso
     */
    public static void validarDesactivacion(Cuenta actual, boolean enUso, ConsultaMovimientosCuenta movimientos) {
        // 1. Con saldo, desactivarla dejaría un saldo en una cuenta que ya no acepta asientos (CON-012);
        //    compareTo ignora la escala (0.0 == 0.00)
        if (movimientos.saldo(actual.id()).signum() != 0) {
            throw ExcepcionContabilidad.conSaldo();
        }
        // 2. En uso por la configuración o por una regla activa, las operaciones de n8n fallarían (CON-016)
        if (enUso) {
            throw ExcepcionContabilidad.cuentaEnUso();
        }
    }

    /**
     * Comprueba que la cuenta pueda usarse en la configuración o en una regla: existe en la empresa, está activa y es
     * de detalle.
     *
     * @param cuenta cuenta encontrada en la empresa, o {@code null} si no existe (o es de otra empresa)
     * @param campo campo del cuerpo de la petición que la trae (p. ej. {@code cuentaIvaDebitoId})
     * @return el error del campo, o vacío si la cuenta sirve
     */
    public static Optional<ErrorCampo> comprobarImputable(Cuenta cuenta, String campo) {
        if (cuenta == null || !cuenta.esImputable()) {
            return Optional.of(new ErrorCampo(campo, MENSAJE_CUENTA_NO_IMPUTABLE));
        }
        return Optional.empty();
    }

    /**
     * Exige que la cuenta pueda usarse en la configuración o en una regla.
     *
     * @param cuenta cuenta encontrada en la empresa, o {@code null} si no existe (o es de otra empresa)
     * @param campo campo del cuerpo de la petición que la trae
     * @throws ExcepcionValidacion 422 {@code CON-006} con {@code errores[{campo, mensaje}]}
     */
    public static void exigirImputable(Cuenta cuenta, String campo) {
        exigirImputables(comprobarImputable(cuenta, campo).stream().toList());
    }

    /**
     * Lanza {@code CON-006} con todos los errores de campo recibidos; no hace nada si la lista está vacía.
     *
     * @param errores errores por campo ya comprobados con {@link #comprobarImputable}
     * @throws ExcepcionValidacion 422 {@code CON-006} con la lista {@code errores}
     */
    public static void exigirImputables(List<ErrorCampo> errores) {
        if (!errores.isEmpty()) {
            throw new ExcepcionValidacion("CON-006", MENSAJE_CUENTA_NO_IMPUTABLE, errores);
        }
    }
}
