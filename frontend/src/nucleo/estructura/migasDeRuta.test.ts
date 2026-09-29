import { describe, expect, it } from 'vitest';
import { migasDeRuta } from './migasDeRuta';

describe('migasDeRuta', () => {
  it('la raíz es solo "Inicio", sin enlace', () => {
    expect(migasDeRuta('/')).toEqual([{ etiqueta: 'Inicio' }]);
  });

  it('una pantalla de Contabilidad antepone Inicio con enlace', () => {
    expect(migasDeRuta('/contabilidad/mayor')).toEqual([
      { etiqueta: 'Inicio', href: '/' },
      { etiqueta: 'Mayor' },
    ]);
  });

  it('una subpantalla de Reportes incluye el nivel intermedio con enlace', () => {
    expect(migasDeRuta('/contabilidad/reportes/balanza')).toEqual([
      { etiqueta: 'Inicio', href: '/' },
      { etiqueta: 'Reportes', href: '/contabilidad/reportes' },
      { etiqueta: 'Balanza de Comprobación' },
    ]);
  });
});
