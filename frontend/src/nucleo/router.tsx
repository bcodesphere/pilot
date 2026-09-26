import { createBrowserRouter, Outlet, type RouteObject } from 'react-router-dom';
import type { Entorno } from './config/entorno';
import { ProveedorAuth } from './auth/ProveedorAuth';
import type { GestorSesion } from './auth/gestorSesion';
import { ManejadorErroresGlobales } from './apps/ManejadorErroresGlobales';
import { RutaApp } from './apps/RutaApp';
import { Layout } from './Layout';
import { PaginaApps } from './PaginaApps';
import { PaginaApiKeys } from './api-keys/PaginaApiKeys';
import { PaginaEspacio } from './espacio/PaginaEspacio';
import { PaginaInicio } from './PaginaInicio';
import { PaginaPerfil } from './perfil/PaginaPerfil';
import { RequiereAdmin } from './RequiereAdmin';
import { ProveedorSesion } from './sesion/ProveedorSesion';

/**
 * Rutas del shell. Las rutas de cada app se resuelven en `/:codigo/*` (`RutaApp`), que monta las de
 * `src/apps/<codigo>/modulo.tsx` solo si la app está instalada (ADR-021). `/auth/callback` la atiende
 * `ProveedorAuth` (canjea el código y vuelve a la ruta de destino).
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
            { path: 'apps', element: <PaginaApps /> },
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
