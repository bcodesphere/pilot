import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json, montarShell } from '../pruebas-arnes';

afterEach(() => vi.unstubAllGlobals());

/** Respuesta `GET /empresas/emp-1` con su ETag. */
const empresa = (nombre: string, version: number) =>
  new Response(JSON.stringify({ id: 'emp-1', tipo: 'PERSONAL', nombre, estado: 'ACTIVA', version }), {
    status: 200,
    headers: { 'content-type': 'application/json', ETag: `"${version}"` },
  });

describe('pantalla Espacio de trabajo (ADR-032)', () => {
  // Regla CLAUDE.md §8.3: el PATCH lleva If-Match con el ETag leído; con éxito se recarga el usuario
  it('el PATCH lleva If-Match con el ETag del GET y al guardar recarga /me', async () => {
    let version = 3;
    let nombre = 'Espacio de Ana';
    const { fetchMock } = montarShell({
      ruta: '/configuracion/espacio',
      manejador: (url, init) => {
        if (!url.endsWith('/empresas/emp-1')) return;
        if (init.method === 'PATCH') {
          nombre = JSON.parse(String(init.body)).nombre;
          version += 1;
          return empresa(nombre, version);
        }
        return empresa(nombre, version);
      },
    });
    const campo = await screen.findByLabelText('Nombre del espacio');
    await waitFor(() => expect(campo).toHaveValue('Espacio de Ana'));
    const llamadasMe = () => fetchMock.mock.calls.filter((c) => String(c[0]).endsWith('/me')).length;
    const antes = llamadasMe();

    await userEvent.clear(campo);
    await userEvent.type(campo, '  Taller de Ana  ');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Nombre actualizado')).toBeInTheDocument();
    const patch = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')!;
    const init = patch[1] as RequestInit;
    expect(new Headers(init.headers).get('If-Match')).toBe('"3"');
    // Regla: el nombre se envía sin espacios de los extremos
    expect(JSON.parse(String(init.body))).toEqual({ nombre: 'Taller de Ana' });
    // La cabecera se refresca con el nombre nuevo (recargarUsuario → /me)
    await waitFor(() => expect(llamadasMe()).toBeGreaterThan(antes));
    expect(campo).toHaveValue('Taller de Ana');
  });

  // Regla Zod: un nombre solo con espacios se rechaza en el cliente y no se envía
  it('un nombre solo con espacios no se envía', async () => {
    const { fetchMock } = montarShell({
      ruta: '/configuracion/espacio',
      manejador: (url) => (url.endsWith('/empresas/emp-1') ? empresa('Espacio de Ana', 1) : undefined),
    });
    const campo = await screen.findByLabelText('Nombre del espacio');
    await waitFor(() => expect(campo).toHaveValue('Espacio de Ana'));
    await userEvent.clear(campo);
    await userEvent.type(campo, '   ');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('El nombre es obligatorio')).toBeInTheDocument();
    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')).toBe(
      false,
    );
  });

  // Regla CLAUDE.md §8.3 y §8.4: 412 PLT-016 avisa del conflicto y "Recargar" vuelve a pedir la empresa
  it('un 412 PLT-016 muestra el aviso y "Recargar" pide de nuevo la empresa', async () => {
    let nombre = 'Espacio de Ana';
    let version = 1;
    const { fetchMock } = montarShell({
      ruta: '/configuracion/espacio',
      manejador: (url, init) => {
        if (!url.endsWith('/empresas/emp-1')) return;
        if (init.method === 'PATCH') return json({ codigo: 'PLT-016', status: 412, title: 'Conflicto' }, 412);
        return empresa(nombre, version);
      },
    });
    const campo = await screen.findByLabelText('Nombre del espacio');
    await waitFor(() => expect(campo).toHaveValue('Espacio de Ana'));
    await userEvent.clear(campo);
    await userEvent.type(campo, 'Mi cambio');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByText('Alguien cambió este dato mientras lo editabas')).toBeInTheDocument();

    // Otra persona cambió el dato: al recargar se lee la versión nueva
    nombre = 'Nombre ajeno';
    version = 2;
    const gets = () =>
      fetchMock.mock.calls.filter(
        (c) => String(c[0]).endsWith('/empresas/emp-1') && (c[1] as RequestInit)?.method !== 'PATCH',
      ).length;
    const antes = gets();
    await userEvent.click(screen.getByRole('button', { name: 'Recargar' }));
    await waitFor(() => expect(gets()).toBe(antes + 1));
    await waitFor(() => expect(campo).toHaveValue('Nombre ajeno'));
    expect(screen.queryByText('Alguien cambió este dato mientras lo editabas')).not.toBeInTheDocument();
  });

  // Regla: un 422 se muestra como error del campo
  it('un 422 muestra el error en el campo', async () => {
    montarShell({
      ruta: '/configuracion/espacio',
      manejador: (url, init) => {
        if (!url.endsWith('/empresas/emp-1')) return;
        if (init.method === 'PATCH')
          return json(
            {
              codigo: 'PLT-002',
              status: 422,
              errores: [{ campo: 'nombre', mensaje: 'Nombre no permitido' }],
            },
            422,
          );
        return empresa('Espacio de Ana', 1);
      },
    });
    const campo = await screen.findByLabelText('Nombre del espacio');
    await waitFor(() => expect(campo).toHaveValue('Espacio de Ana'));
    await userEvent.type(campo, ' X');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByText('Nombre no permitido')).toBeInTheDocument();
  });

  // Regla ADR-032: sin NIT, NRC, nombre comercial ni miembros
  it('no ofrece campos de NIT, NRC, nombre comercial ni miembros', async () => {
    montarShell({
      ruta: '/configuracion/espacio',
      manejador: (url) => (url.endsWith('/empresas/emp-1') ? empresa('Espacio de Ana', 1) : undefined),
    });
    await screen.findByLabelText('Nombre del espacio');
    expect(screen.getAllByRole('textbox')).toHaveLength(1);
    expect(screen.queryByText(/NIT|NRC|comercial|miembro/i)).not.toBeInTheDocument();
  });
});
