import { createContext, useContext } from 'react';

/** Valor que expone el contexto de autenticación. */
export interface ValorAuth {
  /** Cierra la sesión local y en Keycloak; vuelve al origen de la app. */
  cerrarSesion: () => Promise<void>;
}

/** Contexto de autenticación; se exporta para que las pruebas puedan proveer un valor. */
export const ContextoAuth = createContext<ValorAuth | null>(null);

/**
 * Acceso a las acciones de autenticación.
 * @throws Error si se usa fuera de `ProveedorAuth`
 */
export function useAuth(): ValorAuth {
  const ctx = useContext(ContextoAuth);
  if (!ctx) throw new Error('useAuth requiere ProveedorAuth');
  return ctx;
}
