import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO, regla } from '../compartido/datosPrueba';
import { conEtag, llamadas, montarContabilidad, problema } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Reglas precargadas (CLAUDE.md §12.5); `OTRO` sin cuenta e inactiva (ADR-035). */
const REGLAS = [
  regla('VENTAS_GRAVADAS', 'INGRESO', '51010101'),
  regla('EFECTIVO', 'COBRO', '11010101'),
  regla('TARJETA', 'COBRO', '11010103'),
  regla('OTRO', 'COBRO', null),
];

/** Manejador base: catálogo y reglas; el manejador propio se consulta primero. */
const base =
  (propio?: (url: string, init: RequestInit) => Response | undefined) => (url: string, init: RequestInit) => {
    const r = propio?.(url, init);
    if (r) return r;
    if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
    if (url.includes('/contabilidad/reglas-contabilizacion') && (init.method ?? 'GET') === 'GET') {
      return json(REGLAS);
    }
    return undefined;
  };

/** Fila (elemento de lista) de una regla, por su etiqueta. */
const fila = async (etiqueta: string) => (await screen.findByText(etiqueta)).closest('li')!;

describe('pantalla Reglas', () => {
  // Regla ADR-020: dos grupos con etiquetas en español, la cuenta o "Sin cuenta" y el estado
  it('lista los grupos "Conceptos de ingreso" y "Formas de pago" con su cuenta y estado', async () => {
    montarContabilidad('/contabilidad/reglas', 'contador', base());
    const ingresos = (await screen.findByRole('heading', { name: 'Conceptos de ingreso' })).closest(
      'section',
    )!;
    expect(within(ingresos).getByText('Ventas gravadas')).toBeInTheDocument();
    const pagos = screen.getByRole('heading', { name: 'Formas de pago' }).closest('section')!;
    const etiquetas = within(pagos)
      .getAllByRole('listitem')
      .map((f) => f.textContent ?? '');
    // Orden de presentación: Efectivo, Tarjeta, Otro; la regla sin cuenta lo indica
    expect(etiquetas[0]).toContain('Efectivo');
    expect(etiquetas[1]).toContain('Tarjeta');
    expect(etiquetas[2]).toContain('Otro');
    // Sin cuenta, el campo lo indica con su texto de ayuda
    expect(within(pagos).getAllByRole('combobox')[2]).toHaveAttribute('placeholder', 'Sin cuenta');
  });

  // Regla ADR-035: OTRO inactiva rechaza cierres; se avisa
  it('avisa cuando "Otro" está inactiva', async () => {
    montarContabilidad('/contabilidad/reglas', 'contador', base());
    expect(
      await screen.findByText('Los cierres que usen "Otro" se rechazarán hasta que le asignes una cuenta'),
    ).toBeInTheDocument();
  });

  // Regla ADR-035 y CLAUDE.md §9.3: una regla activa siempre tiene cuenta; no se envía nada sin ella
  it('activar sin cuenta no envía nada y muestra el error', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/reglas', 'contador', base());
    const otro = await fila('Otro');
    await userEvent.click(within(otro).getByRole('switch', { name: 'Regla activa: Otro' }));
    await userEvent.click(within(otro).getByRole('button', { name: 'Guardar Otro' }));
    expect(await within(otro).findByText('Asigna una cuenta antes de activarla')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'PUT', '/contabilidad/reglas-contabilizacion/r-OTRO')).toHaveLength(0);
  });

  // Regla CLAUDE.md §8.3: el PUT lleva If-Match con la versión de la regla entre comillas
  it('el PUT lleva If-Match: "<version>" y el cuerpo completo', async () => {
    let guardada = false;
    const { fetchMock } = montarContabilidad(
      '/contabilidad/reglas',
      'contador',
      base((url, init) => {
        if (url.endsWith('/contabilidad/reglas-contabilizacion/r-OTRO') && init.method === 'PUT') {
          guardada = true;
          return conEtag(regla('OTRO', 'COBRO', '11010103', { version: 3 }), 3);
        }
        if (guardada && url.includes('/contabilidad/reglas-contabilizacion')) {
          return json(
            REGLAS.map((r) => (r.codigo === 'OTRO' ? regla('OTRO', 'COBRO', '11010103', { version: 3 }) : r)),
          );
        }
      }),
    );
    const otro = await fila('Otro');
    const campo = within(otro).getByRole('combobox');
    await userEvent.type(campo, 'bancos');
    await userEvent.keyboard('{Enter}');
    await userEvent.click(within(otro).getByRole('switch', { name: 'Regla activa: Otro' }));
    await userEvent.click(within(otro).getByRole('button', { name: 'Guardar Otro' }));

    await waitFor(() =>
      expect(llamadas(fetchMock, 'PUT', '/contabilidad/reglas-contabilizacion/r-OTRO')).toHaveLength(1),
    );
    const init = llamadas(
      fetchMock,
      'PUT',
      '/contabilidad/reglas-contabilizacion/r-OTRO',
    )[0]![1] as RequestInit;
    expect(new Headers(init.headers).get('If-Match')).toBe('"2"');
    expect(JSON.parse(String(init.body))).toEqual({ cuentaId: 'c-11010103', activa: true });
    // Con éxito se vuelve a leer la lista: el aviso de OTRO desaparece
    await waitFor(() => expect(screen.queryByText(/Los cierres que usen/)).not.toBeInTheDocument());
  });

  // Regla CLAUDE.md §10.2: CON-006 es error del campo de cuenta
  it('un 422 CON-006 se muestra junto al campo de cuenta', async () => {
    montarContabilidad(
      '/contabilidad/reglas',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/reglas-contabilizacion/r-EFECTIVO') && init.method === 'PUT'
          ? problema(422, 'CON-006')
          : undefined,
      ),
    );
    const efectivo = await fila('Efectivo');
    await userEvent.click(within(efectivo).getByRole('switch', { name: 'Regla activa: Efectivo' }));
    await userEvent.click(within(efectivo).getByRole('button', { name: 'Guardar Efectivo' }));
    expect(
      await within(efectivo).findByText(
        'La cuenta no existe en la empresa, está inactiva o no es de detalle.',
      ),
    ).toBeInTheDocument();
  });

  // Regla CLAUDE.md §8.3 y §8.4: 412 avisa y "Recargar" vuelve a leer las reglas
  it('un 412 PLT-016 muestra el aviso y "Recargar" vuelve a pedir las reglas', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/reglas',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/reglas-contabilizacion/r-EFECTIVO') && init.method === 'PUT'
          ? problema(412, 'PLT-016')
          : undefined,
      ),
    );
    const efectivo = await fila('Efectivo');
    await userEvent.click(within(efectivo).getByRole('switch', { name: 'Regla activa: Efectivo' }));
    await userEvent.click(within(efectivo).getByRole('button', { name: 'Guardar Efectivo' }));
    expect(
      await within(efectivo).findByText('Alguien cambió este dato mientras lo editabas'),
    ).toBeInTheDocument();
    const antes = llamadas(fetchMock, 'GET', '/contabilidad/reglas-contabilizacion').length;
    await userEvent.click(within(efectivo).getByRole('button', { name: 'Recargar' }));
    await waitFor(() =>
      expect(llamadas(fetchMock, 'GET', '/contabilidad/reglas-contabilizacion').length).toBeGreaterThan(
        antes,
      ),
    );
  });

  // Regla CLAUDE.md §14.2: el auditor solo lee
  it('el auditor ve las reglas sin controles de edición', async () => {
    montarContabilidad('/contabilidad/reglas', 'auditor', base());
    const otro = await fila('Otro');
    expect(within(otro).getByText('Sin cuenta')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Guardar/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
  });
});
