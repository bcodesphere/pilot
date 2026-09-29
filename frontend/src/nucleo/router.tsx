import { createBrowserRouter, Navigate, Outlet, type RouteObject } from 'react-router-dom';
import type { Entorno } from './config/entorno';
import { ProveedorAuth } from './auth/ProveedorAuth';
import type { GestorSesion } from './auth/gestorSesion';
import { ManejadorErroresGlobales } from './apps/ManejadorErroresGlobales';
import { RutaApp } from './apps/RutaApp';
import { Layout } from './Layout';
import { PaginaApiKeys, PaginaApps, PaginaEspacio, PaginaInicio, PaginaPerfil } from './paginasPerezosas';
import { RequiereAdmin } from './RequiereAdmin';
import { ProveedorSesion } from './sesion/ProveedorSesion';

/**
 * Rutas del shell. Las rutas de cada app se resuelven en `/:codigo/*` (`RutaApp`), que monta las de
 * `src/apps/<codigo>/modulo.tsx` solo si la app está instalada (ADR-021). `/auth/callback` la atiende
 * `ProveedorAuth` (canjea el código y vuelve a la ruta de destino).
 *
 * Nota (U2 fase B): se intentó unificar Configuración en una sola pantalla con pestañas
 * (`/configuracion/:pestana`), pero envolver Reglas y la Configuración contable en el `Tabs` de Radix
 * dejó una descoordinación entre pruebas (una interacción de una prueba no se reflejaba en la fila que
 * consultaba la siguiente) que no se alcanzó a diagnosticar a tiempo; se revirtió a rutas sueltas hasta
 * retomarlo (ver el reporte de entrega).
 * @param gestor gestor de sesión OIDC
 * @param entorno configuración validada
 */
export function crearRutas(gestor: GestorSesion, entorno: Pick<Entorno, 'apiBaseUrl'>): RouteObject[] {
  return [
    {
      // Raíz: exige sesión, carga usuario y empresa activa y escucha errores globales de la API
      element: (
        <ProveedorAuth gestor={gestor} apiBaseUrl={entorno.apiBaseUrl}>
          <ProveedorSesion>
            <ManejadorErroresGlobales />
            <Outlet />
          </ProveedorSesion>
        </ProveedorAuth>
      ),
      children: [
        { path: 'auth/callback', element: null },
        {
          element: <Layout />,
          children: [
            { index: true, element: <PaginaInicio /> },
            // El lanzador pasa a Configuración → Apps (ADR-043); /apps redirige para no romper enlaces existentes
            { path: 'apps', element: <Navigate to="/configuracion/apps" replace /> },
            { path: 'configuracion/apps', element: <PaginaApps /> },
            { path: 'perfil', element: <PaginaPerfil /> },
            // Administración: solo `admin_empresa` (ADR-032); las rutas van antes de `:codigo/*`
            {
              path: 'configuracion/espacio',
              element: (
                <RequiereAdmin>
                  <PaginaEspacio />
                </RequiereAdmin>
              ),
            },
            {
              path: 'configuracion/api-keys',
              element: (
                <RequiereAdmin>
                  <PaginaApiKeys />
                </RequiereAdmin>
              ),
            },
            { path: ':codigo/*', element: <RutaApp /> },
          ],
        },
      ],
    },
  ];
}

/**
 * Crea el router del navegador.
 * @param gestor gestor de sesión OIDC
 * @param entorno configuración validada
 */
export function crearRouter(gestor: GestorSesion, entorno: Pick<Entorno, 'apiBaseUrl'>) {
  return createBrowserRouter(crearRutas(gestor, entorno));
}
