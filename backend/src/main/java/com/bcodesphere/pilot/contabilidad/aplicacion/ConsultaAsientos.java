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

    /**
     * Lista el Libro Diario completo (con sus líneas) para un rango, sin paginar, en orden ascendente de
     * (año, número, número de línea) — para su exportación (ADR-038, F4-04). Una sola consulta con JOIN evita
     * N+1 sobre catálogos grandes (objetivo del plan: menos de 2 s con 10 000 asientos).
     *
     * @param filtro filtros opcionales; {@code desde} y {@code hasta} siempre vienen informados en la exportación
     * @return los asientos del rango, cada uno con sus líneas
     */
    List<Asiento> listarCompleto(FiltroAsientos filtro);
}
