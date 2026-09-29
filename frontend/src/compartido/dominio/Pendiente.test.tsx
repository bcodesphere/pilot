import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { Pendiente } from './Pendiente';

describe('Pendiente', () => {
  it('muestra el texto y el enlace de la acción', () => {
    render(
      <Pendiente
        severidad="alerta"
        texto="Falta configurar la cuenta para «Otro gasto»."
        accion={{ etiqueta: 'Configurar', href: '/configuracion/contabilidad#reglas' }}
      />,
      { wrapper: MemoryRouter },
    );
    expect(screen.getByText('Falta configurar la cuenta para «Otro gasto».')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Configurar' })).toHaveAttribute(
      'href',
      '/configuracion/contabilidad#reglas',
    );
  });

  it('severidad error antepone un prefijo accesible sin duplicar el texto visible', () => {
    render(
      <Pendiente
        severidad="error"
        texto="El asiento no cuadra."
        accion={{ etiqueta: 'Revisar', href: '/x' }}
      />,
      { wrapper: MemoryRouter },
    );
    expect(screen.getByText(/^Error:/)).toBeInTheDocument();
  });
});
