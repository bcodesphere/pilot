/// <reference types="vitest/config" />
import path from 'node:path';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

/** Configuración de Vite: React, Tailwind, alias `@` → `src` y entorno de pruebas Vitest. */
export default defineConfig({
  // Plugins de compilación de React y de Tailwind CSS v4
  plugins: [react(), tailwindcss()],
  // El alias `@/` evita rutas relativas largas (coincide con components.json de shadcn/ui)
  resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
  build: {
    rollupOptions: {
      output: {
        /**
         * Separa las dependencias grandes de `node_modules` en sus propios fragmentos
         * (corrección 1 de U1, ADR-043: ningún fragmento mayor a 500 KB). Las rutas del núcleo y
         * de cada app ya se cargan por separado (`React.lazy`, `nucleo/paginasPerezosas.tsx`,
         * `nucleo/apps/registro.ts`); esto evita que las bibliotecas de terceros queden todas
         * juntas en el fragmento de entrada.
         */
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (/[\\/](react|react-dom|scheduler)[\\/]/.test(id)) return 'vendor-react';
          if (id.includes('react-router')) return 'vendor-router';
          if (id.includes('@tanstack')) return 'vendor-tanstack';
          if (id.includes('@radix-ui')) return 'vendor-radix';
          if (id.includes('cmdk')) return 'vendor-cmdk';
          if (id.includes('react-day-picker') || id.includes('date-fns')) return 'vendor-calendario';
          if (id.includes('decimal.js')) return 'vendor-decimal';
          if (id.includes('lucide-react')) return 'vendor-iconos';
          if (id.includes('oidc-client-ts')) return 'vendor-oidc';
          return 'vendor';
        },
      },
    },
  },
  test: {
    // jsdom simula el navegador para las pruebas de componentes
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/pruebas-setup.ts'],
    // e2e lo ejecuta Playwright, no Vitest
    include: ['src/**/*.test.{ts,tsx}'],
    /**
     * Corrección 2 de U1: con la carga diferida por ruta (corrección 1), cada prueba de una
     * pantalla de Contabilidad monta ahora `EstructuraApp` y espera la importación dinámica del
     * módulo antes de ver el contenido, lo que añade un salto asíncrono real (ADR-043). El
     * `testTimeout` por defecto de Vitest (5000 ms) coincide con el `asyncUtilTimeout` de
     * `pruebas-setup.ts`: bajo carga, Vitest puede matar la prueba por tiempo antes de que
     * `findBy*`/`waitFor` alcance a fallar con su propio mensaje. Se amplía solo el límite de
     * Vitest; `asyncUtilTimeout` se mantiene en 5000 ms (ver pruebas-setup.ts) para que siga siendo
     * siempre menor que `testTimeout` y una prueba realmente colgada falle con el mensaje de
     * Testing Library, no con "Test timed out".
     */
    testTimeout: 15000,
  },
});
