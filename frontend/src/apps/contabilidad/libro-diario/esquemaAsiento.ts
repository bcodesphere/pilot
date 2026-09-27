import { z } from 'zod';
import type { ModoPrecio, NuevoAsiento } from '@/api/modelos';
import {
  esCero,
  esMontoValido,
  formatearMonedaConSigno,
  normalizarMonto,
  restarMontos,
  sonIguales,
  sumarMontos,
} from '@/compartido/dinero';

/** Máximo de líneas de un asiento (contrato `NuevoAsiento`, CLAUDE.md §10.1). */
export const MAX_LINEAS = 200;

/**
 * Monto de un campo Debe o Haber como cadena (ADR-013, regla 1.2.2): sin signo y con máximo 2 decimales
 * (CON-003). El campo puede quedar vacío: una línea deja vacío el lado que no usa y ese lado vale "0.00".
 */
const montoCampoSchema = z
  .string()
  .trim()
  .regex(/^(\d{1,17}(\.\d{1,2})?)?$/, 'Monto inválido: sin signo y con máximo 2 decimales');

/** Línea del formulario: los montos son cadenas, nunca `number`. */
const lineaSchema = z.object({
  /** Cuenta elegida; vacía mientras no se elige (el backend valida existencia y detalle: CON-006). */
  cuentaId: z.string().min(1, 'Elige una cuenta'),
  descripcion: z.string().max(300, 'La descripción admite máximo 300 caracteres'),
  debe: montoCampoSchema,
  haber: montoCampoSchema,
  llevaIva: z.boolean(),
});

/** Forma del formulario del Libro Diario, antes de las reglas de negocio de {@link crearEsquemaAsiento}. */
const formaAsientoSchema = z.object({
  fecha: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'La fecha es obligatoria'),
  concepto: z.string().trim().min(1, 'El concepto es obligatorio').max(500, 'Máximo 500 caracteres'),
  modoPrecio: z.enum(['CON_IVA', 'SIN_IVA']),
  lineas: z.array(lineaSchema).max(MAX_LINEAS, `Un asiento admite máximo ${MAX_LINEAS} líneas`),
});

/** Línea del formulario del Libro Diario. */
export type ValoresLineaAsiento = z.infer<typeof lineaSchema>;

/** Valores del formulario del Libro Diario. */
export type ValoresAsiento = z.infer<typeof formaAsientoSchema>;

/** Datos del entorno que el esquema necesita para las reglas que no dependen solo del formulario. */
export interface ContextoEsquemaAsiento {
  /** Fecha de hoy en El Salvador (`AAAA-MM-DD`), límite de CON-007. */
  hoy: string;
  /** Ids de las cuentas de IVA de la configuración contable, sobre las que "lleva IVA" no se permite (CON-013). */
  idsCuentasIva: readonly string[];
}

/** Totales locales del formulario, calculados con decimal.js. */
export interface TotalesLocales {
  /** Σ Debe. */
  debe: string;
  /** Σ Haber. */
  haber: string;
  /** Σ Debe − Σ Haber; negativo si el Haber es mayor. */
  diferencia: string;
}

/**
 * Suma ambos lados del formulario con decimal.js (CON-005). Un campo vacío vale "0.00".
 * @param lineas líneas del formulario
 * @returns los totales, o `null` si algún monto no cumple el formato (todavía no se puede sumar)
 */
export function totalesLocales(lineas: readonly ValoresLineaAsiento[]): TotalesLocales | null {
  // 1. Normaliza cada campo (vacío = "0.00") y verifica el formato antes de sumar
  const debes = lineas.map((l) => normalizarMonto(l.debe));
  const haberes = lineas.map((l) => normalizarMonto(l.haber));
  if (![...debes, ...haberes].every(esMontoValido)) return null;
  // 2. Suma exacta y diferencia con signo
  const debe = sumarMontos(debes);
  const haber = sumarMontos(haberes);
  return { debe, haber, diferencia: restarMontos(debe, haber) };
}

