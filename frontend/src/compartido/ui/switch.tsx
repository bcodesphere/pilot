import type { ButtonHTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/** Propiedades del interruptor. */
interface PropsSwitch extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange'> {
  /** Estado actual. */
  checked: boolean;
  /** Se invoca con el valor nuevo al activarlo (clic, Espacio o Enter). */
  onCheckedChange: (valor: boolean) => void;
}

/**
 * Interruptor accesible (`role="switch"`, `aria-checked`); se nombra con `aria-labelledby` o `aria-label`.
 * @param props ver {@link PropsSwitch}
 */
export function Switch({ checked, onCheckedChange, className, ...props }: PropsSwitch) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onCheckedChange(!checked)}
      className={cn(
        'relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-50',
        checked ? 'bg-neutral-900' : 'bg-neutral-300',
        className,
      )}
      {...props}
    >
      {/* Perilla: se desplaza a la derecha cuando está activo */}
      <span
        aria-hidden="true"
        className={cn(
          'inline-block h-5 w-5 rounded-full bg-white transition-transform',
          checked ? 'translate-x-5' : 'translate-x-0.5',
        )}
      />
    </button>
  );
}
