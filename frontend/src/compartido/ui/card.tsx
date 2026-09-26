import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Tarjeta contenedora (borde y relleno); es la unidad visual del catálogo de apps.
 * @param props atributos de `<div>`
 */
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('rounded-lg border bg-white p-4', className)} {...props} />;
}
