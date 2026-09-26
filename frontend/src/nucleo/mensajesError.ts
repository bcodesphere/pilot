import { esErrorApi } from './http/errorApi';

/** Mensaje genérico cuando el error no se reconoce (no se expone detalle interno, CLAUDE.md §8.4). */
export const MENSAJE_GENERICO = 'No pudimos completar la operación. Inténtalo de nuevo.';

/**
 * Traduce un error de la API a un mensaje en español según su código de negocio.
 * @param error error capturado de una consulta o mutación
 * @param porCodigo mensajes propios de la pantalla, por código (`PLT-xxx`)
 * @returns el mensaje del código si existe; si no, el genérico
 */
export function mensajeDeError(error: unknown, porCodigo: Record<string, string> = {}): string {
  if (esErrorApi(error) && error.codigo) {
    return porCodigo[error.codigo] ?? MENSAJE_GENERICO;
  }
  return MENSAJE_GENERICO;
}
