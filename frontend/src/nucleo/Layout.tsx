import { Link, Outlet } from 'react-router-dom';
import { MenuUsuario } from './MenuUsuario';
import { SelectorEmpresa } from './SelectorEmpresa';
import { useSesion } from './sesion/contextoSesion';

/**
 * Layout del shell de Pilot (ADR-021): cabecera con el producto, el espacio de trabajo activo,
 * el selector de empresa (solo con varias membresías), el enlace a "Apps" y el menú de usuario;
 * debajo, el área donde se pintan las páginas y las apps.
 */
export function Layout() {
  const { empresaActiva } = useSesion();
  return (
    <div className="min-h-screen bg-white text-neutral-900">
      {/* 1. Cabecera */}
      <header className="flex flex-wrap items-center gap-4 border-b px-6 py-3">
        <Link to="/" className="font-semibold">
          Pilot
        </Link>
        <span className="text-sm text-neutral-600">{empresaActiva.nombreEmpresa}</span>
        <nav aria-label="Principal" className="ml-4 text-sm">
          <Link to="/apps" className="underline-offset-4 hover:underline">
            Apps
          </Link>
        </nav>
        <div className="ml-auto flex items-center gap-4">
          <SelectorEmpresa />
          <MenuUsuario />
        </div>
      </header>
      {/* 2. Las rutas hijas (lanzador, catálogo y apps) se pintan aquí */}
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
