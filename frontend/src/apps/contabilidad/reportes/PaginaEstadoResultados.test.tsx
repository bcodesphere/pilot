import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { estadoResultados, LEYENDA_ESTADO_GESTION } from '../compartido/datosPrueba';
import { llamadas, montarContabilidad, problema, respuestaArchivo } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

// nivel=3 e incluirCeros=1 (no los valores por defecto): así una propagación incorrecta de los filtros
// a la consulta o a la exportación sí se detecta (corrección 1, F4-05)
const RUTA = '/contabilidad/reportes/resultados?desde=2026-09-01&hasta=2026-09-30&nivel=3&incluirCeros=1';

describe('Estado de Resultados', () => {
  // Regla: sin período no pide el estado
  it('sin período no pide el Estado de Resultados', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/reportes/resultados', 'auditor', () => undefined);
    expect(await screen.findByText('Elige un período para ver el Estado de Resultados.')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'GET', '/contabilidad/estados/resultados')).toHaveLength(0);
  });

  // Regla: pide la ruta con los filtros de la URL y muestra el impuesto sobre la renta aparte (ADR-037)
  it('pide el estado con el período de la URL y muestra la utilidad y el impuesto tal como llegan', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/estados/resultados?') ? json(estadoResultados()) : undefined,
    );
    expect(await screen.findByText(LEYENDA_ESTADO_GESTION)).toBeInTheDocument();
    expect(screen.getAllByText('$100.00').length).toBeGreaterThan(0);
    expect(screen.getByText('Impuesto sobre la renta')).toBeInTheDocument();
    const llamadasEstado = llamadas(fetchMock, 'GET', '/contabilidad/estados/resultados');
    expect(llamadasEstado).toHaveLength(1);
    const urlConsulta = String(llamadasEstado[0]![0]);
    expect(urlConsulta).toContain('desde=2026-09-01');
    expect(urlConsulta).toContain('hasta=2026-09-30');
    expect(urlConsulta).toContain('nivel=3');
    expect(urlConsulta).toContain('incluirCeros=true');
  });

  // Estado de error: la consulta falla y se avisa sin dejar la pantalla en el esqueleto de carga
  it('muestra un error si el Estado de Resultados no carga', async () => {
    montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/estados/resultados?') ? problema(500, 'PLT-500') : undefined,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el Estado de Resultados.');
  });

  // Regla: exportar exige desde/hasta y pide el archivo con el formato y TODOS los filtros vigentes
  // (corrección 1, F4-05: antes solo se comprobaba el formato)
  it('exporta con el formato y los filtros vigentes', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'contador', (url) => {
      if (url.includes('/contabilidad/estados/resultados/exportacion')) {
        return respuestaArchivo('resultados.csv', 'text/csv');
      }
      if (url.includes('/contabilidad/estados/resultados?')) return json(estadoResultados());
      return undefined;
    });
    await screen.findByText(LEYENDA_ESTADO_GESTION);
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'CSV' }));
    const exportaciones = llamadas(fetchMock, 'GET', '/contabilidad/estados/resultados/exportacion');
    expect(exportaciones).toHaveLength(1);
    const url = String(exportaciones[0]![0]);
    expect(url).toContain('formato=csv');
    expect(url).toContain('desde=2026-09-01');
    expect(url).toContain('hasta=2026-09-30');
    expect(url).toContain('nivel=3');
    expect(url).toContain('incluirCeros=true');
  });
});
