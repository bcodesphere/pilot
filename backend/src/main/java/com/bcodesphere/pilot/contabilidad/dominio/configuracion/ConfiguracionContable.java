package com.bcodesphere.pilot.contabilidad.dominio.configuracion;

import com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta;

/**
 * Configuración contable de la empresa: una fila (CLAUDE.md 11.3).
 *
 * @param modoPrecioDefecto modo de precio por defecto
 * @param cuentaIvaDebito cuenta del IVA débito fiscal (pasivo)
 * @param cuentaIvaCredito cuenta del IVA crédito fiscal (activo)
 * @param version versión para concurrencia optimista
 */
public record ConfiguracionContable(
        ModoPrecio modoPrecioDefecto, ResumenCuenta cuentaIvaDebito, ResumenCuenta cuentaIvaCredito, long version) {}
