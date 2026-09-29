import { hoyElSalvador } from './fecha';

/** Atajos de rango de fechas para los filtros de reportes (CLAUDE.md §10.5, F4-05), en hora de El Salvador. */

/** Rango de fechas `AAAA-MM-DD` inclusivo en ambos extremos. */
export interface RangoFechas {
  desde: string;
  hasta: string;
}

/** Días de cada mes de un año no bisiesto; febrero se ajusta con {@link esBisiesto}. */
const DIAS_POR_MES = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31] as const;

/** Indica si un año es bisiesto (regla gregoriana estándar). */
function esBisiesto(anio: number): boolean {
  return (anio % 4 === 0 && anio % 100 !== 0) || anio % 400 === 0;
}

/** Último día del mes (1-12) de un año, sin pasar por `Date` (evita desfases de huso horario). */
function ultimoDiaDelMes(anio: number, mes: number): number {
  return mes === 2 && esBisiesto(anio) ? 29 : DIAS_POR_MES[mes - 1]!;
}

/** Rellena con cero a la izquierda hasta 2 dígitos. */
function dosDigitos(n: number): string {
  return String(n).padStart(2, '0');
}

/** Año y mes (1-12) de una fecha `AAAA-MM-DD`, leídos como texto (sin construir un `Date`). */
function anioYMes(fecha: string): { anio: number; mes: number } {
  const [anio, mes] = fecha.split('-').map(Number);
  return { anio: anio!, mes: mes! };
}

/** Rango del mes en curso, en hora de El Salvador. */
export function rangoEsteMes(ahora: Date = new Date()): RangoFechas {
  const { anio, mes } = anioYMes(hoyElSalvador(ahora));
  return {
    desde: `${anio}-${dosDigitos(mes)}-01`,
    hasta: `${anio}-${dosDigitos(mes)}-${dosDigitos(ultimoDiaDelMes(anio, mes))}`,
  };
}

/** Rango del mes calendario anterior al actual, en hora de El Salvador. */
export function rangoMesAnterior(ahora: Date = new Date()): RangoFechas {
  const { anio, mes } = anioYMes(hoyElSalvador(ahora));
  const anioPrevio = mes === 1 ? anio - 1 : anio;
  const mesPrevio = mes === 1 ? 12 : mes - 1;
  return {
    desde: `${anioPrevio}-${dosDigitos(mesPrevio)}-01`,
    hasta: `${anioPrevio}-${dosDigitos(mesPrevio)}-${dosDigitos(ultimoDiaDelMes(anioPrevio, mesPrevio))}`,
  };
}

/** Rango del año calendario en curso, en hora de El Salvador. */
export function rangoEsteAnio(ahora: Date = new Date()): RangoFechas {
  const { anio } = anioYMes(hoyElSalvador(ahora));
  return { desde: `${anio}-01-01`, hasta: `${anio}-12-31` };
}
