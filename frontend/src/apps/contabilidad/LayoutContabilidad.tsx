import { Outlet } from 'react-router-dom';
import { Alert } from '@/compartido/ui/alert';
import { usePermisosContabilidad } from './usePermisosContabilidad';

/**
 * Diseño de la app Contabilidad: solo la pantalla activa (`<Outlet />`); la subnavegación propia
 * (Libro Diario, Mayor, Reportes, Catálogo, Configuración, Reglas) se quitó en ADR-043 U2 fase B: la
 * barra lateral ya la reemplaza, y Configuración y Reglas pasaron a pestañas de `/configuracion`.
 * Un rol sin acceso a la contabilidad ve un aviso; el backend responde 403 `PLT-010` de todos modos.
 */
export function LayoutContabilidad() {
  const { puedeLeer } = usePermisosContabilidad();
  if (!puedeLeer) return <Alert variant="error">No tienes permiso para ver esta página</Alert>;

  return <Outlet />;
}
