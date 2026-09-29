import { useId } from 'react';
import { Label } from '@/compartido/ui/label';
import { Select } from '@/compartido/ui/select';
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
      <Label htmlFor={id} className="text-[var(--color-texto-suave)]">
        Empresa
      </Label>
      <Select
        id={id}
        value={empresaActiva.empresaId}
        onChange={(e) => cambiarEmpresa(e.target.value)}
        className="h-8 w-auto"
      >
        {usuario.membresias.map((m) => (
          <option key={m.empresaId} value={m.empresaId}>
            {m.nombreEmpresa}
          </option>
        ))}
      </Select>
    </div>
  );
}
