import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Marcador de carga (esqueleto): un bloque con animación de pulso que ocupa el espacio del
 * contenido real mientras llega (`TablaContable`, tarjetas del tablero…).
 * @param props atributos de `<div>`
 */
export function Skeleton({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      data-slot="skeleton"
      aria-hidden="true"
      className={cn('animate-pulse rounded-md bg-[var(--color-borde)]', className)}
      {...props}
    />
  );
}
