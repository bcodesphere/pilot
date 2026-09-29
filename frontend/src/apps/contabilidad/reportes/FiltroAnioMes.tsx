import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { Select } from '@/compartido/ui/select';

/** Nombres de mes en español, para el selector del Resumen de IVA. */
const MESES = [
  'Enero',
  'Febrero',
  'Marzo',
  'Abril',
  'Mayo',
  'Junio',
  'Julio',
  'Agosto',
  'Septiembre',
  'Octubre',
  'Noviembre',
  'Diciembre',
] as const;

/** Propiedades del filtro de año y mes del Resumen de IVA. */
export interface PropsFiltroAnioMes {
  anio: number;
  mes: number;
  onCambiar: (periodo: { anio: number; mes: number }) => void;
}

/**
 * Filtro de año y mes del Resumen de IVA mensual (CLAUDE.md §10.5, §11).
 * @param props ver {@link PropsFiltroAnioMes}
 */
export function FiltroAnioMes({ anio, mes, onCambiar }: PropsFiltroAnioMes) {
  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="space-y-1">
        <Label htmlFor="iva-anio">Año</Label>
        <Input
          id="iva-anio"
          type="number"
          min={2000}
          max={9999}
          value={anio}
          onChange={(e) => onCambiar({ anio: Number(e.target.value), mes })}
          className="w-24"
        />
      </div>
      <div className="space-y-1">
        <Label htmlFor="iva-mes">Mes</Label>
        <Select id="iva-mes" value={mes} onChange={(e) => onCambiar({ anio, mes: Number(e.target.value) })}>
          {MESES.map((nombre, i) => (
            <option key={nombre} value={i + 1}>
              {nombre}
            </option>
          ))}
        </Select>
      </div>
    </div>
  );
}
