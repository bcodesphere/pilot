import { lazy, type ComponentType, type LazyExoticComponent } from 'react';
import { useRoutes, type RouteObject } from 'react-router-dom';

/**
 * Registro dinámico de apps del frontend (ADR-021).
 * Cada app vive en `src/apps/<codigo>/` y exporta `modulo` desde `modulo.tsx`. El shell las descubre
 * con `import.meta.glob`: agregar una app (o una fila en `aplicacion`) no exige editar una lista aquí.
 * Los módulos se cargan de forma perezosa (`React.lazy`, corrección 1 de U1: carga diferida por
 * ruta): cada app solo entra al bundle cuando alguien navega a su ruta. Los componentes perezosos se
 * crean una sola vez aquí, en el ámbito del módulo (nunca dentro de un render, regla
 * `react-hooks/static-components`).
 */

/** Contrato que cumple el módulo de una app. */
export interface ModuloApp {
  /** Rutas de la app, relativas a `/<codigo>/`. */
  rutas: RouteObject[];
}

/** Cargadores perezosos encontrados en `src/apps/<codigo>/modulo.tsx`, indexados por la ruta del archivo. */
const cargadores = import.meta.glob<{ modulo: ModuloApp }>('/src/apps/*/modulo.tsx');

/** Un componente perezoso por app: al montarse, importa el módulo y registra sus rutas con `useRoutes`. */
const componentesPorCodigo = new Map<string, LazyExoticComponent<ComponentType>>(
  Object.entries(cargadores).flatMap(([ruta, cargar]) => {
    const codigo = /\/src\/apps\/([^/]+)\/modulo\.tsx$/.exec(ruta)?.[1];
    if (!codigo) return [];
    const Componente = lazy(async () => {
      const { modulo } = await cargar();
      // Función nombrada (no una flecha anónima): el linter de hooks solo reconoce como
      // componente algo con nombre en mayúscula, aunque el módulo la exponga como `default`
      function RutasDeLaApp() {
        return useRoutes(modulo.rutas);
      }
      return { default: RutasDeLaApp };
    });
    return [[codigo, Componente] as const];
  }),
);

/**
 * Indica si el frontend tiene un módulo para esa app, sin importarlo (las claves se conocen en
 * tiempo de compilación aunque la carga sea perezosa).
 * @param codigo código de la app (p. ej. `contabilidad`)
 */
export function tieneModuloApp(codigo: string): boolean {
  return componentesPorCodigo.has(codigo);
}

/**
 * Devuelve el componente perezoso que monta las rutas de una app.
 * @param codigo código de la app (p. ej. `contabilidad`)
 * @returns el componente, o `undefined` si el frontend aún no tiene módulo para esa app
 */
export function obtenerComponenteApp(codigo: string): LazyExoticComponent<ComponentType> | undefined {
  return componentesPorCodigo.get(codigo);
}
