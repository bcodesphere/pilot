import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { ComponentProps } from 'react';
import { DayPicker, type DayButtonProps } from 'react-day-picker';
import { cn } from '@/compartido/lib/utils';

/**
 * Selector de fecha (`react-day-picker` 10.0.1, ADR-043): base de los campos de fecha de las
 * operaciones guiadas y de la reversión de asientos. Español de El Salvador; semana desde el lunes.
 */
export function Calendar({ className, ...props }: ComponentProps<typeof DayPicker>) {
  return (
    <DayPicker
      showOutsideDays
      weekStartsOn={1}
      className={cn('p-3', className)}
      classNames={{
        months: 'flex flex-col gap-2',
        month: 'flex flex-col gap-2',
        month_caption: 'flex items-center justify-center pt-1 text-sm font-medium',
        nav: 'flex items-center justify-between absolute inset-x-1 top-1',
        button_previous:
          'inline-flex size-7 items-center justify-center rounded-[var(--radius-control)] text-[var(--color-texto-suave)] hover:bg-[var(--color-lienzo)]',
        button_next:
          'inline-flex size-7 items-center justify-center rounded-[var(--radius-control)] text-[var(--color-texto-suave)] hover:bg-[var(--color-lienzo)]',
        month_grid: 'w-full border-collapse',
        weekdays: 'flex',
        weekday: 'w-9 text-center text-xs font-medium text-[var(--color-texto-suave)]',
        week: 'flex w-full',
        day: 'size-9 p-0 text-center text-sm',
        day_button:
          'size-9 rounded-[var(--radius-control)] font-normal hover:bg-[var(--color-lienzo)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]',
        selected:
          '[&>button]:bg-[var(--color-primario)] [&>button]:text-white [&>button]:hover:bg-[var(--color-primario-oscuro)]',
        today: '[&>button]:font-semibold [&>button]:text-[var(--color-primario)]',
        outside: 'text-[var(--color-texto-suave)] opacity-50',
        disabled: 'text-[var(--color-texto-suave)] opacity-30',
        ...props.classNames,
      }}
      components={{
        Chevron: ({ orientation }) =>
          orientation === 'left' ? <ChevronLeft className="size-4" /> : <ChevronRight className="size-4" />,
        ...props.components,
      }}
      {...props}
    />
  );
}

/** Tipo reexportado para quien necesite tipar un `DayButton` propio. */
export type { DayButtonProps };
