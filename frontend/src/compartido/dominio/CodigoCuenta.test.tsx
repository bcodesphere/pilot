import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { CodigoCuenta } from './CodigoCuenta';

describe('CodigoCuenta', () => {
  it('muestra el código en fuente monoespaciada y el nombre', () => {
    render(<CodigoCuenta codigo="11010101" nombre="Caja general" />);
    expect(screen.getByText('11010101')).toHaveClass('codigo-cuenta');
    expect(screen.getByText('Caja general')).toBeInTheDocument();
  });

  it('sin nombre, muestra solo el código', () => {
    render(<CodigoCuenta codigo="1101" />);
    expect(screen.getByText('1101')).toBeInTheDocument();
  });
});
