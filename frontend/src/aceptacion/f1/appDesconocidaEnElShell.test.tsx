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

  // Fuente: PaginaInicio (ADR-043: ya no lista apps, solo invita a instalar Contabilidad)
  it('mientras no se instala, no aparece en Inicio', async () => {
    montarShell({ ruta: '/', apps: [appDesconocida('DISPONIBLE')] });

    expect(await screen.findByText('Instala Contabilidad para empezar')).toBeInTheDocument();
    expect(screen.queryByText('App de prueba F1')).not.toBeInTheDocument();
  });

  /**
   * Comportamiento documentado (no es un error): instalada pero sin módulo en este frontend, Apps la muestra
   * con "Disponible pronto" (sin enlace "Abrir", ADR-043). Abrir la ruta `/prueba_f1` no rompe nada: `RutaApp`
   * redirige a Inicio con el aviso "App de prueba F1 estará disponible pronto."
   */
  it('instalada pero sin módulo: "Disponible pronto" en Apps y aviso al abrirla, sin errores', async () => {
    const catalogo = [appDesconocida('DISPONIBLE')];
    const { router } = montarShell({
      ruta: '/configuracion/apps',
      apps: catalogo,
      manejador: (url, init) => {
        if (url.endsWith(`/aplicaciones/${CODIGO}/instalacion`) && init.method === 'POST') {
          catalogo[0] = { ...catalogo[0]!, estado: 'INSTALADA', instaladaEn: '2026-09-26T16:00:00Z' };
          return json(catalogo[0], 201);
        }
      },
    });

    // 1. Se instala desde Apps: el catálogo se refresca
    await userEvent.click(await screen.findByRole('button', { name: 'Instalar App de prueba F1' }));
    expect(await screen.findByText('App de prueba F1 instalada')).toBeInTheDocument();

    // 2. La misma tarjeta pasa de "Instalar" a "Disponible pronto", sin enlace "Abrir"
    const tarjetaInstalada = await tarjeta('App de prueba F1');
    expect(tarjetaInstalada.getByText('Disponible pronto')).toBeInTheDocument();
    expect(tarjetaInstalada.queryByRole('link', { name: /Abrir/ })).not.toBeInTheDocument();

    // 3. Abrir su ruta directamente no rompe el shell: vuelve a Inicio con el aviso
    await router.navigate(`/${CODIGO}`);
    expect(await screen.findByRole('alert')).toHaveTextContent('App de prueba F1 estará disponible pronto.');
    expect(router.state.location.pathname).toBe('/');
    expect(screen.getByRole('heading', { name: 'Inicio', level: 1 })).toBeInTheDocument();
  });
});
