import { esErrorApi } from '@/nucleo/http/errorApi';
import { mensajeAsiento } from '../mensajesContabilidad';

/** Errores de un guardado o de una vista previa, repartidos entre las líneas y el formulario. */
export interface ErroresDistribuidos {
  /** Mensaje por índice de línea (desde 0), tomado de `errores[].campo` (p. ej. `lineas[2].cuentaId`). */
  porLinea: Map<number, string>;
  /** Mensaje general, si el error no es de una línea concreta o no trae `errores`. */
  general: string | null;
}

/**
 * Reparte un error de la API entre las líneas del formulario y el formulario entero. Si el Problem Details trae
 * `errores[].campo` con `lineas[i]` (o `lineas.i`), el mensaje se muestra junto a esa línea (p. ej. `CON-006`
 * junto a la cuenta); si no, es un mensaje general del asiento (con la `diferencia` en `CON-005`).
 * @param error error capturado de la mutación o de la vista previa; `null`/`undefined` si no hubo
 */
export function distribuirError(error: unknown): ErroresDistribuidos {
  const porLinea = new Map<number, string>();
  if (!error) return { porLinea, general: null };

  // 1. Errores con campo dentro de una línea: se muestran junto a ella
  if (esErrorApi(error)) {
    for (const e of error.errores) {
      const coincide = /lineas[[.](\d+)/.exec(e.campo);
      if (coincide) porLinea.set(parseInt(coincide[1]!, 10), e.mensaje);
    }
  }
  // 2. Si alguno se asignó a una línea, no se repite como mensaje general
  if (porLinea.size > 0) return { porLinea, general: null };
  return { porLinea, general: mensajeAsiento(error) };
}
