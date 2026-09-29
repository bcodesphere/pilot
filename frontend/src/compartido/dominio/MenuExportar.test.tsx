import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MenuExportar } from './MenuExportar';

describe('MenuExportar', () => {
  it('abre el menú con los tres formatos y exporta el elegido', async () => {
    const onExportar = vi.fn().mockResolvedValue(undefined);
    render(<MenuExportar onExportar={onExportar} />);

    await userEvent.click(screen.getByRole('button', { name: /Exportar/ }));
    expect(screen.getByRole('menuitem', { name: 'PDF' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'Excel' })).toBeInTheDocument();
    expect(screen.getByRole('menuitem', { name: 'CSV' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('menuitem', { name: 'Excel' }));
    expect(onExportar).toHaveBeenCalledWith('xlsx');
  });

  it('deshabilitado impide abrir el menú', async () => {
    const onExportar = vi.fn();
    render(<MenuExportar onExportar={onExportar} deshabilitado />);
    expect(screen.getByRole('button', { name: /Exportar/ })).toBeDisabled();
  });
});
