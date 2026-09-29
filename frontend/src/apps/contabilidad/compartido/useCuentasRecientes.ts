import { useState } from 'react';

/** Máximo de cuentas recordadas por preferencia local (spec F4.5 §8, ficha "Asiento manual (avanzado)"). */
const MAX_RECIENTES = 8;

/** Clave de `localStorage`; una lista por navegador, no por empresa (es solo una comodidad de captura). */
const CLAVE = 'pilot.contabilidad.cuentasRecientes';

/** Lee la lista guardada; nunca falla (modo privado, cuota agotada o dato corrupto devuelven vacío). */
function leer(): string[] {
  try {
    // La regla veda localStorage por los tokens OIDC (CLAUDE.md §1.2.10); esto es una preferencia de
    // captura sin datos de sesión ni de la empresa.
    // eslint-disable-next-line no-restricted-globals
    const crudo = localStorage.getItem(CLAVE);
    const valores = crudo ? (JSON.parse(crudo) as unknown) : [];
    return Array.isArray(valores) ? valores.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    return [];
  }
}

/**
 * Recuerda, solo en este navegador, las últimas cuentas usadas en el asiento manual (spec F4.5 §8: "cuentas
 * recientes primero" en "Asiento manual (avanzado)"). No es información contable ni de la empresa: es una
 * preferencia de captura, así que vive en `localStorage` y nunca en el backend.
 */
export function useCuentasRecientes(): {
  recientes: readonly string[];
  marcarUsada: (cuentaId: string) => void;
} {
  const [recientes, setRecientes] = useState<string[]>(() => leer());

  /** Mueve la cuenta al frente de la lista y recorta al máximo; persiste sin romper si falla el storage. */
  const marcarUsada = (cuentaId: string) => {
    setRecientes((previo) => {
      const siguiente = [cuentaId, ...previo.filter((id) => id !== cuentaId)].slice(0, MAX_RECIENTES);
      try {
        // eslint-disable-next-line no-restricted-globals -- ver la nota de `leer()` arriba
        localStorage.setItem(CLAVE, JSON.stringify(siguiente));
      } catch {
        // Cuota agotada o almacenamiento bloqueado (modo privado): la preferencia solo dura esta sesión
      }
      return siguiente;
    });
  };

  return { recientes, marcarUsada };
}
