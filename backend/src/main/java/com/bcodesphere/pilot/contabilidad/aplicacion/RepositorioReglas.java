package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.reglas.ReglaContabilizacion;
import com.bcodesphere.pilot.contabilidad.dominio.reglas.TipoOperacionContable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de las reglas de contabilización de la empresa activa. */
public interface RepositorioReglas {

    /**
     * Lista las reglas con el resumen de su cuenta.
     *
     * @param tipoOperacion filtra por tipo de operación, o nulo para todos
     * @return las reglas ordenadas por tipo, categoría y código
     */
    List<ReglaContabilizacion> listar(TipoOperacionContable tipoOperacion);

    /**
     * Busca una regla por id.
     *
     * @param id identificador
     * @return la regla, o vacío si no existe en la empresa activa
     */
    Optional<ReglaContabilizacion> buscar(UUID id);

    /**
     * Cambia la cuenta y el estado de una regla con control optimista; sube la versión en uno.
     *
     * @param id regla
     * @param cuentaId cuenta nueva, o nulo (solo válido con la regla inactiva)
     * @param activa estado nuevo
     * @param versionEsperada versión que el cliente leyó
     * @return {@code false} si la versión ya no coincide
     */
    boolean actualizar(UUID id, UUID cuentaId, boolean activa, long versionEsperada);
}
