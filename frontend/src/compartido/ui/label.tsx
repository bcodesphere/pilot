import type { LabelHTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Etiqueta de formulario; siempre debe asociarse a su control con `htmlFor`.
 * @param props atributos de `<label>`
 */
export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn('text-sm font-medium', className)} {...props} />;
}
