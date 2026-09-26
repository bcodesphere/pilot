import type { InputHTMLAttributes, Ref } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Campo de texto del sistema de diseño (estilo shadcn/ui).
 * @param props atributos de `<input>`; `ref` se pasa como prop (React 19) para integrarse con React Hook Form
 */
export function Input({
  className,
  ref,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { ref?: Ref<HTMLInputElement> }) {
  return (
    <input
      ref={ref}
      className={cn(
        'h-9 w-full rounded-md border border-neutral-300 bg-white px-3 text-sm focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-neutral-900 disabled:opacity-50 aria-[invalid=true]:border-red-600',
        className,
      )}
      {...props}
    />
  );
}
