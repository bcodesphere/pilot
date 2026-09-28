import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ErrorApi } from '@/nucleo/http/errorApi';
import { BotonesExportacion } from './BotonesExportacion';

// La descarga real depende de URL.createObjectURL/<a download>, que no aportan nada a esta prueba
// (se prueba aparte en compartido/descargarArchivo.test.ts); aquí solo se espía que se invoque.
const descargarArchivoMock = vi.fn();
vi.mock('@/compartido/descargarArchivo', async () => {
  const real = await vi.importActual<typeof import('@/compartido/descargarArchivo')>(
    '@/compartido/descargarArchivo',
  );
  return { ...real, descargarArchivo: (...args: unknown[]) => descargarArchivoMock(...args) };
});

describe('BotonesExportacion', () => {
  beforeEach(() => descargarArchivoMock.mockReset());

  // Regla: sin filtros completos los tres botones están deshabilitados
  it('deshabilita los tres botones sin filtros completos', () => {
    render(<BotonesExportacion nombreArchivo="balanza" filtrosCompletos={false} exportar={vi.fn()} />);
    for (const nombre of ['PDF', 'Excel', 'CSV']) {
      expect(screen.getByRole('button', { name: nombre })).toBeDisabled();
    }
  });

  // Regla: con filtros completos, pedir "PDF" llama a exportar('pdf') y descarga el archivo con el
  // nombre de Content-Disposition
  it('exporta el formato elegido y descarga el archivo con el nombre de Content-Disposition', async () => {
    const blob = new Blob(['contenido'], { type: 'application/pdf' });
    const exportar = vi.fn().mockResolvedValue({
      data: blob,
      headers: new Headers({ 'content-disposition': 'attachment; filename="balanza.pdf"' }),
    });

    render(<BotonesExportacion nombreArchivo="balanza" filtrosCompletos exportar={exportar} />);
    await userEvent.click(screen.getByRole('button', { name: 'PDF' }));

    await waitFor(() => expect(descargarArchivoMock).toHaveBeenCalledWith(blob, 'balanza.pdf'));
    expect(exportar).toHaveBeenCalledWith('pdf');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  // Regla: un error de la exportación (Problem Details) se muestra con el mensaje traducido
  it('muestra un error si la exportación falla', async () => {
    const exportar = vi.fn().mockRejectedValue(new ErrorApi({ status: 422, codigo: 'CON-020' }));
    render(<BotonesExportacion nombreArchivo="mayor" filtrosCompletos exportar={exportar} />);
    await userEvent.click(screen.getByRole('button', { name: 'Excel' }));
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(descargarArchivoMock).not.toHaveBeenCalled();
  });
});
