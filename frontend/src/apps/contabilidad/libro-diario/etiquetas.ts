import type { QueryClient } from '@tanstack/react-query';
import type { Asiento, EstadoAsiento, LineaVistaPrevia, OrigenAsiento, ResumenCuenta } from '@/api/modelos';

/** Texto en español de cada origen de un asiento (CLAUDE.md §9.3: `origen_tipo`). */
export const ETIQUETA_ORIGEN: Record<OrigenAsiento, string> = {
  MANUAL: 'Manual',
  N8N: 'n8n',
  REVERSION: 'Reversión',
};

/** Texto en español de cada estado de un asiento. */
export const ETIQUETA_ESTADO: Record<EstadoAsiento, string> = {
  CONTABILIZADO: 'Contabilizado',
  REVERTIDO: 'Revertido',
};

/**
 * Invalida todas las consultas del Libro Diario (listados y detalles) tras registrar o revertir un asiento.
 * Las claves generadas empiezan por `/contabilidad/asientos`; la vista previa se descarta sola (`gcTime: 0`).
 * @param cliente cliente de TanStack Query
 */
export function invalidarLibroDiario(cliente: QueryClient) {
  return cliente.invalidateQueries({
    predicate: (q) => typeof q.queryKey[0] === 'string' && q.queryKey[0].startsWith('/contabilidad/asientos'),
  });
}

/** Texto del enlace al detalle de un asiento: `numero/anio`. */
export const numeroAsiento = (a: Pick<Asiento, 'numero' | 'anio'>) => `${a.numero}/${a.anio}`;

/** Fila común de la tabla de líneas, sea de una vista previa o de un asiento guardado. */
export interface FilaLinea {
  /** Posición de la línea (desde 1). */
  numero: number;
  cuenta: ResumenCuenta;
  descripcion: string | null;
  debe: string;
  haber: string;
  /** `true` si es una línea de IVA calculado por el backend. */
  esIva: boolean;
  /** En una línea de IVA, el número de la línea que la originó. */
  deLinea: number | null;
}

/** Adapta las líneas de una vista previa (traen `numeroLineaOrigen`). */
export function filasDeVistaPrevia(lineas: readonly LineaVistaPrevia[]): FilaLinea[] {
  return lineas.map((l) => ({
    numero: l.numeroLinea,
    cuenta: l.cuenta,
    descripcion: l.descripcion,
    debe: l.debe,
    haber: l.haber,
    esIva: l.origenLinea === 'IVA_CALCULADO',
    deLinea: l.numeroLineaOrigen,
  }));
}

/** Adapta las líneas de un asiento guardado (traen `lineaBaseId`, que se traduce al número de la línea base). */
export function filasDeAsiento(asiento: Asiento): FilaLinea[] {
  const numeroPorId = new Map(asiento.lineas.map((l) => [l.id, l.numeroLinea]));
  return asiento.lineas.map((l) => ({
    numero: l.numeroLinea,
    cuenta: l.cuenta,
    descripcion: l.descripcion,
    debe: l.debe,
    haber: l.haber,
    esIva: l.origenLinea === 'IVA_CALCULADO',
    deLinea: l.lineaBaseId ? (numeroPorId.get(l.lineaBaseId) ?? null) : null,
  }));
}
