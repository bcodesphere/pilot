import { Slot } from '@radix-ui/react-slot';
import { useEffect, useState, type ComponentProps, type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '@/compartido/lib/utils';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from './tooltip';
import { ContextoSidebar, useSidebar } from './useSidebar';

/**
 * Barra lateral fija con modo contraído (ADR-043, spec F4.5 §7.4): por debajo de `--ancho-quiebre`
 * (1280 px) se contrae a íconos con tooltip; no hay modo "cajón" para teléfono en 1.0 (§8 solo pide
 * Inicio y reportes de solo lectura en 390 px, no la barra lateral completa). `useSidebar` vive en
 * `./useSidebar` (un hook no es un componente: separarlo evita el warning de react-refresh).
 */
const ANCHO_QUIEBRE = 1280;

/**
 * Observa el ancho de la ventana y expone si está por debajo del quiebre de 1280 px (spec §7.4).
 * En SSR/pruebas sin `window` se asume expandida.
 */
function useContraidaPorAncho(): boolean {
  const [contraida, setContraida] = useState(
    () => typeof window !== 'undefined' && window.innerWidth < ANCHO_QUIEBRE,
  );
  useEffect(() => {
    const medios = window.matchMedia(`(max-width: ${ANCHO_QUIEBRE - 1}px)`);
    const actualizar = () => setContraida(medios.matches);
    actualizar();
    medios.addEventListener('change', actualizar);
    return () => medios.removeEventListener('change', actualizar);
  }, []);
  return contraida;
}

/**
 * Envuelve la app (o la barra) y calcula si debe mostrarse contraída. Incluye su propio
 * `TooltipProvider`: así el tooltip de los íconos contraídos funciona aunque nada más arriba en el
 * árbol provea uno (anidar `TooltipProvider` es inofensivo en Radix).
 */
export function SidebarProvider({ children }: { children: ReactNode }) {
  const contraida = useContraidaPorAncho();
  return (
    <ContextoSidebar value={{ contraida }}>
      <TooltipProvider>{children}</TooltipProvider>
    </ContextoSidebar>
  );
}

/** Contenedor de la barra: ancho fijo (expandida) o solo íconos (contraída), colores del tema oscuro del login. */
export function Sidebar({ className, children, ...props }: HTMLAttributes<HTMLElement>) {
  const { contraida } = useSidebar();
  return (
    // <nav>: es la navegación principal del shell (role="navigation" implícito, distinto de <aside>)
    <nav
      data-contraida={contraida}
      className={cn(
        'flex h-screen shrink-0 flex-col border-r border-[var(--color-sidebar-border)] bg-[var(--color-sidebar)] text-[var(--color-sidebar-foreground)] transition-[width]',
        contraida ? 'w-16' : 'w-64',
        className,
      )}
      {...props}
    >
      {children}
    </nav>
  );
}

export function SidebarHeader({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex h-14 items-center gap-2 px-3', className)} {...props} />;
}

export function SidebarContent({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('flex flex-1 flex-col gap-1 overflow-y-auto overflow-x-hidden py-2', className)}
      {...props}
    />
  );
}

export function SidebarFooter({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('mt-auto flex flex-col gap-1 p-2', className)} {...props} />;
}

export function SidebarGroup({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('flex flex-col gap-0.5 px-2 py-1', className)} {...props} />;
}

/** Título del grupo ("Ingresos", "Gastos"…); se oculta cuando la barra está contraída. */
export function SidebarGroupLabel({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  const { contraida } = useSidebar();
  if (contraida) return null;
  return (
    <div
      className={cn(
        'px-2 pt-2 pb-1 text-xs font-semibold tracking-wide text-[var(--color-lateral-texto)] opacity-70',
        className,
      )}
      {...props}
    />
  );
}

export function SidebarMenu({ className, ...props }: HTMLAttributes<HTMLUListElement>) {
  return <ul className={cn('flex flex-col gap-0.5', className)} {...props} />;
}

export function SidebarMenuItem({ className, ...props }: HTMLAttributes<HTMLLIElement>) {
  return <li className={cn('list-none', className)} {...props} />;
}

/**
 * Enlace o botón de la barra: recibe `asChild` para renderizar un `<Link>` de React Router. Cuando
 * la barra está contraída, envuelve el ícono en un `Tooltip` con `etiqueta` como texto (criterio de
 * UX: "por debajo de 1280 px la barra se contrae a íconos con tooltip").
 */
export function SidebarMenuButton({
  className,
  asChild,
  etiqueta,
  activo,
  ...props
}: ComponentProps<'button'> & { asChild?: boolean; etiqueta: string; activo?: boolean }) {
  const { contraida } = useSidebar();
  const Comp = asChild ? Slot : 'button';
  const boton = (
    <Comp
      data-activo={activo}
      aria-current={activo ? 'page' : undefined}
      className={cn(
        'flex h-9 w-full items-center gap-2 rounded-[var(--radius-control)] px-2 text-sm font-medium text-[var(--color-lateral-texto)] transition-colors',
        'hover:bg-[var(--color-sidebar-accent)] hover:text-[var(--color-sidebar-accent-foreground)]',
        // Corrección 4 de U1: el primario no da 3:1 sobre el fondo oscuro de la barra (WCAG 2.2 §1.4.11);
        // dentro de la barra lateral el foco usa --color-foco-lateral en vez de --color-primario
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-foco-lateral)]',
        'data-[activo=true]:bg-[var(--color-sidebar-accent)] data-[activo=true]:text-[var(--color-sidebar-accent-foreground)]',
        contraida && 'justify-center px-0',
        className,
      )}
      {...props}
    />
  );
  if (!contraida) return boton;
  return (
    <Tooltip>
      <TooltipTrigger asChild>{boton}</TooltipTrigger>
      <TooltipContent side="right">{etiqueta}</TooltipContent>
    </Tooltip>
  );
}
