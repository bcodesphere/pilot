import * as TabsPrimitive from '@radix-ui/react-tabs';
import type { ComponentProps } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Contenedor de pestañas (`@radix-ui/react-tabs` 1.1.21, ADR-043). */
export function Tabs({ className, ...props }: ComponentProps<typeof TabsPrimitive.Root>) {
  return <TabsPrimitive.Root className={cn('flex flex-col gap-3', className)} {...props} />;
}

/** Fila de disparadores de las pestañas. */
export function TabsList({ className, ...props }: ComponentProps<typeof TabsPrimitive.List>) {
  return (
    <TabsPrimitive.List
      className={cn(
        'inline-flex h-9 w-fit items-center gap-1 rounded-[var(--radius-control)] border border-[var(--color-borde)] bg-[var(--color-superficie)] p-1',
        className,
      )}
      {...props}
    />
  );
}

/** Disparador de una pestaña: activa con fondo primario suave y texto primario oscuro. */
export function TabsTrigger({ className, ...props }: ComponentProps<typeof TabsPrimitive.Trigger>) {
  return (
    <TabsPrimitive.Trigger
      className={cn(
        'inline-flex h-7 items-center justify-center rounded-[3px] px-3 text-sm font-medium text-[var(--color-texto-suave)] transition-colors',
        'data-[state=active]:bg-[var(--color-primario-suave)] data-[state=active]:text-[var(--color-primario-oscuro)]',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)] disabled:pointer-events-none disabled:opacity-50',
        className,
      )}
      {...props}
    />
  );
}

/** Panel de contenido de una pestaña. */
export function TabsContent({ className, ...props }: ComponentProps<typeof TabsPrimitive.Content>) {
  return <TabsPrimitive.Content className={cn('focus-visible:outline-none', className)} {...props} />;
}
