import { NavLink, Outlet } from 'react-router-dom';
import { Alert } from '@/compartido/ui/alert';
import { cn } from '@/compartido/lib/utils';
import { usePermisosContabilidad } from './usePermisosContabilidad';

/** Secciones de la subnavegación de Contabilidad. Rutas absolutas: las relativas se resuelven mal dentro de `/contabilidad/*`. */
const SECCIONES = [
  { ruta: '/contabilidad/libro-diario', texto: 'Libro Diario' },
  { ruta: '/contabilidad/catalogo', texto: 'Catálogo' },
  { ruta: '/contabilidad/configuracion', texto: 'Configuración' },
  { ruta: '/contabilidad/reglas', texto: 'Reglas' },
] as const;

/**
 * Diseño de la app Contabilidad: título, subnavegación accesible y la pantalla activa (`<Outlet />`).
 * Un rol sin acceso a la contabilidad ve un aviso; el backend responde 403 `PLT-010` de todos modos.
 */
export function LayoutContabilidad() {
  const { puedeLeer } = usePermisosContabilidad();
  if (!puedeLeer) return <Alert variant="error">No tienes permiso para ver esta página</Alert>;

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">Contabilidad</h1>
      {/* NavLink marca la sección activa con aria-current="page" */}
      <nav aria-label="Secciones de Contabilidad" className="flex gap-1 border-b border-neutral-200">
        {SECCIONES.map((s) => (
          <NavLink
            key={s.ruta}
            to={s.ruta}
            className={({ isActive }) =>
              cn(
                '-mb-px border-b-2 px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-neutral-900',
                isActive
                  ? 'border-neutral-900 text-neutral-900'
                  : 'border-transparent text-neutral-600 hover:text-neutral-900',
              )
            }
          >
            {s.texto}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </div>
  );
}
