import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { vi } from 'vitest';
import type { AplicacionCatalogo, Membresia } from '@/api/modelos';
import type { GestorSesion } from './auth/gestorSesion';
import { crearClienteConsultas } from './clienteConsultas';
import { crearRutas } from './router';
import { fijarEmpresaActivaId } from './sesion/almacenEmpresa';

/** Arnés de pruebas del shell: `fetch` y `UserManager` simulados, sin red (solo lo usan los `*.test.tsx`). */

/** Respuesta JSON simulada. */
export const json = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { 'content-type': 'application/json' } });

/** Membresía de ejemplo. */
export const membresia = (
  id: string,
  nombre: string,
  rol: Membresia['rol'] = 'admin_empresa',
): Membresia => ({
  empresaId: id,
  nombreEmpresa: nombre,
  tipoEmpresa: 'PERSONAL',
  rol,
});

/** App del catálogo de ejemplo. */
export const app = (codigo: string, estado: AplicacionCatalogo['estado']): AplicacionCatalogo => ({
  codigo,
  nombre: codigo.charAt(0).toUpperCase() + codigo.slice(1),
  descripcion: null,
  edicion: 'COMUNITARIA',
  estado,
  instaladaEn: null,
});

/** Opciones del arnés. */
export interface OpcionesShell {
  ruta?: string;
  membresias?: Membresia[];
  apps?: AplicacionCatalogo[];
  /** Respuesta especial para `/aplicaciones` (p. ej. un 403 PLT-004). */
  respuestaApps?: () => Response;
  /**
   * Manejador de pruebas de pantallas: se consulta primero; si devuelve una respuesta, se usa
   * (permite simular `PATCH`, `POST`, `DELETE` y rutas nuevas sin tocar el arnés base).
   */
  manejador?: (url: string, init: RequestInit) => Response | Promise<Response> | undefined;
  /** Simula que no hay sesión en memoria (primer acceso o F5). */
  sinSesion?: boolean;
}

/**
 * Monta el shell completo (auth, sesión, rutas) con un gestor de sesión y `fetch` simulados.
 * @returns utilidades para inspeccionar llamadas y la caché
 */
export function montarShell(opciones: OpcionesShell = {}) {
  const membresias = opciones.membresias ?? [membresia('emp-1', 'Espacio de Ana')];
  fijarEmpresaActivaId(null);

  const usuario = { access_token: 'tok-1', expired: false, state: undefined };
  const gestor = {
    getUser: vi.fn().mockResolvedValue(opciones.sinSesion ? null : usuario),
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    signinRedirectCallback: vi.fn().mockResolvedValue(usuario),
    signinSilent: vi.fn().mockResolvedValue(usuario),
    signoutRedirect: vi.fn().mockResolvedValue(undefined),
  };

  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    const propia = opciones.manejador?.(url, init);
    if (propia) return propia;
    if (url.endsWith('/me')) {
      return json({
        id: 'u-1',
        correo: 'a@x.sv',
        nombre: 'Ana',
        telefono: '+50370000000',
        recomendacionesCorreo: false,
        membresias,
      });
    }
    if (url.endsWith('/aplicaciones')) return opciones.respuestaApps?.() ?? json(opciones.apps ?? []);
    return json({}, 404);
  });
  vi.stubGlobal('fetch', fetchMock);

  const cliente: QueryClient = crearClienteConsultas();
  const router = createMemoryRouter(
    crearRutas(gestor as unknown as GestorSesion, { apiBaseUrl: 'http://api.test/api/v1' }),
    {
      initialEntries: [opciones.ruta ?? '/'],
    },
  );
  const vista = render(
    <QueryClientProvider client={cliente}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { gestor, fetchMock, cliente, router, ...vista };
}
