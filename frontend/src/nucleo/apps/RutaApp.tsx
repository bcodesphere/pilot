import { Suspense, useMemo } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { Skeleton } from '@/compartido/ui/skeleton';
import { obtenerComponenteApp } from './registro';
import { useCatalogoApps } from './useCatalogoApps';

/**
 * Ruta `/:codigo/*`: monta la app solo si está `INSTALADA` para la empresa activa y tiene módulo en
 * el frontend. Si no, avisa y vuelve al lanzador (ADR-021, ADR-030). El módulo se importa bajo
 * demanda con `React.lazy` (corrección 1 de U1: cada app solo entra al bundle al navegar a ella); el
 * componente perezoso se crea una sola vez en `registro.ts`, no aquí en cada render.
 */
export function RutaApp() {
  const { codigo = '' } = useParams();
  const { apps, cargando } = useCatalogoApps();
  // El componente perezoso ya existe desde que cargó registro.ts; esto solo lo busca (estable por `codigo`)
  const ComponenteRutas = useMemo(() => obtenerComponenteApp(codigo), [codigo]);

  // 1. Hasta conocer el catálogo no se decide nada
  if (cargando) {
    return (
      <p role="status" className="text-sm text-[var(--color-texto-suave)]">
        Cargando…
      </p>
    );
  }

  // 2. App ausente del catálogo o no instalada: aviso y de vuelta al lanzador
  const app = apps.find((a) => a.codigo === codigo);
  if (app?.estado !== 'INSTALADA') {
    return (
      <Navigate to="/" replace state={{ aviso: 'Esa app no está instalada en tu espacio de trabajo.' }} />
    );
  }

  // 3. Instalada pero sin módulo en este frontend: no se rompe, se avisa
  if (!ComponenteRutas) {
    return <Navigate to="/" replace state={{ aviso: `${app.nombre} estará disponible pronto.` }} />;
  }

  // 4. Instalada y con módulo: se importa y se registran sus rutas, con un esqueleto mientras carga.
  //    ComponenteRutas no se "crea" aquí: es una referencia estable, ya construida una sola vez en
  //    registro.ts y solo memoizada por `codigo`; el heurístico de la regla no distingue esa búsqueda
  //    estable de una fábrica de componentes nueva en cada render.
  return (
    <Suspense fallback={<Skeleton className="h-40 w-full" />}>
      {/* eslint-disable-next-line react-hooks/static-components */}
      <ComponenteRutas />
    </Suspense>
  );
}
