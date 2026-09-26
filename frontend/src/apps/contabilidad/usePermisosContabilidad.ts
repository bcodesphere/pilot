import { useSesion } from '@/nucleo/sesion/contextoSesion';

/** Permisos del usuario sobre la app Contabilidad en la empresa activa. */
export interface PermisosContabilidad {
  /** Puede crear y editar (catálogo, configuración y reglas): `contador` o `admin_empresa`. */
  puedeEscribir: boolean;
  /** Puede ver las pantallas: quien escribe, más el `auditor` (solo lectura). */
  puedeLeer: boolean;
}

/**
 * Deduce los permisos de Contabilidad del rol de la empresa activa (CLAUDE.md §14.2, §13).
 * Solo decide qué controles se muestran; el backend vuelve a validar el rol en cada petición (`PLT-010`).
 */
export function usePermisosContabilidad(): PermisosContabilidad {
  const rol = useSesion().empresaActiva.rol;
  // 1. Escribir: el contador y el admin_empresa (que incluye todo lo del contador)
  const puedeEscribir = rol === 'contador' || rol === 'admin_empresa';
  // 2. Leer: además de ellos, el auditor
  return { puedeEscribir, puedeLeer: puedeEscribir || rol === 'auditor' };
}
