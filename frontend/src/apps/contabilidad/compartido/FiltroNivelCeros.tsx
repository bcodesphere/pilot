import { Label } from '@/compartido/ui/label';
import { Select } from '@/compartido/ui/select';

/** Niveles del catálogo (1 clase … 5 detalle, CLAUDE.md §10.2). */
const NIVELES = [1, 2, 3, 4, 5] as const;

/** Propiedades del control de nivel y, si aplica, de "incluir ceros". */
export interface PropsFiltroNivelCeros {
  /** Prefijo de los `id` de los campos. */
  idPrefijo: string;
  nivel: number;
  onCambiarNivel: (nivel: number) => void;
  /** Ausente si el reporte no admite "incluir ceros" (la Balanza no lo tiene, ADR-038 §6). */
  incluirCeros?: boolean;
  onCambiarIncluirCeros?: (valor: boolean) => void;
}

/**
 * Control de nivel del catálogo y, opcionalmente, "incluir ceros", reutilizado por la Balanza y los
 * estados financieros (CLAUDE.md §10.2, §10.4). Ambos valores son solo filtros de la consulta al backend.
 * @param props ver {@link PropsFiltroNivelCeros}
 */
export function FiltroNivelCeros({
  idPrefijo,
  nivel,
  onCambiarNivel,
  incluirCeros,
  onCambiarIncluirCeros,
}: PropsFiltroNivelCeros) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="space-y-1">
        <Label htmlFor={`${idPrefijo}-nivel`}>Nivel</Label>
        <Select
          id={`${idPrefijo}-nivel`}
          value={nivel}
          onChange={(e) => onCambiarNivel(Number(e.target.value))}
        >
          {NIVELES.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </Select>
      </div>
      {onCambiarIncluirCeros && (
        <div className="flex items-center gap-2 pb-2">
          <input
            id={`${idPrefijo}-incluir-ceros`}
            type="checkbox"
            checked={!!incluirCeros}
            onChange={(e) => onCambiarIncluirCeros(e.target.checked)}
          />
          <Label htmlFor={`${idPrefijo}-incluir-ceros`}>Incluir filas en cero</Label>
        </div>
      )}
    </div>
  );
}
