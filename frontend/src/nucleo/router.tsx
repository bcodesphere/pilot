import { createBrowserRouter } from 'react-router-dom';
import { Layout } from './Layout';
import { PaginaInicio } from './PaginaInicio';

/**
 * Router mínimo del shell. Las rutas de cada app activa se registrarán dinámicamente
 * desde `src/apps/<app>/` cuando exista el lanzador (ADR-021, F1).
 */
export const router = createBrowserRouter([
  { path: '/', element: <Layout />, children: [{ index: true, element: <PaginaInicio /> }] },
]);
