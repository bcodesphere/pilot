package com.bcodesphere.pilot.contabilidad.api;

import com.bcodesphere.pilot.compartido.api.contrato.Asiento;
import com.bcodesphere.pilot.compartido.api.contrato.AsientosApi;
import com.bcodesphere.pilot.compartido.api.contrato.EstadoAsiento;
import com.bcodesphere.pilot.compartido.api.contrato.NuevaLineaAsiento;
import com.bcodesphere.pilot.compartido.api.contrato.NuevaReversion;
import com.bcodesphere.pilot.compartido.api.contrato.NuevoAsiento;
import com.bcodesphere.pilot.compartido.api.contrato.OrigenAsiento;
import com.bcodesphere.pilot.compartido.api.contrato.PaginaAsientos;
import com.bcodesphere.pilot.compartido.api.contrato.VistaPreviaAsiento;
import com.bcodesphere.pilot.contabilidad.aplicacion.ConsultarAsientos;
import com.bcodesphere.pilot.contabilidad.aplicacion.FiltroAsientos;
import com.bcodesphere.pilot.contabilidad.aplicacion.PrevisualizarAsiento;
import com.bcodesphere.pilot.contabilidad.aplicacion.RegistrarAsientoManual;
import com.bcodesphere.pilot.contabilidad.aplicacion.RevertirAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.LineaSolicitud;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.SolicitudAsiento;
import com.bcodesphere.pilot.contabilidad.dominio.configuracion.ModoPrecio;
import com.bcodesphere.pilot.plataforma.RequiereIdempotencia;
import com.bcodesphere.pilot.plataforma.RespuestaIdempotente;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/**
 * Controlador del Libro Diario ({@code /contabilidad/asientos}, CLAUDE.md 10.1 y 13). No lleva lógica de negocio:
 * convierte el DTO a una solicitud del dominio, llama al caso de uso y mapea la respuesta. Registrar y revertir exigen
 * {@code Idempotency-Key} (el interceptor responde 428 {@code PLT-006} si falta) y el caso de uso abre la transacción
 * que envuelve la idempotencia y la operación; este controlador solo aporta el cuerpo canónico para el hash y el
 * serializador de la respuesta.
 */
@RestController
class ControladorAsientos implements AsientosApi {

    /** Header que marca una respuesta guardada de una petición anterior (CLAUDE.md 12.6). */
    private static final String HEADER_REPETIDA = "Idempotency-Replayed";

    private final RegistrarAsientoManual registrar;
    private final PrevisualizarAsiento previsualizar;
    private final RevertirAsiento revertir;
    private final ConsultarAsientos consultar;
    private final JsonMapper mapeador;

    /**
     * Crea el controlador.
     *
     * @param registrar caso de uso de registro manual
     * @param previsualizar caso de uso de vista previa
     * @param revertir caso de uso de reversión
     * @param consultar consultas del Libro Diario
     * @param mapeador serializador JSON de la aplicación (montos como cadena decimal, ADR-013)
     */
    ControladorAsientos(
            RegistrarAsientoManual registrar,
            PrevisualizarAsiento previsualizar,
            RevertirAsiento revertir,
            ConsultarAsientos consultar,
            JsonMapper mapeador) {
        this.registrar = registrar;
        this.previsualizar = previsualizar;
        this.revertir = revertir;
        this.consultar = consultar;
        this.mapeador = mapeador;
    }

    @Override
    @RequiereIdempotencia
    public ResponseEntity<Asiento> registrarAsiento(
            UUID xEmpresaId, String idempotencyKey, NuevoAsiento nuevoAsiento, String xRequestId) {
        // 1. El cuerpo canónico (el DTO reserializado) alimenta el hash de la idempotencia
        RespuestaIdempotente respuesta = registrar.registrar(
                idempotencyKey,
                mapeador.writeValueAsString(nuevoAsiento),
                aSolicitud(nuevoAsiento),
                a -> mapeador.writeValueAsString(MapeadorContabilidad.aDto(a)));
        return responder(respuesta);
    }

