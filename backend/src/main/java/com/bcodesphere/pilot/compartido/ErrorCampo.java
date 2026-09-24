package com.bcodesphere.pilot.compartido;

/**
 * Error de validación de un campo (esquema {@code ErrorCampo} del contrato).
 *
 * @param campo ruta del campo con error, por ejemplo {@code lineas[0].debe}
 * @param mensaje explicación en español
 */
public record ErrorCampo(String campo, String mensaje) {}
