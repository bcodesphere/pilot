import { z } from 'zod';

/**
 * Esquema del formulario de configuración contable (CLAUDE.md §11.3): modo de precio y las dos cuentas de IVA.
 * Que la cuenta sea de detalle y activa lo valida el backend (`CON-006`); aquí solo se exige elegirlas.
 */
export const esquemaConfiguracion = z.object({
  modoPrecioDefecto: z.enum(['CON_IVA', 'SIN_IVA']),
  cuentaIvaDebitoId: z.string().min(1, 'Selecciona la cuenta de IVA débito fiscal'),
  cuentaIvaCreditoId: z.string().min(1, 'Selecciona la cuenta de IVA crédito fiscal'),
});

/** Valores del formulario de configuración. */
export type ValoresConfiguracion = z.infer<typeof esquemaConfiguracion>;
