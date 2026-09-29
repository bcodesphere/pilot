package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.compartido.Dinero;
import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ConfiguracionContable;
import com.bcodesphere.pilot.contabilidad.dominio.estados.BalanzaComprobacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.DiagnosticoMayorizacion;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoResultados;
import com.bcodesphere.pilot.contabilidad.dominio.estados.EstadoSituacionFinanciera;
import com.bcodesphere.pilot.contabilidad.dominio.estados.LibroMayor;
import com.bcodesphere.pilot.contabilidad.dominio.estados.MovimientoLinea;
import com.bcodesphere.pilot.contabilidad.dominio.estados.NetoCuenta;
import com.bcodesphere.pilot.contabilidad.dominio.estados.ResumenIva;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de lectura de los reportes contables: Libro Mayor, Balanza de Comprobación, los dos estados
 * financieros, el resumen de IVA y el diagnóstico de mayorización (CLAUDE.md 10.4, 10.5 y 13; ADR-038). Reúne los
 * datos crudos de {@link ConsultaReportes} y del catálogo, y delega todo el cálculo a {@code dominio.estados}, que
 * es lógica pura. Rol mínimo {@code auditor} para los cinco reportes; {@code contador} para el diagnóstico, porque
 * expone el detalle técnico de la mayorización (ADR-038 §9). Transacciones de solo lectura.
 */
@Service
public class ConsultarReportes {

    private final ConsultaReportes consulta;
    private final RepositorioCuentas cuentas;
    private final RepositorioConfiguracionContable configuracion;

    /**
     * Crea el caso de uso.
     *
     * @param consulta puerto de lectura de movimientos para los reportes
     * @param cuentas catálogo de cuentas de la empresa activa
     * @param configuracion configuración contable de la empresa activa (cuentas de IVA)
     */
    public ConsultarReportes(
            ConsultaReportes consulta, RepositorioCuentas cuentas, RepositorioConfiguracionContable configuracion) {
        this.consulta = consulta;
        this.cuentas = cuentas;
        this.configuracion = configuracion;
    }

