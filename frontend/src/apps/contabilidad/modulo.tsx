import { Navigate } from 'react-router-dom';
import type { ModuloApp } from '@/nucleo/apps/registro';
import { PaginaCatalogo } from './catalogo/PaginaCatalogo';
import { PaginaConfiguracion } from './configuracion/PaginaConfiguracion';
import { LayoutContabilidad } from './LayoutContabilidad';
import { PaginaAsiento } from './libro-diario/PaginaAsiento';
import { PaginaLibroDiario } from './libro-diario/PaginaLibroDiario';
import { PaginaNuevoAsiento } from './libro-diario/PaginaNuevoAsiento';
import { PaginaMayor } from './mayor/PaginaMayor';
import { PaginaReglas } from './reglas/PaginaReglas';
import { LayoutReportes } from './reportes/LayoutReportes';
import { PaginaBalanza } from './reportes/PaginaBalanza';
import { PaginaDiagnostico } from './reportes/PaginaDiagnostico';
import { PaginaEstadoResultados } from './reportes/PaginaEstadoResultados';
import { PaginaEstadoSituacionFinanciera } from './reportes/PaginaEstadoSituacionFinanciera';
import { PaginaResumenIva } from './reportes/PaginaResumenIva';

/**
 * Módulo de la app Contabilidad: el shell registra sus rutas bajo `/contabilidad/*` si está instalada (ADR-021).
 * Pantallas: Libro Diario (listado, nuevo asiento y detalle, F3), Mayor y Reportes (F4), catálogo,
 * configuración y reglas (F2); el índice sigue redirigiendo al catálogo (el e2e de F2 depende de ello).
 */
export const modulo: ModuloApp = {
  rutas: [
    {
      element: <LayoutContabilidad />,
      children: [
        { index: true, element: <Navigate to="/contabilidad/catalogo" replace /> },
        { path: 'libro-diario', element: <PaginaLibroDiario /> },
        // `nuevo` va antes que `:asientoId` para que no se lea como un id (React Router ya prioriza las estáticas)
        { path: 'libro-diario/nuevo', element: <PaginaNuevoAsiento /> },
        { path: 'libro-diario/:asientoId', element: <PaginaAsiento /> },
        { path: 'mayor', element: <PaginaMayor /> },
        {
          path: 'reportes',
          element: <LayoutReportes />,
          children: [
            { index: true, element: <Navigate to="/contabilidad/reportes/balanza" replace /> },
            { path: 'balanza', element: <PaginaBalanza /> },
            { path: 'situacion-financiera', element: <PaginaEstadoSituacionFinanciera /> },
            { path: 'resultados', element: <PaginaEstadoResultados /> },
            { path: 'iva', element: <PaginaResumenIva /> },
            { path: 'diagnostico', element: <PaginaDiagnostico /> },
          ],
        },
        { path: 'catalogo', element: <PaginaCatalogo /> },
        { path: 'configuracion', element: <PaginaConfiguracion /> },
        { path: 'reglas', element: <PaginaReglas /> },
      ],
    },
  ],
};
