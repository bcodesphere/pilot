import { CircleAlert, TriangleAlert } from 'lucide-react';
import { Link } from 'react-router-dom';

/** Propiedades de {@link Pendiente}. */
export interface PropsPendiente {
  /** `alerta` (ámbar) para algo que conviene revisar; `error` (rojo) para algo que bloquea. */
  severidad: 'alerta' | 'error';
  /** Texto en lenguaje de negocio (nunca un código ni un detalle interno). */
  texto: string;
  /** Acción que resuelve el pendiente; siempre lleva a una pantalla del shell. */
  accion: { etiqueta: string; href: string };
}

/** Color e ícono por severidad (spec F4.5 §7.2: alerta y error nunca solo por color). */
const PRESENTACION = {
  alerta: { Icono: TriangleAlert, clase: 'text-[var(--color-alerta)]' },
  error: { Icono: CircleAlert, clase: 'text-[var(--color-error)]' },
} as const;

/**
 * Fila de un pendiente accionable del tablero de Inicio (spec F4.5 §8): dice qué pasa y qué hacer.
 * @param props ver {@link PropsPendiente}
 */
export function Pendiente({ severidad, texto, accion }: PropsPendiente) {
  const { Icono, clase } = PRESENTACION[severidad];
  return (
    <li className="flex items-center gap-3 border-b border-[var(--color-borde)] py-2 last:border-0">
      <Icono aria-hidden="true" className={`size-4 shrink-0 ${clase}`} />
      <span className="flex-1 text-sm text-[var(--color-texto)]">
        {severidad === 'error' && <span className="sr-only">Error: </span>}
        {texto}
      </span>
      <Link
        to={accion.href}
        className="shrink-0 text-sm font-medium text-[var(--color-primario)] hover:underline"
      >
        {accion.etiqueta}
      </Link>
    </li>
  );
}
