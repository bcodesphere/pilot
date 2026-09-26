package com.bcodesphere.pilot.contabilidad.aplicacion;

/**
 * Puerto de la precarga de Contabilidad: copia las plantillas globales a la empresa activa (ADR-034, ADR-035). Debe
 * ejecutarse dentro de la transacción de la instalación.
 */
public interface PrecargaContable {

    /**
     * Resultado de la precarga.
     *
     * @param cuentas cuentas copiadas al catálogo de la empresa
     * @param cuentasDetalle de ellas, las que aceptan movimientos
     * @param reglas reglas de contabilización copiadas
     */
    record Resumen(int cuentas, int cuentasDetalle, int reglas) {}

    /**
     * Copia catálogo, configuración y reglas a la empresa activa, en ese orden (las dos últimas resuelven códigos a
     * los id de las cuentas ya copiadas).
     *
     * @return los totales copiados
     */
    Resumen precargar();
}
