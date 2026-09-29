import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { app, membresia, montarShell } from '@/nucleo/pruebas-arnes';

afterEach(() => {
  vi.unstubAllGlobals();
  // Restaura el ancho de ventana entre pruebas (algunas simulan la barra contraída)
  window.innerWidth = 1024;
});

/**
 * Pruebas de la estructura del shell (ADR-043, spec F4.5 §7.4 y paso 5 del plan de U1): los atajos
 * `Ctrl+K` y `N`, y que ninguno se dispara con el foco en un campo de texto.
 */
describe('EstructuraApp — atajos de teclado', () => {
  it('Ctrl+K abre el buscador global', async () => {
    montarShell({ apps: [app('contabilidad', 'INSTALADA')] });
    await screen.findByRole('link', { name: 'Pilot' });

    expect(screen.queryByPlaceholderText('Buscar una pantalla o una cuenta…')).not.toBeInTheDocument();
    await userEvent.keyboard('{Control>}k{/Control}');
    expect(await screen.findByPlaceholderText('Buscar una pantalla o una cuenta…')).toBeInTheDocument();
  });

  it('N abre "+ Registrar" con Contabilidad instalada', async () => {
    montarShell({ apps: [app('contabilidad', 'INSTALADA')] });
    await screen.findByRole('button', { name: /Registrar/ });

    await userEvent.keyboard('n');
    expect(await screen.findByRole('menuitem', { name: /Asiento manual/ })).toBeInTheDocument();
  });

  it('N no se dispara con el foco en un campo de texto', async () => {
    montarShell({ apps: [app('contabilidad', 'INSTALADA')] });
    await userEvent.click(await screen.findByRole('button', { name: /Buscar/ }));
    const campo = await screen.findByPlaceholderText('Buscar una pantalla o una cuenta…');
    await userEvent.type(campo, 'n');

    // El menú "+ Registrar" no se abrió: sigue sin verse su contenido
    expect(screen.queryByRole('menuitem', { name: /Asiento manual/ })).not.toBeInTheDocument();
    expect(campo).toHaveValue('n');
  });

  it('sin Contabilidad instalada, "+ Registrar" no se muestra', async () => {
    montarShell({ apps: [app('contabilidad', 'DISPONIBLE')] });
    await screen.findByRole('link', { name: 'Pilot' });
    expect(screen.queryByRole('button', { name: /Registrar/ })).not.toBeInTheDocument();
  });
});

describe('EstructuraApp — barra lateral contraída (spec F4.5 §7.4)', () => {
  it('por debajo de 1280 px se contrae a íconos con tooltip', async () => {
    window.innerWidth = 1024;
    montarShell({
      membresias: [membresia('e1', 'Espacio de Ana')],
      apps: [app('contabilidad', 'INSTALADA')],
    });
    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    await waitFor(() => expect(nav).toHaveAttribute('data-contraida', 'true'));

    // El nombre accesible del enlace sigue disponible (el texto visible se oculta por CSS, no del DOM)
    const inicio = within(nav).getByRole('link', { name: 'Inicio' });
    expect(inicio).toBeInTheDocument();

    // Al enfocarlo (equivalente accesible de pasar el mouse), aparece el tooltip con la etiqueta
    await userEvent.hover(inicio);
    expect(await screen.findByRole('tooltip', { name: 'Inicio' })).toBeInTheDocument();
  });

  // Corrección 4 de U1, punto 6: a 64 px de ancho "Pilot" ya no cabe junto a la insignia "P" y se
  // desbordaba bajo la barra superior, cortado como "Pi"; el texto se oculta visualmente y solo
  // queda como nombre accesible del enlace
  it('el texto "Pilot" del logo se oculta visualmente (sr-only) y el enlace conserva su nombre accesible', async () => {
    window.innerWidth = 1024;
    montarShell({ apps: [app('contabilidad', 'INSTALADA')] });
    const nav = await screen.findByRole('navigation', { name: 'Principal' });
    await waitFor(() => expect(nav).toHaveAttribute('data-contraida', 'true'));

    const enlacePilot = within(nav).getByRole('link', { name: 'Pilot' });
    expect(within(enlacePilot).getByText('Pilot')).toHaveClass('sr-only');
  });
});

describe('EstructuraApp — foco visible dentro de la barra lateral (corrección 4 de U1, WCAG 2.2 §1.4.11)', () => {
  // El primario (#1d4ed8) da solo 1.55:1 sobre el fondo de la barra; el foco de sus enlaces (y del
  // logo "Pilot") usa --color-foco-lateral, que sí llega a 3:1 (ver comentario en index.css)
  it('los enlaces de la barra lateral y el logo "Pilot" enfocan con --color-foco-lateral, no con el primario', async () => {
    // Expandida a propósito: la clase de foco no depende del modo contraído, pero fija el estado
    // (el afterEach de este archivo deja 1024 px tras las pruebas de la barra contraída)
    window.innerWidth = 1440;
    montarShell({ apps: [app('contabilidad', 'INSTALADA')] });
    const nav = await screen.findByRole('navigation', { name: 'Principal' });

    const inicio = within(nav).getByRole('link', { name: 'Inicio' });
    expect(inicio.className).toContain('focus-visible:outline-[var(--color-foco-lateral)]');
    expect(inicio.className).not.toContain('focus-visible:outline-[var(--color-primario)]');

    const enlacePilot = within(nav).getByRole('link', { name: 'Pilot' });
    expect(enlacePilot.className).toContain('focus-visible:outline-[var(--color-foco-lateral)]');
  });
});
