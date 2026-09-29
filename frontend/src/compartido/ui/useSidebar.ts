import { createContext, use } from 'react';

/** Estado compartido de la barra lateral: si está contraída (según el ancho de la ventana). */
export interface EstadoSidebar {
  contraida: boolean;
}

/** Contexto de `SidebarProvider` (`compartido/ui/sidebar`); separado para no mezclar un hook con componentes en el mismo archivo. */
export const ContextoSidebar = createContext<EstadoSidebar | null>(null);

/** Da acceso al estado de la barra lateral a cualquier descendiente (p. ej. para ocultar texto). */
export function useSidebar(): EstadoSidebar {
  const contexto = use(ContextoSidebar);
  if (!contexto) throw new Error('useSidebar debe usarse dentro de <SidebarProvider>');
  return contexto;
}
