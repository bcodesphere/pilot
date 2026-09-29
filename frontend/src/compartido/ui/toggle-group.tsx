import * as ToggleGroupPrimitive from '@radix-ui/react-toggle-group';
import { cva, type VariantProps } from 'class-variance-authority';
import { createContext, use, type ComponentProps } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Variantes visuales de cada opción del grupo (usado por los selectores segmentados de las operaciones guiadas). */
const variantesItem = cva(
  'inline-flex h-8 items-center justify-center gap-1 px-3 text-sm font-medium text-[var(--color-texto-suave)] transition-colors first:rounded-l-[var(--radius-control)] last:rounded-r-[var(--radius-control)]',
  {
    variants: {
      variant: {
        default:
          'border border-[var(--color-borde)] border-l-0 first:border-l bg-[var(--color-superficie)] data-[state=on]:bg-[var(--color-primario-suave)] data-[state=on]:text-[var(--color-primario-oscuro)]',
      },
    },
    defaultVariants: { variant: 'default' },
  },
);

/** Contexto interno: propaga la variante del grupo a cada `ToggleGroupItem`. */
const ContextoGrupo = createContext<VariantProps<typeof variantesItem>>({ variant: 'default' });

/**
 * Selector segmentado de una sola opción (`@radix-ui/react-toggle-group` 1.1.19): forma de cobro,
 * modo de precio, etc. en los formularios de operaciones guiadas.
 */
export function ToggleGroup({
  className,
  variant,
  children,
  ...props
}: ComponentProps<typeof ToggleGroupPrimitive.Root> & VariantProps<typeof variantesItem>) {
  return (
    <ToggleGroupPrimitive.Root
      className={cn('inline-flex items-center rounded-[var(--radius-control)]', className)}
      {...props}
    >
      <ContextoGrupo value={{ variant }}>{children}</ContextoGrupo>
    </ToggleGroupPrimitive.Root>
  );
}

/** Una opción del grupo. */
export function ToggleGroupItem({
  className,
  children,
  ...props
}: ComponentProps<typeof ToggleGroupPrimitive.Item>) {
  const { variant } = use(ContextoGrupo);
  return (
    <ToggleGroupPrimitive.Item className={cn(variantesItem({ variant }), className)} {...props}>
      {children}
    </ToggleGroupPrimitive.Item>
  );
}
