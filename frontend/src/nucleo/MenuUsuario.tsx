import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/compartido/ui/button';
import { useAuth } from '@/nucleo/auth/contextoAuth';
import { useSesion } from '@/nucleo/sesion/contextoSesion';
import { useEsAdmin } from '@/nucleo/sesion/useEsAdmin';

/**
 * Menú de usuario: su nombre y las acciones "Mi perfil", "Espacio de trabajo" y "API keys"
 * (estas dos solo para `admin_empresa`) y "Cerrar sesión". Accesible con teclado; Escape lo cierra.
 */
export function MenuUsuario() {
  const { usuario } = useSesion();
  const { cerrarSesion } = useAuth();
  const esAdmin = useEsAdmin();
  const [abierto, setAbierto] = useState(false);

  // Escape cierra el menú abierto
  useEffect(() => {
    if (!abierto) return;
    const alPulsar = (e: KeyboardEvent) => e.key === 'Escape' && setAbierto(false);
    document.addEventListener('keydown', alPulsar);
    return () => document.removeEventListener('keydown', alPulsar);
  }, [abierto]);

  /** Estilo común de los elementos del menú. */
  const claseItem = 'block w-full rounded px-3 py-2 text-left text-sm hover:bg-neutral-100';

  return (
    <div className="relative">
      <Button
        variant="ghost"
        aria-expanded={abierto}
        aria-haspopup="menu"
        onClick={() => setAbierto((v) => !v)}
      >
        {usuario.nombre}
      </Button>
      {abierto && (
        <div role="menu" className="absolute right-0 z-10 mt-1 w-52 rounded-md border bg-white p-1 shadow">
          <Link role="menuitem" to="/perfil" className={claseItem} onClick={() => setAbierto(false)}>
            Mi perfil
          </Link>
          {esAdmin && (
            <>
              <Link
                role="menuitem"
                to="/configuracion/espacio"
                className={claseItem}
                onClick={() => setAbierto(false)}
              >
                Espacio de trabajo
              </Link>
              <Link
                role="menuitem"
                to="/configuracion/api-keys"
                className={claseItem}
                onClick={() => setAbierto(false)}
              >
                API keys
              </Link>
            </>
          )}
          <button role="menuitem" className={claseItem} onClick={() => void cerrarSesion()}>
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}
