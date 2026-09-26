import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ApiKey } from '@/api/modelos';
import { json, montarShell } from '../pruebas-arnes';

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

/** API key de ejemplo. */
const clave = (n: number, extra: Partial<ApiKey> = {}): ApiKey => ({
  id: `k-${n}`,
  nombre: `Clave ${n}`,
  prefijo: `pk_${n}`,
  alcances: ['integracion:operaciones'],
  expiraEn: null,
  revocadaEn: null,
  ultimoUsoEn: null,
  creadaEn: '2026-09-25T16:30:00Z',
  ...extra,
});

/** Secreto de prueba: se busca en el DOM, las cachés y el almacenamiento. */
const SECRETO = 'pk_9.secreto-de-prueba-xyz';

/** ¿Es una llamada al listado de API keys? */
const esLista = (url: string, init: RequestInit) =>
  /\/api-keys(\?|$)/.test(url) && (!init.method || init.method === 'GET');

describe('pantalla API keys (CLAUDE.md §14.1)', () => {
  // Regla: la tabla muestra los campos y el estado (Vigente, Revocada, Vencida) con fechas en hora de El Salvador
  it('lista con estado y fechas en hora de El Salvador', async () => {
    montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) =>
        esLista(url, init)
          ? json({
              elementos: [
                clave(1),
                clave(2, { revocadaEn: '2026-09-26T00:00:00Z' }),
                clave(3, { expiraEn: '2020-01-01T00:00:00Z' }),
              ],
              siguienteCursor: null,
            })
          : undefined,
    });
    expect(await screen.findByText('Clave 1')).toBeInTheDocument();
    expect(screen.getByText('Vigente')).toBeInTheDocument();
    expect(screen.getByText('Revocada')).toBeInTheDocument();
    expect(screen.getByText('Vencida')).toBeInTheDocument();
    // 16:30 UTC = 10:30 en El Salvador (UTC−6)
    expect(screen.getAllByText(/10:30/).length).toBeGreaterThan(0);
    expect(screen.getByText(/Authorization: Bearer <clave>/)).toBeInTheDocument();
  });

  // Regla: "Cargar más" pide la página siguiente con el cursor recibido
  it('"Cargar más" usa siguienteCursor', async () => {
    const { fetchMock } = montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) => {
        if (!esLista(url, init)) return;
        return url.includes('cursor=CUR2')
          ? json({ elementos: [clave(2)], siguienteCursor: null })
          : json({ elementos: [clave(1)], siguienteCursor: 'CUR2' });
      },
    });
    await screen.findByText('Clave 1');
    await userEvent.click(screen.getByRole('button', { name: 'Cargar más' }));
    expect(await screen.findByText('Clave 2')).toBeInTheDocument();
    expect(screen.getByText('Clave 1')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('cursor=CUR2'))).toBe(true);
    expect(screen.queryByRole('button', { name: 'Cargar más' })).not.toBeInTheDocument();
  });

  // Regla §14.1: el secreto se muestra una sola vez y no queda en el DOM, en las cachés ni en el almacenamiento
  it('crear muestra el secreto una vez y al cerrar no queda en DOM, cachés ni almacenamiento', async () => {
    const usuario = userEvent.setup();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    let creadas = 0;
    const { cliente, fetchMock } = montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) => {
        if (url.endsWith('/api-keys') && init.method === 'POST') {
          creadas += 1;
          return json({ ...clave(9), secreto: SECRETO }, 201);
        }
        if (esLista(url, init)) return json({ elementos: creadas ? [clave(9)] : [], siguienteCursor: null });
      },
    });
    await usuario.click(await screen.findByRole('button', { name: 'Crear API key' }));
    await usuario.type(screen.getByLabelText('Nombre'), 'n8n producción');
    // Regla: el único alcance está marcado y es obligatorio
    expect(screen.getByRole('checkbox', { name: 'integracion:operaciones' })).toBeChecked();
    await usuario.click(screen.getByRole('button', { name: 'Crear' }));

    // Segundo diálogo: secreto visible con advertencia
    const dialogo = await screen.findByRole('dialog', { name: 'API key creada' });
    expect(within(dialogo).getByDisplayValue(SECRETO)).toBeInTheDocument();
    expect(within(dialogo).getByText(/No podrás volver a verlo/)).toBeInTheDocument();
    // Regla: sin vencimiento se envía expiraEn nulo y el alcance elegido
    const post = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'POST')!;
    expect(JSON.parse(String((post[1] as RequestInit).body))).toEqual({
      nombre: 'n8n producción',
      alcances: ['integracion:operaciones'],
      expiraEn: null,
    });

    // Copiar usa el portapapeles
    await usuario.click(within(dialogo).getByRole('button', { name: 'Copiar' }));
    expect(await navigator.clipboard.readText()).toBe(SECRETO);

    // Cerrar: el secreto desaparece de todas partes
    await usuario.click(within(dialogo).getByRole('button', { name: 'Cerrar' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(document.body.innerHTML).not.toContain(SECRETO);
    await waitFor(() => expect(cliente.getMutationCache().getAll()).toHaveLength(0));
    expect(
      JSON.stringify(
        cliente
          .getMutationCache()
          .getAll()
          .map((m) => m.state),
      ),
    ).not.toContain(SECRETO);
    expect(
      JSON.stringify(
        cliente
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data),
      ),
    ).not.toContain(SECRETO);
    expect(setItem.mock.calls.flat().join('|')).not.toContain(SECRETO);
    // La lista se refrescó con la clave nueva
    expect(await screen.findByText('Clave 9')).toBeInTheDocument();
  });

  // Regla: Escape cierra el diálogo y el foco vuelve al botón que lo abrió
  it('Escape cierra el diálogo de creación y devuelve el foco', async () => {
    const usuario = userEvent.setup();
    montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) =>
        esLista(url, init) ? json({ elementos: [], siguienteCursor: null }) : undefined,
    });
    const abrir = await screen.findByRole('button', { name: 'Crear API key' });
    await usuario.click(abrir);
    expect(await screen.findByRole('dialog', { name: 'Crear API key' })).toBeInTheDocument();
    await usuario.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(abrir).toHaveFocus();
  });

  // Regla: un vencimiento pasado se rechaza en el cliente y no se envía
  it('un vencimiento pasado no se envía', async () => {
    const usuario = userEvent.setup();
    const { fetchMock } = montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) =>
        esLista(url, init) ? json({ elementos: [], siguienteCursor: null }) : undefined,
    });
    await usuario.click(await screen.findByRole('button', { name: 'Crear API key' }));
    await usuario.type(screen.getByLabelText('Nombre'), 'Vieja');
    fireEvent.change(screen.getByLabelText(/Vencimiento/), { target: { value: '2020-01-01' } });
    await usuario.click(screen.getByRole('button', { name: 'Crear' }));

    expect(await screen.findByText('El vencimiento debe ser una fecha futura')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'POST')).toBe(
      false,
    );
  });

  // Regla: un vencimiento futuro viaja como instante UTC (fin del día en El Salvador)
  it('un vencimiento futuro se envía como instante UTC', async () => {
    const usuario = userEvent.setup();
    const { fetchMock } = montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) => {
        if (url.endsWith('/api-keys') && init.method === 'POST')
          return json({ ...clave(9), secreto: SECRETO }, 201);
        if (esLista(url, init)) return json({ elementos: [], siguienteCursor: null });
      },
    });
    await usuario.click(await screen.findByRole('button', { name: 'Crear API key' }));
    await usuario.type(screen.getByLabelText('Nombre'), 'Futura');
    fireEvent.change(screen.getByLabelText(/Vencimiento/), { target: { value: '2999-12-31' } });
    await usuario.click(screen.getByRole('button', { name: 'Crear' }));
    await screen.findByRole('dialog', { name: 'API key creada' });
    const post = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'POST')!;
    // 23:59:59 en UTC−6 = 05:59:59 UTC del día siguiente
    expect(JSON.parse(String((post[1] as RequestInit).body)).expiraEn).toBe('3000-01-01T05:59:59.000Z');
  });

  // Regla: revocar exige confirmar en un diálogo accesible y llama a DELETE
  it('revocar pide confirmación, llama a DELETE y refresca', async () => {
    const usuario = userEvent.setup();
    let revocada = false;
    const { fetchMock } = montarShell({
      ruta: '/configuracion/api-keys',
      manejador: (url, init) => {
        if (url.endsWith('/api-keys/k-1') && init.method === 'DELETE') {
          revocada = true;
          return new Response(null, { status: 204 });
        }
        if (esLista(url, init))
          return json({
            elementos: [clave(1, revocada ? { revocadaEn: '2026-09-26T00:00:00Z' } : {})],
            siguienteCursor: null,
          });
      },
    });
    await usuario.click(await screen.findByRole('button', { name: 'Revocar Clave 1' }));
    // Aún no se llamó a DELETE: falta confirmar
    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(
      false,
    );
    const dialogo = await screen.findByRole('dialog', { name: 'Revocar API key' });
    await usuario.click(within(dialogo).getByRole('button', { name: 'Revocar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(await screen.findByText('Revocada')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'DELETE')).toBe(
      true,
    );
  });
});
