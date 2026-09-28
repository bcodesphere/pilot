package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.estados.BalanzaComprobacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ComprobacionSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DesgloseIva;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DiagnosticoMayorizacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DiferenciaMayorizacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.FilaBalanza;
import com.bcodesphere.pilot.contabilidad.dominio.estados.FilaEstado;
import com.bcodesphere.pilot.contabilidad.dominio.estados.LibroMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.MovimientoMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva;
import com.bcodesphere.pilot.contabilidad.dominio.estados.RubroEstado;
import com.bcodesphere.pilot.contabilidad.dominio.estados.Saldo;

/**
 * Convierte los objetos del dominio de reportes ({@code dominio.estados}) a los DTO del contrato (sin lógica de
 * negocio, CLAUDE.md 8.3). Los enums del dominio y del contrato tienen los mismos valores, así que se convierten
 * por nombre; los montos viajan como cadena decimal de 2 decimales (ADR-013).
 */
final class MapeadorReportes {

    private MapeadorReportes() {}

    /** Monto del dominio como cadena decimal de 2 decimales (ADR-013). */
    private static String monto(Dinero d) {
        return d.toString();
    }

    /** Cuenta de dominio (con su código completo) a resumen del contrato. */
    private static com.bcodesphere.pilot.compartido.api.contrato.ResumenCuenta aDto(Cuenta c) {
        return new com.bcodesphere.pilot.compartido.api.contrato.ResumenCuenta(
                c.id(), c.codigo().valor(), c.nombre());
    }

    /** Resumen de cuenta del dominio (id, código, nombre) a resumen del contrato. */
    private static com.bcodesphere.pilot.compartido.api.contrato.ResumenCuenta aDto(
            com.bcodesphere.pilot.contabilidad.dominio.catalogo.ResumenCuenta r) {
        return new com.bcodesphere.pilot.compartido.api.contrato.ResumenCuenta(r.id(), r.codigo(), r.nombre());
    }