/**
 * Crea el esquema Zod del formulario del Libro Diario. Replica en el frontend las validaciones de
 * CLAUDE.md §10.1 (`CON-001` a `CON-005`, `CON-007` y `CON-013`); el backend las vuelve a aplicar y el
 * trigger de base de datos es la última defensa (regla 1.1.5). Cada incidencia de negocio lleva su código
 * en `params.codigo`.
 *
 * Si alguna línea "lleva IVA" **no** se valida `CON-005` aquí: en modo `SIN_IVA` la expansión del IVA cambia
 * los totales, así que la partida doble se valida sobre las líneas expandidas que devuelve el backend en la
 * vista previa (ADR-036). Calcular ese IVA en el frontend está prohibido (regla 1.2.1).
 *
 * @param contexto fecha de hoy y cuentas de IVA de la configuración (los valores cambian por empresa y por día)
 */
export function crearEsquemaAsiento(contexto: ContextoEsquemaAsiento) {
  return formaAsientoSchema.superRefine((asiento, ctx) => {
    /** Registra una incidencia de negocio con su código para poder distinguirla y probarla. */
    const incidencia = (codigo: string, message: string, path: (string | number)[]) =>
      ctx.addIssue({ code: 'custom', message, path, params: { codigo } });

    // 1. CON-007: la fecha no puede ser posterior a hoy en El Salvador (ISO-8601 ordena como texto)
    if (asiento.fecha > contexto.hoy) {
      incidencia('CON-007', 'La fecha del asiento no puede ser futura', ['fecha']);
    }

    // 2. CON-001: mínimo dos líneas
    if (asiento.lineas.length < 2) {
      incidencia('CON-001', 'El asiento debe tener al menos dos líneas', ['lineas']);
    }

    asiento.lineas.forEach((linea, i) => {
      const debe = normalizarMonto(linea.debe);
      const haber = normalizarMonto(linea.haber);
      // 3. CON-002: solo Debe o solo Haber (se omite si un monto tiene formato inválido: ya lo marca CON-003)
      if (esMontoValido(debe) && esMontoValido(haber) && esCero(debe) === esCero(haber)) {
        incidencia('CON-002', 'Use solo Debe o solo Haber', ['lineas', i, 'debe']);
      }
      // 4. CON-013: "lleva IVA" no se permite sobre las cuentas de IVA
      if (linea.llevaIva && contexto.idsCuentasIva.includes(linea.cuentaId)) {
        incidencia('CON-013', 'No se permite "lleva IVA" en una cuenta de IVA', ['lineas', i, 'llevaIva']);
      }
    });

    // 5. CON-004 y CON-005: suma exacta con decimal.js; solo si todos los montos tienen formato válido
    const totales = totalesLocales(asiento.lineas);
    if (!totales) return;
    if (esCero(totales.debe) && esCero(totales.haber)) {
      incidencia('CON-004', 'Los totales deben ser mayores que cero', ['lineas']);
    } else if (!asiento.lineas.some((l) => l.llevaIva) && !sonIguales(totales.debe, totales.haber)) {
      incidencia('CON-005', `Diferencia: ${formatearMonedaConSigno(totales.diferencia)}`, ['lineas']);
    }
  });
}

/** Valores iniciales de una línea vacía. */
export const LINEA_VACIA: ValoresLineaAsiento = {
  cuentaId: '',
  descripcion: '',
  debe: '',
  haber: '',
  llevaIva: false,
};

/**
 * Convierte los valores del formulario en el cuerpo de `POST /contabilidad/asientos` (y de la vista previa).
 * Los campos vacíos de monto viajan como "0.00" y la descripción vacía se omite. `modoPrecio` solo se envía
 * si alguna línea lleva IVA: sin IVA no interviene y así el cuerpo no cambia al tocarlo.
 * @param v valores ya válidos del formulario
 */
export function construirNuevoAsiento(v: ValoresAsiento): NuevoAsiento {
  const llevaIva = v.lineas.some((l) => l.llevaIva);
  return {
    fecha: v.fecha,
    concepto: v.concepto.trim(),
    ...(llevaIva ? { modoPrecio: v.modoPrecio as ModoPrecio } : {}),
    lineas: v.lineas.map((l) => ({
      cuentaId: l.cuentaId,
      ...(l.descripcion.trim() ? { descripcion: l.descripcion.trim() } : {}),
      debe: normalizarMonto(l.debe),
      haber: normalizarMonto(l.haber),
      llevaIva: l.llevaIva,
    })),
  };
}
