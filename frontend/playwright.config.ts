import { defineConfig } from '@playwright/test';

/** Configuración mínima de Playwright; los navegadores se instalan en F6 (`playwright install`). */
export default defineConfig({
  testDir: './e2e',
  use: { baseURL: 'http://localhost:5173' },
});
