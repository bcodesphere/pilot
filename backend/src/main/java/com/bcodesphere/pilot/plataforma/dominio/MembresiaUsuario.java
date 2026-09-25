package com.bcodesphere.pilot.plataforma.dominio;

import java.util.UUID;

/**
 * Pertenencia activa de un usuario a una empresa con su rol (resultado de {@code membresias_de_usuario}, ADR-026).
 *
 * @param empresaId empresa
 * @param nombreEmpresa nombre de la empresa
 * @param tipoEmpresa {@code PERSONAL} o {@code JURIDICA}
 * @param rol rol del usuario en la empresa
 */
public record MembresiaUsuario(UUID empresaId, String nombreEmpresa, String tipoEmpresa, Rol rol) {}
