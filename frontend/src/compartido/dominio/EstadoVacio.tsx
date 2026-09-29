import type { LucideIcon } from 'lucide-react';
import { Inbox } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Button } from '@/compartido/ui/button';

/** Acción que resuelve el estado vacío: un enlace interno (`href`) o una función (`onClick`). */
export interface AccionEstadoVacio {
  etiqueta: string;
  href?: string;
  onClick?: () => void;
}

/** Propiedades de {@link EstadoVacio}. */
export interface PropsEstadoVacio {
  titulo: string;
  descripcion: string;
  accion?: AccionEstadoVacio;
  /** Ícono decorativo; `Inbox` por defecto. */
  icono?: LucideIcon;
}

/**
 * Estado vacío con acción (spec F4.5 §7.1: "cada pantalla dice qué hacer después"). Se usa cuando
 * una lista no tiene filas, o cuando falta instalar Contabilidad para ver el Inicio.
 * @param props ver {@link PropsEstadoVacio}
 */
export function EstadoVacio({ titulo, descripcion, accion, icono: Icono = Inbox }: PropsEstadoVacio) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-[var(--radius-panel)] border border-dashed border-[var(--color-borde)] px-6 py-10 text-center">
      <Icono aria-hidden="true" className="size-8 text-[var(--color-texto-suave)]" />
      <div className="space-y-1">
        <h2 className="font-[family-name:var(--font-titulo)] text-base font-semibold text-[var(--color-texto)]">
          {titulo}
        </h2>
        <p className="text-sm text-[var(--color-texto-suave)]">{descripcion}</p>
      </div>
      {accion &&
        (accion.href ? (
          <Button asChild>
            <Link to={accion.href}>{accion.etiqueta}</Link>
          </Button>
        ) : (
          <Button onClick={accion.onClick}>{accion.etiqueta}</Button>
        ))}
    </div>
  );
}
