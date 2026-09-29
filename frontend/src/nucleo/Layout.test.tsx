import { screen } from '@testing-library/react';
import { app, membresia, montarShell } from './pruebas-arnes';

/**
 * Desde ADR-043 (F4.5), `Layout` delega en `EstructuraApp` (barra lateral + barra superior +
 * contenido). La cobertura detallada de la navegación vive en `estructura/*.test.tsx`; esta prueba
 * es el humo de que el layout compone correctamente dentro del shell real (`montarShell`).
 */
describe('Layout del shell', () => {
  it('monta la barra lateral, la barra superior y el contenido de la ruta activa', async () => {
    montarShell({
      membresias: [membresia('e1', 'Espacio de Ana')],
      apps: [app('contabilidad', 'INSTALADA')],
      ruta: '/contabilidad/catalogo',
    });

    // Barra lateral: nombre del producto y navegación principal
    expect(await screen.findByRole('link', { name: 'Pilot' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('navigation', { name: 'Principal' })).toBeInTheDocument();
    // Barra superior: migas de pan (nivel "Inicio", junto al de la barra lateral) y menú de usuario
    const inicio = await screen.findAllByRole('link', { name: 'Inicio' });
    expect(inicio.length).toBeGreaterThanOrEqual(2);
    expect(inicio[0]).toHaveAttribute('href', '/');
    expect(screen.getByRole('button', { name: 'Ana' })).toBeInTheDocument();
    // Contenido de la ruta activa
    expect(await screen.findByRole('heading', { name: 'Catálogo de cuentas' })).toBeInTheDocument();
  });
});
