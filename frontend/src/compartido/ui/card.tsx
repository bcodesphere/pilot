import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Tarjeta contenedora (borde y relleno); es la unidad visual del catálogo de apps.
 * @param props atributos de `<div>`
 */
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn(
        'rounded-[var(--radius-panel)] border border-[var(--color-borde)] bg-[var(--color-superficie)] p-4',
        className,
      )}
      {...props}
    />
  );
}
