import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Variantes de la insignia (estado de una app o de una API key), con los colores semánticos del tema. */
const variantes = cva('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', {
  variants: {
    variant: {
      default: 'bg-[var(--color-lienzo)] text-[var(--color-texto-suave)]',
      success: 'bg-[var(--color-exito)]/10 text-[var(--color-exito)]',
      warning: 'bg-[var(--color-alerta)]/10 text-[var(--color-alerta)]',
      danger: 'bg-[var(--color-error)]/10 text-[var(--color-error)]',
    },
  },
  defaultVariants: { variant: 'default' },
});

/**
 * Insignia de estado.
 * @param props atributos de `<span>` más `variant`
 */
export function Badge({
  className,
  variant,
  ...props
}: HTMLAttributes<HTMLSpanElement> & VariantProps<typeof variantes>) {
  return <span className={cn(variantes({ variant }), className)} {...props} />;
}
