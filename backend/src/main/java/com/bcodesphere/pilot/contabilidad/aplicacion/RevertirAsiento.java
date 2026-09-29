package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.AsientoRevertido;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ReglasReversion;
import com.bcodesphere.pilot.plataforma.ContextoEmpresa;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import com.bcodesphere.pilot.plataforma.RegistroAuditoria;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import com.bcodesphere.pilot.plataforma.ServicioIdempotencia;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso «revertir un asiento» (CLAUDE.md 10.1, ADR-019, ADR-036). Rol mínimo: {@code contador}. Un asiento
 * guardado nunca se modifica: se corrige con un contra-asiento que intercambia Debe y Haber, se mayoriza igual que
 * cualquier asiento y marca al original como {@code REVERTIDO}.
 *
 * <p>Usa la misma política de transacción que {@link RegistrarAsientoManual}: {@link #revertir} abre la transacción
 * que envuelve la idempotencia, la lectura con bloqueo, el contra-asiento, la mayorización, la auditoría y el evento.
 */
@Service
public class RevertirAsiento {

    private final ServicioIdempotencia idempotencia;
    private final RepositorioAsientos asientos;
    private final PreparadorAsiento preparador;
    private final GuardarAsiento guardar;
    private final RegistroAuditoria auditoria;
    private final ApplicationEventPublisher eventos;

    /**
     * Crea el caso de uso.
     *
     * @param idempotencia servicio de idempotencia de {@code plataforma}
     * @param asientos puerto de escritura del Libro Diario
     * @param preparador para conocer «hoy» en hora de El Salvador
     * @param guardar numeración, inserción, mayorización y auditoría del contra-asiento
     * @param auditoria puerto de auditoría (para el asiento original)
     * @param eventos publicador de eventos síncronos
     */
    public RevertirAsiento(
            ServicioIdempotencia idempotencia,
            RepositorioAsientos asientos,
            PreparadorAsiento preparador,
            GuardarAsiento guardar,
            RegistroAuditoria auditoria,
            ApplicationEventPublisher eventos) {
        this.idempotencia = idempotencia;
        this.asientos = asientos;
        this.preparador = preparador;
        this.guardar = guardar;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    /**
     * Revierte un asiento una sola vez por clave de idempotencia y cuerpo.
     *
     * @param asientoId asiento que se revierte
     * @param claveIdempotencia valor del header {@code Idempotency-Key}
     * @param cuerpoSolicitud asiento y cuerpo de la petición en JSON canónico, para el hash de la idempotencia
     * @param fecha fecha contable de la reversión, o nulo para hoy en hora de El Salvador
     * @param serializador convierte la reversión guardada al JSON de la respuesta 201
     * @return respuesta 201 nueva, o la guardada marcada como repetida
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si el asiento no existe en la empresa activa
     * @throws com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad {@code CON-007}, {@code CON-008},
     *     {@code CON-009}, {@code CON-018}
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional
    public RespuestaIdempotente revertir(
            UUID asientoId,
            String claveIdempotencia,
            String cuerpoSolicitud,
            LocalDate fecha,
            Function<Asiento, String> serializador) {
        return idempotencia.ejecutar(claveIdempotencia, cuerpoSolicitud, () -> {
            // 1. Lee el original con bloqueo: dos reversiones simultáneas se serializan aquí
            Asiento original = asientos.buscarBloqueando(asientoId).orElseThrow(ExcepcionPlataforma::noEncontrado);

            // 2. Fecha del cuerpo o de hoy; CON-009, CON-008, CON-007 y CON-018 las decide el dominio
            LocalDate fechaReversion = fecha != null ? fecha : preparador.hoy();
            ReglasReversion.validar(original, fechaReversion, preparador.hoy());

            // 3. Guarda el contra-asiento (numera en el año de su fecha, inserta, mayoriza y audita CREAR)
            Asiento reversion = guardar.guardarReversion(original, fechaReversion);

            // 4. Marca el original como REVERTIDO (el trigger exige el enlace y version + 1) y lo audita
            asientos.marcarRevertido(original.id(), reversion.id());
            auditoria.registrar(
                    "asiento",
                    original.id().toString(),
                    "REVERTIR",
                    instantanea("CONTABILIZADO", null),
                    instantanea("REVERTIDO", reversion.id()));

            // 5. Evento síncrono en la misma transacción: quien lo escuche (F5) actúa o revierte todo
            eventos.publishEvent(new AsientoRevertido(
                    ContextoEmpresa.empresaRequerida(),
                    original.id(),
                    reversion.id(),
                    original.origenTipo().name(),
                    original.origenId()));

            // 6. La respuesta es la reversión, y se guarda con la idempotencia en la misma transacción
            return RespuestaIdempotente.de(201, serializador.apply(reversion));
        });
    }

    /** Campos auditables del paso a REVERTIDO. */
    private static Map<String, Object> instantanea(String estado, UUID reversionId) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("estado", estado);
        valores.put("asientoReversionId", reversionId == null ? null : reversionId.toString());
        return valores;
    }
}
