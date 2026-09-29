import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { EstadoDocumento } from './EstadoDocumento';

describe('EstadoDocumento', () => {
  it('CONTABILIZADO no ofrece enlace de reversión', () => {
    render(<EstadoDocumento estado="CONTABILIZADO" />, { wrapper: MemoryRouter });
    expect(screen.getByText('Contabilizado')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('REVERTIDO con enlaceReversion muestra "Ver la reversión"', () => {
    render(<EstadoDocumento estado="REVERTIDO" enlaceReversion="/contabilidad/asientos/abc" />, {
      wrapper: MemoryRouter,
    });
    expect(screen.getByText('Revertido')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver la reversión' })).toHaveAttribute(
      'href',
      '/contabilidad/asientos/abc',
    );
  });

  it('REVERTIDO sin enlaceReversion no muestra el enlace', () => {
    render(<EstadoDocumento estado="REVERTIDO" />, { wrapper: MemoryRouter });
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
