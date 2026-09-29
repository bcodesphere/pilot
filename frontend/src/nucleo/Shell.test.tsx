import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from './http/errorApi';
import { app, membresia, montarShell } from './pruebas-arnes';

afterEach(() => vi.unstubAllGlobals());

describe('selector de empresa (ADR-032)', () => {
  // Regla ADR-032: con una sola membresía el selector no aparece
  it('está oculto con una sola membresía', async () => {
    montarShell({ apps: [] });
    await screen.findByText('Espacio de Ana');
    expect(screen.queryByLabelText('Empresa')).not.toBeInTheDocument();
  });

  // Regla CLAUDE.md §4.5: al cambiar de empresa se vacía la caché y la siguiente petición lleva el nuevo X-Empresa-Id
  it('con dos membresías es visible; al cambiar vacía la caché y usa la nueva empresa', async () => {
    const { cliente, fetchMock } = montarShell({
      membresias: [membresia('emp-1', 'Espacio de Ana'), membresia('emp-2', 'Otro espacio')],
      apps: [app('contabilidad', 'INSTALADA')],
    });
    const selector = await screen.findByLabelText('Empresa');
    await screen.findAllByText('Contabilidad'); // catálogo de emp-1 ya en caché (título de grupo + enlace de configuración)
    expect(cliente.getQueryCache().getAll().length).toBeGreaterThan(0);
    const limpiar = vi.spyOn(cliente, 'clear');
    const antes = fetchMock.mock.calls.length;

    await userEvent.selectOptions(selector, 'emp-2');

    // La siguiente petición del catálogo lleva la empresa nueva
    expect(limpiar).toHaveBeenCalled();
    await waitFor(() => expect(fetchMock.mock.calls.length).toBeGreaterThan(antes));
    const ultima = fetchMock.mock.calls.filter((c) => String(c[0]).endsWith('/aplicaciones')).at(-1);
    expect(new Headers((ultima as unknown as [string, RequestInit])[1].headers).get('X-Empresa-Id')).toBe(
      'emp-2',
    );
    // El espacio de trabajo activo se muestra en la barra lateral (ADR-043), no en la cabecera
    expect(
      within(screen.getByRole('navigation', { name: 'Principal' })).getByText('Otro espacio', {
        selector: 'span',
      }),
    ).toBeInTheDocument();
  });
});

// Desde ADR-043 (F4.5) el lanzador de apps individuales pasó a Configuración → Apps (PaginaApps);
// Inicio (PaginaInicio) ya no lista apps una por una, solo invita a instalar Contabilidad o muestra
// el estado vacío provisional del tablero. La navegación por app vive en BarraLateral.
describe('Inicio y la barra lateral según las apps instaladas (ADR-021, ADR-030, ADR-043)', () => {
  // Regla: solo con Contabilidad instalada aparece su grupo en la barra lateral, y Reportes con él
  it('la barra lateral solo ofrece Contabilidad y Reportes con la app instalada', async () => {
    montarShell({
      apps: [
        app('contabilidad', 'INSTALADA'),
        app('ventas', 'BLOQUEADA_ENTERPRISE'),
        app('inventario', 'DISPONIBLE'),
      ],
    });
    const barraLateral = await screen.findByRole('navigation', { name: 'Principal' });
    // El grupo se renderiza tras cargar el catálogo (GET /aplicaciones); "Contabilidad" aparece dos veces:
    // el título del grupo de pantallas y el enlace de configuración de la app
    expect((await within(barraLateral).findAllByText('Contabilidad')).length).toBeGreaterThanOrEqual(2);
    expect(within(barraLateral).getByText('Reportes')).toBeInTheDocument();
    // Ventas e Inventario no tienen módulo ni grupo propio en 1.0
    expect(within(barraLateral).queryByText('Ventas')).not.toBeInTheDocument();
    expect(within(barraLateral).queryByText('Inventario')).not.toBeInTheDocument();
  });

  // Regla: sin Contabilidad instalada, Inicio invita a instalarla y la barra lateral no ofrece su grupo
  it('sin apps instaladas, Inicio invita a instalar Contabilidad', async () => {
    montarShell({ apps: [app('contabilidad', 'DISPONIBLE')] });
    expect(await screen.findByText('Instala Contabilidad para empezar')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a Apps' })).toHaveAttribute('href', '/configuracion/apps');
    expect(screen.queryByText('Contabilidad', { selector: 'div' })).not.toBeInTheDocument();
  });

  // Regla ADR-021: agregar una fila en `aplicacion` no requiere cambios en el shell; Apps la muestra como "Disponible pronto"
  it('una app nueva instalada sin módulo aparece como "Disponible pronto" en Apps, sin cambios de código', async () => {
    montarShell({ ruta: '/configuracion/apps', apps: [app('nomina', 'INSTALADA')] });
    expect(await screen.findByText('Nomina')).toBeInTheDocument();
    expect(screen.getByText('Disponible pronto')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Nomina/ })).not.toBeInTheDocument();
  });
});

