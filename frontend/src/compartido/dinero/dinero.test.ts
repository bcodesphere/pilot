import {
  esCero,
  esMontoValido,
  formatearMoneda,
  formatearMonedaConSigno,
  normalizarMonto,
  restarMontos,
  sonIguales,
  sumarMontos,
} from './dinero';

// Fuente de los valores esperados: criterios de aceptación de F0-07 y CLAUDE.md §5.2
describe('sumarMontos', () => {
  it('suma sin error de coma flotante', () => {
    expect(sumarMontos(['0.10', '0.20'])).toBe('0.30');
  });
  it('no pierde precisión al sumar montos de 19 dígitos', () => {
    // Caso del revisor: con precision 20 de decimal.js daba "9999999999999999999.90"
    const montos = [...Array<string>(100).fill('99999999999999999.99'), '0.01'];
    expect(sumarMontos(montos)).toBe('9999999999999999999.01');
  });
  it('devuelve 0.00 para una lista vacía', () => {
    expect(sumarMontos([])).toBe('0.00');
  });
  it('rechaza entradas inválidas', () => {
    expect(() => sumarMontos(['1.005'])).toThrow();
  });
});

describe('formatearMoneda', () => {
  it.each([
    ['1234.5', '$1,234.50'],
    ['12345678901234567.89', '$12,345,678,901,234,567.89'], // 17 dígitos sin pérdida de precisión
    ['0', '$0.00'],
  ])('formatea %s como %s', (entrada, esperado) => {
    expect(formatearMoneda(entrada)).toBe(esperado);
  });
  it.each(['1.005', '-1', 'abc'])('lanza error con la entrada inválida %s', (entrada) => {
    expect(() => formatearMoneda(entrada)).toThrow();
  });
});

describe('sonIguales', () => {
  it('ignora la escala', () => {
    expect(sonIguales('100.0', '100.00')).toBe(true);
  });
  it('distingue montos distintos', () => {
    expect(sonIguales('100.00', '100.01')).toBe(false);
  });
});

// Fuente: CLAUDE.md §10.1 (CON-003, CON-005) y §11.2; ADR-013 (montos como cadena decimal)
describe('utilidades de montos del Libro Diario', () => {
  it('esMontoValido acepta hasta 2 decimales y rechaza negativos y 3 decimales', () => {
    expect(esMontoValido('113.00')).toBe(true);
    expect(esMontoValido('5')).toBe(true);
    expect(esMontoValido('1.005')).toBe(false);
    expect(esMontoValido('-1')).toBe(false);
    expect(esMontoValido('')).toBe(false);
  });
  it('normalizarMonto convierte el campo vacío en 0.00 y deja el resto intacto', () => {
    expect(normalizarMonto('')).toBe('0.00');
    expect(normalizarMonto('  ')).toBe('0.00');
    expect(normalizarMonto(' 12.5 ')).toBe('12.5');
  });
  it('esCero distingue 0, 0.00 y montos positivos', () => {
    expect(esCero('0')).toBe(true);
    expect(esCero('0.00')).toBe(true);
    expect(esCero('0.01')).toBe(false);
  });
  it('restarMontos es exacta y conserva el signo', () => {
    expect(restarMontos('0.30', '0.10')).toBe('0.20');
    expect(restarMontos('100.00', '113.00')).toBe('-13.00');
    expect(restarMontos('5.00', '5.00')).toBe('0.00');
  });
  it('formatearMonedaConSigno muestra el signo solo cuando hay una diferencia negativa', () => {
    expect(formatearMonedaConSigno('-13.00')).toBe('-$13.00');
    expect(formatearMonedaConSigno('1234.50')).toBe('$1,234.50');
    expect(formatearMonedaConSigno('-0.00')).toBe('$0.00');
  });
});
