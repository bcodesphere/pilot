import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Toaster } from '@/compartido/ui/sonner';
import { TooltipProvider } from '@/compartido/ui/tooltip';

/**
 * Agrupa los proveedores globales (TanStack Query, tooltips y los avisos flotantes de `sonner`;
 * ADR-043): un solo `TooltipProvider` para toda la app evita que cada uso de `Tooltip`
 * (`ValorBloqueado`, la barra lateral contraída…) tenga que declarar el suyo.
 * @param props.cliente cliente de consultas compartido por la aplicación
 * @param props.children árbol de la aplicación
 */
export function Proveedores({ cliente, children }: { cliente: QueryClient; children: ReactNode }) {
  return (
    <QueryClientProvider client={cliente}>
      <TooltipProvider>{children}</TooltipProvider>
      <Toaster />
    </QueryClientProvider>
  );
}
