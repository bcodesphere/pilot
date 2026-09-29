package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.api.contrato.ConfiguracionContable;
import com.bcodesphere.pilot.compartido.api.contrato.CuentaContable;
import com.bcodesphere.pilot.compartido.api.contrato.LineaVistaPrevia;
import com.bcodesphere.pilot.compartido.api.contrato.ReglaContabilizacion;
import com.bcodesphere.pilot.compartido.api.contrato.ResumenCuenta;
import com.bcodesphere.pilot.compartido.api.contrato.VistaPreviaAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.AsientoExpandido;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaExpandida;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

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
                c.version(),
                // B3: cuenta_contable.sistema (ADR-042) todavía no existe en el dominio; se responde false
                // provisional hasta que B1/B3 lo agreguen a Cuenta.
                false);
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
                // B1/B3: el dominio (TipoOperacionContable) hoy solo tiene CIERRE_INGRESOS_DIARIO; su name() es un
                // valor válido del TipoOperacion ampliado del contrato (ADR-041), así que fromValue no falla.
                com.bcodesphere.pilot.compartido.api.contrato.TipoOperacion.fromValue(
                        r.tipoOperacion().name()),
                com.bcodesphere.pilot.compartido.api.contrato.CategoriaRegla.fromValue(
                        r.categoria().name()),
                r.codigo(),
                r.cuenta() == null ? null : aDto(r.cuenta()),
                r.activa(),
                r.version(),
                // B1/B3: regla_contabilizacion.prefijo_permitido (ADR-042) todavía no existe en el dominio; se
                // responde null provisional hasta que B1/B3 lo agreguen.
                null);
    }

    /** Resumen de cuenta del dominio a DTO. */
    private static ResumenCuenta aDto(com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta r) {
        return new ResumenCuenta(r.id(), r.codigo(), r.nombre());
    }

    // ------------------------------------------------------------------------------------------ Libro Diario

    /** Monto del dominio como cadena decimal de 2 decimales (ADR-013). */
    private static String monto(Dinero d) {
        return d.toString();
    }

    /**
     * Asiento guardado del dominio a DTO con sus líneas.
     *
     * @param a asiento del dominio
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.Asiento aDto(Asiento a) {
        List<com.bcodesphere.pilot.compartido.api.contrato.LineaAsiento> lineas = a.lineas().stream()
                .map(l -> new com.bcodesphere.pilot.compartido.api.contrato.LineaAsiento(
                        l.id(),
                        l.numeroLinea(),
                        aDto(l.cuenta()),
                        l.descripcion(),
                        monto(l.debe()),
                        monto(l.haber()),
                        com.bcodesphere.pilot.compartido.api.contrato.OrigenLinea.fromValue(
                                l.origenLinea().name()),
                        l.lineaBaseId()))
                .toList();
        return new com.bcodesphere.pilot.compartido.api.contrato.Asiento(
                a.id(),
                a.anio(),
                a.numero(),
                a.fecha(),
                a.concepto(),
                com.bcodesphere.pilot.compartido.api.contrato.EstadoAsiento.fromValue(
                        a.estado().name()),
                // El contrato de 1.0 solo conoce MANUAL y REVERSION; N8N llega con F5 y su ampliación del contrato
                com.bcodesphere.pilot.compartido.api.contrato.OrigenAsiento.fromValue(
                        a.origenTipo().name()),
                a.origenId(),
                a.modoPrecio() == null
                        ? null
                        : com.bcodesphere.pilot.compartido.api.contrato.ModoPrecio.fromValue(
                                a.modoPrecio().name()),
                a.asientoRevertidoId(),
                a.asientoReversionId(),
                monto(a.totalDebe()),
                monto(a.totalHaber()),
                a.creadoEn().atOffset(ZoneOffset.UTC),
                lineas);
    }

    /**
     * Cabecera de asiento del dominio a DTO de listado.
     *
     * @param r resumen del dominio
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.ResumenAsiento aDto(
            com.bcodesphere.pilot.contabilidad.dominio.asiento.ResumenAsiento r) {
        return new com.bcodesphere.pilot.compartido.api.contrato.ResumenAsiento(
                r.id(),
                r.anio(),
                r.numero(),
                r.fecha(),
                r.concepto(),
                com.bcodesphere.pilot.compartido.api.contrato.EstadoAsiento.fromValue(
                        r.estado().name()),
                com.bcodesphere.pilot.compartido.api.contrato.OrigenAsiento.fromValue(
                        r.origenTipo().name()),
                monto(r.totalDebe()),
                monto(r.totalHaber()));
    }

    /**
     * Asiento expandido a DTO de vista previa.
     *
     * @param e asiento expandido con sus totales
     * @return DTO del contrato; {@code modoPrecio} y {@code tasaIva} son nulos si ninguna línea lleva IVA
     */
    static VistaPreviaAsiento aDtoVistaPrevia(AsientoExpandido e) {
        List<LineaVistaPrevia> lineas =
                e.lineas().stream().map(MapeadorContabilidad::aDto).toList();
        BigDecimal tasa = e.tasaIva();
        return new VistaPreviaAsiento(
                e.modoPrecio() == null
                        ? null
                        : com.bcodesphere.pilot.compartido.api.contrato.ModoPrecio.fromValue(
                                e.modoPrecio().name()),
                // La tasa viaja con 4 decimales, como en tasa_impuesto (0.1300)
                tasa == null
                        ? null
                        : tasa.setScale(4, java.math.RoundingMode.UNNECESSARY).toPlainString(),
                lineas,
                monto(e.totalDebe()),
                monto(e.totalHaber()),
                monto(e.diferencia()),
                e.cuadra());
    }

    /** Línea expandida a DTO de vista previa. */
    private static LineaVistaPrevia aDto(LineaExpandida l) {
        return new LineaVistaPrevia(
                l.numeroLinea(),
                aDto(l.cuenta()),
                l.descripcion(),
                monto(l.debe()),
                monto(l.haber()),
                com.bcodesphere.pilot.compartido.api.contrato.OrigenLinea.fromValue(
                        l.origenLinea().name()),
                l.numeroLineaOrigen());
    }
}
