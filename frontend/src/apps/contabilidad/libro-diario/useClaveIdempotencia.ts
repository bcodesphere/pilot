import { useCallback, useRef } from 'react';

/**
 * Entrega la clave `Idempotency-Key` de una operación que crea datos (registrar o revertir un asiento).
 *
 * Política (CLAUDE.md §1.1.4, §12.6): la **misma clave** acompaña a todo reintento del **mismo cuerpo**
 * (doble clic, error de red, 409 `PLT-008`), de modo que el backend devuelva la respuesta original en vez de
 * crear un segundo asiento; cuando el cuerpo **cambia** es otra operación y se genera una clave nueva
 * (con la clave vieja el backend respondería 422 `PLT-005`). La clave vive en un `ref`: no provoca renders.
 *
 * @returns función que, dado el cuerpo serializado, devuelve la clave que debe enviarse
 */
export function useClaveIdempotencia(): (cuerpo: string) => string {
  const ultima = useRef<{ cuerpo: string; clave: string } | null>(null);

  return useCallback((cuerpo: string) => {
    // 1. Cuerpo distinto (o primera vez): clave nueva; cuerpo igual: se conserva la anterior
    if (ultima.current?.cuerpo !== cuerpo) {
      ultima.current = { cuerpo, clave: crypto.randomUUID() };
    }
    return ultima.current.clave;
  }, []);
}
