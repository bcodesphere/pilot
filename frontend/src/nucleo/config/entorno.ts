/**
 * Configuración del frontend leída de las variables `VITE_*` (ver `.env.example`).
 * Se valida al arrancar para fallar con un mensaje claro y no con errores confusos más adelante.
 */

/** Configuración validada del entorno. */
export interface Entorno {
  /** Emisor OIDC (realm `pilot` de Keycloak). */
  oidcAuthority: string;
  /** Identificador del cliente público con PKCE. */
  oidcClientId: string;
  /** Base de la API REST, sin barra final (p. ej. `http://localhost:8080/api/v1`). */
  apiBaseUrl: string;
}

/** Error de configuración: lista las variables ausentes o inválidas. */
export class ErrorConfiguracion extends Error {
  /**
   * @param problemas descripción de cada variable ausente o inválida
   */
  constructor(readonly problemas: string[]) {
    super(`Configuración del frontend incompleta: ${problemas.join('; ')}`);
    this.name = 'ErrorConfiguracion';
  }
}

/**
 * Lee y valida las variables de entorno requeridas.
 * @param env origen de las variables (por defecto `import.meta.env`; se inyecta en pruebas)
 * @returns la configuración validada
 * @throws ErrorConfiguracion si falta una variable o una URL es inválida
 */
export function leerEntorno(env: Record<string, unknown> = import.meta.env): Entorno {
  const problemas: string[] = [];

  // 1. Toma cada variable, exigiendo texto no vacío
  const requerida = (nombre: string, esUrl: boolean): string => {
    const valor = env[nombre];
    if (typeof valor !== 'string' || valor.trim() === '') {
      problemas.push(`falta ${nombre}`);
      return '';
    }
    // 2. Las URL deben poder interpretarse; así se detecta un valor mal escrito
    if (esUrl && !URL.canParse(valor)) {
      problemas.push(`${nombre} no es una URL válida`);
    }
    return valor.trim();
  };

  const oidcAuthority = requerida('VITE_OIDC_AUTHORITY', true);
  const oidcClientId = requerida('VITE_OIDC_CLIENT_ID', false);
  const apiBaseUrl = requerida('VITE_API_BASE_URL', true);

  // 3. Un solo error con todos los problemas juntos
  if (problemas.length > 0) throw new ErrorConfiguracion(problemas);

  // 4. Sin barra final, para poder concatenar rutas que empiezan con `/`
  return { oidcAuthority, oidcClientId, apiBaseUrl: apiBaseUrl.replace(/\/+$/, '') };
}
