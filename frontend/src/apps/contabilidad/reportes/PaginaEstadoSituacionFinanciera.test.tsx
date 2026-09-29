import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { estadoSituacionFinanciera, LEYENDA_ESTADO_GESTION } from '../compartido/datosPrueba';
import { llamadas, montarContabilidad, respuestaArchivo } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

// nivel=3 e incluirCeros=1 (no los valores por defecto): así una propagación incorrecta de los filtros
// a la consulta o a la exportación sí se detecta (corrección 1, F4-05)
const RUTA = '/contabilidad/reportes/situacion-financiera?fechaCorte=2026-09-30&nivel=3&incluirCeros=1';

describe('Estado de Situación Financiera', () => {
  // Regla: pide la ruta con los filtros de la URL y muestra los montos tal como llegan
  it('pide el estado con la fecha de corte de la URL y muestra los montos sin recalcular', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/estados/situacion-financiera?')
        ? json(estadoSituacionFinanciera())
        : undefined,
    );
    expect(await screen.findByText(LEYENDA_ESTADO_GESTION)).toBeInTheDocument();
    expect(screen.getAllByText('$113.00').length).toBeGreaterThan(0);
    const llamadasEstado = llamadas(fetchMock, 'GET', '/contabilidad/estados/situacion-financiera');
    expect(llamadasEstado).toHaveLength(1);
    const url = String(llamadasEstado[0]![0]);
    expect(url).toContain('fechaCorte=2026-09-30');
    expect(url).toContain('nivel=3');
    expect(url).toContain('incluirCeros=true');
  });

  // Regla: si no cuadra, muestra la diferencia exacta y un enlace al Diagnóstico
  it('si no cuadra, muestra la diferencia y un enlace al Diagnóstico', async () => {
    montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/estados/situacion-financiera?')
        ? json(estadoSituacionFinanciera({ comprobacion: { cuadra: false, diferencia: '5.00' } }))
        : undefined,
    );
    const alerta = await screen.findByRole('alert');
    expect(alerta).toHaveTextContent('Diferencia: $5.00');
    expect(screen.getByRole('link', { name: 'diagnóstico de mayorización' })).toHaveAttribute(
      'href',
      '/contabilidad/reportes/diagnostico',
    );
  });

  // Regla: estado de error si la consulta falla (4xx: no se reintenta, a diferencia de un 5xx)
  it('muestra un error si la consulta falla', async () => {
    montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/estados/situacion-financiera?')
        ? json({ codigo: 'PLT-500' }, 400)
        : undefined,
    );
    expect(
      await screen.findByText('No pudimos cargar el Estado de Situación Financiera.'),
    ).toBeInTheDocument();
  });

  // Regla: exportar exige la fecha de corte (siempre presente aquí) y pide el archivo con el formato elegido
  it('exporta con el formato elegido y la misma fecha de corte', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'contador', (url) => {
      if (url.includes('/contabilidad/estados/situacion-financiera/exportacion')) {
        return respuestaArchivo('situacion-financiera.pdf', 'application/pdf');
      }
      if (url.includes('/contabilidad/estados/situacion-financiera?'))
        return json(estadoSituacionFinanciera());
      return undefined;
    });
    await screen.findByText(LEYENDA_ESTADO_GESTION);
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'PDF' }));
    // Corrección 1 (F4-05): se comprueban TODOS los filtros vigentes (fechaCorte, nivel e incluirCeros)
    const exportaciones = llamadas(
      fetchMock,
      'GET',
      '/contabilidad/estados/situacion-financiera/exportacion',
    );
    expect(exportaciones).toHaveLength(1);
    const url = String(exportaciones[0]![0]);
    expect(url).toContain('formato=pdf');
    expect(url).toContain('fechaCorte=2026-09-30');
    expect(url).toContain('nivel=3');
    expect(url).toContain('incluirCeros=true');
  });
});