    /**
     * Libro Mayor / auxiliar de una cuenta (de detalle o padre) en un rango de fechas.
     *
     * @param cuentaId cuenta consultada
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @return el Libro Mayor
     * @throws ExcepcionValidacion 422 {@code PLT-002} si {@code desde} es posterior a {@code hasta}
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si la cuenta no existe en la empresa activa
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public LibroMayor obtenerLibroMayor(UUID cuentaId, LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        Cuenta cuenta = cuentas.buscar(cuentaId).orElseThrow(ExcepcionPlataforma::noEncontrado);
        // 1. La cuenta puede ser padre: sus movimientos son los de todas sus cuentas de detalle (ADR-038 §5)
        Set<UUID> descendientes = descendientesDeDetalle(cuenta, cuentas.listar(null, false, false));
        if (descendientes.isEmpty()) {
            return LibroMayor.generar(cuenta, Dinero.CERO, List.of(), desde, hasta);
        }
        NetoCuenta saldoInicial = consulta.saldoAcumuladoAFecha(descendientes, desde.minusDays(1));
        List<MovimientoLinea> lineas = consulta.listarMovimientos(descendientes, desde, hasta);
        return LibroMayor.generar(cuenta, saldoInicial.neto(), lineas, desde, hasta);
    }

    /**
     * Balanza de Comprobación de un rango de fechas.
     *
     * @param desde inicio del rango, inclusive
     * @param hasta fin del rango, inclusive
     * @param nivel nivel de la jerarquía pedido (1 a 5)
     * @return la Balanza de Comprobación
     * @throws ExcepcionValidacion 422 {@code PLT-002} si {@code desde} es posterior a {@code hasta} o el nivel no
     *     está entre 1 y 5
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public BalanzaComprobacion obtenerBalanza(LocalDate desde, LocalDate hasta, int nivel) {
        validarRango(desde, hasta);
        validarNivel(nivel);
        List<Cuenta> catalogo = cuentas.listar(null, false, false);
        Map<UUID, NetoCuenta> saldoInicial = consulta.saldoAcumuladoAFecha(desde.minusDays(1));
        Map<UUID, NetoCuenta> movimientos = consulta.movimientosEnRango(desde, hasta);
        return BalanzaComprobacion.generar(catalogo, saldoInicial, movimientos, desde, hasta, nivel);
    }

    /**
     * Estado de Resultados de gestión de un rango de fechas.
     *
     * @param desde inicio del período, inclusive
     * @param hasta fin del período, inclusive
     * @param nivel nivel de la jerarquía pedido (1 a 5)
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @return el Estado de Resultados
     * @throws ExcepcionValidacion 422 {@code PLT-002} si {@code desde} es posterior a {@code hasta} o el nivel no
     *     está entre 1 y 5
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public EstadoResultados obtenerEstadoResultados(LocalDate desde, LocalDate hasta, int nivel, boolean incluirCeros) {
        validarRango(desde, hasta);
        validarNivel(nivel);
        List<Cuenta> catalogo = cuentas.listar(null, false, false);
        Map<UUID, NetoCuenta> movimientos = consulta.movimientosEnRango(desde, hasta);
        return EstadoResultados.generar(catalogo, movimientos, desde, hasta, nivel, incluirCeros);
    }

    /**
     * Estado de Situación Financiera de gestión a una fecha de corte.
     *
     * @param fechaCorte fecha de corte
     * @param nivel nivel de la jerarquía pedido (1 a 5)
     * @param incluirCeros {@code true} conserva también las filas en cero
     * @return el Estado de Situación Financiera, con su comprobación
     * @throws ExcepcionValidacion 422 {@code PLT-002} si el nivel no está entre 1 y 5
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public EstadoSituacionFinanciera obtenerEstadoSituacionFinanciera(
            LocalDate fechaCorte, int nivel, boolean incluirCeros) {
        validarNivel(nivel);
        List<Cuenta> catalogo = cuentas.listar(null, false, false);
        Map<UUID, NetoCuenta> acumuladoAFechaCorte = consulta.saldoAcumuladoAFecha(fechaCorte);
        // El cierre del año anterior al corte, para separar los resultados no cerrados de la utilidad del ejercicio
        LocalDate finAnioAnterior = LocalDate.of(fechaCorte.getYear() - 1, 12, 31);
        Map<UUID, NetoCuenta> acumuladoAFinAnioAnterior = consulta.saldoAcumuladoAFecha(finAnioAnterior);
        return EstadoSituacionFinanciera.generar(
                catalogo, acumuladoAFechaCorte, acumuladoAFinAnioAnterior, fechaCorte, nivel, incluirCeros);
    }

    /**
     * Resumen de IVA del mes.
     *
     * @param anio año del período
     * @param mes mes del período (1 a 12)
     * @return el resumen de IVA
     * @throws ExcepcionValidacion 422 {@code PLT-002} si el mes no está entre 1 y 12
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si la empresa no tiene configuración contable
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public ResumenIva obtenerResumenIva(int anio, int mes) {
        validarMes(mes);
        ConfiguracionContable config = configuracion.obtener().orElseThrow(ExcepcionPlataforma::noEncontrado);
        // El IVA débito fiscal es de naturaleza acreedora: se presenta Haber - Debe; el crédito, Debe - Haber
        Map<OrigenAsiento, Dinero> debito = netoPorOrigen(
                consulta.movimientosPorOrigen(config.cuentaIvaDebito().id(), anio, mes),
                n -> n.haber().restar(n.debe()));
        Map<OrigenAsiento, Dinero> credito = netoPorOrigen(
                consulta.movimientosPorOrigen(config.cuentaIvaCredito().id(), anio, mes),
                n -> n.debe().restar(n.haber()));
        return ResumenIva.de(anio, mes, config.cuentaIvaDebito(), config.cuentaIvaCredito(), debito, credito);
    }

    /**
     * Diagnóstico de mayorización: compara {@code saldo_cuenta_mensual} contra la suma de {@code asiento_linea}
     * (invariante de ADR-018). Rol mínimo {@code contador}.
     *
     * @return el diagnóstico
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional(readOnly = true)
    public DiagnosticoMayorizacion diagnosticarMayorizacion() {
        return DiagnosticoMayorizacion.de(consulta.contarCombinacionesRevisadas(), consulta.diagnosticarMayorizacion());
    }

    /** Cuentas de detalle descendientes de {@code cuenta} (ella misma si ya es de detalle, ADR-038 §5). */
    private static Set<UUID> descendientesDeDetalle(Cuenta cuenta, List<Cuenta> catalogo) {
        String prefijo = cuenta.codigo().valor();
        return catalogo.stream()
                .filter(Cuenta::aceptaMovimientos)
                .filter(c -> c.codigo().valor().startsWith(prefijo))
                .map(Cuenta::id)
                .collect(Collectors.toSet());
    }

    /** Convierte el neto (Debe, Haber) de cada origen a un monto con la fórmula dada. */
    private static Map<OrigenAsiento, Dinero> netoPorOrigen(
            Map<OrigenAsiento, NetoCuenta> porOrigen, java.util.function.Function<NetoCuenta, Dinero> formula) {
        return porOrigen.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> formula.apply(e.getValue())));
    }

    /** {@code desde} no puede ser posterior a {@code hasta} (mismo criterio que el Libro Diario, CLAUDE.md 10.5). */
    private static void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("desde", "La fecha inicial no puede ser posterior a la final")));
        }
    }

    /** Defensa adicional: el contrato ya rechaza un nivel fuera de 1 a 5 con Bean Validation (ADR-038 §10). */
    private static void validarNivel(int nivel) {
        if (nivel < 1 || nivel > 5) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("nivel", "El nivel debe estar entre 1 y 5")));
        }
    }

    /** Defensa adicional: el contrato ya rechaza un mes fuera de 1 a 12 con Bean Validation (ADR-038 §10). */
    private static void validarMes(int mes) {
        if (mes < 1 || mes > 12) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("mes", "El mes debe estar entre 1 y 12")));
        }
    }
}
