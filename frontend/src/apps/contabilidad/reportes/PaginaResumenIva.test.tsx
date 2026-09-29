import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { resumenIva } from '../compartido/datosPrueba';
import { llamadas, montarContabilidad, problema, respuestaArchivo } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

const RUTA = '/contabilidad/reportes/iva?anio=2026&mes=9';

describe('Resumen de IVA', () => {
  // Regla: pide la ruta con año y mes de la URL y muestra el desglose tal como llega, sin recalcular
  it('pide el resumen con el período de la URL y muestra el desglose sin recalcular', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/reportes/iva?') ? json(resumenIva()) : undefined,
    );
    expect((await screen.findAllByText('$13.00')).length).toBeGreaterThan(0); // IVA débito y diferencia estimada
    // U2 (F4.5): la nota es un texto propio del frontend, sin citar el documento interno del backend
    expect(screen.getByText(/punto de partida para preparar la declaración/i)).toBeInTheDocument();
    const llamadasIva = llamadas(fetchMock, 'GET', '/contabilidad/reportes/iva');
    expect(llamadasIva).toHaveLength(1);
    expect(String(llamadasIva[0]![0])).toContain('anio=2026');
    expect(String(llamadasIva[0]![0])).toContain('mes=9');
  });

  // Regla: 404 PLT-017 (sin configuración contable) muestra el mismo aviso que la pantalla de Configuración
  it('sin configuración contable muestra el aviso de siempre', async () => {
    montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/reportes/iva?') ? problema(404, 'PLT-017') : undefined,
    );
    expect(
      await screen.findByText(
        'Este espacio de trabajo no tiene configuración contable. Contacta al soporte de Pilot.',
      ),
    ).toBeInTheDocument();
  });

  // Estado de error: un 5xx distinto de PLT-017 muestra el aviso genérico, no el de "sin configuración"
  it('muestra un error si el Resumen de IVA no carga', async () => {
    montarContabilidad(RUTA, 'auditor', (url) =>
      url.includes('/contabilidad/reportes/iva?') ? problema(500, 'PLT-500') : undefined,
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el resumen de IVA.');
  });

  // Regla: exportar pide el archivo con el año, el mes y el formato elegido (corrección 1, F4-05: se
  // agrega la comprobación de `formato`, que antes faltaba)
  it('exporta el resumen con el año, el mes y el formato elegido', async () => {
    const { fetchMock } = montarContabilidad(RUTA, 'contador', (url) => {
      if (url.includes('/contabilidad/reportes/iva/exportacion'))
        return respuestaArchivo('iva.pdf', 'application/pdf');
      if (url.includes('/contabilidad/reportes/iva?')) return json(resumenIva());
      return undefined;
    });
    await screen.findAllByText('$13.00');
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'PDF' }));
    const exportaciones = llamadas(fetchMock, 'GET', '/contabilidad/reportes/iva/exportacion');
    expect(exportaciones).toHaveLength(1);
    const url = String(exportaciones[0]![0]);
    expect(url).toContain('formato=pdf');
    expect(url).toContain('anio=2026');
    expect(url).toContain('mes=9');
  });
});
