import { Link } from 'react-router-dom';
import { Button } from '@/compartido/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/compartido/ui/dropdown-menu';
import { useAuth } from '@/nucleo/auth/contextoAuth';
import { useSesion } from '@/nucleo/sesion/contextoSesion';
import { useEsAdmin } from '@/nucleo/sesion/useEsAdmin';

/**
 * Menú de usuario (ADR-043): su nombre y las acciones "Mi perfil", "Espacio de trabajo" y "API keys"
 * (estas dos solo para `admin_empresa`) y "Cerrar sesión". Construido sobre `DropdownMenu`
 * (`@radix-ui/react-dropdown-menu`): roving tabindex, cierre con Escape y clic fuera de forma
 * consistente con el resto de menús de la barra superior.
 */
export function MenuUsuario() {
  const { usuario } = useSesion();
  const { cerrarSesion } = useAuth();
  const esAdmin = useEsAdmin();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost">{usuario.nombre}</Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem asChild>
          <Link to="/perfil">Mi perfil</Link>
        </DropdownMenuItem>
        {esAdmin && (
          <>
            <DropdownMenuItem asChild>
              <Link to="/configuracion/espacio">Espacio de trabajo</Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link to="/configuracion/api-keys">API keys</Link>
            </DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => void cerrarSesion()}>Cerrar sesión</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
