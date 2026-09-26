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
 * Esquema del diálogo "Nueva cuenta". `SEGUN_CLASE` es solo un valor del formulario: en ese caso no se
 * envía naturaleza y el backend usa la de la clase (CLAUDE.md §10.2).
 */
export const esquemaNuevaCuenta = z.object({
  codigo: codigoSchema,
  nombre: nombreSchema,
  naturaleza: z.enum(['SEGUN_CLASE', 'DEUDORA', 'ACREEDORA']),
});

/** Valores del formulario de nueva cuenta. */
export type ValoresNuevaCuenta = z.infer<typeof esquemaNuevaCuenta>;

/** Esquema del diálogo "Editar cuenta": los cuatro campos editables del contrato `ActualizacionCuentaContable`. */
export const esquemaEditarCuenta = z.object({
  codigo: codigoSchema,
  nombre: nombreSchema,
  naturaleza: z.enum(['DEUDORA', 'ACREEDORA']),
  activa: z.boolean(),
});

/** Valores del formulario de edición. */
export type ValoresEditarCuenta = z.infer<typeof esquemaEditarCuenta>;
