import { createContext, useContext } from 'react';
import type { Membresia, UsuarioActual } from '@/api/modelos';

/** Valor del contexto de sesión: usuario, membresías y empresa activa. */
export interface ValorSesion {
  /** Usuario autenticado (`GET /me`). */
  usuario: UsuarioActual;
  /** Membresía de la empresa activa. */
  empresaActiva: Membresia;
  /** Cambia de empresa: vacía la caché y vuelve al lanzador. */
  cambiarEmpresa: (empresaId: string) => void;
  /** Vuelve a cargar `/me` y elige otra membresía válida si la activa ya no lo es (403 PLT-003). */
  recargarUsuario: () => Promise<void>;
}

/** Contexto de sesión; se exporta para que las pruebas puedan proveer un valor sin red. */
export const ContextoSesion = createContext<ValorSesion | null>(null);

/**
 * Acceso al usuario y la empresa activa.
 * @throws Error si se usa fuera de `ProveedorSesion`
 */
export function useSesion(): ValorSesion {
  const ctx = useContext(ContextoSesion);
  if (!ctx) throw new Error('useSesion requiere ProveedorSesion');
  return ctx;
}
