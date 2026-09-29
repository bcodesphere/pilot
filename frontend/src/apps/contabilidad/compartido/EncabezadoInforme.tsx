import { useMemo } from 'react';
import { formatearFechaHora } from '@/compartido/formato/fecha';
import { useSesion } from '@/nucleo/sesion/contextoSesion';

/** Propiedades de {@link EncabezadoInforme}. */
export interface PropsEncabezadoInforme {
  /** Texto del período ya armado (p. ej. "1 sep 2026 – 30 sep 2026" o "Corte al 30 sep 2026"). */
  periodo: string;
}

/**
 * Encabezado de informe de un reporte (spec F4.5 §7.4 "patrón de reporte"): espacio de trabajo,
 * período consultado y el instante de generación en hora de El Salvador. Precede a la `TablaContable`
 * de cada reporte, después de `BarraFiltrosReporte`.
 * @param props ver {@link PropsEncabezadoInforme}
 */
export function EncabezadoInforme({ periodo }: PropsEncabezadoInforme) {
  const { empresaActiva } = useSesion();
  // Se fija una sola vez al montar: un reporte abierto largo rato no debe recalcular su "generado el"
  const generadoEn = useMemo(() => new Date().toISOString(), []);
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-sm text-[var(--color-texto-suave)]">
      <span className="font-medium text-[var(--color-texto)]">{empresaActiva.nombreEmpresa}</span>
      <span>{periodo}</span>
      <span>Generado el {formatearFechaHora(generadoEn)}</span>
    </div>
  );
}
