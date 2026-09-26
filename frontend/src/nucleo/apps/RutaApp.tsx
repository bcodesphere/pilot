import { Navigate, useParams, useRoutes } from 'react-router-dom';
import { obtenerModuloApp, type ModuloApp } from './registro';
import { useCatalogoApps } from './useCatalogoApps';

/** Renderiza las rutas internas de una app dentro de `/<codigo>/*`. */
function RutasDeApp({ modulo }: { modulo: ModuloApp }) {
  return useRoutes(modulo.rutas);
}

/**
 * Ruta `/:codigo/*`: monta la app solo si está `INSTALADA` para la empresa activa y tiene módulo en el frontend.
 * Si no, avisa y vuelve al lanzador (ADR-021, ADR-030).
 */
export function RutaApp() {
  const { codigo = '' } = useParams();
  const { apps, cargando } = useCatalogoApps();

  // 1. Hasta conocer el catálogo no se decide nada
  if (cargando) {
    return (
      <p role="status" className="text-sm text-neutral-600">
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
  const modulo = obtenerModuloApp(codigo);
  if (!modulo) {
    return <Navigate to="/" replace state={{ aviso: `${app.nombre} estará disponible pronto.` }} />;
  }

  // 4. Instalada y con módulo: sus rutas se registran aquí
  return <RutasDeApp modulo={modulo} />;
}
