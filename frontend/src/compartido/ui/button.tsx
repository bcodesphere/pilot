import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import type { ButtonHTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Variantes visuales del botón (ADR-043): el primario usa `--color-primario` del sistema de diseño
 * (nunca negro por defecto, criterio de UX obligatorio de AGENTS.md §3.7).
 */
const variantes = cva(
  'inline-flex items-center justify-center gap-2 rounded-[var(--radius-control)] text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)] disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-[var(--color-primario)] text-white hover:bg-[var(--color-primario-oscuro)]',
        outline:
          'border border-[var(--color-borde)] bg-[var(--color-superficie)] text-[var(--color-texto)] hover:bg-[var(--color-lienzo)]',
        ghost: 'text-[var(--color-texto)] hover:bg-[var(--color-lienzo)]',
        destructive: 'bg-[var(--color-error)] text-white hover:bg-red-800',
      },
      size: { default: 'h-9 px-4 py-2', sm: 'h-8 px-3' },
    },
    defaultVariants: { variant: 'default', size: 'default' },
  },
);

/**
 * Botón del sistema de diseño.
 * @param props atributos de `<button>` más `variant`, `size` y `asChild` (renderiza el hijo, p. ej.
 * un `<Link>` de React Router, con el mismo estilo — patrón `Slot` de Radix)
 */
export function Button({
  className,
  variant,
  size,
  type = 'button',
  asChild = false,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof variantes> & { asChild?: boolean }) {
  if (asChild) {
    return <Slot className={cn(variantes({ variant, size }), className)} {...props} />;
  }
  return <button type={type} className={cn(variantes({ variant, size }), className)} {...props} />;
}
