import { expect, test } from '@playwright/test';
import { codigoTotp, codigoTotpDesdeBytes, decodificarBase32, msHastaSiguienteVentana } from './totp';

/**
 * Pruebas de la utilidad TOTP contra los vectores oficiales de RFC 6238, Apéndice B (SHA-1). No abren navegador.
 * La semilla del apéndice es la cadena ASCII "12345678901234567890" y los códigos de referencia tienen 8 dígitos.
 */

/** Semilla de los vectores de RFC 6238 Apéndice B para SHA-1. */
const SEMILLA_RFC = Buffer.from('12345678901234567890', 'ascii');

/** [tiempo Unix en segundos, código de 8 dígitos] de la tabla del Apéndice B (columna SHA1). */
const VECTORES_RFC_6238: Array<[number, string]> = [
  [59, '94287082'],
  [1111111109, '07081804'],
  [1111111111, '14050471'],
  [1234567890, '89005924'],
  [2000000000, '69279037'],
  [20000000000, '65353130'],
];

test.describe('TOTP (RFC 6238, Apéndice B, SHA-1)', () => {
  for (const [segundos, esperado] of VECTORES_RFC_6238) {
    // Fuente del valor esperado: RFC 6238 Apéndice B, fila con T = segundos
    test(`vector oficial con T = ${segundos} s produce ${esperado}`, () => {
      expect(codigoTotpDesdeBytes(SEMILLA_RFC, segundos * 1000, { digitos: 8 })).toBe(esperado);
    });
  }

  // Fuente: con 6 dígitos el código es el sufijo de 6 cifras del de 8 (mismo truncado, distinto módulo)
  test('con 6 dígitos (como el realm) es el sufijo del código de 8 dígitos', () => {
    expect(codigoTotpDesdeBytes(SEMILLA_RFC, 59_000)).toBe('287082');
    expect(codigoTotpDesdeBytes(SEMILLA_RFC, 1111111109_000)).toBe('081804');
  });

  // Fuente: RFC 4648 §10, vectores de Base32 ("foobar" → MZXW6YTBOI======)
  test('decodifica Base32 con espacios, minúsculas y relleno', () => {
    expect(decodificarBase32('MZXW6YTBOI======').toString('ascii')).toBe('foobar');
    expect(decodificarBase32('mzxw 6ytb oi').toString('ascii')).toBe('foobar');
  });

  // Fuente: la semilla ASCII del RFC en Base32 (GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ) da los mismos códigos
  test('la semilla en Base32 produce el mismo código que la semilla en bytes', () => {
    expect(codigoTotp('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', 59_000, { digitos: 8 })).toBe('94287082');
  });

  // Fuente: un carácter fuera del alfabeto no debe decodificarse en silencio
  test('rechaza caracteres fuera del alfabeto Base32', () => {
    expect(() => decodificarBase32('ABC1')).toThrow(/inválido/);
  });

  // Fuente: definición de ventana de 30 s; el segundo inicio de sesión espera a la siguiente
  test('calcula los milisegundos hasta la siguiente ventana de 30 s', () => {
    expect(msHastaSiguienteVentana(0)).toBe(30_000);
    expect(msHastaSiguienteVentana(29_999)).toBe(1);
    expect(msHastaSiguienteVentana(30_000)).toBe(30_000);
  });
});
