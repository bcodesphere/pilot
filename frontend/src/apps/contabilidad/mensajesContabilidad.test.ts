import { describe, expect, it } from 'vitest';
import { MENSAJE_RANGO_INVALIDO, validarRangoPeriodo } from './mensajesContabilidad';

describe('validarRangoPeriodo', () => {
  // Regla (corrección 1, F4-05): mismo texto que da el backend en PLT-002 para el campo `desde`
  it('devuelve el mensaje de PLT-002 cuando desde es posterior a hasta', () => {
    expect(validarRangoPeriodo('2026-09-30', '2026-09-01')).toBe(MENSAJE_RANGO_INVALIDO);
  });

  // Regla: un rango válido, o incompleto (falta alguna fecha), no es un error
  it('no da error con un rango válido o incompleto', () => {
    expect(validarRangoPeriodo('2026-09-01', '2026-09-30')).toBeNull();
    expect(validarRangoPeriodo('2026-09-01', '2026-09-01')).toBeNull();
    expect(validarRangoPeriodo('', '2026-09-30')).toBeNull();
    expect(validarRangoPeriodo('2026-09-01', '')).toBeNull();
  });
});
