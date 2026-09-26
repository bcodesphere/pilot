import { useId } from 'react';
import { useSesion } from '@/nucleo/sesion/contextoSesion';

/**
 * Selector de empresa activa. Solo se muestra con más de una membresía (ADR-032): en 1.0 cada
 * persona tiene una sola, así que normalmente no aparece. Cambiar de empresa vacía la caché de consultas.
 */
export function SelectorEmpresa() {
  const { usuario, empresaActiva, cambiarEmpresa } = useSesion();
  const id = useId();
  if (usuario.membresias.length <= 1) return null;

  return (
    <div className="flex items-center gap-2 text-sm">
      <label htmlFor={id} className="text-neutral-600">
        Empresa
      </label>
      <select
        id={id}
        value={empresaActiva.empresaId}
        onChange={(e) => cambiarEmpresa(e.target.value)}
        className="rounded-md border border-neutral-300 bg-white px-2 py-1"
      >
        {usuario.membresias.map((m) => (
          <option key={m.empresaId} value={m.empresaId}>
            {m.nombreEmpresa}
          </option>
        ))}
      </select>
    </div>
  );
}
