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
  test: {
    // jsdom simula el navegador para las pruebas de componentes
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/pruebas-setup.ts'],
    // e2e lo ejecuta Playwright, no Vitest
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
