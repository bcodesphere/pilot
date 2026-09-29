import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Variantes del aviso, con los colores semánticos del tema (spec F4.5 §7.2). */
const variantes = cva('rounded-[var(--radius-panel)] border p-3 text-sm', {
  variants: {
    variant: {
      info: 'border-[var(--color-borde)] bg-[var(--color-lienzo)] text-[var(--color-texto)]',
      success: 'border-[var(--color-exito)]/30 bg-[var(--color-exito)]/10 text-[var(--color-exito)]',
      warning: 'border-[var(--color-alerta)]/30 bg-[var(--color-alerta)]/10 text-[var(--color-alerta)]',
      error: 'border-[var(--color-error)]/30 bg-[var(--color-error)]/10 text-[var(--color-error)]',
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
