import { useListarAplicaciones } from '@/api/aplicaciones/aplicaciones';
import type { AplicacionCatalogo } from '@/api/modelos';

/**
 * Catálogo de apps con su estado para la empresa activa (`GET /aplicaciones`).
 * El lanzador y las rutas de apps comparten esta consulta, así que no hay lista fija en el código.
 * @returns apps (vacío mientras carga), y banderas de carga y error
 */
export function useCatalogoApps(): { apps: AplicacionCatalogo[]; cargando: boolean; error: boolean } {
  const consulta = useListarAplicaciones();
  // El cliente HTTP lanza en no-2xx, así que un dato presente es siempre la respuesta 200
  const apps = (consulta.data?.data as AplicacionCatalogo[] | undefined) ?? [];
  return { apps, cargando: consulta.isPending, error: consulta.isError };
}
