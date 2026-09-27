import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { json } from '@/nucleo/pruebas-arnes';
import { asiento, resumenAsiento } from '../compartido/datosPrueba';
import { llamadas, montarContabilidad, problema } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Ruta y método de una llamada simulada, sin la cadena de consulta. */
const esGet = (url: string, init: RequestInit, sufijo: string) =>
  url.split('?')[0]!.endsWith(sufijo) && (init.method ?? 'GET') === 'GET';

describe('listado del Libro Diario', () => {
  // §10.1 / F3-04: columnas N.º numero/anio, fecha, concepto, origen, estado y totales con formato de moneda
  it('lista los asientos con número, origen, estado insignia y totales en moneda', async () => {
    montarContabilidad('/contabilidad/libro-diario', 'contador', (url, init) =>
      esGet(url, init, '/contabilidad/asientos')
        ? json({
            elementos: [
              resumenAsiento(1, { totalDebe: '1234.50', totalHaber: '1234.50' }),
              resumenAsiento(2, { estado: 'REVERTIDO', origenTipo: 'N8N' }),
            ],
            siguienteCursor: null,
          })
        : undefined,
    );
    await screen.findByRole('link', { name: 'Asiento 1/2026' });
    const tabla = screen.getByRole('table', { name: 'Asientos del Libro Diario' });
    const filas = within(tabla).getAllByRole('row');
    expect(within(filas[1]!).getByRole('link', { name: 'Asiento 1/2026' })).toHaveAttribute(
      'href',
      '/contabilidad/libro-diario/a-1',
    );
    expect(within(filas[1]!).getAllByText('$1,234.50')).toHaveLength(2);
    expect(within(filas[2]!).getByText('Revertido')).toBeInTheDocument();
    expect(within(filas[2]!).getByText('n8n')).toBeInTheDocument();
  });

  // §13: paginación por cursor; "Cargar más" pide la página siguiente con el cursor devuelto y conserva las anteriores
  it('"Cargar más" pide la página siguiente con el cursor y conserva las filas ya cargadas', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/libro-diario', 'contador', (url, init) => {
      if (!esGet(url, init, '/contabilidad/asientos')) return undefined;
      return url.includes('cursor=c2')
        ? json({ elementos: [resumenAsiento(2)], siguienteCursor: null })
        : json({ elementos: [resumenAsiento(1)], siguienteCursor: 'c2' });
    });
    await screen.findByRole('link', { name: 'Asiento 1/2026' });
    await userEvent.click(screen.getByRole('button', { name: 'Cargar más' }));
    expect(await screen.findByRole('link', { name: 'Asiento 2/2026' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Asiento 1/2026' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cargar más' })).not.toBeInTheDocument();
    expect(fetchMock.mock.calls.some((c) => String(c[0]).includes('cursor=c2'))).toBe(true);
  });

  // Filtros: cada uno viaja como parámetro de consulta y reinicia la lista
  it('los filtros de estado y origen se envían como parámetros', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/libro-diario', 'contador', (url, init) =>
      esGet(url, init, '/contabilidad/asientos') ? json({ elementos: [resumenAsiento(1)] }) : undefined,
    );
    await screen.findByRole('link', { name: 'Asiento 1/2026' });
    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'REVERTIDO');
    await userEvent.selectOptions(screen.getByLabelText('Origen'), 'N8N');
    await userEvent.type(screen.getByLabelText('Desde'), '2026-09-01');
    const ultima = String(fetchMock.mock.calls.at(-1)![0]);
    expect(ultima).toContain('estado=REVERTIDO');
    expect(ultima).toContain('origen=N8N');
    expect(ultima).toContain('desde=2026-09-01');
  });

  // Estado vacío accesible
  it('sin asientos muestra un estado vacío con role="status"', async () => {
    montarContabilidad('/contabilidad/libro-diario', 'contador', (url, init) =>
      esGet(url, init, '/contabilidad/asientos') ? json({ elementos: [] }) : undefined,
    );
    const estado = await screen.findByText('No hay asientos que coincidan con los filtros.');
    expect(estado.closest('[role="status"]')).toBeInTheDocument();
  });

  // §13: el contador ve "Nuevo asiento"; el auditor ve el listado sin ese botón
  it('"Nuevo asiento" solo aparece para el contador', async () => {
    const manejador = (url: string, init: RequestInit) =>
      esGet(url, init, '/contabilidad/asientos') ? json({ elementos: [resumenAsiento(1)] }) : undefined;
    const contador = montarContabilidad('/contabilidad/libro-diario', 'contador', manejador);
    await screen.findByRole('link', { name: 'Asiento 1/2026' });
    expect(screen.getByRole('link', { name: 'Nuevo asiento' })).toHaveAttribute(
      'href',
      '/contabilidad/libro-diario/nuevo',
    );
    contador.unmount();

    montarContabilidad('/contabilidad/libro-diario', 'auditor', manejador);
    await screen.findByRole('link', { name: 'Asiento 1/2026' });
    expect(screen.queryByRole('link', { name: 'Nuevo asiento' })).not.toBeInTheDocument();
  });
});

