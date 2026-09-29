package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.AsientoExpandido;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ReglasAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.SolicitudAsiento;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import com.bcodesphere.pilot.plataforma.ServicioIdempotencia;
import java.util.function.Function;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso «registrar un asiento manual» del Libro Diario (CLAUDE.md 10.1). Rol mínimo: {@code contador}.
 *
 * <p><strong>Transacción única:</strong> el método {@link #registrar} lleva {@code @Transactional} y es el que abre
 * la transacción que envuelve a la vez la idempotencia ({@link ServicioIdempotencia} exige una transacción activa) y
 * el caso de uso: expandir el IVA, validar, numerar, insertar, mayorizar, auditar y guardar la respuesta se confirman
 * o se revierten juntos (CLAUDE.md 4.1, ADR-018). El controlador solo llama a este método y mapea, sin lógica de
 * negocio (CLAUDE.md 8.3). Como {@code ServicioIdempotencia} no guarda respuestas 4xx y las excepciones de negocio
 * revierten la transacción, un rechazo no deja nada guardado y se puede reintentar corregido.
 */
@Service
public class RegistrarAsientoManual {

    private final ServicioIdempotencia idempotencia;
    private final PreparadorAsiento preparador;
    private final GuardarAsiento guardar;

    /**
     * Crea el caso de uso.
     *
     * @param idempotencia servicio de idempotencia de {@code plataforma}
     * @param preparador lecturas y expansión del asiento
     * @param guardar numeración, inserción, mayorización y auditoría
     */
    public RegistrarAsientoManual(
            ServicioIdempotencia idempotencia, PreparadorAsiento preparador, GuardarAsiento guardar) {
        this.idempotencia = idempotencia;
        this.preparador = preparador;
        this.guardar = guardar;
    }

    /**
     * Registra el asiento una sola vez por clave de idempotencia y cuerpo.
     *
     * @param claveIdempotencia valor del header {@code Idempotency-Key}
     * @param cuerpoSolicitud cuerpo de la petición en JSON canónico; su hash detecta el reuso de la clave con otro cuerpo
     * @param solicitud datos capturados del asiento
     * @param serializador convierte el asiento guardado al JSON de la respuesta 201 (lo define la capa api, que conoce
     *     el DTO); se guarda para devolverlo igual en una repetición
     * @return respuesta 201 nueva, o la guardada marcada como repetida
     * @throws com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad {@code CON-001} a {@code CON-005},
     *     {@code CON-007}, {@code CON-013}, {@code CON-017}
     * @throws com.bcodesphere.pilot.compartido.ExcepcionValidacion {@code CON-006}
     */
    @PreAuthorize("hasRole('CONTADOR')")
    @Transactional
    public RespuestaIdempotente registrar(
            String claveIdempotencia,
            String cuerpoSolicitud,
            SolicitudAsiento solicitud,
            Function<Asiento, String> serializador) {
        return idempotencia.ejecutar(claveIdempotencia, cuerpoSolicitud, () -> {
            // 1. Resolver cuentas, expandir el IVA y aplicar las validaciones que impiden expandir
            AsientoExpandido expandido = preparador.preparar(solicitud);
            // 2. Partida doble sobre las líneas expandidas (CON-001, CON-004, CON-005)
            ReglasAsiento.validar(expandido);
            // 3. Numerar, insertar cabecera y líneas, mayorizar y auditar
            Asiento guardado = guardar.guardarManual(expandido);
            // 4. La respuesta 201 se guarda con la idempotencia dentro de la misma transacción
            return RespuestaIdempotente.de(201, serializador.apply(guardado));
        });
    }
}
