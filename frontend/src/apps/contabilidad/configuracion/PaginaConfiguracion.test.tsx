import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { conEtag, llamadas, montarContabilidad, problema } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Configuración de ejemplo (ETag "3"): IVA débito 21010101 e IVA crédito 11030101, ambas fijas (ADR-042). */
const CONFIG = {
  modoPrecioDefecto: 'CON_IVA',
  cuentaIvaDebito: { id: 'c-21010101', codigo: '21010101', nombre: 'IVA débito fiscal' },
  cuentaIvaCredito: { id: 'c-11030101', codigo: '11030101', nombre: 'IVA crédito fiscal' },
  version: 3,
};

/** Manejador base: configuración; el manejador propio se consulta primero. */
const base =
  (propio?: (url: string, init: RequestInit) => Response | undefined) => (url: string, init: RequestInit) => {
    const r = propio?.(url, init);
    if (r) return r;
    if (url.endsWith('/contabilidad/configuracion') && (init.method ?? 'GET') === 'GET') {
      return conEtag(CONFIG, 3);
    }
    return undefined;
  };

describe('pestaña Contabilidad de Configuración', () => {
  // Regla ADR-042: las cuentas de IVA son fijas (ValorBloqueado, con candado), nunca un campo editable
  it('muestra el modo de precio editable y las cuentas de IVA bloqueadas con candado', async () => {
    montarContabilidad('/contabilidad/configuracion', 'contador', base());
    expect(await screen.findByLabelText('Precios con IVA incluido')).toBeChecked();
    expect(screen.getByLabelText('Precios más IVA')).not.toBeChecked();
    // Las cuentas de IVA se ven, pero no hay ningún combobox/input para cambiarlas
    expect(screen.getByText('21010101', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('11030101', { exact: false })).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    // Ambas cuentas de IVA (débito y crédito) llevan su propio candado con el mismo motivo
    expect(screen.getAllByRole('button', { name: 'Cuentas fijas del catálogo base' })).toHaveLength(2);
    expect(
      screen.getByText(
        'Los cambios aplican a los asientos que se registren después; quedan en la auditoría.',
      ),
    ).toBeInTheDocument();
  });

  // Regla CLAUDE.md §8.3 y §13: PUT solo con el modo de precio y su If-Match
  it('el PUT lleva If-Match y únicamente el modo de precio', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/configuracion',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/configuracion') && init.method === 'PUT'
          ? conEtag({ ...CONFIG, modoPrecioDefecto: 'SIN_IVA', version: 4 }, 4)
          : undefined,
      ),
    );
    await userEvent.click(await screen.findByLabelText('Precios más IVA'));
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Configuración guardada')).toBeInTheDocument();
    const put = llamadas(fetchMock, 'PUT', '/contabilidad/configuracion')[0]!;
    const init = put[1] as RequestInit;
    expect(new Headers(init.headers).get('If-Match')).toBe('"3"');
    expect(JSON.parse(String(init.body))).toEqual({ modoPrecioDefecto: 'SIN_IVA' });
  });

  // Regla CLAUDE.md §8.3 y §8.4: 412 avisa y "Recargar" vuelve a leer la configuración
  it('un 412 PLT-016 muestra el aviso y "Recargar" vuelve a pedir la configuración', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/configuracion',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/configuracion') && init.method === 'PUT'
          ? problema(412, 'PLT-016')
          : undefined,
      ),
    );
    await screen.findByLabelText('Precios más IVA');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByText('Alguien cambió este dato mientras lo editabas')).toBeInTheDocument();
    const antes = llamadas(fetchMock, 'GET', '/contabilidad/configuracion').length;
    await userEvent.click(screen.getByRole('button', { name: 'Recargar' }));
    await waitFor(() =>
      expect(llamadas(fetchMock, 'GET', '/contabilidad/configuracion').length).toBe(antes + 1),
    );
    await waitFor(() =>
      expect(screen.queryByText('Alguien cambió este dato mientras lo editabas')).not.toBeInTheDocument(),
    );
  });

  // Regla CLAUDE.md §14.2: el auditor solo lee; el modo de precio tampoco tiene controles
  it('el auditor ve los valores sin formulario ni botón Guardar', async () => {
    montarContabilidad('/contabilidad/configuracion', 'auditor', base());
    expect(await screen.findByText('Precios con IVA incluido')).toBeInTheDocument();
    expect(screen.getByText('21010101', { exact: false })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });
});

describe('pestaña Contabilidad de Configuración: sin configuración y errores', () => {
  // Caso: 404 PLT-017 (empresa sin precarga) se distingue del error genérico y no muestra formulario
  it('con 404 PLT-017 muestra el mensaje específico y ningún formulario', async () => {
    montarContabilidad(
      '/contabilidad/configuracion',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/configuracion') && (init.method ?? 'GET') === 'GET'
          ? problema(404, 'PLT-017')
          : undefined,
      ),
    );
    expect(
      await screen.findByText(
        'Este espacio de trabajo no tiene configuración contable. Contacta al soporte de Pilot.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar la configuración contable.')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
  });

  // Caso de control: un 500 conserva el mensaje genérico.
  // Los 5xx se reintentan 2 veces con espera (clienteConsultas), por eso el plazo ampliado
  it('con 500 sigue mostrando el mensaje genérico', { timeout: 15_000 }, async () => {
    montarContabilidad(
      '/contabilidad/configuracion',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/configuracion') && (init.method ?? 'GET') === 'GET'
          ? problema(500, 'PLT-500')
          : undefined,
      ),
    );
    expect(
      await screen.findByText('No pudimos cargar la configuración contable.', undefined, { timeout: 10_000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/no tiene configuración contable/)).not.toBeInTheDocument();
  });
});
