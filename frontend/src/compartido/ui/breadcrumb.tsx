import { Slot } from '@radix-ui/react-slot';
import { ChevronRight, MoreHorizontal } from 'lucide-react';
import type { ComponentProps, HTMLAttributes, LiHTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Migas de pan (spec F4.5 §7.4): navegación semántica `<nav aria-label="Ruta">` con separadores `/`. */
export function Breadcrumb(props: ComponentProps<'nav'>) {
  return <nav aria-label="Ruta" {...props} />;
}

export function BreadcrumbList({ className, ...props }: HTMLAttributes<HTMLOListElement>) {
  return (
    <ol
      className={cn('flex flex-wrap items-center gap-1.5 text-sm text-[var(--color-texto-suave)]', className)}
      {...props}
    />
  );
}

export function BreadcrumbItem({ className, ...props }: LiHTMLAttributes<HTMLLIElement>) {
  return <li className={cn('inline-flex items-center gap-1.5', className)} {...props} />;
}

/** Enlace a un nivel intermedio; `asChild` permite pasar el `Link` de React Router. */
export function BreadcrumbLink({
  asChild,
  className,
  ...props
}: ComponentProps<'a'> & { asChild?: boolean }) {
  const Comp = asChild ? Slot : 'a';
  return (
    <Comp className={cn('transition-colors hover:text-[var(--color-primario)]', className)} {...props} />
  );
}

/** Último nivel: la pantalla actual, sin enlace (`aria-current="page"`). */
export function BreadcrumbPage({ className, ...props }: HTMLAttributes<HTMLSpanElement>) {
  return (
    <span
      role="link"
      aria-disabled="true"
      aria-current="page"
      className={cn('font-medium text-[var(--color-texto)]', className)}
      {...props}
    />
  );
}

/** Separador visual entre niveles (decorativo: se oculta a los lectores de pantalla). */
export function BreadcrumbSeparator({ className, ...props }: ComponentProps<'li'>) {
  return (
    <li role="presentation" aria-hidden="true" className={cn('[&>svg]:size-3.5', className)} {...props}>
      <ChevronRight />
    </li>
  );
}

/** Marcador de niveles ocultos ("…") cuando la ruta es muy larga. */
export function BreadcrumbEllipsis({ className, ...props }: ComponentProps<'span'>) {
  return (
    <span
      role="presentation"
      aria-hidden="true"
      className={cn('flex h-9 w-9 items-center justify-center', className)}
      {...props}
    >
      <MoreHorizontal className="size-4" />
      <span className="sr-only">Más</span>
    </span>
  );
}
