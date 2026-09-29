import { lazy } from 'react';

/**
 * Páginas del núcleo con carga diferida por ruta (corrección 1 de U1: ningún chunk > 500 KB).
 * Separadas de `router.tsx` (que también exporta `crearRutas`/`crearRouter`, funciones, no
 * componentes) para que este archivo solo exporte componentes y `react-refresh` no lo marque.
 */
export const PaginaInicio = lazy(() => import('./PaginaInicio').then((m) => ({ default: m.PaginaInicio })));
export const PaginaApps = lazy(() => import('./PaginaApps').then((m) => ({ default: m.PaginaApps })));
export const PaginaPerfil = lazy(() =>
  import('./perfil/PaginaPerfil').then((m) => ({ default: m.PaginaPerfil })),
);
export const PaginaEspacio = lazy(() =>
  import('./espacio/PaginaEspacio').then((m) => ({ default: m.PaginaEspacio })),
);
export const PaginaApiKeys = lazy(() =>
  import('./api-keys/PaginaApiKeys').then((m) => ({ default: m.PaginaApiKeys })),
);
