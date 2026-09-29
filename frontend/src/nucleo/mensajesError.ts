import { mensajeDeError as mensajeDelCatalogo } from '@/compartido/errores/catalogoErrores';
import { esErrorApi } from './http/errorApi';

/** Mensaje genérico cuando el error no se reconoce (no se expone detalle interno, CLAUDE.md §8.4). */
export const MENSAJE_GENERICO = 'No pudimos completar la operación. Inténtalo de nuevo.';

/**
 * Traduce un error de la API a un mensaje en español según su código de negocio.
 *
 * Se mantiene por compatibilidad con las pantallas existentes (devuelve una cadena, no la acción):
 * el catálogo único de `compartido/errores/catalogoErrores` (ADR-043) es la fuente de verdad de los
 * mensajes; `porCodigo` solo permite que una pantalla concreta lo sobrescriba con un texto más
 * específico para su contexto.
 * @param error error capturado de una consulta o mutación
 * @param porCodigo mensajes propios de la pantalla, por código (`PLT-xxx`), que tienen prioridad
 * @returns el mensaje de `porCodigo` si existe; si no, el del catálogo; si no, el genérico
 */
export function mensajeDeError(error: unknown, porCodigo: Record<string, string> = {}): string {
  if (esErrorApi(error) && error.codigo) {
    const propio = porCodigo[error.codigo];
    if (propio) return propio;
    return mensajeDelCatalogo({ codigo: error.codigo, detail: error.detail, diferencia: error.diferencia })
      .mensaje;
  }
  return MENSAJE_GENERICO;
}
