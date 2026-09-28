import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO } from '../compartido/datosPrueba';
import { montarContabilidad } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Manejador mínimo: el catálogo basta para no romper Balanza (que exige desde/hasta antes de pedirlo). */
const manejador = (url: string) => (url.endsWith('/contabilidad/cuentas') ? json(CATALOGO) : undefined);

describe('Reportes', () => {
  // Regla: /contabilidad/reportes redirige a la Balanza y marca la sección activa
  it('redirige a la Balanza y muestra la subnavegación', async () => {
    montarContabilidad('/contabilidad/reportes', 'contador', manejador);
    expect(await screen.findByRole('heading', { name: 'Balanza de Comprobación' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Reportes de Contabilidad' });
    const enlaces = within(nav)
      .getAllByRole('link')
      .map((a) => a.textContent);
    expect(enlaces).toEqual([
      'Balanza de Comprobación',
      'Estado de Situación Financiera',
      'Estado de Resultados',
      'Resumen de IVA',
      'Diagnóstico',
    ]);
  });

  // Regla: el Diagnóstico no aparece en la subnavegación del auditor (solo lectura)
  it('el auditor no ve la sección "Diagnóstico"', async () => {
    montarContabilidad('/contabilidad/reportes', 'auditor', manejador);
    await screen.findByRole('heading', { name: 'Balanza de Comprobación' });
    expect(screen.queryByRole('link', { name: 'Diagnóstico' })).not.toBeInTheDocument();
  });
});
