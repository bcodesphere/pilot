import { Link } from 'react-router-dom';
import type { EstadoAsiento } from '@/api/modelos';

/** Propiedades de {@link EstadoDocumento}. */
export interface PropsEstadoDocumento {
  /** Estado de un asiento u operación (ADR-019): `CONTABILIZADO` o `REVERTIDO`. */
  estado: EstadoAsiento;
  /** Ruta al asiento de reversión; solo se muestra cuando `estado` es `REVERTIDO`. */
  enlaceReversion?: string;
}

/** Texto y color de cada estado (nunca un booleano crudo en pantalla, AGENTS.md §3.7). */
const PRESENTACION: Record<EstadoAsiento, { texto: string; clase: string }> = {
  CONTABILIZADO: { texto: 'Contabilizado', clase: 'bg-[var(--color-exito)]/10 text-[var(--color-exito)]' },
  REVERTIDO: {
    texto: 'Revertido',
    clase: 'bg-[var(--color-neutro-revertido)]/10 text-[var(--color-neutro-revertido)]',
  },
};

/**
 * Barra de estado de un documento contable (asiento u operación guiada): "Contabilizado" en verde
 * o "Revertido" en el tono neutro, con un enlace al asiento de reversión si se conoce (ficha
 * "Detalle de operación o asiento", spec F4.5 §8).
 * @param props ver {@link PropsEstadoDocumento}
 */
export function EstadoDocumento({ estado, enlaceReversion }: PropsEstadoDocumento) {
  const { texto, clase } = PRESENTACION[estado];
  return (
    <span className="inline-flex items-center gap-2 text-sm">
      <span className={`inline-flex items-center rounded-full px-2 py-0.5 font-medium ${clase}`}>
        {texto}
      </span>
      {estado === 'REVERTIDO' && enlaceReversion && (
        <Link to={enlaceReversion} className="text-[var(--color-primario)] hover:underline">
          Ver la reversión
        </Link>
      )}
    </span>
  );
}
