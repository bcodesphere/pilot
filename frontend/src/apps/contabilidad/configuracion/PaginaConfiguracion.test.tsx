import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO } from '../compartido/datosPrueba';
import { conEtag, llamadas, montarContabilidad, problema } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Configuración de ejemplo (ETag "3"): IVA débito 21010101 e IVA crédito 11030101. */
const CONFIG = {
  modoPrecioDefecto: 'CON_IVA',
  cuentaIvaDebito: { id: 'c-21010101', codigo: '21010101', nombre: 'IVA débito fiscal' },
  cuentaIvaCredito: { id: 'c-11030101', codigo: '11030101', nombre: 'IVA crédito fiscal' },
  version: 3,
};

/** Manejador base: catálogo y configuración; el manejador propio se consulta primero. */
const base =
  (propio?: (url: string, init: RequestInit) => Response | undefined) => (url: string, init: RequestInit) => {
    const r = propio?.(url, init);
    if (r) return r;
    if (url.endsWith('/contabilidad/cuentas')) return json(CATALOGO);
    if (url.endsWith('/contabilidad/configuracion') && (init.method ?? 'GET') === 'GET') {
      return conEtag(CONFIG, 3);
    }
    return undefined;
  };

describe('pantalla Configuración', () => {
  // Regla CLAUDE.md §11.3: el texto de que los cambios solo aplican a asientos futuros y quedan auditados
  it('muestra el modo de precio, las cuentas de IVA y el aviso de que aplica a asientos futuros', async () => {
    montarContabilidad('/contabilidad/configuracion', 'contador', base());
    expect(await screen.findByLabelText('Precios con IVA incluido')).toBeChecked();
    expect(screen.getByLabelText('Precios más IVA')).not.toBeChecked();
    expect(screen.getByLabelText('Cuenta de IVA débito fiscal')).toHaveValue('21010101 — IVA débito fiscal');
    expect(screen.getByLabelText('Cuenta de IVA crédito fiscal')).toHaveValue(
      '11030101 — IVA crédito fiscal',
    );
    expect(
      screen.getByText(
        'Los cambios aplican a los asientos que se registren después; quedan en la auditoría.',
      ),
    ).toBeInTheDocument();
  });

  // Regla CLAUDE.md §8.3 y §13: PUT completo con If-Match del ETag leído
  it('el PUT lleva If-Match y el cuerpo completo', async () => {
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
    expect(JSON.parse(String(init.body))).toEqual({
      modoPrecioDefecto: 'SIN_IVA',
      cuentaIvaDebitoId: 'c-21010101',
      cuentaIvaCreditoId: 'c-11030101',
    });
  });

  // Regla CON-006: solo cuentas de detalle y activas; funciona con el teclado
  it('el selector solo ofrece cuentas de detalle y activas y funciona con el teclado', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/configuracion',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/configuracion') && init.method === 'PUT'
          ? conEtag({ ...CONFIG, version: 4 }, 4)
          : undefined,
      ),
    );
    const campo = await screen.findByLabelText('Cuenta de IVA débito fiscal');
    await userEvent.click(campo);
    const lista = await screen.findByRole('listbox');
    const opciones = within(lista)
      .getAllByRole('option')
      .map((o) => o.textContent);
    // Cuentas de detalle activas: sí. Padres (Activo) e inactiva (Caja chica antigua): no
    expect(opciones).toContain('11010101 — Caja general');
    expect(opciones.some((t) => t?.includes('Activo corriente'))).toBe(false);
    expect(opciones.some((t) => t?.includes('Caja chica antigua'))).toBe(false);
    expect(opciones).toHaveLength(5);

    // Teclado: escribir filtra, Enter elige la opción activa
    await userEvent.clear(campo);
    await userEvent.type(campo, 'bancos');
    expect(campo).toHaveAttribute('aria-activedescendant');
    await userEvent.keyboard('{Enter}');
    expect(campo).toHaveValue('11010103 — Bancos');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    await screen.findByText('Configuración guardada');
    const put = llamadas(fetchMock, 'PUT', '/contabilidad/configuracion')[0]!;
    expect(JSON.parse(String((put[1] as RequestInit).body)).cuentaIvaDebitoId).toBe('c-11010103');
  });

  // Regla de teclado: ↓ y ↑ mueven la opción activa; Escape cierra la lista sin elegir
  it('las flechas mueven la opción activa y Escape cierra la lista', async () => {
    montarContabilidad('/contabilidad/configuracion', 'contador', base());
    const campo = await screen.findByLabelText('Cuenta de IVA débito fiscal');
    await userEvent.click(campo);
    const opciones = within(await screen.findByRole('listbox')).getAllByRole('option');
    expect(campo).toHaveAttribute('aria-activedescendant', opciones[0]!.id);
    await userEvent.keyboard('{ArrowDown}');
    expect(campo).toHaveAttribute('aria-activedescendant', opciones[1]!.id);
    await userEvent.keyboard('{ArrowUp}');
    expect(campo).toHaveAttribute('aria-activedescendant', opciones[0]!.id);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(campo).toHaveValue('21010101 — IVA débito fiscal');
  });

  // Regla CLAUDE.md §10.2: CON-006 se muestra como error del campo de cuenta
  it('un 422 CON-006 se muestra en el campo de cuenta', async () => {
    montarContabilidad(
      '/contabilidad/configuracion',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/configuracion') && init.method === 'PUT'
          ? problema(422, 'CON-006', { errores: [{ campo: 'cuentaIvaCreditoId', mensaje: 'x' }] })
          : undefined,
      ),
    );
    await screen.findByLabelText('Precios más IVA');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    const mensaje = await screen.findByText(
      'La cuenta no existe en la empresa, está inactiva o no es de detalle.',
    );
    expect(mensaje).toHaveAttribute('id', 'config-iva-credito-error');
    expect(screen.getByLabelText('Cuenta de IVA crédito fiscal')).toHaveAttribute('aria-invalid', 'true');
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

  // Regla CLAUDE.md §14.2: el auditor solo lee
  it('el auditor ve los valores sin formulario ni botón Guardar', async () => {
    montarContabilidad('/contabilidad/configuracion', 'auditor', base());
    expect(await screen.findByText('Precios con IVA incluido')).toBeInTheDocument();
    expect(screen.getByText('21010101 — IVA débito fiscal')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
  });
});
