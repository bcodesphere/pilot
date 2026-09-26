import type { RouteObject } from 'react-router-dom';

/**
 * Registro dinámico de apps del frontend (ADR-021).
 * Cada app vive en `src/apps/<codigo>/` y exporta `modulo` desde `modulo.tsx`. El shell las descubre
 * con `import.meta.glob`: agregar una app (o una fila en `aplicacion`) no exige editar una lista aquí.
 */

/** Contrato que cumple el módulo de una app. */
export interface ModuloApp {
  /** Rutas de la app, relativas a `/<codigo>/`. */
  rutas: RouteObject[];
}

/** Módulos encontrados en `src/apps/<codigo>/modulo.tsx`, indexados por la ruta del archivo. */
const encontrados = import.meta.glob<{ modulo: ModuloApp }>('/src/apps/*/modulo.tsx', { eager: true });

/** Módulos indexados por código de app (el nombre de la carpeta). */
const porCodigo = new Map<string, ModuloApp>(
  Object.entries(encontrados).flatMap(([ruta, m]) => {
    const codigo = /\/src\/apps\/([^/]+)\/modulo\.tsx$/.exec(ruta)?.[1];
    return codigo ? [[codigo, m.modulo] as const] : [];
  }),
);

/**
 * Busca el módulo de frontend de una app.
 * @param codigo código de la app (p. ej. `contabilidad`)
 * @returns el módulo, o undefined si el frontend aún no lo tiene
 */
export function obtenerModuloApp(codigo: string): ModuloApp | undefined {
  return porCodigo.get(codigo);
}
