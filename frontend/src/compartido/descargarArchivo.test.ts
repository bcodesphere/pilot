import { afterEach, describe, expect, it, vi } from 'vitest';
import { descargarArchivo, nombreDeContentDisposition } from './descargarArchivo';

describe('nombreDeContentDisposition', () => {
  // Regla: el nombre citado con comillas se lee tal cual
  it('lee el filename entre comillas', () => {
    expect(nombreDeContentDisposition('attachment; filename="balanza.pdf"')).toBe('balanza.pdf');
  });

  // Regla: la forma filename*=UTF-8''… (RFC 5987) también se reconoce y se decodifica
  it("lee y decodifica la forma filename*=UTF-8''…", () => {
    expect(nombreDeContentDisposition("attachment; filename*=UTF-8''libro%20mayor.xlsx")).toBe(
      'libro mayor.xlsx',
    );
  });

  // Regla: sin header, o sin filename reconocible, se usa el nombre por defecto dado
  it('usa el nombre por defecto si no hay header o no trae filename', () => {
    expect(nombreDeContentDisposition(null, 'reporte.csv')).toBe('reporte.csv');
    expect(nombreDeContentDisposition('attachment')).toBe('reporte');
  });
});

describe('descargarArchivo', () => {
  afterEach(() => vi.restoreAllMocks());

  // Regla: crea y libera un Object URL, y dispara la descarga con un enlace <a download> temporal
  it('crea la URL del blob, la descarga con un enlace y libera la URL', () => {
    const crear = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:falso');
    const liberar = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const clic = vi.fn();
    const crearElemento = document.createElement.bind(document);
    vi.spyOn(document, 'createElement').mockImplementation((tag: string) => {
      const el = crearElemento(tag);
      if (tag === 'a') el.click = clic;
      return el;
    });

    const blob = new Blob(['contenido'], { type: 'application/pdf' });
    descargarArchivo(blob, 'balanza.pdf');

    expect(crear).toHaveBeenCalledWith(blob);
    expect(clic).toHaveBeenCalledTimes(1);
    expect(liberar).toHaveBeenCalledWith('blob:falso');
  });
});
