import { TriangleAlert } from 'lucide-react';
import type { LadoSaldo, Saldo } from '@/api/modelos';
import { Monto } from './Monto';

/** Propiedades de {@link EtiquetaSaldo}. */
export interface PropsEtiquetaSaldo {
  /** Saldo ya calculado por el backend (CLAUDE.md §10.3): monto absoluto, lado y alerta. */
  saldo: Saldo;
}

/** Letra neutra del lado del saldo (nunca "bueno"/"malo" por color, spec F4.5 §7.2). */
function etiquetaLado(lado: LadoSaldo): string {
  if (lado === 'DEUDOR') return 'D';
  if (lado === 'ACREEDOR') return 'A';
  return '—';
}

/**
 * Muestra un {@link Saldo}: el monto con {@link Monto}, la letra D/A y, si `contrarioNaturaleza` es
 * `true`, un ícono de alerta con texto accesible (nunca solo color, CLAUDE.md §10.3).
 * @param props ver {@link PropsEtiquetaSaldo}
 */
export function EtiquetaSaldo({ saldo }: PropsEtiquetaSaldo) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <Monto valor={saldo.monto} /> <span aria-hidden="true">{etiquetaLado(saldo.lado)}</span>
      {saldo.contrarioNaturaleza && (
        <span role="alert" className="inline-flex items-center text-[var(--color-alerta)]">
          <TriangleAlert aria-hidden="true" className="size-3.5" />
          <span className="sr-only">saldo contrario a la naturaleza de la cuenta</span>
        </span>
      )}
    </span>
  );
}
