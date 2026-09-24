import { Outlet } from 'react-router-dom';

/**
 * Layout base del shell de Pilot (ADR-021). Por ahora solo una cabecera y el área de contenido;
 * el selector de empresa y el lanzador de apps llegan en F1.
 */
export function Layout() {
  return (
    <div className="min-h-screen bg-white text-neutral-900">
      {/* 1. Cabecera vacía: aquí irán el lanzador y el selector de empresa (F1) */}
      <header className="border-b px-6 py-3 font-semibold">Pilot</header>
      {/* 2. Las rutas hijas (páginas de cada app) se pintan aquí */}
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
