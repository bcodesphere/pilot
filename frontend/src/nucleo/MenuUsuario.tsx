import { useState } from 'react';
import { Button } from '@/compartido/ui/button';
import { useAuth } from '@/nucleo/auth/contextoAuth';
import { useSesion } from '@/nucleo/sesion/contextoSesion';

/** Menú de usuario: muestra su nombre y la acción "Cerrar sesión" (accesible con teclado). */
export function MenuUsuario() {
  const { usuario } = useSesion();
  const { cerrarSesion } = useAuth();
  const [abierto, setAbierto] = useState(false);

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
        <div role="menu" className="absolute right-0 z-10 mt-1 w-44 rounded-md border bg-white p-1 shadow">
          <button
            role="menuitem"
            className="w-full rounded px-3 py-2 text-left text-sm hover:bg-neutral-100"
            onClick={() => void cerrarSesion()}
          >
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}
