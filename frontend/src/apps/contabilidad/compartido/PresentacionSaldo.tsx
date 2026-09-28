import type { LadoSaldo, Saldo } from '@/api/modelos';
import { formatearMoneda } from '@/compartido/dinero';

/**
 * Presentación de saldos del Mayor y la Balanza (CLAUDE.md §10.3): el monto siempre lo calcula el
 * backend, aquí solo se formatea y se muestra el lado ("D"/"A") y la alerta de saldo contrario.
 */

/** Letra del lado del saldo; "—" cuando el saldo es CERO (Debe y Haber iguales). */
function etiquetaLado(lado: LadoSaldo): string {
  if (lado === 'DEUDOR') return 'D';
  if (lado === 'ACREEDOR') return 'A';
  return '—';
}

/**
 * Muestra un {@link Saldo} ya calculado por el backend: monto con formato de moneda, su lado y,
 * si `contrarioNaturaleza` es `true`, una alerta en texto (no solo color, para lectores de pantalla).
 * @param saldo saldo devuelto por el Mayor o la Balanza
 */
export function TextoSaldo({ saldo }: { saldo: Saldo }) {
  return (
    <span className="tabular-nums">
      {formatearMoneda(saldo.monto)} {etiquetaLado(saldo.lado)}
      {saldo.contrarioNaturaleza && (
        <span role="alert" className="ml-1 font-medium text-red-700">
          (saldo contrario a la naturaleza de la cuenta)
        </span>
      )}
    </span>
  );
}
