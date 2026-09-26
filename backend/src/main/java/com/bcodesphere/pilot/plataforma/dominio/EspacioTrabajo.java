package com.bcodesphere.pilot.plataforma.dominio;

import java.util.UUID;

/**
 * Vista del espacio de trabajo (empresa) de una persona natural: solo lo que expone la versión abierta (ADR-032).
 * No incluye nombre comercial, NIT ni NRC, que la versión abierta no captura.
 *
 * @param id identificador de la empresa
 * @param tipo {@code PERSONAL} o {@code JURIDICA}
 * @param nombre nombre del espacio de trabajo
 * @param estado {@code ACTIVA} o {@code INACTIVA}
 * @param version versión para concurrencia optimista (se expone como ETag)
 */
public record EspacioTrabajo(UUID id, String tipo, String nombre, String estado, long version) {}
