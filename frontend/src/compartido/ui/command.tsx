import { Command as CommandPrimitive } from 'cmdk';
import { Search } from 'lucide-react';
import type { ComponentProps } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Paleta de comandos (`cmdk` 1.1.1, ADR-043): base del buscador global `Ctrl+K` (pantallas y
 * cuentas). `CommandDialog` usa el `Dialog` de Radix que trae `cmdk` (foco atrapado, Escape,
 * `Portal`), así que no depende del `Dialogo` hecho a mano del resto de la app.
 */
export function Command({ className, ...props }: ComponentProps<typeof CommandPrimitive>) {
  return (
    <CommandPrimitive
      className={cn(
        'flex h-full w-full flex-col overflow-hidden rounded-[var(--radius-panel)] bg-[var(--color-superficie)] text-[var(--color-texto)]',
        className,
      )}
      {...props}
    />
  );
}

/** Diálogo del buscador global; `label` es el nombre accesible (no se ve en pantalla). */
export function CommandDialog({
  label = 'Buscador global',
  children,
  ...props
}: ComponentProps<typeof CommandPrimitive.Dialog>) {
  return (
    <CommandPrimitive.Dialog
      label={label}
      overlayClassName="fixed inset-0 z-50 bg-[var(--color-velo)]"
      contentClassName="fixed top-[15%] left-1/2 z-50 w-full max-w-xl -translate-x-1/2 overflow-hidden rounded-[var(--radius-panel)] border border-[var(--color-borde)] bg-[var(--color-superficie)] shadow-lg"
      {...props}
    >
      <Command className="[&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1.5 [&_[cmdk-group-heading]]:text-xs [&_[cmdk-group-heading]]:font-semibold [&_[cmdk-group-heading]]:text-[var(--color-texto-suave)]">
        {children}
      </Command>
    </CommandPrimitive.Dialog>
  );
}

/** Campo de búsqueda, con el ícono de lupa y el borde inferior que lo separa de la lista. */
export function CommandInput({ className, ...props }: ComponentProps<typeof CommandPrimitive.Input>) {
  return (
    <div className="flex items-center gap-2 border-b border-[var(--color-borde)] px-3" cmdk-input-wrapper="">
      <Search className="size-4 shrink-0 text-[var(--color-texto-suave)]" />
      <CommandPrimitive.Input
        className={cn(
          'flex h-11 w-full rounded-md bg-transparent py-3 text-sm outline-none placeholder:text-[var(--color-texto-suave)] disabled:opacity-50',
          className,
        )}
        {...props}
      />
    </div>
  );
}

export function CommandList({ className, ...props }: ComponentProps<typeof CommandPrimitive.List>) {
  return (
    <CommandPrimitive.List
      className={cn('max-h-80 overflow-x-hidden overflow-y-auto p-1', className)}
      {...props}
    />
  );
}

export function CommandEmpty(props: ComponentProps<typeof CommandPrimitive.Empty>) {
  return (
    <CommandPrimitive.Empty className="py-6 text-center text-sm text-[var(--color-texto-suave)]" {...props} />
  );
}

export function CommandGroup({ className, ...props }: ComponentProps<typeof CommandPrimitive.Group>) {
  return (
    <CommandPrimitive.Group
      className={cn('overflow-hidden p-1 text-[var(--color-texto)]', className)}
      {...props}
    />
  );
}

export function CommandSeparator({ className, ...props }: ComponentProps<typeof CommandPrimitive.Separator>) {
  return (
    <CommandPrimitive.Separator
      className={cn('-mx-1 my-1 h-px bg-[var(--color-borde)]', className)}
      {...props}
    />
  );
}

export function CommandItem({ className, ...props }: ComponentProps<typeof CommandPrimitive.Item>) {
  return (
    <CommandPrimitive.Item
      className={cn(
        'flex cursor-pointer items-center gap-2 rounded-[3px] px-2 py-1.5 text-sm outline-none select-none data-[disabled=true]:pointer-events-none data-[disabled=true]:opacity-50',
        'data-[selected=true]:bg-[var(--color-primario-suave)] data-[selected=true]:text-[var(--color-primario-oscuro)]',
        className,
      )}
      {...props}
    />
  );
}

export function CommandShortcut({ className, ...props }: ComponentProps<'span'>) {
  return (
    <span
      className={cn('ml-auto text-xs tracking-widest text-[var(--color-texto-suave)]', className)}
      {...props}
    />
  );
}
