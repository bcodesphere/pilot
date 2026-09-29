import { NavLink, Outlet } from 'react-router-dom';
import { cn } from '@/compartido/lib/utils';
import { usePermisosContabilidad } from '../usePermisosContabilidad';

/** Subsecciones de "Reportes"; el Diagnóstico exige poder escribir (contador o admin_empresa, CLAUDE.md §13). */
const SUBSECCIONES = [
  { ruta: '/contabilidad/reportes/balanza', texto: 'Balanza de Comprobación', soloEscritura: false },
  {
    ruta: '/contabilidad/reportes/situacion-financiera',
    texto: 'Estado de Situación Financiera',
    soloEscritura: false,
  },
  { ruta: '/contabilidad/reportes/resultados', texto: 'Estado de Resultados', soloEscritura: false },
  { ruta: '/contabilidad/reportes/iva', texto: 'Resumen de IVA', soloEscritura: false },
  { ruta: '/contabilidad/reportes/diagnostico', texto: 'Diagnóstico', soloEscritura: true },
] as const;

/**
 * Diseño de "Reportes" (`/contabilidad/reportes/*`): sub-subnavegación con Balanza, los dos estados
 * financieros, el Resumen de IVA y el Diagnóstico (CLAUDE.md §10.4, §10.5). El Diagnóstico solo se
 * muestra a quien puede escribir (`contador`/`admin_empresa`); un `auditor` no ve ni el enlace ni,
 * si entra por la URL directamente, la pantalla (la propia `PaginaDiagnostico` redirige).
 */
export function LayoutReportes() {
  const { puedeEscribir } = usePermisosContabilidad();
  const subsecciones = SUBSECCIONES.filter((s) => !s.soloEscritura || puedeEscribir);

  return (
    <div className="space-y-4">
      <nav
        aria-label="Reportes de Contabilidad"
        className="flex flex-wrap gap-1 border-b border-[var(--color-borde)]"
      >
        {subsecciones.map((s) => (
          <NavLink
            key={s.ruta}
            to={s.ruta}
            className={({ isActive }) =>
              cn(
                '-mb-px border-b-2 px-3 py-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]',
                isActive
                  ? 'border-[var(--color-primario)] text-[var(--color-primario)]'
                  : 'border-transparent text-[var(--color-texto-suave)] hover:text-[var(--color-texto)]',
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
