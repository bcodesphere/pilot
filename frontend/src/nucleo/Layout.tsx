import { EstructuraApp } from './estructura/EstructuraApp';

/**
 * Punto de entrada del layout del shell, referenciado por `router.tsx`. Desde ADR-043 (F4.5) la
 * composición real vive en `nucleo/estructura/EstructuraApp` (barra lateral + barra superior +
 * `<Outlet/>`); este archivo se conserva para no mover la ruta raíz del árbol de rutas.
 */
export function Layout() {
  return <EstructuraApp />;
}
