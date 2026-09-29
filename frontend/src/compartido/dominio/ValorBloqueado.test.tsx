import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { TooltipProvider } from '@/compartido/ui/tooltip';
import { ValorBloqueado } from './ValorBloqueado';

describe('ValorBloqueado', () => {
  it('muestra el valor y el candado con el motivo, sin ningún <input>', () => {
    const { container } = render(
      <TooltipProvider>
        <ValorBloqueado
          valor="Deudora"
          motivo="Esta cuenta es del catálogo base: su naturaleza no se puede cambiar."
        />
      </TooltipProvider>,
    );
    expect(screen.getByText('Deudora')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: 'Esta cuenta es del catálogo base: su naturaleza no se puede cambiar.',
      }),
    ).toBeInTheDocument();
    expect(container.querySelector('input')).not.toBeInTheDocument();
  });
});
