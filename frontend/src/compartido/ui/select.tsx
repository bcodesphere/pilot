import type { Ref, SelectHTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Lista desplegable nativa con el estilo del sistema de diseño (estilo shadcn/ui).
 * Se usa el `<select>` del navegador porque ya es accesible por teclado y por lectores de pantalla.
 * @param props atributos de `<select>`; `ref` se pasa como prop (React 19) para integrarse con React Hook Form
 */
export function Select({
  className,
  ref,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { ref?: Ref<HTMLSelectElement> }) {
  return (
    <select
      ref={ref}
      className={cn(
        'h-9 w-full rounded-[var(--radius-control)] border border-[var(--color-borde)] bg-[var(--color-superficie)] px-3 text-sm text-[var(--color-texto)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)] disabled:opacity-50 aria-[invalid=true]:border-[var(--color-error)]',
        className,
      )}
      {...props}
    />
  );
}
