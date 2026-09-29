import type { HTMLAttributes, TdHTMLAttributes, ThHTMLAttributes } from 'react';
import { cn } from '@/compartido/lib/utils';

/**
 * Tabla contable (spec F4.5 §7.1): filas de 36 px, bordes finos de 1 px, sin sombras. El encabezado
 * y el pie (totales) quedan pegajosos dentro de un contenedor con `overflow`, para tablas largas.
 * @param props atributos de `<table>`, envuelta en un `<div>` con scroll horizontal
 */
export function Table({ className, ...props }: HTMLAttributes<HTMLTableElement>) {
  return (
    <div className="w-full overflow-x-auto">
      <table className={cn('w-full caption-bottom border-collapse text-sm', className)} {...props} />
    </div>
  );
}

/** Encabezado de la tabla, pegajoso al desplazar verticalmente. */
export function TableHeader({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <thead
      className={cn('sticky top-0 z-10 bg-[var(--color-superficie)] [&_tr]:border-b', className)}
      {...props}
    />
  );
}

/** Cuerpo de la tabla. */
export function TableBody({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return <tbody className={cn('[&_tr:last-child]:border-0', className)} {...props} />;
}

/** Pie de la tabla (fila de totales); pegajoso al fondo del contenedor con scroll. */
export function TableFooter({ className, ...props }: HTMLAttributes<HTMLTableSectionElement>) {
  return (
    <tfoot
      className={cn(
        'sticky bottom-0 z-10 border-t bg-[var(--color-lienzo)] font-semibold [&>tr]:last:border-b-0',
        className,
      )}
      {...props}
    />
  );
}

/** Fila: 36 px de alto (densidad de sistema contable) y resalte de selección con el primario suave. */
export function TableRow({ className, ...props }: HTMLAttributes<HTMLTableRowElement>) {
  return (
    <tr
      className={cn(
        'h-9 border-b border-[var(--color-borde)] transition-colors hover:bg-[var(--color-lienzo)] data-[selected=true]:bg-[var(--color-primario-suave)]',
        className,
      )}
      {...props}
    />
  );
}

/** Celda de encabezado. */
export function TableHead({ className, ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={cn(
        'px-3 text-left align-middle text-xs font-semibold uppercase tracking-wide text-[var(--color-texto-suave)]',
        className,
      )}
      {...props}
    />
  );
}

/** Celda de datos. */
export function TableCell({ className, ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn('px-3 align-middle', className)} {...props} />;
}

/** Título o resumen de la tabla (accesible, `<caption>`). */
export function TableCaption({ className, ...props }: HTMLAttributes<HTMLTableCaptionElement>) {
  return <caption className={cn('mt-2 text-sm text-[var(--color-texto-suave)]', className)} {...props} />;
}