describe('rutas de apps (ADR-021)', () => {
  // Regla: /<codigo> de una app instalada con módulo muestra su página
  it('/contabilidad con la app instalada muestra su página', async () => {
    montarShell({ ruta: '/contabilidad', apps: [app('contabilidad', 'INSTALADA')] });
    expect(await screen.findByRole('heading', { name: 'Catálogo de cuentas' })).toBeInTheDocument();
  });

  // Regla: una app no instalada avisa y vuelve al lanzador
  it('/contabilidad sin instalar avisa y vuelve al lanzador', async () => {
    const { router } = montarShell({ ruta: '/contabilidad', apps: [app('contabilidad', 'DISPONIBLE')] });
    expect(await screen.findByRole('alert')).toHaveTextContent('no está instalada');
    expect(router.state.location.pathname).toBe('/');
  });

  // Regla CLAUDE.md §8.4: cualquier 403 PLT-004 (app no instalada) avisa y vuelve al lanzador
  it('un 403 PLT-004 en una consulta avisa y vuelve al lanzador', async () => {
    const { router, cliente } = montarShell({
      ruta: '/contabilidad',
      apps: [app('contabilidad', 'INSTALADA')],
    });
    await screen.findByRole('heading', { name: 'Catálogo de cuentas' });
    // Una consulta de la app falla con PLT-004 (p. ej. la app se desinstaló en otra sesión)
    await cliente
      .fetchQuery({
        queryKey: ['contabilidad', 'x'],
        queryFn: () => Promise.reject(new ErrorApi({ status: 403, codigo: 'PLT-004' })),
      })
      .catch(() => undefined);
    // Se busca por texto (no por rol) porque el catálogo de la ruta anterior puede mostrar su propio
    // aviso de error (rol "alert" también) mientras la navegación al lanzador todavía no se completa
    expect(await screen.findByText(/no está instalada/)).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/');
  });
});

describe('errores globales de la API', () => {
  // Regla CLAUDE.md §8.4: 403 PLT-003 (sin membresía en la empresa activa) recarga /me
  it('un 403 PLT-003 vuelve a cargar /me', async () => {
    const { cliente, fetchMock } = montarShell({ apps: [] });
    await screen.findByText('Espacio de Ana');
    const llamadasMe = () => fetchMock.mock.calls.filter((c) => String(c[0]).endsWith('/me')).length;
    expect(llamadasMe()).toBe(1);
    await cliente
      .fetchQuery({
        queryKey: ['x'],
        queryFn: () => Promise.reject(new ErrorApi({ status: 403, codigo: 'PLT-003' })),
      })
      .catch(() => undefined);
    await waitFor(() => expect(llamadasMe()).toBe(2));
  });
});

describe('autenticación', () => {
  // Regla: sin sesión en memoria (incluido un F5) se llama a signinRedirect guardando la ruta de destino
  it('sin sesión inicia el login guardando la ruta de destino', async () => {
    const { gestor } = montarShell({ ruta: '/contabilidad', sinSesion: true });
    await waitFor(() =>
      expect(gestor.signinRedirect).toHaveBeenCalledWith({ state: { ruta: '/contabilidad' } }),
    );
    expect(screen.getByRole('status')).toHaveTextContent('Iniciando sesión');
  });

  // Regla: "Cerrar sesión" hace signoutRedirect hacia el origen de la app
  it('Cerrar sesión llama a signoutRedirect con el origen de la app', async () => {
    const { gestor } = montarShell({ apps: [] });
    await userEvent.click(await screen.findByRole('button', { name: 'Ana' }));
    await userEvent.click(screen.getByRole('menuitem', { name: 'Cerrar sesión' }));
    expect(gestor.signoutRedirect).toHaveBeenCalledWith({ post_logout_redirect_uri: window.location.origin });
  });
});
