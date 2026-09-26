import { defineConfig } from 'orval';

/**
 * Orval genera el cliente de la API desde el contrato OpenAPI (ADR-003).
 * Todo lo que hay en `src/api/` es código generado: nunca se edita a mano.
 */
export default defineConfig({
  pilot: {
    input: '../api-spec/openapi/pilot-v1.yaml',
    output: {
      // Un archivo por etiqueta (tag) del contrato, con esquemas de tipos aparte
      mode: 'tags-split',
      target: 'src/api',
      schemas: 'src/api/modelos',
      client: 'react-query', // Hooks de TanStack Query
      clean: true, // Borra lo generado antes para no dejar archivos obsoletos
      // Genera hooks `useQuery` para las operaciones de lectura
      override: {
        query: { useQuery: true },
        // Todas las llamadas pasan por el cliente HTTP del shell: antepone la base `/api/v1`, agrega
        // Authorization y X-Empresa-Id, renueva el token ante 401 y lanza `ErrorApi` en respuestas no 2xx
        mutator: { path: './src/nucleo/http/clienteHttp.ts', name: 'clienteHttp' },
      },
      // Nota: los montos salen como `string` porque así lo declara el contrato (ADR-013); Orval no los convierte
    },
  },
});
