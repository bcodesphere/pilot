import { User } from 'oidc-client-ts';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { crearGestorSesion } from './gestorSesion';

describe('gestor de sesión OIDC', () => {
  afterEach(() => vi.restoreAllMocks());

  // Regla CLAUDE.md §1.2.10: los tokens nunca tocan localStorage ni sessionStorage.
  // Se espía `Storage.prototype.setItem` (sin nombrar los almacenamientos, que ESLint prohíbe) para ver todo lo que se escribe.
  it('tras un login simulado ningún token se escribe en el almacenamiento del navegador', async () => {
    const escrituras: string[] = [];
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((clave, valor) => {
      escrituras.push(`${clave}=${valor}`);
    });
    const gestor = crearGestorSesion({
      oidcAuthority: 'http://kc.test/realms/pilot',
      oidcClientId: 'pilot-web',
      apiBaseUrl: 'http://api.test',
    });
    // Guarda un usuario con los tres tokens, como haría el callback de login
    await gestor.storeUser(
      new User({
        access_token: 'AT-secreto',
        id_token: 'IDT-secreto',
        refresh_token: 'RT-secreto',
        token_type: 'Bearer',
        profile: { sub: 'u1', iss: 'x', aud: 'pilot-web', exp: 1, iat: 1 },
        expires_at: Math.floor(Date.now() / 1000) + 300,
      }),
    );

    // El usuario sí está disponible en memoria...
    expect((await gestor.getUser())?.access_token).toBe('AT-secreto');
    // ...pero nada de lo escrito en el navegador contiene tokens ni sus nombres
    expect(escrituras.join('\n')).not.toMatch(
      /access_token|id_token|refresh_token|AT-secreto|IDT-secreto|RT-secreto/,
    );
    expect(escrituras).toHaveLength(0);
  });
});
