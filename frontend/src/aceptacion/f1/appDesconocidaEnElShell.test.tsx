import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AplicacionCatalogo } from '@/api/modelos';
import { app, json, montarShell } from '@/nucleo/pruebas-arnes';

/**
 * Aceptación F1, criterio 6 del plan ("agregar una fila en `aplicacion` no requiere cambios en el shell"), lado
 * frontend (docs/plan-de-trabajo.md F1; ADR-021 y ADR-030). El catálogo trae una app que el frontend NO conoce
 * (`prueba_f1`, sin `src/apps/prueba_f1/modulo.tsx`). Se documenta lo que el shell hace con ella en cada estado.
 */

afterEach(() => vi.unstubAllGlobals());

/** Código de una app sin módulo de frontend: nadie la registró en `src/apps/`. */
const CODIGO = 'prueba_f1';

/** App desconocida del catálogo, con nombre y descripción propios. */
const appDesconocida = (estado: AplicacionCatalogo['estado']): AplicacionCatalogo => ({
  ...app(CODIGO, estado),
  nombre: 'App de prueba F1',
  descripcion: 'Fila nueva de la tabla aplicacion',
});

/** Tarjeta (contenedor) de una app en la pantalla Apps, localizada por el encabezado con su nombre. */
const tarjeta = async (nombre: string) =>
  within((await screen.findByRole('heading', { name: nombre })).closest('div')!.parentElement!);

describe('aceptación F1: una app desconocida para el frontend (ADR-021, ADR-030)', () => {
  // Fuente: criterio 6 del plan. DISPONIBLE: aparece en "Apps" con su nombre y descripción y ofrece Instalar
  it('aparece en Apps con su nombre y su descripción y se ofrece instalarla', async () => {
    montarShell({ ruta: '/apps', apps: [appDesconocida('DISPONIBLE')] });

    const t = await tarjeta('App de prueba F1');
    expect(t.getByText('Fila nueva de la tabla aplicacion')).toBeInTheDocument();
    expect(t.getByRole('button', { name: 'Instalar App de prueba F1' })).toBeEnabled();
  });

  // Fuente: PaginaInicio (lanzador). Una app DISPONIBLE no está instalada: no aparece en el lanzador
  it('mientras no se instala, no aparece en el lanzador', async () => {
    montarShell({ ruta: '/', apps: [appDesconocida('DISPONIBLE')] });

    expect(await screen.findByText(/Aún no tienes aplicaciones instaladas/)).toBeInTheDocument();
    expect(screen.queryByText('App de prueba F1')).not.toBeInTheDocument();
  });

  /**
   * Comportamiento documentado (no es un error): instalada pero sin módulo en este frontend, el lanzador la muestra
   * como mosaico DESHABILITADO con "Disponible pronto" (sin enlace), y en Apps ofrece "Abrir". Abrir la ruta
   * `/prueba_f1` no rompe nada: `RutaApp` redirige al lanzador con el aviso "App de prueba F1 estará disponible pronto."
   */
  it('instalada pero sin módulo: mosaico deshabilitado en el lanzador y aviso al abrirla, sin errores', async () => {
    const catalogo = [appDesconocida('DISPONIBLE')];
    const { router } = montarShell({
      ruta: '/apps',
      apps: catalogo,
      manejador: (url, init) => {
        if (url.endsWith(`/aplicaciones/${CODIGO}/instalacion`) && init.method === 'POST') {
          catalogo[0] = { ...catalogo[0]!, estado: 'INSTALADA', instaladaEn: '2026-09-26T16:00:00Z' };
          return json(catalogo[0], 201);
        }
      },
    });

    // 1. Se instala desde Apps: el catálogo se refresca y ofrece "Abrir"
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar App de prueba F1' }));
    expect(await screen.findByText('App de prueba F1 instalada')).toBeInTheDocument();

    // 2. El lanzador la muestra deshabilitada, con "Disponible pronto" y sin enlace
    await router.navigate('/');
    // "Disponible pronto" solo existe en el mosaico del lanzador: esperarlo evita leer la pantalla Apps anterior
    const mosaico = (await screen.findByText('Disponible pronto')).closest(
      '[aria-disabled="true"]',
    ) as HTMLElement;
    expect(mosaico).not.toBeNull();
    expect(within(mosaico).getByText('App de prueba F1')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /App de prueba F1/ })).not.toBeInTheDocument();

    // 3. Abrir su ruta directamente no rompe el shell: vuelve al lanzador con el aviso
    await router.navigate(`/${CODIGO}`);
    expect(await screen.findByRole('alert')).toHaveTextContent('App de prueba F1 estará disponible pronto.');
    expect(router.state.location.pathname).toBe('/');
    expect(screen.getByRole('heading', { name: 'Tus aplicaciones' })).toBeInTheDocument();
  });
});
