import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Variantes del aviso. */
const variantes = cva('rounded-md border p-3 text-sm', {
  variants: {
    variant: {
      info: 'border-neutral-300 bg-neutral-50',
      success: 'border-green-300 bg-green-50 text-green-900',
      warning: 'border-amber-300 bg-amber-50 text-amber-900',
      error: 'border-red-300 bg-red-50 text-red-900',
    },
  },
  defaultVariants: { variant: 'info' },
});

/**
 * Aviso en línea. Los de error y advertencia usan `role="alert"` (se anuncian de inmediato);
 * los demás, `role="status"` (anuncio cortés).
 * @param props atributos de `<div>` más `variant`
 */
export function Alert({
  className,
  variant,
  ...props
}: HTMLAttributes<HTMLDivElement> & VariantProps<typeof variantes>) {
  const urgente = variant === 'error' || variant === 'warning';
  return (
    <div role={urgente ? 'alert' : 'status'} className={cn(variantes({ variant }), className)} {...props} />
  );
}
