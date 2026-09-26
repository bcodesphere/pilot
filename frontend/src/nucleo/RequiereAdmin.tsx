import type { ReactNode } from 'react';
import { Alert } from '@/compartido/ui/alert';
import { useEsAdmin } from './sesion/useEsAdmin';

/**
 * Guardia de rutas de administración: muestra el contenido solo al `admin_empresa` de la empresa activa;
 * con otro rol, un aviso de falta de permiso (el backend responde 403 `PLT-010` de todos modos).
 * @param props.children pantalla de administración
 */
export function RequiereAdmin({ children }: { children: ReactNode }) {
  const esAdmin = useEsAdmin();
  if (!esAdmin) return <Alert variant="error">No tienes permiso para ver esta página</Alert>;
  return <>{children}</>;
}
