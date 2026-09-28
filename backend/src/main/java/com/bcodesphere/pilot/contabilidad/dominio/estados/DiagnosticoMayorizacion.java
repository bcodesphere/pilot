package com.bcodesphere.pilot.contabilidad.dominio.estados;

import java.util.List;

/**
 * Resultado del diagnóstico de mayorización (ADR-018, ADR-038 §9): verifica que {@code saldo_cuenta_mensual} sea
 * igual a la suma de las líneas de {@code asiento_linea} para toda cuenta, año y mes con movimiento.
 *
 * @param consistente {@code true} si no se encontró ninguna diferencia
 * @param cantidadCuentasRevisadas cantidad de cuentas de detalle distintas revisadas
 * @param diferencias las diferencias encontradas, si las hay
 */
public record DiagnosticoMayorizacion(
        boolean consistente, int cantidadCuentasRevisadas, List<DiferenciaMayorizacion> diferencias) {

    /** Copia defensiva de las diferencias. */
    public DiagnosticoMayorizacion {
        diferencias = List.copyOf(diferencias);
    }

    /**
     * Arma el diagnóstico.
     *
     * @param cantidadCuentasRevisadas cuentas de detalle distintas revisadas
     * @param diferencias diferencias encontradas (vacío si todo cuadra)
     * @return el diagnóstico
     */
    public static DiagnosticoMayorizacion de(int cantidadCuentasRevisadas, List<DiferenciaMayorizacion> diferencias) {
        return new DiagnosticoMayorizacion(diferencias.isEmpty(), cantidadCuentasRevisadas, diferencias);
    }
}
