import { Lock } from 'lucide-react';
import type { ReactNode } from 'react';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/compartido/ui/tooltip';

/** Propiedades de {@link ValorBloqueado}. */
export interface PropsValorBloqueado {
  /** El valor ya presentado (texto, `Monto`, `CodigoCuenta`…); nunca un campo editable. */
  valor: ReactNode;
  /** Por qué no se puede editar (p. ej. "Esta cuenta es del catálogo base", CON-021). */
  motivo: string;
}

/**
 * Presenta un valor que el bloqueo de edición (ADR-042) no permite cambiar: el candado explica por
 * qué en un tooltip, y nunca se renderiza un `<input>` (criterio de UX: no simular edición posible).
 * @param props ver {@link PropsValorBloqueado}
 */
export function ValorBloqueado({ valor, motivo }: PropsValorBloqueado) {
  return (
    <span className="inline-flex items-center gap-1.5 text-[var(--color-texto)]">
      {valor}
      <Tooltip>
        <TooltipTrigger asChild>
          <button
            type="button"
            aria-label={motivo}
            className="inline-flex items-center text-[var(--color-texto-suave)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]"
          >
            <Lock aria-hidden="true" className="size-3.5" />
          </button>
        </TooltipTrigger>
        <TooltipContent>{motivo}</TooltipContent>
      </Tooltip>
    </span>
  );
}
