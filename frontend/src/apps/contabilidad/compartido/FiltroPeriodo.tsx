import {
  rangoEsteAnio,
  rangoEsteMes,
  rangoMesAnterior,
  type RangoFechas,
} from '@/compartido/formato/rangoPeriodo';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';

/** Propiedades del filtro de período (Mayor, Balanza y Estado de Resultados, CLAUDE.md §10.5). */
export interface PropsFiltroPeriodo {
  /** Prefijo de los `id` de los campos, para que cada pantalla tenga los suyos. */
  idPrefijo: string;
  /** Fecha `Desde`, en `AAAA-MM-DD` (cadena vacía si no se ha elegido). */
  desde: string;
  /** Fecha `Hasta`, en `AAAA-MM-DD`. */
  hasta: string;
  /** Se invoca con el rango nuevo, sea por edición manual o por un atajo. */
  onCambiar: (rango: RangoFechas) => void;
  /** Mensaje de validación local (`desde` posterior a `hasta`); `null`/ausente si no hay error. */
  error?: string | null;
}

/**
 * Filtro de período reutilizable: `Desde`/`Hasta` con atajos "Este mes", "Mes anterior" y "Este año"
 * (hora de El Salvador, CLAUDE.md §10.5). Los filtros viven en la URL de cada pantalla que lo usa.
 * @param props ver {@link PropsFiltroPeriodo}
 */
export function FiltroPeriodo({ idPrefijo, desde, hasta, onCambiar, error }: PropsFiltroPeriodo) {
  const idError = `${idPrefijo}-periodo-error`;
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="space-y-1">
        <Label htmlFor={`${idPrefijo}-desde`}>Desde</Label>
        <Input
          id={`${idPrefijo}-desde`}
          type="date"
          value={desde}
          aria-invalid={!!error}
          aria-describedby={error ? idError : undefined}
          onChange={(e) => onCambiar({ desde: e.target.value, hasta })}
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor={`${idPrefijo}-hasta`}>Hasta</Label>
        <Input
          id={`${idPrefijo}-hasta`}
          type="date"
          value={hasta}
          aria-invalid={!!error}
          aria-describedby={error ? idError : undefined}
          onChange={(e) => onCambiar({ desde, hasta: e.target.value })}
        />
      </div>
      {/* Atajos: fijan ambos extremos a la vez, en hora de El Salvador */}
      <div className="flex gap-1">
        <Button type="button" variant="outline" size="sm" onClick={() => onCambiar(rangoEsteMes())}>
          Este mes
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={() => onCambiar(rangoMesAnterior())}>
          Mes anterior
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={() => onCambiar(rangoEsteAnio())}>
          Este año
        </Button>
      </div>
      {error && (
        <p id={idError} role="alert" className="w-full text-sm text-red-700">
          {error}
        </p>
      )}
    </div>
  );
}
