package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.compartido.ErrorCampo;
import com.bcodesphere.pilot.compartido.ExcepcionValidacion;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ResumenAsiento;
import com.bcodesphere.pilot.plataforma.ExcepcionPlataforma;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas del Libro Diario (CLAUDE.md 10.5, 13): un asiento con sus líneas y el listado paginado por llave. Rol
 * mínimo: {@code auditor}. Transacciones de solo lectura.
 */
@Service
public class ConsultarAsientos {

    /** Tamaño de página por defecto, igual que el contrato. */
    static final int LIMITE_POR_DEFECTO = 50;

    private final ConsultaAsientos consulta;

    /**
     * Crea el caso de uso.
     *
     * @param consulta puerto de lectura del Libro Diario
     */
    public ConsultarAsientos(ConsultaAsientos consulta) {
        this.consulta = consulta;
    }

    /**
     * Lee un asiento con sus líneas.
     *
     * @param asientoId asiento buscado
     * @return el asiento
     * @throws ExcepcionPlataforma 404 {@code PLT-017} si no existe en la empresa activa
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public Asiento obtener(UUID asientoId) {
        return consulta.obtener(asientoId).orElseThrow(ExcepcionPlataforma::noEncontrado);
    }

    /**
     * Lista el Libro Diario en orden ascendente de (año, número), paginado por llave.
     *
     * @param filtro filtros opcionales
     * @param cursor cursor de la página anterior, o nulo en la primera
     * @param limite tamaño de página, o nulo para el valor por defecto (50)
     * @return la página y el cursor de la siguiente, si hay
     * @throws ExcepcionValidacion 422 {@code PLT-002} si {@code desde} es posterior a {@code hasta} o el cursor no es válido
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public PaginaAsientos listar(FiltroAsientos filtro, String cursor, Integer limite) {
        // 1. Un rango de fechas invertido no puede devolver nada útil: se informa como error de forma
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("desde", "La fecha inicial no puede ser posterior a la final")));
        }

        // 2. Se pide una fila de más para saber si existe otra página sin contar todo
        int tamano = limite == null ? LIMITE_POR_DEFECTO : limite;
        CursorAsiento llave = cursor == null ? null : CursorAsiento.decodificar(cursor);
        List<ResumenAsiento> filas = consulta.listar(
                filtro, llave == null ? null : llave.anio(), llave == null ? null : llave.numero(), tamano + 1);

        // 3. Si sobró una fila hay más páginas: el cursor apunta al último asiento entregado
        if (filas.size() <= tamano) {
            return new PaginaAsientos(filas, null);
        }
        List<ResumenAsiento> pagina = filas.subList(0, tamano);
        ResumenAsiento ultimo = pagina.get(tamano - 1);
        return new PaginaAsientos(List.copyOf(pagina), new CursorAsiento(ultimo.anio(), ultimo.numero()).codificar());
    }

    /**
     * Lista el Libro Diario completo (con líneas) de un rango, sin paginar, para su exportación (ADR-038, F4-04).
     *
     * @param filtro filtros opcionales; {@code desde} y {@code hasta} siempre vienen informados
     * @return los asientos del rango, cada uno con sus líneas, en orden ascendente de (año, número)
     * @throws ExcepcionValidacion 422 {@code PLT-002} si {@code desde} es posterior a {@code hasta}
     */
    @PreAuthorize("hasRole('AUDITOR')")
    @Transactional(readOnly = true)
    public List<Asiento> listarCompleto(FiltroAsientos filtro) {
        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw new ExcepcionValidacion(
                    "PLT-002",
                    "La solicitud contiene datos inválidos",
                    List.of(new ErrorCampo("desde", "La fecha inicial no puede ser posterior a la final")));
        }
        return consulta.listarCompleto(filtro);
    }
}
