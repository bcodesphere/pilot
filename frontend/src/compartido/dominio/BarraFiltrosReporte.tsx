import type { ReactNode } from 'react';
import {
  rangoEsteAnio,
  rangoEsteMes,
  rangoMesAnterior,
  type RangoFechas,
} from '@/compartido/formato/rangoPeriodo';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';

/** Propiedades de {@link BarraFiltrosReporte}. */
export interface PropsBarraFiltrosReporte {
  /** Período vigente (`Desde`/`Hasta`, `AAAA-MM-DD`). */
  periodo: RangoFechas;
  /** Se invoca con el rango nuevo, sea por edición manual o por un atajo. */
  onPeriodo: (rango: RangoFechas) => void;
  /** Filtros propios de un reporte (p. ej. el nivel de la Balanza). */
  extras?: ReactNode;
  /** El `MenuExportar` del reporte; se alinea al final de la barra. */
  exportar?: ReactNode;
  /** Mensaje de validación local (`desde` posterior a `hasta`); ausente si no hay error. */
  error?: string | null;
}

/**
 * Barra común de filtros de los reportes (spec F4.5 §8: "Barra común de filtros y 'Exportar ▾'"):
 * rango de fechas con atajos, el hueco para filtros propios y el menú de exportación al final.
 * @param props ver {@link PropsBarraFiltrosReporte}
 */
export function BarraFiltrosReporte({
  periodo,
  onPeriodo,
  extras,
  exportar,
  error,
}: PropsBarraFiltrosReporte) {
  const idError = 'barra-filtros-reporte-error';
  return (
    <div className="flex flex-wrap items-end gap-3 border-b border-[var(--color-borde)] pb-3">
      <div className="space-y-1">
        <Label htmlFor="barra-filtros-desde">Desde</Label>
        <Input
          id="barra-filtros-desde"
          type="date"
          value={periodo.desde}
          aria-invalid={!!error}
          aria-describedby={error ? idError : undefined}
          onChange={(e) => onPeriodo({ desde: e.target.value, hasta: periodo.hasta })}
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="barra-filtros-hasta">Hasta</Label>
        <Input
          id="barra-filtros-hasta"
          type="date"
          value={periodo.hasta}
          aria-invalid={!!error}
          aria-describedby={error ? idError : undefined}
          onChange={(e) => onPeriodo({ desde: periodo.desde, hasta: e.target.value })}
        />
      </div>
      <div className="flex gap-1">
        <Button type="button" variant="outline" size="sm" onClick={() => onPeriodo(rangoEsteMes())}>
          Este mes
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={() => onPeriodo(rangoMesAnterior())}>
          Mes anterior
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={() => onPeriodo(rangoEsteAnio())}>
          Este año
        </Button>
      </div>
      {extras}
      {exportar && <div className="ml-auto">{exportar}</div>}
      {error && (
        <p id={idError} role="alert" className="w-full text-sm text-[var(--color-error)]">
          {error}
        </p>
      )}
    </div>
  );
}
