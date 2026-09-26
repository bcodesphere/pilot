import { Navigate } from 'react-router-dom';
import type { ModuloApp } from '@/nucleo/apps/registro';
import { PaginaCatalogo } from './catalogo/PaginaCatalogo';
import { PaginaConfiguracion } from './configuracion/PaginaConfiguracion';
import { LayoutContabilidad } from './LayoutContabilidad';
import { PaginaReglas } from './reglas/PaginaReglas';

/**
 * Módulo de la app Contabilidad: el shell registra sus rutas bajo `/contabilidad/*` si está instalada (ADR-021).
 * En F2 tiene tres pantallas (catálogo, configuración y reglas); el índice redirige al catálogo.
 */
export const modulo: ModuloApp = {
  rutas: [
    {
      element: <LayoutContabilidad />,
      children: [
        { index: true, element: <Navigate to="/contabilidad/catalogo" replace /> },
        { path: 'catalogo', element: <PaginaCatalogo /> },
        { path: 'configuracion', element: <PaginaConfiguracion /> },
        { path: 'reglas', element: <PaginaReglas /> },
      ],
    },
  ],
};
