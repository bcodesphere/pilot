package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.api.contrato.ConfiguracionContable;
import com.bcodesphere.pilot.compartido.api.contrato.CuentaContable;
import com.bcodesphere.pilot.compartido.api.contrato.ReglaContabilizacion;
import com.bcodesphere.pilot.compartido.api.contrato.ResumenCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.NaturalezaCuenta;

/**
 * Convierte los objetos del dominio contable a los DTO del contrato y viceversa (sin lógica de negocio,
 * CLAUDE.md 8.3). Los enums del dominio y del contrato tienen los mismos valores, así que se convierten por nombre.
 */
final class MapeadorContabilidad {

    private MapeadorContabilidad() {}

    /**
     * Cuenta del dominio a DTO.
     *
     * @param c cuenta del dominio
     * @return DTO del contrato
     */
    static CuentaContable aDto(Cuenta c) {
        return new CuentaContable(
                c.id(),
                c.codigo().valor(),
                c.nombre(),
                c.codigo().clase(),
                c.codigo().nivel(),
                c.padreId(),
                com.bcodesphere.pilot.compartido.api.contrato.NaturalezaCuenta.fromValue(
                        c.naturaleza().name()),
                c.aceptaMovimientos(),
                c.activa(),
                c.version());
    }

    /**
     * Naturaleza del contrato al dominio.
     *
     * @param n naturaleza del DTO, o nulo
     * @return naturaleza del dominio, o nulo si no se envió
     */
    static NaturalezaCuenta aDominio(com.bcodesphere.pilot.compartido.api.contrato.NaturalezaCuenta n) {
        return n == null ? null : NaturalezaCuenta.valueOf(n.getValue());
    }

    /**
     * Configuración del dominio a DTO.
     *
     * @param c configuración del dominio
     * @return DTO del contrato
     */
    static ConfiguracionContable aDto(
            com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable c) {
        return new ConfiguracionContable(
                com.bcodesphere.pilot.compartido.api.contrato.ModoPrecio.fromValue(
                        c.modoPrecioDefecto().name()),
                aDto(c.cuentaIvaDebito()),
                aDto(c.cuentaIvaCredito()),
                c.version());
    }

    /**
     * Regla del dominio a DTO.
     *
     * @param r regla del dominio
     * @return DTO del contrato; {@code cuenta} es nula si la regla no tiene cuenta
     */
    static ReglaContabilizacion aDto(com.bcodesphere.pilot.contabilidad.dominio.reglas.ReglaContabilizacion r) {
        return new ReglaContabilizacion(
                r.id(),
                com.bcodesphere.pilot.compartido.api.contrato.TipoOperacionContable.fromValue(
                        r.tipoOperacion().name()),
                com.bcodesphere.pilot.compartido.api.contrato.CategoriaRegla.fromValue(
                        r.categoria().name()),
                r.codigo(),
                r.cuenta() == null ? null : aDto(r.cuenta()),
                r.activa(),
                r.version());
    }

    /** Resumen de cuenta del dominio a DTO. */
    private static ResumenCuenta aDto(com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta r) {
        return new ResumenCuenta(r.id(), r.codigo(), r.nombre());
    }
}
