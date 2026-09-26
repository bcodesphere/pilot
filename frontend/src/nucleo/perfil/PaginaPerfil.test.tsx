import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json, montarShell } from '../pruebas-arnes';

afterEach(() => vi.unstubAllGlobals());

describe('pantalla Mi perfil (ADR-028)', () => {
  // Regla ADR-028: los datos de la cuenta son de solo lectura y se editan en Keycloak
  it('muestra nombre, correo y teléfono con el aviso de Keycloak', async () => {
    montarShell({ ruta: '/perfil' });
    expect(await screen.findByText('a@x.sv')).toBeInTheDocument();
    expect(screen.getByText('+50370000000')).toBeInTheDocument();
    expect(screen.getByText(/se editan en tu cuenta de Keycloak/)).toBeInTheDocument();
  });

  // Regla ADR-028: el interruptor envía PATCH /me con el valor y recarga el usuario
  it('el interruptor envía PATCH /me con el valor nuevo', async () => {
    const { fetchMock } = montarShell({
      ruta: '/perfil',
      manejador: (url, init) => (url.endsWith('/me') && init.method === 'PATCH' ? json({}, 200) : undefined),
    });
    const interruptor = await screen.findByRole('switch', {
      name: 'Acepto recibir recomendaciones por correo',
    });
    expect(interruptor).toHaveAttribute('aria-checked', 'false');
    await userEvent.click(interruptor);

    const patch = await waitFor(() => {
      const c = fetchMock.mock.calls.find((x) => (x[1] as RequestInit | undefined)?.method === 'PATCH');
      expect(c).toBeDefined();
      return c!;
    });
    expect(JSON.parse(String((patch[1] as RequestInit).body))).toEqual({ recomendacionesCorreo: true });
  });

  // Regla: la actualización es optimista y, si el servidor falla, vuelve al valor anterior
  it('si el PATCH falla, el interruptor vuelve al valor anterior', async () => {
    // El PATCH queda pendiente hasta que la prueba lo libera, para observar el estado optimista
    let liberar!: () => void;
    const pendiente = new Promise<Response>((resolver) => {
      liberar = () => resolver(json({ codigo: 'PLT-500' }, 500));
    });
    montarShell({
      ruta: '/perfil',
      manejador: (url, init) => (url.endsWith('/me') && init.method === 'PATCH' ? pendiente : undefined),
    });
    const interruptor = await screen.findByRole('switch');
    await userEvent.click(interruptor);
    // Optimista: cambia de inmediato
    expect(interruptor).toHaveAttribute('aria-checked', 'true');
    // Reversión tras el fallo
    liberar();
    await waitFor(() => expect(interruptor).toHaveAttribute('aria-checked', 'false'));
    expect(await screen.findByRole('alert')).toHaveTextContent('Se restauró el valor anterior');
  });
});
