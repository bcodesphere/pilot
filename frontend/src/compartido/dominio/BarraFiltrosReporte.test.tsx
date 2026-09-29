import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BarraFiltrosReporte } from './BarraFiltrosReporte';

describe('BarraFiltrosReporte', () => {
  it('llama a onPeriodo al editar Desde/Hasta y con los atajos', async () => {
    const onPeriodo = vi.fn();
    render(
      <BarraFiltrosReporte periodo={{ desde: '2026-09-01', hasta: '2026-09-30' }} onPeriodo={onPeriodo} />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Este mes' }));
    expect(onPeriodo).toHaveBeenCalledWith(expect.objectContaining({ desde: expect.any(String) }));
  });

  it('muestra los filtros extra y el exportar al final', () => {
    render(
      <BarraFiltrosReporte
        periodo={{ desde: '', hasta: '' }}
        onPeriodo={vi.fn()}
        extras={<span>Nivel: Cuenta</span>}
        exportar={<button type="button">Exportar</button>}
      />,
    );
    expect(screen.getByText('Nivel: Cuenta')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Exportar' })).toBeInTheDocument();
  });

  it('muestra el error como alert', () => {
    render(
      <BarraFiltrosReporte
        periodo={{ desde: '2026-09-30', hasta: '2026-09-01' }}
        onPeriodo={vi.fn()}
        error="Desde no puede ser posterior a Hasta"
      />,
    );
    expect(screen.getByRole('alert')).toHaveTextContent('Desde no puede ser posterior a Hasta');
  });
});
