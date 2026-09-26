import { Link, useLocation } from 'react-router-dom';
import { cn } from '@/compartido/lib/utils';
import { obtenerModuloApp } from '@/nucleo/apps/registro';
import { useCatalogoApps } from '@/nucleo/apps/useCatalogoApps';

/**
 * Página de inicio = lanzador de apps (ADR-021). Muestra solo las apps `INSTALADA`, leídas de
 * `GET /aplicaciones`; una app instalada sin módulo en este frontend sale deshabilitada
 * ("Disponible pronto"), así que agregar una fila en `aplicacion` no exige cambios en el shell.
 */
export function PaginaInicio() {
  const { apps, cargando, error } = useCatalogoApps();
  // Aviso que dejan otras rutas al redirigir aquí (app no instalada, PLT-004)
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;
  const instaladas = apps.filter((a) => a.estado === 'INSTALADA');

  return (
    <section aria-labelledby="titulo-lanzador">
      <h1 id="titulo-lanzador" className="text-2xl font-bold">
        Tus aplicaciones
      </h1>

      {/* 1. Aviso de redirección */}
      {aviso && (
        <p role="alert" className="mt-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm">
          {aviso}
        </p>
      )}

      {/* 2. Estados de carga, error y vacío */}
      {cargando && (
        <p role="status" className="mt-4 text-sm text-neutral-600">
          Cargando aplicaciones…
        </p>
      )}
      {error && (
        <p role="alert" className="mt-4 text-sm text-red-700">
          No pudimos cargar las aplicaciones.
        </p>
      )}
      {!cargando && !error && instaladas.length === 0 && (
        <p className="mt-4 text-neutral-600">
          Aún no tienes aplicaciones instaladas. Ve a{' '}
          <Link to="/apps" className="underline">
            Apps
          </Link>{' '}
          para instalar una.
        </p>
      )}

      {/* 3. Mosaicos de las apps instaladas */}
      <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {instaladas.map((app) => {
          const disponible = obtenerModuloApp(app.codigo) !== undefined;
          const clases = 'block rounded-lg border p-4 h-full';
          return (
            <li key={app.codigo}>
              {disponible ? (
                <Link
                  to={`/${app.codigo}`}
                  className={cn(clases, 'hover:bg-neutral-50 focus-visible:outline-2')}
                >
                  <span className="font-semibold">{app.nombre}</span>
                  {app.descripcion && (
                    <span className="mt-1 block text-sm text-neutral-600">{app.descripcion}</span>
                  )}
                </Link>
              ) : (
                // Sin módulo en el frontend: mosaico deshabilitado, no navegable
                <div
                  aria-disabled="true"
                  className={cn(clases, 'cursor-not-allowed bg-neutral-50 text-neutral-500')}
                >
                  <span className="font-semibold">{app.nombre}</span>
                  <span className="mt-1 block text-sm">Disponible pronto</span>
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
