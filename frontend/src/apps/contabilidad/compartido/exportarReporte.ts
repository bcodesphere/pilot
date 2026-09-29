import type { FormatoExportacion } from '@/api/modelos';
import { descargarArchivo, nombreDeContentDisposition } from '@/compartido/descargarArchivo';

/** Respuesta 2xx del cliente HTTP para una exportación: `data` es el `Blob` del archivo (ADR-038). */
interface RespuestaExportacion {
  data: unknown;
  headers: Headers;
}

/**
 * Pide un reporte exportado al backend y dispara su descarga (spec F4.5 §7.5, `MenuExportar`). El
 * cálculo del archivo es todo del backend (ADR-038); aquí solo se nombra y se descarga con el nombre
 * que trae `Content-Disposition`, o el que arma esta función si no vino.
 * @param pedido llama al endpoint de exportación con los filtros vigentes en pantalla
 * @param nombreArchivoBase nombre del reporte, sin extensión, para cuando el backend no manda uno
 * @param formato formato elegido en {@link import('@/compartido/dominio/MenuExportar').MenuExportar}
 */
export async function descargarExportacion(
  pedido: () => Promise<RespuestaExportacion>,
  nombreArchivoBase: string,
  formato: FormatoExportacion,
): Promise<void> {
  const respuesta = await pedido();
  const nombre = nombreDeContentDisposition(
    respuesta.headers.get('content-disposition'),
    `${nombreArchivoBase}.${formato}`,
  );
  descargarArchivo(respuesta.data as Blob, nombre);
}
