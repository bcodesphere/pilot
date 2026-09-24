import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Une clases CSS resolviendo conflictos de Tailwind (utilidad estándar de shadcn/ui).
 * @param entradas clases condicionales o listas de clases
 * @returns cadena de clases sin duplicados en conflicto
 */
export function cn(...entradas: ClassValue[]): string {
  return twMerge(clsx(entradas));
}
