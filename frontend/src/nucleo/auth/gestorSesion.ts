import { InMemoryWebStorage, UserManager, WebStorageStateStore, type User } from 'oidc-client-ts';
import type { Entorno } from '@/nucleo/config/entorno';

/**
 * Subconjunto de `UserManager` que usa el shell; permite simularlo en las pruebas.
 */
export type GestorSesion = Pick<
  UserManager,
  'getUser' | 'signinRedirect' | 'signinRedirectCallback' | 'signinSilent' | 'signoutRedirect'
>;

/** Ruta a la que Keycloak devuelve al usuario tras autenticarse. */
export const RUTA_CALLBACK = '/auth/callback';

/** Estado que viaja con la redirección de login para volver a la ruta de destino. */
export interface EstadoLogin {
  /** Ruta (con búsqueda y hash) a la que volver tras autenticarse. */
  ruta: string;
}

/**
 * Crea el `UserManager` de OIDC con Code + PKCE (S256) contra Keycloak (ADR-008).
 * Los tokens viven solo en memoria (CLAUDE.md §1.2.10): el `userStore` es `InMemoryWebStorage`.
 * @param entorno configuración validada
 */
export function crearGestorSesion(entorno: Entorno): UserManager {
  return new UserManager({
    authority: entorno.oidcAuthority,
    client_id: entorno.oidcClientId,
    redirect_uri: `${window.location.origin}${RUTA_CALLBACK}`,
    post_logout_redirect_uri: window.location.origin,
    response_type: 'code', // Code + PKCE S256 (oidc-client-ts usa S256 por defecto)
    scope: 'openid profile email',
    // 1. Tokens y usuario solo en memoria: se pierden al recargar, y entonces el SSO de Keycloak re-autentica sin pedir credenciales
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    // 2. Estado temporal de PKCE (state y code_verifier): debe sobrevivir a la redirección a Keycloak.
    //    ÚNICA excepción a la regla de almacenamiento; nunca contiene access, id ni refresh token.
    // eslint-disable-next-line no-restricted-properties
    stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
    // 3. Renueva el token con el refresh token antes de que venza (sin iframes)
    automaticSilentRenew: true,
  });
}

/** Indica si el usuario en memoria sigue vigente. */
export function sesionVigente(usuario: User | null): usuario is User {
  return usuario !== null && !usuario.expired;
}
