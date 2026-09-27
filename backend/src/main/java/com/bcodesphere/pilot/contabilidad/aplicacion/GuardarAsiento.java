package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.AsientoExpandido;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ReglasReversion;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda un asiento nuevo: numerar, insertar cabecera y líneas, mayorizar y auditar (CLAUDE.md 10.1 y 10.3). Es la
 * parte común del registro manual y de la reversión (y de la contabilización de operaciones en F5). Exige la
 * transacción del caso de uso: o se guardan asiento, líneas y saldos, o no se guarda nada (ADR-018).
 */
@Component
class GuardarAsiento {

    private final RepositorioAsientos asientos;
    private final RegistroAuditoria auditoria;
    private final Clock reloj;

    /**
     * Crea el componente.
     *
     * @param asientos puerto de escritura del Libro Diario
     * @param auditoria puerto de auditoría
     * @param reloj reloj del negocio, para el instante de creación
     */
    GuardarAsiento(RepositorioAsientos asientos, RegistroAuditoria auditoria, Clock reloj) {
        this.asientos = asientos;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    /** Instante actual con la precisión de microsegundos de {@code TIMESTAMPTZ}, para que la respuesta coincida con lo guardado. */
    private Instant ahora() {
        return Instant.now(reloj).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Guarda un asiento manual ya expandido y validado.
     *
     * @param expandido asiento que cumple la partida doble
     * @return el asiento guardado, con su número
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Asiento guardarManual(AsientoExpandido expandido) {
        // 1. El número sale del correlativo del año de la fecha contable
        long numero = asientos.siguienteNumero(expandido.fecha().getYear());
        return persistir(Asiento.manual(expandido, numero, ahora()));
    }

    /**
     * Guarda la reversión de un asiento (aún sin marcar el original).
     *
     * @param original asiento que se revierte, ya validado
     * @param fecha fecha contable de la reversión
     * @return la reversión guardada, numerada en el año de su propia fecha
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Asiento guardarReversion(Asiento original, LocalDate fecha) {
        long numero = asientos.siguienteNumero(fecha.getYear());
        return persistir(ReglasReversion.armar(original, fecha, numero, ahora()));
    }

    /** Inserta, mayoriza y audita, en ese orden y dentro de la misma transacción. */
    private Asiento persistir(Asiento asiento) {
        // 1. Cabecera y líneas
        asientos.guardar(asiento);
        // 2. Mayorización en tiempo real: acumula cada línea en el saldo mensual de su cuenta (ADR-018)
        asientos.mayorizar(asiento);
        // 3. Auditoría con el asiento completo como valor nuevo
        auditoria.registrar("asiento", asiento.id().toString(), "CREAR", null, asiento);
        return asiento;
    }
}
