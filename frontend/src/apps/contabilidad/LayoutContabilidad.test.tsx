import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO } from './compartido/datosPrueba';
import { montarContabilidad } from './compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Manejador mínimo: catálogo vacío-ish, configuración y reglas no se piden en estas pruebas. */
const manejador = (url: string) => (url.endsWith('/contabilidad/cuentas') ? json(CATALOGO) : undefined);

describe('layout de Contabilidad', () => {
  // Regla del encargo: el índice de /contabilidad redirige a "catalogo"
  it('/contabilidad redirige al catálogo y marca la sección activa', async () => {
    const { router } = montarContabilidad('/contabilidad', 'contador', manejador);
    expect(await screen.findByRole('heading', { name: 'Catálogo de cuentas' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/contabilidad/catalogo');
    const nav = screen.getByRole('navigation', { name: 'Secciones de Contabilidad' });
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Catálogo' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'Configuración' })).toHaveAttribute(
      'href',
      '/contabilidad/configuracion',
    );
    expect(screen.getByRole('link', { name: 'Reglas' })).toHaveAttribute('href', '/contabilidad/reglas');
    // F3/F4: "Libro Diario", "Mayor" y "Reportes" van antes que "Catálogo" en la subnavegación
    const enlaces = within(nav)
      .getAllByRole('link')
      .map((a) => a.textContent);
    expect(enlaces).toEqual(['Libro Diario', 'Mayor', 'Reportes', 'Catálogo', 'Configuración', 'Reglas']);
    expect(screen.getByRole('link', { name: 'Libro Diario' })).toHaveAttribute(
      'href',
      '/contabilidad/libro-diario',
    );
    expect(screen.getByRole('link', { name: 'Mayor' })).toHaveAttribute('href', '/contabilidad/mayor');
    expect(screen.getByRole('link', { name: 'Reportes' })).toHaveAttribute('href', '/contabilidad/reportes');
  });

  // Regla: la subnavegación lleva a cada pantalla
  it('la subnavegación cambia de pantalla', async () => {
    const { router } = montarContabilidad('/contabilidad', 'contador', manejador);
    await screen.findByRole('heading', { name: 'Catálogo de cuentas' });
    await userEvent.click(screen.getByRole('link', { name: 'Reglas' }));
    expect(router.state.location.pathname).toBe('/contabilidad/reglas');
    expect(await screen.findByRole('heading', { name: 'Reglas de contabilización' })).toBeInTheDocument();
  });
});
