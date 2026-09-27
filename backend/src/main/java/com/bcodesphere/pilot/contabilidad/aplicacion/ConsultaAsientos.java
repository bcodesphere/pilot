package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.asiento.Asiento;
import com.bcodesphere.pilot.contabilidad.dominio.asiento.ResumenAsiento;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de lectura del Libro Diario (CQRS ligero, CLAUDE.md 4.1): consultas SQL directas de la empresa activa.
 */
public interface ConsultaAsientos {

    /**
     * Lee un asiento con sus líneas y el resumen de la cuenta de cada una.
     *
     * @param id asiento buscado
     * @return el asiento, o vacío si no existe en la empresa activa
     */
    Optional<Asiento> obtener(UUID id);

    /**
     * Lista cabeceras en orden ascendente de (año, número) a partir de una llave.
     *
     * @param filtro filtros opcionales
     * @param despuesDeAnio año de la llave de la página anterior, o nulo en la primera página
     * @param despuesDeNumero número de la llave de la página anterior, o nulo en la primera página
     * @param cantidad cuántas cabeceras devolver como máximo
     * @return hasta {@code cantidad} cabeceras posteriores a la llave
     */
    List<ResumenAsiento> listar(FiltroAsientos filtro, Integer despuesDeAnio, Long despuesDeNumero, int cantidad);
}
