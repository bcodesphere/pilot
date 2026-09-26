import { QueryCache, QueryClient } from '@tanstack/react-query';
import { esErrorApi } from './http/errorApi';

/**
 * Crea el cliente de TanStack Query. Los errores 4xx de la API son definitivos (no se reintentan);
 * solo se reintenta ante fallos de red o 5xx.
 */
export function crearClienteConsultas(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache(),
    defaultOptions: {
      queries: {
        retry: (intentos, error) => (esErrorApi(error) && error.status < 500 ? false : intentos < 2),
        refetchOnWindowFocus: false,
      },
    },
  });
}
