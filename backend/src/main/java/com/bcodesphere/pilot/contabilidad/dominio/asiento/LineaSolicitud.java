package com.bcodesphere.pilot.contabilidad.dominio.asiento;

import java.util.UUID;

/**
 * Línea de un asiento tal como la captura el usuario. Los montos llegan como cadena (esquema {@code MontoEntrada},
 * ADR-036) para que la regla de negocio, y no la validación de forma, responda {@code CON-003}.
 *
 * @param cuentaId cuenta de detalle elegida
 * @param descripcion descripción opcional de la línea
 * @param debe monto al Debe como cadena decimal ({@code "0"} si la línea va al Haber)
 * @param haber monto al Haber como cadena decimal ({@code "0"} si la línea va al Debe)
 * @param llevaIva si el backend debe expandirla en base e IVA (CLAUDE.md 11.2)
 */
public record LineaSolicitud(UUID cuentaId, String descripcion, String debe, String haber, boolean llevaIva) {}
