import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO, libroMayor, movimientoMayor, saldo } from '../compartido/datosPrueba';
import { llamadas, montarContabilidad, problema, respuestaArchivo } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

const RUTA_BASE = '/contabilidad/mayor?cuentaId=c-11010101&desde=2026-09-01&hasta=2026-09-30';

describe('Mayor', () => {
  // Regla: sin cuenta o sin período elegidos, no se pide el reporte y se invita a completarlos
  it('sin filtros completos no pide el Mayor', async () => {
    const manejador = (url: string) => (url.endsWith('/contabilidad/cuentas') ? json(CATALOGO) : undefined);
    const { fetchMock } = montarContabilidad('/contabilidad/mayor', 'auditor', manejador);
    expect(await screen.findByText('Elige una cuenta y un período para ver el Mayor.')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'GET', '/contabilidad/mayor')).toHaveLength(0);
  });

  // Regla: con los filtros en la URL, pide el Mayor y muestra los montos y saldos tal como llegan
  it('pide el Mayor con los filtros de la URL y muestra los movimientos sin recalcular', async () => {
    const manejador = (url: string) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/mayor?')) return json(libroMayor());
      return undefined;
    };
    const { fetchMock } = montarContabilidad(RUTA_BASE, 'auditor', manejador);

    expect(await screen.findAllByText('11010101', { exact: false })).not.toHaveLength(0);
    // Saldo final y saldo del único movimiento tal como los devuelve el backend
    expect(screen.getAllByText('$113.00').length).toBeGreaterThan(0);
    expect(document.body).toHaveTextContent('$113.00 D');
    expect(screen.getByRole('link', { name: '7/2026' })).toHaveAttribute(
      'href',
      '/contabilidad/libro-diario/a-1',
    );
    const llamadasMayor = llamadas(fetchMock, 'GET', '/contabilidad/mayor');
    expect(llamadasMayor).toHaveLength(1);
    expect(String(llamadasMayor[0]![0])).toContain('cuentaId=c-11010101');
  });

  // Estado de error: la consulta falla y se avisa sin dejar la pantalla en el esqueleto de carga
  it('muestra un error si el Mayor no carga', async () => {
    const manejador = (url: string) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/mayor?')) return problema(500, 'PLT-500');
      return undefined;
    };
    montarContabilidad(RUTA_BASE, 'auditor', manejador);
    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el Libro Mayor.');
  });

  // Regla: un saldo contrario a la naturaleza de la cuenta se anuncia con texto, no solo con color
  it('anuncia un saldo contrario a la naturaleza', async () => {
    const manejador = (url: string) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/mayor?')) {
        return json(
          libroMayor({
            saldoFinal: saldo('50.00', 'ACREEDOR', true),
            movimientos: [movimientoMayor('11010101', '0.00', '163.00', saldo('50.00', 'ACREEDOR', true))],
          }),
        );
      }
      return undefined;
    };
    montarContabilidad(RUTA_BASE, 'auditor', manejador);
    // EtiquetaSaldo (U1) anuncia el saldo contrario con role="alert" y este texto (sin paréntesis)
    expect(await screen.findAllByRole('alert')).not.toHaveLength(0);
    expect(screen.getAllByText('saldo contrario a la naturaleza de la cuenta').length).toBeGreaterThan(0);
  });

  // Regla: exportar pide el archivo con los mismos filtros y en el formato elegido
  it('exporta el Mayor en el formato elegido con los mismos filtros', async () => {
    const manejador = (url: string) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/mayor/exportacion'))
        return respuestaArchivo('mayor.pdf', 'application/pdf');
      if (url.includes('/contabilidad/mayor?')) return json(libroMayor());
      return undefined;
    };
    const { fetchMock } = montarContabilidad(RUTA_BASE, 'contador', manejador);
    await screen.findAllByText('11010101', { exact: false });
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: 'PDF' }));
    // Corrección 1 (F4-05): se comprueban TODOS los filtros de la pantalla (cuentaId, desde y hasta), no
    // solo cuentaId; antes, una exportación con `hasta` distinto de la pantalla no se detectaba.
    const exportaciones = llamadas(fetchMock, 'GET', '/contabilidad/mayor/exportacion');
    expect(exportaciones).toHaveLength(1);
    const url = String(exportaciones[0]![0]);
    expect(url).toContain('formato=pdf');
    expect(url).toContain('cuentaId=c-11010101');
    expect(url).toContain('desde=2026-09-01');
    expect(url).toContain('hasta=2026-09-30');
  });

  // Regla: cambiar el período (aquí, con el atajo "Este año") pide un Mayor nuevo, sin conservar el anterior
  it('cambiar el período pide un Mayor nuevo en vez de conservar el anterior', async () => {
    let vez = 0;
    const manejador = (url: string) => {
      if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
      if (url.includes('/contabilidad/mayor?')) {
        vez += 1;
        return json(libroMayor({ hasta: vez === 1 ? '2026-09-30' : '2026-12-31' }));
      }
      return undefined;
    };
    const { fetchMock } = montarContabilidad(RUTA_BASE, 'auditor', manejador);
    await screen.findAllByText('11010101', { exact: false });
    await userEvent.click(screen.getByRole('button', { name: 'Este año' }));
    await screen.findAllByText('11010101', { exact: false });
    const llamadasMayor = llamadas(fetchMock, 'GET', '/contabilidad/mayor');
    expect(llamadasMayor.length).toBeGreaterThanOrEqual(2);
    expect(String(llamadasMayor.at(-1)![0])).toContain('hasta=2026-12-31');
  });
});
