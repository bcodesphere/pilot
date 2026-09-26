import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import prettier from 'eslint-config-prettier';

/**
 * Configuración de ESLint (flat config).
 * `src/api/` es código generado por Orval y queda excluido.
 */
export default tseslint.config(
  // Exclusiones: código generado y salidas de compilación
  { ignores: ['dist', 'src/api', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended, prettier],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks, 'react-refresh': reactRefresh },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // Los tokens nunca se guardan en el navegador (CLAUDE.md §1.2.10)
      'no-restricted-globals': [
        'error',
        { name: 'localStorage', message: 'Prohibido: los tokens viven solo en memoria.' },
        { name: 'sessionStorage', message: 'Prohibido: los tokens viven solo en memoria.' },
      ],
      'no-restricted-properties': [
        'error',
        ...['localStorage', 'sessionStorage'].flatMap((o) =>
          ['window', 'globalThis', 'self'].map((g) => ({
            object: g,
            property: o,
            message: 'Prohibido: los tokens viven solo en memoria.',
          })),
        ),
      ],
    },
  },
  {
    // Ninguna app importa código de otra app (ADR-021): solo del shell público y de `compartido`
    files: ['src/apps/*/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              regex: '^@/apps/',
              message: 'Una app no puede importar código de otra app (ADR-021).',
            },
            {
              regex: '^(\\.\\./)+([^.].*/)?apps/',
              message: 'Una app no puede importar código de otra app (ADR-021).',
            },
          ],
        },
      ],
    },
  },
  {
    // Módulos de dinero: prohibido convertir a number (CLAUDE.md §1.2.2)
    files: ['src/compartido/dinero/**/*.ts'],
    ignores: ['**/*.test.ts'],
    rules: {
      'no-restricted-globals': [
        'error',
        { name: 'parseFloat', message: 'Use decimal.js, nunca number, para dinero.' },
        { name: 'parseInt', message: 'Use decimal.js, nunca number, para dinero.' },
        { name: 'Number', message: 'Use decimal.js, nunca number, para dinero.' },
        { name: 'localStorage', message: 'Prohibido: los tokens viven solo en memoria.' },
        { name: 'sessionStorage', message: 'Prohibido: los tokens viven solo en memoria.' },
      ],
      'no-restricted-properties': [
        'error',
        { object: 'Number', property: 'parseFloat', message: 'Use decimal.js para dinero.' },
        { object: 'Number', property: 'parseInt', message: 'Use decimal.js para dinero.' },
        { object: 'Decimal', property: 'toNumber', message: 'Use decimal.js para dinero.' },
      ],
      'no-restricted-syntax': [
        'error',
        // El `+` unario convierte a number: `+valor`
        { selector: "UnaryExpression[operator='+']", message: 'El + unario convierte a number.' },
        {
          selector: "CallExpression[callee.property.name='toNumber']",
          message: 'Use decimal.js, no number.',
        },
      ],
    },
  },
);
