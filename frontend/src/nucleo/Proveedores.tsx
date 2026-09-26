import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * Agrupa los proveedores globales (TanStack Query).
 * @param props.cliente cliente de consultas compartido por la aplicación
 * @param props.children árbol de la aplicación
 */
export function Proveedores({ cliente, children }: { cliente: QueryClient; children: ReactNode }) {
  return <QueryClientProvider client={cliente}>{children}</QueryClientProvider>;
}
