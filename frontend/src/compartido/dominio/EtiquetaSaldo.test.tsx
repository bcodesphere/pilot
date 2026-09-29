import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { EtiquetaSaldo } from './EtiquetaSaldo';

describe('EtiquetaSaldo', () => {
  it('muestra $13.00 A para {monto: "13.00", lado: "ACREEDOR"}', () => {
    const { container } = render(
      <EtiquetaSaldo saldo={{ monto: '13.00', lado: 'ACREEDOR', contrarioNaturaleza: false }} />,
    );
    expect(container).toHaveTextContent('$13.00 A');
  });

  it('DEUDOR se muestra con la letra D', () => {
    const { container } = render(
      <EtiquetaSaldo saldo={{ monto: '5.00', lado: 'DEUDOR', contrarioNaturaleza: false }} />,
    );
    expect(container).toHaveTextContent('$5.00 D');
  });

  it('contrarioNaturaleza muestra un ícono de alerta con texto accesible', () => {
    render(<EtiquetaSaldo saldo={{ monto: '5.00', lado: 'ACREEDOR', contrarioNaturaleza: true }} />);
    expect(screen.getByRole('alert')).toHaveTextContent('saldo contrario a la naturaleza de la cuenta');
  });

  it('sin contrarioNaturaleza no hay alerta', () => {
    render(<EtiquetaSaldo saldo={{ monto: '5.00', lado: 'ACREEDOR', contrarioNaturaleza: false }} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
