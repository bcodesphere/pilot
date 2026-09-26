import { z } from 'zod';

/**
 * Esquema de la edición de una regla de contabilización (ADR-035): una regla activa siempre tiene cuenta
 * (`CHECK (NOT activa OR cuenta_id IS NOT NULL)`, CLAUDE.md §9.3). Que la cuenta sea de detalle y activa
 * lo valida el backend (`CON-006`).
 */
export const esquemaRegla = z
  .object({
    cuentaId: z.string().nullable(),
    activa: z.boolean(),
  })
  .superRefine((regla, ctx) => {
    // Activar sin cuenta no se envía: el error va al campo de la cuenta
    if (regla.activa && !regla.cuentaId) {
      ctx.addIssue({ code: 'custom', path: ['cuentaId'], message: 'Asigna una cuenta antes de activarla' });
    }
  });

/** Valores de una fila de regla. */
export type ValoresRegla = z.infer<typeof esquemaRegla>;
