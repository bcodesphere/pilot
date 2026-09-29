// Agrega los matchers de jest-dom (toBeInTheDocument, etc.) a Vitest
import '@testing-library/jest-dom/vitest';
import { configure } from '@testing-library/react';

/**
 * La carga perezosa por ruta (corrección 1 de U1) agrega un salto asíncrono (la importación
 * dinámica del módulo) antes de que una pantalla aparezca. Con la concurrencia por defecto de
 * Vitest (un proceso por archivo) el tiempo de espera de 1000 ms de `findBy*`/`waitFor` a veces no
 * alcanza bajo carga de CPU alta; se amplía a 5000 ms para toda la suite (no cambia el resultado de
 * una prueba que ya pasaba: `findBy*` se resuelve en cuanto aparece la condición, no espera el máximo).
 *
 * Este valor debe quedar siempre por debajo del `testTimeout` de Vitest (`vite.config.ts`,
 * corrección 2 de U1, 15000 ms): así, una espera que de verdad se cuelga falla primero por este
 * timeout con el mensaje propio de Testing Library ("Unable to find element…"), en vez de que
 * Vitest mate la prueba antes con el genérico "Test timed out".
 */
configure({ asyncUtilTimeout: 5000 });

/**
 * jsdom simula un ancho de 1024 px por defecto, por debajo del quiebre de 1280 px de la barra
 * lateral (ADR-043): sin esto, toda prueba vería la barra contraída a íconos. Se fija un ancho de
 * escritorio (como en la revisión del arquitecto, spec F4.5 §9.1); las pruebas de `EstructuraApp`
 * que verifican el modo contraído sobrescriben `window.innerWidth` a propósito.
 */
window.innerWidth = 1440;

/**
 * jsdom no implementa `window.matchMedia` (ADR-043: lo usa `compartido/ui/sidebar` para el quiebre
 * de 1280 px). Se simula evaluando `(max-width: Npx)` contra `window.innerWidth`, para que una
 * prueba pueda fijar `window.innerWidth` y comprobar la barra contraída.
 */
if (typeof window.matchMedia !== 'function') {
  window.matchMedia = (query: string) => {
    const maximo = /\(max-width:\s*(\d+)px\)/.exec(query);
    const matches = maximo ? window.innerWidth <= Number(maximo[1]) : false;
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    } as MediaQueryList;
  };
}

/**
 * jsdom no implementa `ResizeObserver` (lo usa `cmdk`, base del buscador global `Ctrl+K`). Un
 * observador nulo basta: las pruebas no dependen de medir el tamaño real de la lista.
 */
if (typeof window.ResizeObserver !== 'function') {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

/** jsdom no implementa `scrollIntoView` (lo usa `cmdk` al resaltar el elemento seleccionado). */
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = () => {};
}
