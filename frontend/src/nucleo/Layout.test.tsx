import { render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { ContextoAuth } from './auth/contextoAuth';
import { Layout } from './Layout';
import { membresia } from './pruebas-arnes';
import { ContextoSesion } from './sesion/contextoSesion';

// Caso de negocio: el shell muestra la cabecera de Pilot con el espacio de trabajo activo y el enlace a Apps (ADR-021, ADR-032)
describe('Layout del shell', () => {
  it('muestra el producto, el espacio activo y el enlace a Apps', () => {
    const m = membresia('e1', 'Espacio de Ana');
    const router = createMemoryRouter([
      { path: '/', element: <Layout />, children: [{ index: true, element: <p>contenido</p> }] },
    ]);
    render(
      <ContextoAuth.Provider value={{ cerrarSesion: async () => {} }}>
        <ContextoSesion.Provider
          value={{
            usuario: {
              id: 'u',
              correo: 'a@x.sv',
              nombre: 'Ana',
              telefono: '+50370000000',
              recomendacionesCorreo: false,
              membresias: [m],
            },
            empresaActiva: m,
            cambiarEmpresa: () => {},
            recargarUsuario: async () => {},
          }}
        >
          <RouterProvider router={router} />
        </ContextoSesion.Provider>
      </ContextoAuth.Provider>,
    );
    expect(screen.getByRole('link', { name: 'Pilot' })).toBeInTheDocument();
    expect(screen.getByText('Espacio de Ana')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Apps' })).toHaveAttribute('href', '/apps');
    expect(screen.getByText('contenido')).toBeInTheDocument();
  });
});
