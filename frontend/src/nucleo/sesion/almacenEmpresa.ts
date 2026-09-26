/**
 * Empresa activa, solo en memoria (no se persiste en el navegador, CLAUDE.md §4.5 y §1.2.10).
 * Vive fuera de React para que el cliente HTTP la lea de forma síncrona en cada petición.
 */

/** Empresa activa actual; null antes de cargar `/me`. */
let empresaActivaId: string | null = null;
/** Suscriptores (React `useSyncExternalStore`) avisados en cada cambio. */
const oyentes = new Set<() => void>();

/** Devuelve el id de la empresa activa, o null. */
export function obtenerEmpresaActivaId(): string | null {
  return empresaActivaId;
}

/**
 * Fija la empresa activa y avisa a los suscriptores.
 * @param id identificador de la empresa (o null para limpiar)
 */
export function fijarEmpresaActivaId(id: string | null): void {
  empresaActivaId = id;
  oyentes.forEach((o) => o());
}

/**
 * Suscripción para `useSyncExternalStore`.
 * @param oyente función a invocar en cada cambio
 * @returns función para cancelar la suscripción
 */
export function suscribirEmpresaActiva(oyente: () => void): () => void {
  oyentes.add(oyente);
  return () => oyentes.delete(oyente);
}
