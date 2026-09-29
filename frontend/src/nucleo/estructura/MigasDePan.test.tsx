import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { MigasDePan } from './MigasDePan';

describe('MigasDePan', () => {
  it('muestra el último nivel sin enlace y los anteriores como enlaces', () => {
    render(<MigasDePan />, {
      wrapper: ({ children }) => (
        <MemoryRouter initialEntries={['/contabilidad/reportes/balanza']}>{children}</MemoryRouter>
      ),
    });
    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'Reportes' })).toHaveAttribute('href', '/contabilidad/reportes');
    // El último nivel no es un enlace: aria-current="page" (BreadcrumbPage)
    expect(screen.getByText('Balanza de Comprobación')).toHaveAttribute('aria-current', 'page');
  });
});
