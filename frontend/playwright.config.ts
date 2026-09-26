import { defineConfig, devices } from '@playwright/test';

/**
 * Configuración de Playwright para el e2e de F1-10 (docs/plan-de-trabajo.md, F1).
 *
 * El e2e necesita el entorno real: compose (PostgreSQL, Keycloak y Mailpit) y el backend en `dev,api`, que levanta el
 * usuario porque requiere el `.env`. El servidor de Vite sí lo levanta Playwright (o reutiliza el que ya corra).
 * Las direcciones de Keycloak (`E2E_KEYCLOAK_URL`), Mailpit (`E2E_MAILPIT_URL`, la lee el spec) y la API (`E2E_API_URL`)
 * se leen de variables `E2E_*` con los valores locales por defecto.
 */

/** Direcciones del entorno real; se pueden sobrescribir con variables `E2E_*` (valores locales por defecto). */
const URL_KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180';
const URL_API = process.env.E2E_API_URL ?? 'http://localhost:8080';

/** URL del frontend en desarrollo; debe coincidir con las `redirectUris` del cliente `pilot-web` del realm. */
const URL_FRONTEND = 'http://localhost:5173';

export default defineConfig({
  testDir: './e2e',
  // Un solo trabajador: el e2e comparte Keycloak, Mailpit y la base, y cada prueba crea su propio usuario
  workers: 1,
  // La verificación de correo, el TOTP (que exige esperar al siguiente período de 30 s) y dos inicios de sesión son lentos
  timeout: 240_000,
  expect: { timeout: 15_000 },
  // Evidencia del recorrido: traza, video y captura de cada prueba (en `test-results/`)
  use: {
    baseURL: URL_FRONTEND,
    trace: 'on',
    video: 'on',
    screenshot: 'on',
  },
  // Solo Chromium (decisión de F1-10)
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Vite con las variables VITE_* que apuntan al Keycloak y a la API locales; reutiliza un servidor ya iniciado
  webServer: {
    command: 'pnpm dev',
    url: URL_FRONTEND,
    reuseExistingServer: true,
    timeout: 60_000,
    env: {
      VITE_OIDC_AUTHORITY: `${URL_KEYCLOAK}/realms/pilot`,
      VITE_OIDC_CLIENT_ID: 'pilot-web',
      VITE_API_BASE_URL: `${URL_API}/api/v1`,
    },
  },
});
