import type { Rol } from '@/api/modelos';
import { app, json, membresia, montarShell, type OpcionesShell } from '@/nucleo/pruebas-arnes';

/** Apoyo de las pruebas de las pantallas de Contabilidad (solo lo usan los `*.test.tsx`). */

/** Respuesta JSON con `ETag`, como las de lectura y edición de cuentas, configuración y reglas. */
export function conEtag(cuerpo: unknown, version: number, status = 200): Response {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'content-type': 'application/json', ETag: `"${version}"` },
  });
}

/** Problem Details de error de negocio con su código. */
export function problema(status: number, codigo: string, extra: Record<string, unknown> = {}): Response {
  return json({ status, codigo, title: 'Error', ...extra }, status);
}

/** Monta el shell en una ruta de Contabilidad, con la app instalada y el rol indicado. */
export function montarContabilidad(
  ruta: string,
  rol: Rol,
  manejador: NonNullable<OpcionesShell['manejador']>,
) {
  return montarShell({
    ruta,
    membresias: [membresia('emp-1', 'Espacio de Ana', rol)],
    apps: [app('contabilidad', 'INSTALADA')],
    manejador,
  });
}

/** Llamadas hechas con un método y una ruta que termina en `sufijo` (se ignora la cadena de consulta). */
export function llamadas(fetchMock: { mock: { calls: unknown[][] } }, metodo: string, sufijo: string) {
  return fetchMock.mock.calls.filter((c) => {
    const init = (c[1] ?? {}) as RequestInit;
    return String(c[0]).split('?')[0]!.endsWith(sufijo) && (init.method ?? 'GET') === metodo;
  });
}

/** Respuesta binaria simulada de una exportación (F4-05, ADR-038): PDF, XLSX o CSV con `Content-Disposition`. */
export function respuestaArchivo(nombreArchivo: string, tipo: string, contenido = 'contenido'): Response {
  return new Response(contenido, {
    status: 200,
    headers: { 'content-type': tipo, 'content-disposition': `attachment; filename="${nombreArchivo}"` },
  });
}
