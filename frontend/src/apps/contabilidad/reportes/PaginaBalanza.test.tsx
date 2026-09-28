import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { balanza, CATALOGO } from '../compartido/datosPrueba';
import { llamadas, montarContabilidad, respuestaArchivo } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

// nivel=3 (no el valor por defecto, 5): una mutación que ignore el nivel de la URL y use el valor por
// defecto en su lugar no se detectaría si la URL de prueba ya coincidiera con ese valor (corrección 1, F4-05)
const RUTA = '/contabilidad/reportes/balanza?desde=2026-09-01&hasta=2026-09-30&nivel=3';

describe('Balanza de Comprobación', () => {
  // Regla: sin período no se pide la Balanza
  it('sin período no pide la Balanza', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/reportes/balanza', 'auditor', (url) =>
      url.endsWith('/contabilidad/cuentas') ? json(CATALOGO) : undefined,
    );
    expect(await screen.findByText('Elige un período para ver la Balanza.')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'GET', '/contabilidad/balanza')).toHaveLength(0);
  });

  // Regla: pide la Balanza con los filtros de la URL y muestra los montos tal como llegan, sin recalcular
  it('pide la Balanza con los filtros de la URL y muestra los saldos tal como llegan', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'auditor', (url) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/balanza?')) return json(balanza());
      return undefined;
    });
    expect(await screen.findByText('11010101', { exact: false })).toBeInTheDocument();
    expect(screen.getAllByText('$113.00 D').length).toBeGreaterThan(0);
    const llamadasBalanza = llamadas(fetchMock, 'GET', '/contabilidad/balanza');
    expect(llamadasBalanza).toHaveLength(1);
    const url = String(llamadasBalanza[0]![0]);
    expect(url).toContain('desde=2026-09-01');
    expect(url).toContain('hasta=2026-09-30');
    expect(url).toContain('nivel=3');
  });

  // Regla: expandir/contraer una clase oculta o muestra sus cuentas hijas
  it('contrae y expande las cuentas de una clase', async () => {
    montarContabilidad(RUTA, 'auditor', (url) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/balanza?')) return json(balanza());
      return undefined;
    });
    await screen.findByText('11010101', { exact: false });
    expect(screen.getByText('11010101', { exact: false })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Contraer Activo' }));
    expect(screen.queryByText('11010101', { exact: false })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Expandir Activo' }));
    expect(screen.getByText('11010101', { exact: false })).toBeInTheDocument();
  });

  // Regla: cada cuenta enlaza al Mayor con el mismo período
  it('cada fila enlaza al Mayor con el mismo período', async () => {
    montarContabilidad(RUTA, 'auditor', (url) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/balanza?')) return json(balanza());
      return undefined;
    });
    const enlace = await screen.findByRole('link', { name: /11010101/ });
    expect(enlace).toHaveAttribute('href', expect.stringContaining('desde=2026-09-01&hasta=2026-09-30'));
  });

  // Regla: si no cuadra, muestra la alerta con los totales y un enlace al Diagnóstico
  it('si no cuadra, muestra una alerta con enlace al Diagnóstico', async () => {
    montarContabilidad(RUTA, 'auditor', (url) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/balanza?')) return json(balanza({ cuadra: false }));
      return undefined;
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('La Balanza no cuadra');
    expect(screen.getByRole('link', { name: 'diagnóstico de mayorización' })).toHaveAttribute(
      'href',
      '/contabilidad/reportes/diagnostico',
    );
  });

  // Regla: exportar exige desde/hasta y pide el archivo con el formato y los filtros vigentes
  it('exporta con el formato y los filtros vigentes', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'contador', (url) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/balanza/exportacion'))
        return respuestaArchivo(
          'balanza.xlsx',
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        );
      if (url.includes('/contabilidad/balanza?')) return json(balanza());
      return undefined;
    });
    await screen.findByText('11010101', { exact: false });
    await userEvent.click(screen.getByRole('button', { name: 'Excel' }));
    // Corrección 1 (F4-05): se comprueban TODOS los filtros vigentes (desde, hasta y nivel), no solo el
    // formato y el nivel; con nivel=3 en la URL (no el valor por defecto) una propagación incorrecta sí se detecta.
    const exportaciones = llamadas(fetchMock, 'GET', '/contabilidad/balanza/exportacion');
    expect(exportaciones).toHaveLength(1);
    const url = String(exportaciones[0]![0]);
    expect(url).toContain('formato=xlsx');
    expect(url).toContain('desde=2026-09-01');
    expect(url).toContain('hasta=2026-09-30');
    expect(url).toContain('nivel=3');
  });
});