    /** Saldo de presentación del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.Saldo aDto(Saldo s) {
        return new com.bcodesphere.pilot.compartido.api.contrato.Saldo(
                monto(s.monto()),
                com.bcodesphere.pilot.compartido.api.contrato.LadoSaldo.fromValue(
                        s.lado().name()),
                s.contrarioNaturaleza());
    }

    /**
     * Libro Mayor del dominio a DTO.
     *
     * @param m Libro Mayor calculado
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.LibroMayor aDto(LibroMayor m) {
        return new com.bcodesphere.pilot.compartido.api.contrato.LibroMayor(
                aDto(m.cuenta()),
                com.bcodesphere.pilot.compartido.api.contrato.NaturalezaCuenta.fromValue(
                        m.naturaleza().name()),
                m.desde(),
                m.hasta(),
                aDto(m.saldoInicial()),
                m.movimientos().stream().map(MapeadorReportes::aDto).toList(),
                monto(m.totalDebe()),
                monto(m.totalHaber()),
                aDto(m.saldoFinal()));
    }

    /** Movimiento de mayor del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.MovimientoMayor aDto(MovimientoMayor mm) {
        var l = mm.linea();
        return new com.bcodesphere.pilot.compartido.api.contrato.MovimientoMayor(
                l.fecha(),
                l.asientoId(),
                l.anio(),
                l.numero(),
                l.concepto(),
                l.descripcion(),
                aDto(l.cuenta()),
                monto(l.debe()),
                monto(l.haber()),
                aDto(mm.saldo()));
    }

    /**
     * Balanza de Comprobación del dominio a DTO.
     *
     * @param b balanza calculada
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.BalanzaComprobacion aDto(BalanzaComprobacion b) {
        return new com.bcodesphere.pilot.compartido.api.contrato.BalanzaComprobacion(
                b.desde(),
                b.hasta(),
                b.nivel(),
                b.filas().stream().map(MapeadorReportes::aDto).toList(),
                monto(b.totalDebe()),
                monto(b.totalHaber()),
                monto(b.totalSaldosDeudores()),
                monto(b.totalSaldosAcreedores()),
                b.cuadra());
    }

    /** Fila de balanza del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.FilaBalanza aDto(FilaBalanza f) {
        return new com.bcodesphere.pilot.compartido.api.contrato.FilaBalanza(
                aDto(f.cuenta()),
                f.nivel(),
                f.esDetalle(),
                aDto(f.saldoInicial()),
                monto(f.debe()),
                monto(f.haber()),
                aDto(f.saldoFinal()));
    }

    /**
     * Estado de Resultados del dominio a DTO.
     *
     * @param e estado calculado
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.EstadoResultados aDto(EstadoResultados e) {
        return new com.bcodesphere.pilot.compartido.api.contrato.EstadoResultados(
                e.desde(),
                e.hasta(),
                e.nivel(),
                aDto(e.ingresos()),
                aDto(e.costosGastos()),
                monto(e.utilidadAntesImpuesto()),
                aDto(e.impuestoSobreRenta()),
                monto(e.utilidadEjercicio()),
                e.leyenda());
    }

    /**
     * Estado de Situación Financiera del dominio a DTO.
     *
     * @param e estado calculado
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.EstadoSituacionFinanciera aDto(EstadoSituacionFinanciera e) {
        return new com.bcodesphere.pilot.compartido.api.contrato.EstadoSituacionFinanciera(
                e.fechaCorte(),
                e.nivel(),
                aDto(e.activo()),
                aDto(e.pasivo()),
                aDto(e.patrimonio()),
                monto(e.resultadosEjerciciosAnteriores()),
                monto(e.utilidadEjercicio()),
                monto(e.totalPasivoPatrimonio()),
                aDto(e.comprobacion()),
                e.leyenda());
    }

    /** Comprobación del Estado de Situación Financiera del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.EstadoSituacionFinancieraComprobacion aDto(
            ComprobacionSituacionFinanciera c) {
        return new com.bcodesphere.pilot.compartido.api.contrato.EstadoSituacionFinancieraComprobacion(
                c.cuadra(), monto(c.diferencia()));
    }

    /** Rubro de estado del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.RubroEstado aDto(RubroEstado r) {
        return new com.bcodesphere.pilot.compartido.api.contrato.RubroEstado(
                r.clase(),
                r.nombre(),
                r.filas().stream().map(MapeadorReportes::aDto).toList(),
                monto(r.total()));
    }

    /** Fila de estado del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.FilaEstado aDto(FilaEstado f) {
        return new com.bcodesphere.pilot.compartido.api.contrato.FilaEstado(
                aDto(f.cuenta()), f.nivel(), monto(f.monto()));
    }

    /**
     * Diagnóstico de mayorización del dominio a DTO.
     *
     * @param d diagnóstico calculado
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.DiagnosticoMayorizacion aDto(DiagnosticoMayorizacion d) {
        return new com.bcodesphere.pilot.compartido.api.contrato.DiagnosticoMayorizacion(
                d.consistente(),
                d.cantidadCuentasRevisadas(),
                d.diferencias().stream().map(MapeadorReportes::aDto).toList());
    }

    /** Diferencia de mayorización del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.DiferenciaMayorizacion aDto(
            DiferenciaMayorizacion diff) {
        return new com.bcodesphere.pilot.compartido.api.contrato.DiferenciaMayorizacion(
                aDto(diff.cuenta()),
                diff.anio(),
                diff.mes(),
                monto(diff.saldoDebe()),
                monto(diff.saldoHaber()),
                monto(diff.lineasDebe()),
                monto(diff.lineasHaber()));
    }

    /**
     * Resumen de IVA del dominio a DTO.
     *
     * @param r resumen calculado
     * @return DTO del contrato
     */
    static com.bcodesphere.pilot.compartido.api.contrato.ResumenIva aDto(ResumenIva r) {
        return new com.bcodesphere.pilot.compartido.api.contrato.ResumenIva(
                r.anio(),
                r.mes(),
                aDto(r.cuentaIvaDebito()),
                aDto(r.cuentaIvaCredito()),
                aDto(r.ivaDebito()),
                aDto(r.ivaCredito()),
                monto(r.diferenciaEstimada()),
                r.nota());
    }

    /** Desglose de IVA por origen del dominio a DTO. */
    private static com.bcodesphere.pilot.compartido.api.contrato.DesgloseIva aDto(DesgloseIva d) {
        return new com.bcodesphere.pilot.compartido.api.contrato.DesgloseIva(
                monto(d.manual()), monto(d.n8n()), monto(d.reversion()), monto(d.total()));
    }
}
