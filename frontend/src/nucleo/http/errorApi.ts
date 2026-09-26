import type { ErrorCampo } from '@/api/modelos';

/**
 * Error de la API de Pilot construido desde un Problem Details (RFC 9457, CLAUDE.md §8.4).
 * Al lanzarse en el cliente HTTP, TanStack Query lo trata como error de la consulta.
 */
export class ErrorApi extends Error {
  /** Código HTTP de la respuesta. */
  readonly status: number;
  /** Código de negocio (`PLT-xxx`, `CON-xxx`, `INT-xxx`); ausente si la respuesta no era Problem Details. */
  readonly codigo?: string;
  /** Explicación de esta ocurrencia, en español. */
  readonly detail?: string;
  /** Errores de validación por campo; lista vacía si no hay. */
  readonly errores: ErrorCampo[];
  /** Diferencia Σ Debe − Σ Haber (cadena decimal) en descuadres; ausente en el resto. */
  readonly diferencia?: string;

  /**
   * @param datos campos leídos del Problem Details (todos opcionales salvo `status`)
   */
  constructor(datos: {
    status: number;
    codigo?: string;
    detail?: string;
    title?: string;
    errores?: ErrorCampo[];
    diferencia?: string;
  }) {
    super(datos.detail ?? datos.title ?? `Error HTTP ${datos.status}`);
    this.name = 'ErrorApi';
    this.status = datos.status;
    this.codigo = datos.codigo;
    this.detail = datos.detail;
    this.errores = datos.errores ?? [];
    this.diferencia = datos.diferencia;
  }
}

/**
 * Indica si un valor es un `ErrorApi` (con el código de negocio opcional dado).
 * @param error valor capturado
 * @param codigo si se indica, exige además ese código de negocio
 */
export function esErrorApi(error: unknown, codigo?: string): error is ErrorApi {
  return error instanceof ErrorApi && (codigo === undefined || error.codigo === codigo);
}