describe('detalle de un asiento', () => {
  /** Manejador que sirve un asiento por su id. */
  const sirve =
    (a: ReturnType<typeof asiento>, propio?: (url: string, init: RequestInit) => Response | undefined) =>
    (url: string, init: RequestInit) =>
      propio?.(url, init) ?? (esGet(url, init, `/contabilidad/asientos/${a.id}`) ? json(a) : undefined);

  // §11.2: la línea de IVA se marca y se asocia a su línea de origen
  it('muestra cabecera, líneas con la de IVA marcada y totales', async () => {
    montarContabilidad('/contabilidad/libro-diario/a-1', 'contador', sirve(asiento()));
    expect(await screen.findByRole('heading', { name: 'Asiento N.º 7/2026' })).toBeInTheDocument();
    expect(screen.getByText('Venta al contado')).toBeInTheDocument();
    expect(screen.getByText('Precios con IVA incluido')).toBeInTheDocument();
    expect(screen.getByText(/IVA calculado · de la línea 2/)).toBeInTheDocument();
    expect(screen.getByText('Total Debe').nextElementSibling).toHaveTextContent('$113.00');
  });

  // §13: el auditor lee el detalle, pero no puede revertir
  it('el auditor ve el detalle sin el botón "Revertir"', async () => {
    montarContabilidad('/contabilidad/libro-diario/a-1', 'auditor', sirve(asiento()));
    await screen.findByRole('heading', { name: 'Asiento N.º 7/2026' });
    expect(screen.queryByRole('button', { name: 'Revertir' })).not.toBeInTheDocument();
  });

  // CON-008 / CON-009: el botón no aparece en un asiento revertido ni en una reversión
  it('"Revertir" no aparece en un asiento revertido ni en una reversión y ambos enlazan entre sí', async () => {
    const revertido = asiento({ estado: 'REVERTIDO', asientoReversionId: 'a-2' });
    const vista = montarContabilidad('/contabilidad/libro-diario/a-1', 'contador', sirve(revertido));
    await screen.findByRole('heading', { name: 'Asiento N.º 7/2026' });
    expect(screen.queryByRole('button', { name: 'Revertir' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ver la reversión' })).toHaveAttribute(
      'href',
      '/contabilidad/libro-diario/a-2',
    );
    vista.unmount();

    const reversion = asiento({ id: 'a-2', numero: 8, origenTipo: 'REVERSION', asientoRevertidoId: 'a-1' });
    montarContabilidad('/contabilidad/libro-diario/a-2', 'contador', sirve(reversion));
    await screen.findByRole('heading', { name: 'Asiento N.º 8/2026' });
    expect(screen.queryByRole('button', { name: 'Revertir' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'ver el asiento revertido' })).toBeInTheDocument();
  });

  // ADR-036: reversión con fecha elegida (por defecto hoy) e Idempotency-Key; navega a la reversión
  it('revierte con la fecha elegida y Idempotency-Key, y navega a la reversión', async () => {
    const original = asiento({ fecha: '2026-09-01' });
    const reversion = asiento({ id: 'a-2', numero: 8, origenTipo: 'REVERSION', asientoRevertidoId: 'a-1' });
    const { fetchMock, router } = montarContabilidad(
      '/contabilidad/libro-diario/a-1',
      'contador',
      sirve(original, (url, init) => {
        if (url.endsWith('/contabilidad/asientos/a-1/reversion') && init.method === 'POST') {
          return json(reversion, 201);
        }
        return esGet(url, init, '/contabilidad/asientos/a-2') ? json(reversion) : undefined;
      }),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Revertir' }));
    // La fecha por defecto es hoy en El Salvador
    const campo = screen.getByLabelText('Fecha de la reversión');
    expect(campo).toHaveValue(hoyElSalvador());
    await userEvent.clear(campo);
    await userEvent.type(campo, '2026-09-10');
    await userEvent.click(screen.getByRole('button', { name: 'Revertir asiento' }));

    expect(await screen.findByRole('heading', { name: 'Asiento N.º 8/2026' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/contabilidad/libro-diario/a-2');
    const post = llamadas(fetchMock, 'POST', '/contabilidad/asientos/a-1/reversion')[0]![1] as RequestInit;
    expect(JSON.parse(String(post.body))).toEqual({ fecha: '2026-09-10' });
    expect(new Headers(post.headers).get('Idempotency-Key')).toBeTruthy();
  });

  // CON-018 se valida en el diálogo antes de llamar y el backend puede devolver CON-008/CON-009/CON-007/CON-018
  it('no permite una fecha anterior al asiento original (CON-018) y muestra los errores del backend', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/a-1',
      'contador',
      sirve(asiento({ fecha: '2026-09-20' }), (url, init) =>
        url.endsWith('/contabilidad/asientos/a-1/reversion') && init.method === 'POST'
          ? problema(409, 'CON-008')
          : undefined,
      ),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Revertir' }));
    const campo = screen.getByLabelText('Fecha de la reversión');
    await userEvent.clear(campo);
    await userEvent.type(campo, '2026-09-19');
    expect(
      screen.getByText('La fecha de la reversión no puede ser anterior a la del asiento original.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Revertir asiento' })).toBeDisabled();

    // Con una fecha válida, un 409 CON-008 del backend se muestra en el diálogo
    await userEvent.clear(campo);
    await userEvent.type(campo, '2026-09-21');
    await userEvent.click(screen.getByRole('button', { name: 'Revertir asiento' }));
    expect(await screen.findByText('El asiento ya está revertido.')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'POST', '/contabilidad/asientos/a-1/reversion')).toHaveLength(1);
  });

  // Política de idempotencia: el reintento de la misma reversión (409 PLT-008) reutiliza la clave
  it('el reintento de la misma reversión usa la misma Idempotency-Key', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/a-1',
      'contador',
      sirve(asiento(), (url, init) =>
        url.endsWith('/contabilidad/asientos/a-1/reversion') && init.method === 'POST'
          ? problema(409, 'PLT-008')
          : undefined,
      ),
    );
    await userEvent.click(await screen.findByRole('button', { name: 'Revertir' }));
    await userEvent.click(screen.getByRole('button', { name: 'Revertir asiento' }));
    await screen.findByText(/Ya hay una petición igual en proceso/);
    await userEvent.click(screen.getByRole('button', { name: 'Revertir asiento' }));
    await vi.waitFor(() =>
      expect(llamadas(fetchMock, 'POST', '/contabilidad/asientos/a-1/reversion')).toHaveLength(2),
    );
    const claves = llamadas(fetchMock, 'POST', '/contabilidad/asientos/a-1/reversion').map((c) =>
      new Headers((c[1] as RequestInit).headers).get('Idempotency-Key'),
    );
    expect(claves[0]).toBeTruthy();
    expect(claves[1]).toBe(claves[0]);
  });

  // PLT-017: un asiento de otra empresa o inexistente
  it('un asiento inexistente muestra un aviso', async () => {
    montarContabilidad('/contabilidad/libro-diario/zzz', 'contador', (url, init) =>
      esGet(url, init, '/contabilidad/asientos/zzz') ? problema(404, 'PLT-017') : undefined,
    );
    expect(await screen.findByText(/El asiento no existe/)).toBeInTheDocument();
  });
});
