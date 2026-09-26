import { z } from 'zod';

/**
 * Esquema del formulario "Espacio de trabajo": único campo editable en la versión abierta (ADR-032).
 * Se recortan los espacios de los extremos antes de validar, así un nombre de solo espacios cuenta como vacío
 * y nunca se envía. El máximo de 250 refleja el contrato (`nombre VARCHAR(250)`, CLAUDE.md §9.2).
 */
export const esquemaEspacio = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio')
    .max(250, 'El nombre admite máximo 250 caracteres'),
});

/** Valores del formulario ya validados. */
export type ValoresEspacio = z.infer<typeof esquemaEspacio>;
