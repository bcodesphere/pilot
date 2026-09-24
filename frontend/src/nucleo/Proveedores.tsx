import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/** Cliente de TanStack Query compartido por toda la aplicación. */
const queryClient = new QueryClient();

/**
 * Agrupa los proveedores globales (por ahora solo TanStack Query).
 * @param props.children árbol de la aplicación
 */
export function Proveedores({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
