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
    await screen.findByText('Contabilidad'); // catálogo de emp-1 ya en caché
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
    expect(
      within(screen.getByRole('banner')).getByText('Otro espacio', { selector: 'span' }),
    ).toBeInTheDocument();
  });
});

describe('lanzador de apps (ADR-021, ADR-030)', () => {
  // Regla: solo las apps INSTALADA aparecen como mosaicos
  it('muestra solo las apps instaladas', async () => {
    montarShell({
      apps: [
        app('contabilidad', 'INSTALADA'),
        app('ventas', 'BLOQUEADA_ENTERPRISE'),
        app('inventario', 'DISPONIBLE'),
      ],
    });
    expect(await screen.findByRole('link', { name: /Contabilidad/ })).toHaveAttribute(
      'href',
      '/contabilidad',
    );
    expect(screen.queryByText('Ventas')).not.toBeInTheDocument();
    expect(screen.queryByText('Inventario')).not.toBeInTheDocument();
  });

  // Regla: sin apps instaladas, un estado vacío invita a ir a "Apps"
  it('sin apps instaladas muestra un estado vacío que invita a ir a Apps', async () => {
    montarShell({ apps: [app('contabilidad', 'DISPONIBLE')] });
    expect(await screen.findByText(/Aún no tienes aplicaciones instaladas/)).toBeInTheDocument();
  });

  // Regla ADR-021: agregar una fila en `aplicacion` no requiere cambios en el shell
  it('una app nueva instalada sin módulo aparece como "Disponible pronto" sin cambios de código', async () => {
    montarShell({ apps: [app('nomina', 'INSTALADA')] });
    expect(await screen.findByText('Nomina')).toBeInTheDocument();
    expect(screen.getByText('Disponible pronto')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Nomina/ })).not.toBeInTheDocument();
  });
});

describe('rutas de apps (ADR-021)', () => {
  // Regla: /<codigo> de una app instalada con módulo muestra su página
  it('/contabilidad con la app instalada muestra su página', async () => {
    montarShell({ ruta: '/contabilidad', apps: [app('contabilidad', 'INSTALADA')] });
    expect(await screen.findByRole('heading', { name: 'Contabilidad' })).toBeInTheDocument();
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
    await screen.findByRole('heading', { name: 'Contabilidad' });
    // Una consulta de la app falla con PLT-004 (p. ej. la app se desinstaló en otra sesión)
    await cliente
      .fetchQuery({
        queryKey: ['contabilidad', 'x'],
        queryFn: () => Promise.reject(new ErrorApi({ status: 403, codigo: 'PLT-004' })),
      })
      .catch(() => undefined);
    expect(await screen.findByRole('alert')).toHaveTextContent('no está instalada');
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
