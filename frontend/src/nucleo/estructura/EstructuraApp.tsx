import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import { Skeleton } from '@/compartido/ui/skeleton';
import { SidebarProvider } from '@/compartido/ui/sidebar';
import { BarraLateral } from './BarraLateral';
import { BarraSuperior } from './BarraSuperior';

/** Esqueleto de la pantalla mientras se descarga su fragmento (corrección 1 de U1: rutas perezosas). */
function CargandoPantalla() {
  return (
    <div role="status" aria-label="Cargando" className="space-y-3">
      <span className="sr-only">Cargando…</span>
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-32 w-full" />
    </div>
  );
}

/**
 * Estructura del shell (ADR-043, spec F4.5 §7.4): barra lateral fija + barra superior + el
 * contenido de la ruta activa. Reemplaza la cabecera plana de F0–F4 (`Layout`, que ahora delega
 * aquí) para las pantallas del núcleo y de cada app instalada. El `<Outlet/>` va dentro de
 * `Suspense`: las páginas del núcleo y las de cada app se cargan por ruta (`React.lazy`).
 */
export function EstructuraApp() {
  return (
    <SidebarProvider>
      <div className="flex h-screen overflow-hidden bg-[var(--color-lienzo)]">
        <BarraLateral />
        <div className="flex min-w-0 flex-1 flex-col">
          <BarraSuperior />
          <main className="flex-1 overflow-y-auto p-6">
            <Suspense fallback={<CargandoPantalla />}>
              <Outlet />
            </Suspense>
          </main>
        </div>
      </div>
    </SidebarProvider>
  );
}
