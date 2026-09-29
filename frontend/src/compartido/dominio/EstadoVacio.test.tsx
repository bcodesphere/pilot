import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { EstadoVacio } from './EstadoVacio';

describe('EstadoVacio', () => {
  it('muestra título y descripción sin acción', () => {
    render(<EstadoVacio titulo="Sin operaciones" descripcion="Registra tu primera venta." />, {
      wrapper: MemoryRouter,
    });
    expect(screen.getByText('Sin operaciones')).toBeInTheDocument();
    expect(screen.getByText('Registra tu primera venta.')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('con accion.href muestra un enlace', () => {
    render(
      <EstadoVacio
        titulo="Instala Contabilidad para empezar"
        descripcion="Activa la app desde Apps."
        accion={{ etiqueta: 'Ir a Apps', href: '/configuracion/apps' }}
      />,
      { wrapper: MemoryRouter },
    );
    expect(screen.getByRole('link', { name: 'Ir a Apps' })).toHaveAttribute('href', '/configuracion/apps');
  });

  it('con accion.onClick muestra un botón que lo invoca', async () => {
    const onClick = vi.fn();
    render(
      <EstadoVacio
        titulo="Sin resultados"
        descripcion="Prueba otro filtro."
        accion={{ etiqueta: 'Limpiar', onClick }}
      />,
      { wrapper: MemoryRouter },
    );
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar' }));
    expect(onClick).toHaveBeenCalledOnce();
  });
});
