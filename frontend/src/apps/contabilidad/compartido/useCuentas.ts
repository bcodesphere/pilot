import { useListarCuentasContables } from '@/api/cuentas-contables/cuentas-contables';
import type { CuentaContable } from '@/api/modelos';

/**
 * Carga el catálogo completo de cuentas de la empresa activa, una sola vez y sin paginar (ADR-035).
 * Lo comparten el catálogo, la configuración y las reglas, por lo que TanStack Query lo cachea.
 * @returns la lista (indefinida mientras carga) y la consulta para leer sus estados
 */
export function useCuentas() {
  const consulta = useListarCuentasContables();
  // La respuesta 2xx trae `data` con el arreglo de cuentas
  const cuentas = consulta.data?.data as CuentaContable[] | undefined;
  return { cuentas, consulta };
}
