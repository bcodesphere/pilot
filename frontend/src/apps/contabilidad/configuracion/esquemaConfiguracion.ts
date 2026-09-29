import { z } from 'zod';

/**
 * Esquema del formulario de configuración contable (CLAUDE.md §11.3): solo el modo de precio por
 * defecto. Las cuentas de IVA débito y crédito fiscal son fijas desde la plantilla (ADR-042) y no
 * viajan aquí: `ActualizacionConfiguracionContable` ya no las acepta.
 */
export const esquemaConfiguracion = z.object({
  modoPrecioDefecto: z.enum(['CON_IVA', 'SIN_IVA']),
});

/** Valores del formulario de configuración. */
export type ValoresConfiguracion = z.infer<typeof esquemaConfiguracion>;
