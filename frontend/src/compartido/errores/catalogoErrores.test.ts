import { describe, expect, it } from 'vitest';
import { CODIGOS_CONOCIDOS, mensajeDeError } from './catalogoErrores';

/**
 * catalogoErrores.test.ts — cada código de la guía técnica (CLAUDE.md §8.4, §10 y §12.4/12.7)
 * tiene un mensaje humano; ninguno devuelve el código crudo (ADR-043, spec F4.5 §7.6).
 */
describe('catalogoErrores', () => {
  it('traduce CON-020 con la acción de configurar reglas', () => {
    expect(
      mensajeDeError({
        codigo: 'CON-020',
        detail: 'No hay una regla activa con cuenta para GASTO/OTRO_GASTO',
      }),
    ).toEqual({
      mensaje: 'Falta configurar la cuenta para «Otro gasto».',
      accion: { etiqueta: 'Configurar', href: '/configuracion/contabilidad#reglas' },
    });
  });

  it('nunca devuelve el código crudo como mensaje', () => {
    for (const codigo of CODIGOS_CONOCIDOS) {
      expect(mensajeDeError({ codigo }).mensaje).not.toMatch(/^[A-Z]{3}-\d{3}$/);
    }
  });

  it('cubre todos los códigos PLT, CON e INT documentados en CLAUDE.md', () => {
    const esperados = [
      // §8.4 — plataforma
      'PLT-001',
      'PLT-002',
      'PLT-003',
      'PLT-004',
      'PLT-005',
      'PLT-006',
      'PLT-007',
      'PLT-008',
      'PLT-009',
      'PLT-010',
      'PLT-011',
      'PLT-012',
      'PLT-013',
      'PLT-014',
      'PLT-015',
      'PLT-016',
      'PLT-017',
      'PLT-500',
      // §10.1 y §10.2 — contabilidad
      'CON-001',
      'CON-002',
      'CON-003',
      'CON-004',
      'CON-005',
      'CON-006',
      'CON-007',
      'CON-008',
      'CON-009',
      'CON-010',
      'CON-011',
      'CON-012',
      'CON-013',
      'CON-014',
      'CON-015',
      'CON-016',
      'CON-017',
      'CON-018',
      'CON-020',
      'CON-021',
      'CON-022',
      'CON-023',
      // §12.4 y §12.7 — integración
      'INT-001',
      'INT-002',
      'INT-004',
      'INT-005',
      'INT-006',
      'INT-008',
      'INT-009',
      'INT-010',
    ];
    for (const codigo of esperados) {
      expect(CODIGOS_CONOCIDOS, `falta ${codigo} en el catálogo`).toContain(codigo);
    }
  });

  it('un código desconocido recibe el mensaje genérico, sin exponer detalle interno', () => {
    expect(mensajeDeError({ codigo: 'XYZ-999', detail: 'traza interna sensible' })).toEqual({
      mensaje: 'No pudimos completar la operación. Inténtalo de nuevo.',
    });
  });

  it('CON-005 muestra la diferencia exacta del descuadre', () => {
    expect(mensajeDeError({ codigo: 'CON-005', diferencia: '-13.00' })).toEqual({
      mensaje: 'El asiento no cuadra por $13.00.',
    });
  });

  it('CON-005 sin diferencia muestra un mensaje genérico de descuadre', () => {
    expect(mensajeDeError({ codigo: 'CON-005' }).mensaje).toBe('El asiento no cuadra.');
  });

  it('INT-006 muestra la diferencia de los cobros contra los ingresos', () => {
    expect(mensajeDeError({ codigo: 'INT-006', diferencia: '146.90' })).toEqual({
      mensaje: 'Los cobros no cuadran con los ingresos por $146.90.',
    });
  });

  it('CON-020 sin un detalle reconocible cae en un mensaje genérico con la misma acción', () => {
    expect(mensajeDeError({ codigo: 'CON-020' })).toEqual({
      mensaje: 'Falta configurar una cuenta para completar esta operación.',
      accion: { etiqueta: 'Configurar', href: '/configuracion/contabilidad#reglas' },
    });
  });

  it('PLT-004 ofrece la acción de ir a Apps', () => {
    expect(mensajeDeError({ codigo: 'PLT-004' })).toEqual({
      mensaje: 'Esta aplicación no está instalada en tu espacio de trabajo.',
      accion: { etiqueta: 'Ir a Apps', href: '/configuracion/apps' },
    });
  });

  it('los booleanos y códigos crudos nunca aparecen en los mensajes del catálogo', () => {
    for (const codigo of CODIGOS_CONOCIDOS) {
      const { mensaje } = mensajeDeError({ codigo });
      expect(mensaje).not.toMatch(/\btrue\b|\bfalse\b/i);
      expect(mensaje).not.toContain(codigo);
    }
  });
});
