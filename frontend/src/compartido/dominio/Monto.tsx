import { esCero, formatearMonedaConSigno } from '@/compartido/dinero';
import { cn } from '@/compartido/lib/utils';

/** Propiedades de {@link Monto}. */
export interface PropsMonto {
  /** Cadena decimal del contrato (`Monto` o `MontoConSigno`, ADR-013): nunca un `number`. */
  valor: string;
  /** `true` antepone "+" a un monto positivo (para diferencias); un negativo siempre lleva "-". */
  conSigno?: boolean;
  /** `true` lo muestra tachado, en el tono neutro de "revertido" (spec F4.5 §7.2). */
  tachado?: boolean;
}

/**
 * Presenta un monto del contrato con cifras tabulares, alineado a la derecha y en formato `es-SV`
 * (`$1,234.56`). Nunca calcula nada: el monto siempre lo trae el backend (ADR-006).
 * @param props ver {@link PropsMonto}
 */
export function Monto({ valor, conSigno = false, tachado = false }: PropsMonto) {
  const negativo = valor.trim().startsWith('-');
  const formateado = formatearMonedaConSigno(valor);
  const texto = conSigno && !negativo && !esCero(valor) ? `+${formateado}` : formateado;
  return (
    <span
      className={cn(
        'cifra inline-block text-right',
        tachado && 'text-[var(--color-neutro-revertido)] line-through decoration-1',
      )}
    >
      {texto}
      {tachado && <span className="sr-only"> (revertido)</span>}
    </span>
  );
}
