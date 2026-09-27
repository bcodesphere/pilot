import { describe, expect, it } from 'vitest';
import {
  construirNuevoAsiento,
  crearEsquemaAsiento,
  totalesLocales,
  type ValoresAsiento,
  type ValoresLineaAsiento,
} from './esquemaAsiento';

/** Contexto de las pruebas: hoy es 2026-09-26 y las cuentas de IVA son `iva-d` e `iva-c`. */
const esquema = crearEsquemaAsiento({ hoy: '2026-09-26', idsCuentasIva: ['iva-d', 'iva-c'] });

/** Línea de ejemplo; los montos vacíos representan el lado que no se usa. */
const linea = (cuentaId: string, debe: string, haber: string, llevaIva = false): ValoresLineaAsiento => ({
  cuentaId,
  descripcion: '',
  debe,
  haber,
  llevaIva,
});

/** Asiento válido base (Caja 100.00 / Ventas 100.00) sobre el que cada prueba cambia un solo dato. */
const valido = (extra: Partial<ValoresAsiento> = {}): ValoresAsiento => ({
  fecha: '2026-09-26',
  concepto: 'Venta al contado',
  modoPrecio: 'CON_IVA',
  lineas: [linea('caja', '100.00', ''), linea('ventas', '', '100.00')],
  ...extra,
});

/** Códigos de negocio (`params.codigo`) que produce el esquema para unos valores. */
function codigos(v: ValoresAsiento): string[] {
  const r = esquema.safeParse(v);
  return r.success
    ? []
    : r.error.issues.map((i) => String((i as { params?: { codigo?: string } }).params?.codigo));
}

// Fuente de todos los casos: CLAUDE.md §10.1 (tabla de validaciones y esquema Zod de ejemplo)
describe('esquema Zod del Libro Diario', () => {
  it('acepta un asiento cuadrado con campos vacíos en el lado que no se usa', () => {
    expect(esquema.safeParse(valido()).success).toBe(true);
  });

  // CON-001: mínimo 2 líneas
  it('rechaza un asiento con una sola línea (CON-001)', () => {
    expect(codigos(valido({ lineas: [linea('caja', '100.00', '')] }))).toContain('CON-001');
  });

  // CON-002: una línea con Debe y Haber a la vez, o con ninguno
  it('rechaza una línea con Debe y Haber a la vez (CON-002)', () => {
    const v = valido({ lineas: [linea('caja', '100.00', '100.00'), linea('ventas', '', '100.00')] });
    expect(codigos(v)).toContain('CON-002');
  });
  it('rechaza una línea sin Debe ni Haber (CON-002)', () => {
    const v = valido({ lineas: [linea('caja', '', ''), linea('ventas', '', '100.00')] });
    expect(codigos(v)).toContain('CON-002');
  });

  // CON-003: montos no negativos y con máximo 2 decimales
  it.each(['-5', '1.005', 'abc', '1e3'])('rechaza el monto "%s" (CON-003)', (monto) => {
    const r = esquema.safeParse(
      valido({ lineas: [linea('caja', monto, ''), linea('ventas', '', '100.00')] }),
    );
    expect(r.success).toBe(false);
  });

  // CON-004: totales mayores que cero
  it('rechaza un asiento con todos los montos en cero (CON-004)', () => {
    const v = valido({ lineas: [linea('caja', '0.00', ''), linea('ventas', '', '0')] });
    expect(codigos(v)).toContain('CON-004');
  });

  // CON-005: la diferencia se calcula exacta con decimal.js (0.10 + 0.20 = 0.30, no 0.30000000000000004)
  it('acepta 0.10 + 0.20 contra 0.30 sin error de coma flotante (CON-005)', () => {
    const v = valido({
      lineas: [linea('a', '0.10', ''), linea('b', '0.20', ''), linea('c', '', '0.30')],
    });
    expect(esquema.safeParse(v).success).toBe(true);
  });
  it('rechaza un asiento descuadrado e informa la diferencia exacta (CON-005)', () => {
    const v = valido({ lineas: [linea('caja', '113.00', ''), linea('ventas', '', '100.00')] });
    const r = esquema.safeParse(v);
    expect(r.success).toBe(false);
    const issue = r.error!.issues.find(
      (i) => (i as { params?: { codigo?: string } }).params?.codigo === 'CON-005',
    );
    expect(issue?.message).toBe('Diferencia: $13.00');
  });

  // CON-007: fecha no posterior a hoy en El Salvador; hoy sí es válido
  it('rechaza una fecha futura (CON-007) y acepta la de hoy', () => {
    expect(codigos(valido({ fecha: '2026-09-27' }))).toContain('CON-007');
    expect(codigos(valido({ fecha: '2026-09-26' }))).not.toContain('CON-007');
  });

  // CON-013: "lleva IVA" no se permite sobre las cuentas de IVA de la configuración
  it('rechaza "lleva IVA" sobre una cuenta de IVA (CON-013)', () => {
    const v = valido({ lineas: [linea('caja', '113.00', ''), linea('iva-d', '', '113.00', true)] });
    expect(codigos(v)).toContain('CON-013');
  });

  // ADR-036: con IVA la partida doble se valida sobre las líneas del backend, no aquí (no se calcula IVA en el frontend)
  it('con una línea que lleva IVA no valida el cuadre localmente (SIN_IVA: 100.00 vs 113.00)', () => {
    const v = valido({
      modoPrecio: 'SIN_IVA',
      lineas: [linea('compras', '100.00', '', true), linea('caja', '', '113.00')],
    });
    expect(codigos(v)).not.toContain('CON-005');
  });

  // Concepto 1–500 y máximo 200 líneas (PLT-002 en el backend)
  it('valida el concepto (1 a 500) y el máximo de 200 líneas', () => {
    expect(esquema.safeParse(valido({ concepto: '   ' })).success).toBe(false);
    expect(esquema.safeParse(valido({ concepto: 'x'.repeat(501) })).success).toBe(false);
    const muchas = Array.from({ length: 201 }, (_, i) =>
      linea(`c${i}`, i % 2 ? '' : '1.00', i % 2 ? '1.00' : ''),
    );
    expect(esquema.safeParse(valido({ lineas: muchas })).success).toBe(false);
  });
});

describe('totalesLocales', () => {
  it('suma con decimal.js, trata el campo vacío como 0.00 y da la diferencia con signo', () => {
    const t = totalesLocales([linea('a', '100.00', ''), linea('b', '', '113.00')]);
    expect(t).toEqual({ debe: '100.00', haber: '113.00', diferencia: '-13.00' });
  });
  it('devuelve null si algún monto tiene formato inválido', () => {
    expect(totalesLocales([linea('a', '1.005', ''), linea('b', '', '1.00')])).toBeNull();
  });
});

describe('construirNuevoAsiento', () => {
  it('normaliza los montos vacíos a "0.00" y omite modoPrecio y descripción cuando no aplican', () => {
    expect(construirNuevoAsiento(valido())).toEqual({
      fecha: '2026-09-26',
      concepto: 'Venta al contado',
      lineas: [
        { cuentaId: 'caja', debe: '100.00', haber: '0.00', llevaIva: false },
        { cuentaId: 'ventas', debe: '0.00', haber: '100.00', llevaIva: false },
      ],
    });
  });
  it('envía modoPrecio solo cuando alguna línea lleva IVA', () => {
    const cuerpo = construirNuevoAsiento(
      valido({ modoPrecio: 'SIN_IVA', lineas: [linea('c', '100.00', '', true), linea('d', '', '113.00')] }),
    );
    expect(cuerpo.modoPrecio).toBe('SIN_IVA');
  });
});
