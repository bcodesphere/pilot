import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Variantes de la insignia (estado de una app o de una API key). */
const variantes = cva('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', {
  variants: {
    variant: {
      default: 'bg-neutral-100 text-neutral-800',
      success: 'bg-green-100 text-green-800',
      warning: 'bg-amber-100 text-amber-800',
      danger: 'bg-red-100 text-red-800',
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
