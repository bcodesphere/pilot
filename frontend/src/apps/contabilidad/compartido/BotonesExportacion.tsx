import { useState } from 'react';
import type { FormatoExportacion } from '@/api/modelos';
import { descargarArchivo, nombreDeContentDisposition } from '@/compartido/descargarArchivo';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { mensajeContabilidad } from '../mensajesContabilidad';

/** Respuesta 2xx del cliente HTTP para una exportación: `data` es el `Blob` del archivo (F4-05). */
interface RespuestaExportacion {
  data: unknown;
  headers: Headers;
}

/** Propiedades de los botones de exportación de un reporte. */
export interface PropsBotonesExportacion {
  /** Nombre del reporte; se usa como nombre de archivo si el backend no manda uno. */
  nombreArchivo: string;
  /** `false` mientras falten los filtros que la exportación exige (p. ej. `desde`/`hasta`); deshabilita los tres botones. */
  filtrosCompletos: boolean;
  /** Pide la exportación al backend con los mismos filtros que están en pantalla. */
  exportar: (formato: FormatoExportacion) => Promise<RespuestaExportacion>;
}

/** Un botón por formato, en el orden que pide el encargo. */
const FORMATOS: { formato: FormatoExportacion; etiqueta: string }[] = [
  { formato: 'pdf', etiqueta: 'PDF' },
  { formato: 'xlsx', etiqueta: 'Excel' },
  { formato: 'csv', etiqueta: 'CSV' },
];

/**
 * Botones "PDF", "Excel" y "CSV" que exportan un reporte con los filtros vigentes en pantalla
 * (CLAUDE.md §10.5, ADR-038). El cálculo del archivo es todo del backend; aquí solo se dispara la
 * descarga con el nombre que trae `Content-Disposition`.
 * @param props ver {@link PropsBotonesExportacion}
 */
export function BotonesExportacion({ nombreArchivo, filtrosCompletos, exportar }: PropsBotonesExportacion) {
  const [enCurso, setEnCurso] = useState<FormatoExportacion | null>(null);
  const [error, setError] = useState<string | null>(null);

  /** Pide el archivo, lo descarga y libera el estado "en curso" al terminar, con o sin error. */
  const alExportar = async (formato: FormatoExportacion) => {
    setError(null);
    setEnCurso(formato);
    try {
      const respuesta = await exportar(formato);
      const nombre = nombreDeContentDisposition(
        respuesta.headers.get('content-disposition'),
        `${nombreArchivo}.${formato}`,
      );
      descargarArchivo(respuesta.data as Blob, nombre);
    } catch (e) {
      setError(mensajeContabilidad(e));
    } finally {
      setEnCurso(null);
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2" role="group" aria-label={`Exportar ${nombreArchivo}`}>
        {FORMATOS.map(({ formato, etiqueta }) => (
          <Button
            key={formato}
            type="button"
            variant="outline"
            size="sm"
            disabled={!filtrosCompletos || enCurso !== null}
            onClick={() => void alExportar(formato)}
          >
            {enCurso === formato ? 'Descargando…' : etiqueta}
          </Button>
        ))}
      </div>
      {error && <Alert variant="error">{error}</Alert>}
    </div>
  );
}
