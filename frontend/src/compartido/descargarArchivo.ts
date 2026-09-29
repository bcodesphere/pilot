/**
 * Descarga de archivos binarios en el navegador (reportes exportados en PDF, XLSX o CSV, F4-05, ADR-038).
 */

/** Nombre de archivo cuando la respuesta no trae `Content-Disposition` o no se puede leer. */
const NOMBRE_POR_DEFECTO = 'reporte';

/**
 * Extrae el nombre de archivo de un header `Content-Disposition` (`attachment; filename="balanza.pdf"`,
 * o su forma `filename*=UTF-8''balanza.pdf`).
 * @param disposicion valor del header `Content-Disposition`, o `null`/`undefined` si no vino
 * @param porDefecto nombre a usar si no se puede leer uno del header
 */
export function nombreDeContentDisposition(
  disposicion: string | null | undefined,
  porDefecto = NOMBRE_POR_DEFECTO,
): string {
  const coincidencia = disposicion?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i);
  if (!coincidencia?.[1]) return porDefecto;
  try {
    return decodeURIComponent(coincidencia[1]);
  } catch {
    // Un valor que no es UTF-8 percent-encoded se usa tal cual
    return coincidencia[1];
  }
}

/**
 * Dispara la descarga de un archivo binario en el navegador con un enlace temporal.
 * Libera la URL del objeto al terminar, para no dejar memoria retenida.
 * @param blob contenido del archivo (PDF, XLSX o CSV)
 * @param nombreArchivo nombre sugerido para guardar, con su extensión
 */
export function descargarArchivo(blob: Blob, nombreArchivo: string): void {
  // 1. Un objeto URL apunta al Blob en memoria sin necesidad de subirlo a ningún lado
  const url = URL.createObjectURL(blob);
  // 2. Un <a download> temporal es la forma estándar de forzar la descarga desde JavaScript
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombreArchivo;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  // 3. Libera la URL del objeto; el navegador ya inició la descarga con el enlace anterior
  URL.revokeObjectURL(url);
}
