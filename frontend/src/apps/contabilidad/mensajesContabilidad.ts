import { formatearMonedaConSigno } from '@/compartido/dinero';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { mensajeDeError } from '@/nucleo/mensajesError';

/**
 * Mensajes en español (`es-SV`) de los códigos `CON-` del catálogo, la configuración, las reglas y el
 * Libro Diario. Reproducen las tablas de CLAUDE.md §10.1 y §10.2; no se inventa ningún código.
 */
export const MENSAJES_CONTABILIDAD: Record<string, string> = {
  'CON-006': 'La cuenta no existe en la empresa, está inactiva o no es de detalle.',
  'CON-010': 'El primer dígito del código debe ser una clase de 1 a 5.',
  'CON-011':
    'No se puede cambiar el código de una cuenta con movimientos, ni crear una cuenta hija dentro de una que ya tiene movimientos.',
  'CON-012': 'No se puede desactivar una cuenta con saldo distinto de cero.',
  'CON-014': 'El código ya existe en el catálogo de la empresa.',
  'CON-015':
    'Longitud de código no válida (1, 2, 4, 6 u 8 dígitos) o no existe una cuenta padre activa cuyo código sea su prefijo.',
  'CON-016':
    'La cuenta está en uso por la configuración contable o por una regla activa: no se puede desactivar ni dejar de ser de detalle.',
  // Libro Diario (CLAUDE.md §10.1, ADR-036). CON-005 se completa con la `diferencia` del Problem Details
  'CON-001': 'El asiento debe tener al menos dos líneas.',
  'CON-002': 'Cada línea debe llevar solo Debe o solo Haber.',
  'CON-003': 'Los montos no pueden ser negativos y admiten máximo 2 decimales.',
  'CON-004': 'Los totales del asiento deben ser mayores que cero.',
  'CON-005': 'El asiento no cuadra: el total del Debe debe ser igual al del Haber.',
  'CON-007': 'La fecha no puede ser futura.',
  'CON-008': 'El asiento ya está revertido.',
  'CON-009': 'Una reversión no se puede revertir.',
  'CON-013': 'No se puede usar "lleva IVA" en una cuenta de IVA.',
  'CON-017': 'No hay una tasa de IVA vigente a la fecha del asiento.',
  'CON-018': 'La fecha de la reversión no puede ser anterior a la del asiento original.',
  // Códigos de plataforma que también pueden aparecer en estas pantallas (CLAUDE.md §8.4)
  'PLT-002': 'Revisa los datos del asiento: hay campos con errores.',
  'PLT-008': 'Ya hay una petición igual en proceso. Espera unos segundos e inténtalo de nuevo.',
  'PLT-010': 'No tienes permiso para realizar esta operación.',
  'PLT-015': 'No pudimos verificar la versión del dato. Recarga e inténtalo de nuevo.',
  'PLT-017': 'El dato ya no existe o no pertenece a tu espacio de trabajo.',
};

/** Campo del formulario al que se asocia cada código de negocio (el mensaje se muestra junto a él). */
const CAMPO_POR_CODIGO: Record<string, string> = {
  'CON-006': 'cuenta',
  'CON-010': 'codigo',
  'CON-011': 'codigo',
  'CON-014': 'codigo',
  'CON-015': 'codigo',
  'CON-012': 'activa',
  'CON-016': 'activa',
};

/**
 * Campos del formulario del Libro Diario a los que se asocia un código de negocio (el mensaje se muestra
 * junto a él). Los errores de una línea (`CON-002`, `CON-003`, `CON-006`, `CON-013`) llegan con
 * `errores[].campo` y se asocian a la línea por ese campo; los de aquí son de la cabecera o del total.
 */
const CAMPO_ASIENTO_POR_CODIGO: Record<string, 'fecha' | 'lineas'> = {
  'CON-007': 'fecha',
  'CON-018': 'fecha',
  'CON-001': 'lineas',
  'CON-004': 'lineas',
  'CON-005': 'lineas',
};

/**
 * Traduce un error de la API a un mensaje en español; los códigos no reconocidos dan el mensaje genérico.
 * @param error error capturado de una mutación
 */
export function mensajeContabilidad(error: unknown): string {
  return mensajeDeError(error, MENSAJES_CONTABILIDAD);
}

/**
 * Indica a qué campo pertenece un error de negocio, para mostrarlo junto a él.
 * @param error error capturado de una mutación
 * @returns `codigo`, `activa` o `cuenta`; `null` si el error no es de un campo concreto
 */
export function campoDeError(error: unknown): string | null {
  return esErrorApi(error) && error.codigo ? (CAMPO_POR_CODIGO[error.codigo] ?? null) : null;
}

/** Texto del aviso de conflicto de versión (412 `PLT-016`), igual que en el espacio de trabajo. */
export const MENSAJE_CONFLICTO = 'Alguien cambió este dato mientras lo editabas';

/**
 * Texto que usa el backend (F4-03) para el campo `desde` de `PLT-002` cuando es posterior a `hasta` en
 * los filtros de un reporte (Mayor, Balanza, Estado de Resultados). Se valida igual en el frontend
 * (CLAUDE.md §1.1.5) con el mismo texto, para no mostrar dos redacciones distintas del mismo error.
 */
export const MENSAJE_RANGO_INVALIDO = 'La fecha inicial no puede ser posterior a la final';

/**
 * Valida localmente el rango de un reporte con el mismo texto que da el backend en `PLT-002` para el
 * campo `desde` (F4-03); centraliza la redacción para no repetirla en cada pantalla que filtra por
 * `desde`/`hasta` (Mayor, Balanza, Estado de Resultados).
 * @param desde fecha `Desde`, vacía si no se ha elegido
 * @param hasta fecha `Hasta`, vacía si no se ha elegido
 * @returns el mensaje si `desde` es posterior a `hasta`; `null` si el rango es válido o está incompleto
 */
export function validarRangoPeriodo(desde: string, hasta: string): string | null {
  return desde && hasta && desde > hasta ? MENSAJE_RANGO_INVALIDO : null;
}

/**
 * Mensaje de un error del Libro Diario. `CON-005` agrega la `diferencia` del Problem Details con formato de
 * moneda (p. ej. "Diferencia: $13.00"); el resto usa {@link mensajeContabilidad}.
 * @param error error capturado de una mutación o de la vista previa
 */
export function mensajeAsiento(error: unknown): string {
  const base = mensajeContabilidad(error);
  if (esErrorApi(error, 'CON-005') && error.diferencia !== undefined) {
    return `${base} Diferencia: ${formatearMonedaConSigno(error.diferencia)}.`;
  }
  return base;
}

/**
 * Cabecera del formulario a la que pertenece un error de negocio del Libro Diario.
 * @param error error capturado
 * @returns `fecha` o `lineas` (el total del asiento); `null` si no es de un campo de cabecera
 */
export function campoCabeceraAsiento(error: unknown): 'fecha' | 'lineas' | null {
  return esErrorApi(error) && error.codigo ? (CAMPO_ASIENTO_POR_CODIGO[error.codigo] ?? null) : null;
}
