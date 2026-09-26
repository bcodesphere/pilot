/** Formato de fechas de negocio: hora de El Salvador (UTC−6, sin horario de verano) y localización `es-SV` (CLAUDE.md §1.1.10). */

/** Zona horaria de negocio. */
export const ZONA_NEGOCIO = 'America/El_Salvador';

/** Formateador de fecha y hora legibles (p. ej. "25 sept 2026, 10:30"). */
const formatoFechaHora = new Intl.DateTimeFormat('es-SV', {
  timeZone: ZONA_NEGOCIO,
  dateStyle: 'medium',
  timeStyle: 'short',
});

/**
 * Formatea un instante UTC (ISO-8601) en hora de El Salvador.
 * @param instante instante ISO o nulo
 * @returns texto localizado, o "—" si no hay valor
 */
export function formatearFechaHora(instante: string | null | undefined): string {
  return instante ? formatoFechaHora.format(new Date(instante)) : '—';
}

/**
 * Fecha de hoy en El Salvador como `AAAA-MM-DD` (la localización `en-CA` produce ese formato ISO).
 * @param ahora instante de referencia (parametrizable para pruebas)
 */
export function hoyElSalvador(ahora: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: ZONA_NEGOCIO }).format(ahora);
}

/**
 * Convierte una fecha `AAAA-MM-DD` a instante UTC: el último segundo de ese día en El Salvador
 * (UTC−6 fijo), para que una clave "venza el día X" siga vigente durante todo ese día.
 * @param fecha fecha ISO sin hora
 * @returns instante ISO-8601 en UTC
 */
export function finDeDiaElSalvadorEnUtc(fecha: string): string {
  return new Date(`${fecha}T23:59:59-06:00`).toISOString();
}
