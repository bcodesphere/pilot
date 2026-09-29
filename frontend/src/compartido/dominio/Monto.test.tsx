import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Monto } from './Monto';

describe('Monto', () => {
  it('formatea "1234.5" como $1,234.50, alineado a la derecha y con cifras tabulares', () => {
    render(<Monto valor="1234.5" />);
    const nodo = screen.getByText('$1,234.50');
    expect(nodo).toHaveClass('cifra');
    expect(nodo).toHaveClass('text-right');
  });

  it('un monto negativo muestra el signo sin necesitar conSigno', () => {
    render(<Monto valor="-13.00" />);
    expect(screen.getByText('-$13.00')).toBeInTheDocument();
  });

  it('conSigno antepone "+" a un monto positivo', () => {
    render(<Monto valor="13.00" conSigno />);
    expect(screen.getByText('+$13.00')).toBeInTheDocument();
  });

  it('conSigno no antepone "+" a un monto en cero', () => {
    render(<Monto valor="0.00" conSigno />);
    expect(screen.getByText('$0.00')).toBeInTheDocument();
  });

  it('tachado muestra el monto con la clase de revertido y un texto accesible', () => {
    render(<Monto valor="100.00" tachado />);
    const nodo = screen.getByText('$100.00');
    expect(nodo).toHaveClass('line-through');
    expect(screen.getByText('(revertido)')).toBeInTheDocument();
  });
});
