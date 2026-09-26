import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AplicacionCatalogo } from '@/api/modelos';
import { app, json, membresia, montarShell } from './pruebas-arnes';

afterEach(() => vi.unstubAllGlobals());

/** Catálogo de la versión abierta: una app instalable y una Enterprise bloqueada. */
const catalogo = (): AplicacionCatalogo[] => [
  { ...app('contabilidad', 'DISPONIBLE'), descripcion: 'Libro diario y estados financieros' },
  { ...app('ventas', 'BLOQUEADA_ENTERPRISE'), edicion: 'ENTERPRISE' },
];

describe('pantalla Apps (ADR-030)', () => {
  // Regla ADR-030: DISPONIBLE ofrece Instalar; BLOQUEADA_ENTERPRISE, un botón deshabilitado; INSTALADA, "Abrir"
  it('muestra los tres estados como se describe', async () => {
    montarShell({
      ruta: '/apps',
      apps: [
        app('contabilidad', 'INSTALADA'),
        app('inventario', 'DISPONIBLE'),
        app('ventas', 'BLOQUEADA_ENTERPRISE'),
      ],
    });
    const tarjeta = async (nombre: string) =>
      (await screen.findByRole('heading', { name: nombre })).closest('div')!.parentElement!;

    const instalada = within(await tarjeta('Contabilidad'));
    expect(instalada.getByText('Instalada')).toBeInTheDocument();
    expect(instalada.getByRole('link', { name: 'Abrir Contabilidad' })).toHaveAttribute(
      'href',
      '/contabilidad',
    );

    expect(
      within(await tarjeta('Inventario')).getByRole('button', { name: 'Instalar Inventario' }),
    ).toBeEnabled();

    const bloqueada = within(await tarjeta('Ventas'));
    expect(bloqueada.getByText('Enterprise')).toBeInTheDocument();
    // Regla ADR-030: el botón Enterprise no se puede accionar
    expect(bloqueada.getByRole('button', { name: 'Disponible en Enterprise' })).toBeDisabled();
  });

  // Regla F1: instalar (201) refresca el catálogo, avisa y la app aparece en el lanzador
  it('Instalar llama a POST /aplicaciones/contabilidad/instalacion, refresca y la app aparece en el lanzador', async () => {
    const apps = catalogo();
    const { fetchMock, router } = montarShell({
      ruta: '/apps',
      apps,
      manejador: (url, init) => {
        if (url.endsWith('/aplicaciones/contabilidad/instalacion') && init.method === 'POST') {
          apps[0] = { ...apps[0]!, estado: 'INSTALADA', instaladaEn: '2026-09-25T16:00:00Z' };
          return json(apps[0], 201);
        }
      },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Contabilidad' }));

    expect(await screen.findByText('Contabilidad instalada')).toBeInTheDocument();
    const post = fetchMock.mock.calls.find((c) =>
      String(c[0]).endsWith('/aplicaciones/contabilidad/instalacion'),
    );
    expect(post).toBeDefined();
    // El catálogo se volvió a pedir y ahora la pantalla ofrece "Abrir"
    expect(await screen.findByRole('link', { name: 'Abrir Contabilidad' })).toBeInTheDocument();
    // Y el lanzador (inicio) la muestra como mosaico
    await router.navigate('/');
    expect(await screen.findByRole('link', { name: /Contabilidad/ })).toHaveAttribute(
      'href',
      '/contabilidad',
    );
  });

  // Regla ADR-030: un 200 (ya estaba instalada) también es éxito
  it('un 200 (ya instalada) se trata como éxito', async () => {
    const apps = catalogo();
    montarShell({
      ruta: '/apps',
      apps,
      manejador: (url, init) =>
        url.endsWith('/instalacion') && init.method === 'POST' ? json(apps[0], 200) : undefined,
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Contabilidad' }));
    expect(await screen.findByText('Contabilidad instalada')).toBeInTheDocument();
  });

  // Regla CLAUDE.md §8.4: 403 PLT-011 → mensaje de Enterprise
  it('un 403 PLT-011 muestra el mensaje de Enterprise', async () => {
    montarShell({
      ruta: '/apps',
      apps: catalogo(),
      manejador: (url, init) =>
        url.endsWith('/instalacion') && init.method === 'POST'
          ? json({ codigo: 'PLT-011', title: 'Enterprise', status: 403 }, 403)
          : undefined,
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Contabilidad' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('edición Enterprise');
  });

  // Regla: un código desconocido no expone detalle interno; se muestra el mensaje genérico
  it('un error desconocido muestra el mensaje genérico', async () => {
    montarShell({
      ruta: '/apps',
      apps: catalogo(),
      manejador: (url, init) =>
        url.endsWith('/instalacion') && init.method === 'POST' ? json({ codigo: 'PLT-500' }, 500) : undefined,
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar Contabilidad' }));
    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('No pudimos completar la operación'),
    );
  });

  // Regla CLAUDE.md §14.2: solo admin_empresa instala; con otro rol la pantalla es de solo lectura
  it('con rol auditor no se ve Instalar ni los enlaces de administración', async () => {
    montarShell({
      ruta: '/apps',
      membresias: [membresia('emp-1', 'Espacio de Ana', 'auditor')],
      apps: catalogo(),
    });
    await screen.findByRole('heading', { name: 'Contabilidad' });
    expect(screen.queryByRole('button', { name: /Instalar/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Ana' }));
    expect(screen.getByRole('menuitem', { name: 'Mi perfil' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Espacio de trabajo' })).not.toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'API keys' })).not.toBeInTheDocument();
  });

  // Regla CLAUDE.md §14.2: con rol insuficiente la ruta de administración avisa que no hay permiso
  it('/configuracion/api-keys con rol auditor muestra "No tienes permiso"', async () => {
    montarShell({
      ruta: '/configuracion/api-keys',
      membresias: [membresia('emp-1', 'Espacio de Ana', 'auditor')],
    });
    expect(await screen.findByText('No tienes permiso para ver esta página')).toBeInTheDocument();
  });
});
