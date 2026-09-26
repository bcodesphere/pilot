import { z } from 'zod';
import { hoyElSalvador } from '@/compartido/formato/fecha';

/** Único alcance disponible en 1.0 (CLAUDE.md §9.2, §12.1). */
export const ALCANCE_INTEGRACION = 'integracion:operaciones' as const;

/**
 * Esquema del formulario de creación de una API key (CLAUDE.md §14.1):
 * - `nombre`: obligatorio (sin espacios de los extremos) y de máximo 100 caracteres.
 * - `alcances`: al menos uno; en 1.0 solo existe `integracion:operaciones`.
 * - `vencimiento`: opcional; si se indica debe ser posterior a hoy en hora de El Salvador (`AAAA-MM-DD`),
 *   así una clave nunca nace vencida. La comparación de cadenas ISO equivale a la de fechas.
 */
export const esquemaApiKey = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio')
    .max(100, 'El nombre admite máximo 100 caracteres'),
  alcances: z.array(z.literal(ALCANCE_INTEGRACION)).min(1, 'Selecciona al menos un alcance'),
  vencimiento: z
    .string()
    .refine((v) => v === '' || v > hoyElSalvador(), 'El vencimiento debe ser una fecha futura'),
});

/** Valores del formulario ya validados. */
export type ValoresApiKey = z.infer<typeof esquemaApiKey>;
