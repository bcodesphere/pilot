import { describe, expect, it } from 'vitest';
import { CATALOGO, cuenta } from '../compartido/datosPrueba';
import { buscarEnArbol, construirArbol, deducirPadre } from './arbol';

describe('construirArbol (CLAUDE.md §10.2)', () => {
  // Regla §10.2: el padre de cada cuenta es la de nivel anterior; las raíces son las clases
  it('arma la jerarquía del catálogo base a partir de cuentaPadreId', () => {
    const raices = construirArbol(CATALOGO);
    expect(raices.map((n) => n.cuenta.codigo)).toEqual(['1', '2', '5']);
    const activo = raices[0]!;
    expect(activo.hijos.map((n) => n.cuenta.codigo)).toEqual(['11']);
    const caja = activo.hijos[0]!.hijos[0]!.hijos[0]!;
    expect(caja.cuenta.codigo).toBe('110101');
    expect(caja.hijos.map((n) => n.cuenta.codigo)).toEqual(['11010101', '11010103', '11010199', '11030101']);
  });

  // Regla: el orden es por código, sin importar el orden en que llegue la lista
  it('ordena por código aunque la lista llegue desordenada', () => {
    const raices = construirArbol([...CATALOGO].reverse());
    expect(raices.map((n) => n.cuenta.codigo)).toEqual(['1', '2', '5']);
    expect(raices[0]!.hijos[0]!.hijos[0]!.hijos[0]!.hijos.map((n) => n.cuenta.codigo)[0]).toBe('11010101');
  });

  // Regla: datos inesperados no rompen el árbol (huérfanas suben a la raíz; las inactivas se conservan)
  it('una cuenta huérfana sube a la raíz y una inactiva se conserva', () => {
    const huerfana = cuenta('3101', 'Capital social', { cuentaPadreId: 'no-existe' });
    const propia = cuenta('4', 'Costos', { cuentaPadreId: 'c-4' });
    const raices = construirArbol([...CATALOGO, huerfana, propia]);
    expect(raices.map((n) => n.cuenta.codigo)).toEqual(['1', '2', '3101', '4', '5']);
    const inactiva = raices[0]!.hijos[0]!.hijos[0]!.hijos[0]!.hijos.find(
      (n) => n.cuenta.codigo === '11010199',
    );
    expect(inactiva?.cuenta.activa).toBe(false);
  });
});

describe('buscarEnArbol', () => {
  // Regla: la búsqueda muestra las coincidencias junto con sus ancestros
  it('"iva" muestra las cuentas de IVA con sus ancestros', () => {
    const r = buscarEnArbol(construirArbol(CATALOGO), 'iva')!;
    // "Efectivo y equivalentes" también contiene "iva" (equ-iva-lentes): la búsqueda es por subcadena
    expect(r.coincidencias).toEqual(new Set(['c-210101', 'c-21010101', 'c-11030101', 'c-1101']));
    // Ancestros de IVA débito fiscal: 2101, 21 y 2
    for (const id of ['c-2', 'c-21', 'c-2101', 'c-1', 'c-11']) expect(r.visibles.has(id)).toBe(true);
    // Lo que no coincide ni es ancestro queda fuera
    expect(r.visibles.has('c-5')).toBe(false);
  });

  // Regla: se busca también por código
  it('"1101" muestra esa rama', () => {
    const r = buscarEnArbol(construirArbol(CATALOGO), '1101')!;
    expect(r.coincidencias.has('c-1101')).toBe(true);
    expect(r.coincidencias.has('c-11010101')).toBe(true);
    expect(r.visibles.has('c-2')).toBe(false);
  });

  // Regla: sin texto no hay filtro
  it('un texto vacío no filtra', () => {
    expect(buscarEnArbol(construirArbol(CATALOGO), '   ')).toBeNull();
  });
});

describe('deducirPadre (informativo, ADR-035)', () => {
  // Regla: el padre es la cuenta cuyo código es el prefijo con la longitud del nivel anterior
  it('deduce el padre por el prefijo del nivel anterior', () => {
    expect(deducirPadre('11010104', CATALOGO)?.codigo).toBe('110101');
    expect(deducirPadre('1102', CATALOGO)?.codigo).toBe('11');
  });

  // Regla: una clase no tiene padre, una longitud inválida no se deduce y un padre inexistente da null
  it('devuelve null para una clase, una longitud inválida o un padre inexistente', () => {
    expect(deducirPadre('1', CATALOGO)).toBeNull();
    expect(deducirPadre('110', CATALOGO)).toBeNull();
    expect(deducirPadre('120101', CATALOGO)).toBeNull();
  });
});
