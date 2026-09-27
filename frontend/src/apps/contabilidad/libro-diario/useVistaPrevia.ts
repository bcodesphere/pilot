import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { previsualizarAsiento } from '@/api/asientos/asientos';
import type { NuevoAsiento, VistaPreviaAsiento } from '@/api/modelos';

/** Retardo tras el último cambio antes de pedir la vista previa (CLAUDE.md §10.1). */
export const RETARDO_VISTA_PREVIA_MS = 300;

/** Estado de la vista previa del asiento en edición. */
export interface EstadoVistaPrevia {
  /** Respuesta del backend para el cuerpo actual; `undefined` mientras no haya una vigente. */
  vista: VistaPreviaAsiento | undefined;
  /** Hay un cambio sin enviar (esperando el retardo) o una petición en vuelo: el resultado aún no es vigente. */
  pendiente: boolean;
  /** Error de la petición más reciente (p. ej. 422 `CON-006`), si lo hubo. */
  error: unknown;
}

/**
 * Pide al backend la vista previa (líneas expandidas con el IVA, totales, diferencia y `cuadra`, ADR-036) de
 * un asiento con líneas que llevan IVA. El IVA lo calcula **solo el backend** (regla 1.2.1).
 *
 * Solo se llama cuando el cuerpo cambia y pasan {@link RETARDO_VISTA_PREVIA_MS} ms sin nuevos cambios, así que
 * una ráfaga de teclas produce una única petición. Un resultado solo es vigente si corresponde al cuerpo
 * actual: mientras se escribe, `vista` es `undefined` y `pendiente` es `true`, para que Guardar no use un
 * resultado viejo.
 *
 * @param cuerpo asiento a previsualizar, o `null` si no corresponde (sin líneas con IVA o formulario inválido)
 */
export function useVistaPrevia(cuerpo: NuevoAsiento | null): EstadoVistaPrevia {
  // 1. Clave estable del cuerpo actual y la última clave que ya esperó su retardo
  const clave = cuerpo ? JSON.stringify(cuerpo) : null;
  const [claveEstable, setClaveEstable] = useState<string | null>(null);

  // 2. Cada cambio reinicia el temporizador; la clave solo pasa a "estable" tras 300 ms sin cambios
  useEffect(() => {
    if (clave === null) return;
    const temporizador = setTimeout(() => setClaveEstable(clave), RETARDO_VISTA_PREVIA_MS);
    return () => clearTimeout(temporizador);
  }, [clave]);

  // 3. Consulta por clave: TanStack Query descarta por sí mismo las respuestas de cuerpos anteriores
  const vigente = clave !== null && clave === claveEstable;
  const consulta = useQuery({
    queryKey: ['/contabilidad/asientos/vista-previa', claveEstable],
    queryFn: async () => (await previsualizarAsiento(JSON.parse(claveEstable!) as NuevoAsiento)).data,
    enabled: vigente,
    staleTime: 0,
    gcTime: 0,
  });

  return {
    vista: vigente ? (consulta.data as VistaPreviaAsiento | undefined) : undefined,
    pendiente: clave !== null && (!vigente || consulta.isFetching),
    error: vigente && consulta.isError ? consulta.error : null,
  };
}
