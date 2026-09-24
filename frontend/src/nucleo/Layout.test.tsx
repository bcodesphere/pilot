import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { Layout } from './Layout';
import { PaginaInicio } from './PaginaInicio';

// Caso de negocio: el shell muestra la página de inicio "Pilot" dentro del layout (ADR-021)
describe('Layout del shell', () => {
  it('muestra la página de inicio Pilot', () => {
    const router = createMemoryRouter([
      { path: '/', element: <Layout />, children: [{ index: true, element: <PaginaInicio /> }] },
    ]);
    render(<RouterProvider router={router} />);
    expect(screen.getByRole('heading', { name: 'Pilot' })).toBeInTheDocument();
  });
});
