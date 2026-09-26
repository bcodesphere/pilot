package com.bcodesphere.pilot.contabilidad.aplicacion;

import com.bcodesphere.pilot.contabilidad.dominio.ExcepcionContabilidad;
import com.bcodesphere.pilot.contabilidad.dominio.catalogo.Cuenta;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del catálogo de cuentas de la empresa activa (RLS filtra por empresa). Solo puede usarse
 * dentro de una transacción con el contexto de empresa.
 */
public interface RepositorioCuentas {

    /**
     * Lista el catálogo completo, sin paginar (ADR-035), ordenado por código.
     *
     * @param busqueda texto que filtra por prefijo del código o parte del nombre, o nulo para no filtrar
     * @param soloDetalle {@code true} para devolver solo cuentas que aceptan movimientos
     * @param soloActivas {@code true} para devolver solo cuentas activas
     * @return las cuentas que cumplen los filtros
     */
    List<Cuenta> listar(String busqueda, boolean soloDetalle, boolean soloActivas);

    /**
     * Busca una cuenta por id.
     *
     * @param id identificador
     * @return la cuenta, o vacío si no existe en la empresa activa
     */
    Optional<Cuenta> buscar(UUID id);

    /**
     * Busca una cuenta por código.
     *
     * @param codigo código exacto
     * @return la cuenta, o vacío si no existe en la empresa activa
     */
    Optional<Cuenta> buscarPorCodigo(String codigo);

    /**
     * Indica si la cuenta tiene cuentas hijas.
     *
     * @param id cuenta consultada
     * @return {@code true} si tiene al menos una hija
     */
    boolean tieneHijas(UUID id);

    /**
     * Indica si la configuración contable o una regla activa usa la cuenta (CON-016).
     *
     * @param id cuenta consultada
     * @return {@code true} si está en uso
     */
    boolean enUsoPorConfiguracionOReglaActiva(UUID id);

    /**
     * Crea (inserta) una cuenta nueva.
     *
     * @param cuenta cuenta a insertar (versión 0)
     * @throws ExcepcionContabilidad {@code CON-014} si el código ya existe en la empresa
     */
    void crear(Cuenta cuenta);

    /**
     * Actualiza código, nombre, naturaleza y estado con control optimista; sube la versión en uno.
     *
     * @param cuenta valores nuevos
     * @param versionEsperada versión que el cliente leyó
     * @return {@code false} si la versión ya no coincide (otra petición ganó la carrera)
     * @throws ExcepcionContabilidad {@code CON-014} si el código nuevo ya existe
     */
    boolean actualizar(Cuenta cuenta, long versionEsperada);

    /**
     * Marca la cuenta como no aceptante de movimientos, porque acaba de recibir su primera hija; sube la versión.
     *
     * @param id cuenta padre
     */
    void dejarDeAceptarMovimientos(UUID id);
}
