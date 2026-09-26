import { describe, expect, it } from 'vitest';
import { ErrorConfiguracion, leerEntorno } from './entorno';

describe('configuración del entorno', () => {
  // Regla: la app falla al arrancar con un mensaje claro si falta una variable (tarea 1)
  it('lanza un error claro que lista las variables ausentes', () => {
    expect(() => leerEntorno({ VITE_OIDC_CLIENT_ID: 'pilot-web' })).toThrowError(ErrorConfiguracion);
    expect(() => leerEntorno({})).toThrowError(/VITE_OIDC_AUTHORITY.*VITE_OIDC_CLIENT_ID.*VITE_API_BASE_URL/);
  });

  // Regla: la base de la API se normaliza sin barra final para concatenar rutas del contrato
  it('quita la barra final de la base de la API', () => {
    const e = leerEntorno({
      VITE_OIDC_AUTHORITY: 'http://kc/realms/pilot',
      VITE_OIDC_CLIENT_ID: 'pilot-web',
      VITE_API_BASE_URL: 'http://api/api/v1/',
    });
    expect(e.apiBaseUrl).toBe('http://api/api/v1');
  });
});
