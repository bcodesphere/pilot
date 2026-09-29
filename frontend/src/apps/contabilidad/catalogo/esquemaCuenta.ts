import { z } from 'zod';

/** Código de cuenta: solo dígitos, de 1 a 8 (el resto de reglas, como la clase o el padre, las decide el backend). */
const codigoSchema = z.string().regex(/^[0-9]{1,8}$/, 'Usa solo dígitos, de 1 a 8');

/** Nombre de cuenta: obligatorio y de hasta 200 caracteres (contrato `NuevaCuentaContable`). */
const nombreSchema = z
  .string()
  .trim()
  .min(1, 'El nombre es obligatorio')
  .max(200, 'El nombre admite máximo 200 caracteres');

/**
 * Esquema del diálogo "Nueva cuenta". La naturaleza ya no se pide: el backend siempre la deriva de la
 * cuenta padre, o de la clase en el nivel 1 (ADR-042); el contrato de C1 la quitó de `NuevaCuentaContable`.
 */
export const esquemaNuevaCuenta = z.object({
  codigo: codigoSchema,
  nombre: nombreSchema,
});

/** Valores del formulario de nueva cuenta. */
export type ValoresNuevaCuenta = z.infer<typeof esquemaNuevaCuenta>;

/**
 * Esquema del diálogo "Editar cuenta": código, nombre y estado (`ActualizacionCuentaContable`, ADR-042).
 * La naturaleza tampoco se envía aquí: se muestra derivada, como texto de solo lectura.
 */
export const esquemaEditarCuenta = z.object({
  codigo: codigoSchema,
  nombre: nombreSchema,
  activa: z.boolean(),
});

/** Valores del formulario de edición. */
export type ValoresEditarCuenta = z.infer<typeof esquemaEditarCuenta>;
