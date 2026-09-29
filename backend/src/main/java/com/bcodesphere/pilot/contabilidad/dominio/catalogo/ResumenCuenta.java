package com.bcodesphere.pilot.contabilidad.dominio.catalogo;

import java.util.UUID;

/**
 * Datos mínimos de una cuenta para mostrarla dentro de la configuración o de una regla.
 *
 * @param id identificador de la cuenta
 * @param codigo código de la cuenta
 * @param nombre nombre de la cuenta
 */
public record ResumenCuenta(UUID id, String codigo, String nombre) {}
