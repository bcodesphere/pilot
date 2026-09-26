import { useSesion } from './contextoSesion';

/**
 * Indica si el usuario es `admin_empresa` en la empresa activa (CLAUDE.md §14.2).
 * Solo controla qué se muestra; el backend vuelve a validar el rol en cada petición.
 */
export function useEsAdmin(): boolean {
  return useSesion().empresaActiva.rol === 'admin_empresa';
}