    @Override
    public ResponseEntity<VistaPreviaAsiento> previsualizarAsiento(
            UUID xEmpresaId, NuevoAsiento nuevoAsiento, String xRequestId) {
        return ResponseEntity.ok(
                MapeadorContabilidad.aDtoVistaPrevia(previsualizar.previsualizar(aSolicitud(nuevoAsiento))));
    }

    @Override
    public ResponseEntity<Asiento> obtenerAsiento(UUID xEmpresaId, UUID asientoId, String xRequestId) {
        return ResponseEntity.ok(MapeadorContabilidad.aDto(consultar.obtener(asientoId)));
    }

    @Override
    public ResponseEntity<PaginaAsientos> listarAsientos(
            UUID xEmpresaId,
            String xRequestId,
            Integer limite,
            String cursor,
            LocalDate desde,
            LocalDate hasta,
            Integer anio,
            Long numero,
            OrigenAsiento origen,
            EstadoAsiento estado,
            UUID cuentaId) {
        // 1. Los enums del contrato y del dominio comparten valores; se convierten por nombre
        FiltroAsientos filtro = new FiltroAsientos(
                desde,
                hasta,
                anio,
                numero,
                origen == null
                        ? null
                        : com.bcodesphere.pilot.contabilidad.dominio.asiento.OrigenAsiento.valueOf(origen.getValue()),
                estado == null
                        ? null
                        : com.bcodesphere.pilot.contabilidad.dominio.asiento.EstadoAsiento.valueOf(estado.getValue()),
                cuentaId);
        com.bcodesphere.pilot.contabilidad.aplicacion.PaginaAsientos pagina = consultar.listar(filtro, cursor, limite);
        return ResponseEntity.ok(new PaginaAsientos(pagina.elementos().stream()
                        .map(MapeadorContabilidad::aDto)
                        .toList())
                .siguienteCursor(pagina.siguienteCursor()));
    }

    @Override
    @RequiereIdempotencia
    public ResponseEntity<Asiento> revertirAsiento(
            UUID xEmpresaId, UUID asientoId, String idempotencyKey, String xRequestId, NuevaReversion nuevaReversion) {
        // 1. El hash incluye el asiento: la misma clave sobre otro asiento es otro cuerpo (PLT-005)
        LocalDate fecha = nuevaReversion == null ? null : nuevaReversion.getFecha();
        RespuestaIdempotente respuesta = revertir.revertir(
                asientoId,
                idempotencyKey,
                asientoId + ":" + (nuevaReversion == null ? "{}" : mapeador.writeValueAsString(nuevaReversion)),
                fecha,
                a -> mapeador.writeValueAsString(MapeadorContabilidad.aDto(a)));
        return responder(respuesta);
    }

    /** Convierte el DTO de entrada a la solicitud del dominio; los montos siguen como cadena (ADR-036). */
    private static SolicitudAsiento aSolicitud(NuevoAsiento dto) {
        List<LineaSolicitud> lineas =
                dto.getLineas().stream().map(ControladorAsientos::aLinea).toList();
        // El modo es opcional: se lee una sola vez porque el getter es anulable
        var modoDto = dto.getModoPrecio();
        ModoPrecio modo = modoDto == null ? null : ModoPrecio.valueOf(modoDto.getValue());
        return new SolicitudAsiento(dto.getFecha(), dto.getConcepto(), modo, lineas);
    }

    /** Línea de entrada del DTO a la del dominio. */
    private static LineaSolicitud aLinea(NuevaLineaAsiento l) {
        return new LineaSolicitud(
                l.getCuentaId(), l.getDescripcion(), l.getDebe(), l.getHaber(), Boolean.TRUE.equals(l.getLlevaIva()));
    }

    /** Arma la respuesta 201 desde el JSON guardado; una repetición lleva {@code Idempotency-Replayed: true}. */
    private ResponseEntity<Asiento> responder(RespuestaIdempotente respuesta) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.valueOf(respuesta.estadoHttp()))
                .contentType(MediaType.APPLICATION_JSON);
        if (respuesta.repetida()) {
            builder.header(HEADER_REPETIDA, "true");
        }
        return builder.body(mapeador.readValue(respuesta.cuerpoJson(), Asiento.class));
    }
}
