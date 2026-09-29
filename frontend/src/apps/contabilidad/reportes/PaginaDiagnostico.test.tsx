import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { diagnosticoMayorizacion, diferenciaMayorizacion } from '../compartido/datosPrueba';
import { montarContabilidad } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

const RUTA = '/contabilidad/reportes/diagnostico';

describe('Diagnóstico de mayorización', () => {
  // Regla: el auditor no puede ver el Diagnóstico ni por el enlace ni entrando por la URL directamente
  it('redirige a la Balanza si un auditor entra por la URL', async () => {
    const { router } = montarContabilidad(RUTA, 'auditor', () => undefined);
    await screen.findByRole('heading', { name: 'Balanza de Comprobación' });
    expect(router.state.location.pathname).toBe('/contabilidad/reportes/balanza');
  });

  // Regla: el contador ve el botón y, sin diferencias, un aviso de éxito
  it('el contador verifica la mayorización y ve "Consistente" sin diferencias', async () => {
    montarContabilidad(RUTA, 'contador', (url, init) =>
      url.endsWith('/contabilidad/diagnostico/mayorizacion') && init.method === 'GET'
        ? json(diagnosticoMayorizacion())
        : undefined,
    );
    await screen.findByRole('heading', { name: 'Diagnóstico de mayorización' });
    await userEvent.click(screen.getByRole('button', { name: 'Verificar mayorización' }));
    expect(await screen.findByText(/Consistente/)).toBeInTheDocument();
  });

  // Regla: con diferencias, se listan en una tabla con sus montos tal como llegan
  it('muestra la tabla de diferencias cuando no es consistente', async () => {
    montarContabilidad(RUTA, 'contador', (url, init) =>
      url.endsWith('/contabilidad/diagnostico/mayorizacion') && init.method === 'GET'
        ? json(
            diagnosticoMayorizacion({
              consistente: false,
              diferencias: [diferenciaMayorizacion('11010101')],
            }),
          )
        : undefined,
    );
    await screen.findByRole('heading', { name: 'Diagnóstico de mayorización' });
    await userEvent.click(screen.getByRole('button', { name: 'Verificar mayorización' }));
    const tabla = await screen.findByRole('table', { name: 'Diferencias de mayorización' });
    expect(tabla).toHaveTextContent('11010101');
    expect(tabla).toHaveTextContent('$113.00');
  });
});
