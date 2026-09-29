import Decimal from 'decimal.js';

/**
 * Constructor de Decimal propio del módulo. El valor por defecto de decimal.js (precision = 20 dígitos
 * significativos) redondea en silencio: un monto válido llega a 19 dígitos y sus sumas pueden pasarse.
 * Se usa un clon con precision 50 (sin tocar `Decimal.set`, para no afectar otro código global)
 * y redondeo HALF_UP (CLAUDE.md §1.1.2).
 */
const DecimalDinero = Decimal.clone({ precision: 50, rounding: Decimal.ROUND_HALF_UP });

/** Patrón de `Monto` del contrato OpenAPI: no negativo, hasta 17 enteros y máximo 2 decimales (ADR-013). */
const PATRON_MONTO = /^\d{1,17}(\.\d{1,2})?$/;

/**
 * Convierte una cadena a Decimal validando el patrón del contrato.
 * @param monto cadena decimal (p. ej. "123.45")
 * @throws Error si la cadena no cumple el patrón (p. ej. "1.005", "-1", "abc")
 */
function aDecimal(monto: string): Decimal {
  // 1. Se rechaza todo lo que el backend también rechazaría
  if (!PATRON_MONTO.test(monto)) throw new Error(`Monto inválido: "${monto}"`);
  return new DecimalDinero(monto);
}

/**
 * Suma montos con aritmética decimal exacta.
 * @param montos cadenas decimales válidas
 * @returns la suma con exactamente 2 decimales (p. ej. ["0.10","0.20"] → "0.30")
 */
export function sumarMontos(montos: string[]): string {
  // 1. Suma con decimal.js: nunca con number, para evitar errores de coma flotante
  const total = montos.reduce((acumulado, m) => acumulado.plus(aDecimal(m)), new DecimalDinero(0));
  return total.toFixed(2, Decimal.ROUND_HALF_UP);
}

/**
 * Compara dos montos por valor, ignorando la escala ("100.0" = "100.00").
 * @param a primer monto
 * @param b segundo monto
 */
export function sonIguales(a: string, b: string): boolean {
  return aDecimal(a).equals(aDecimal(b));
}

/**
 * Formatea un monto como moneda `$1,234.56` sin pasar por number (no pierde precisión con 17 dígitos).
 * @param monto cadena decimal válida
 */
export function formatearMoneda(monto: string): string {
  // 1. Fija 2 decimales con redondeo HALF_UP (CLAUDE.md §1.1.2)
  const [enteros = '0', decimales = '00'] = aDecimal(monto).toFixed(2, Decimal.ROUND_HALF_UP).split('.');
  // 2. Agrupa los miles a mano (el resultado no depende de los datos de Intl)
  const agrupados = enteros.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `$${agrupados}.${decimales}`;
}

/**
 * Indica si una cadena cumple el patrón de monto del contrato (no negativo, hasta 2 decimales).
 * @param monto texto a revisar
 */
export function esMontoValido(monto: string): boolean {
  return PATRON_MONTO.test(monto);
}

/**
 * Normaliza el texto de un campo de monto del formulario: un campo vacío vale "0.00" (una línea del
 * Libro Diario deja vacío el lado que no usa) y el resto se devuelve tal cual, sin recortar decimales.
 * @param texto contenido del campo
 */
export function normalizarMonto(texto: string): string {
  return texto.trim() === '' ? '0.00' : texto.trim();
}

/**
 * Indica si un monto vale cero, sin pasar por number.
 * @param monto cadena decimal válida
 */
export function esCero(monto: string): boolean {
  return aDecimal(monto).isZero();
}

/**
 * Resta dos montos con aritmética decimal exacta; el resultado puede ser negativo.
 * @param a minuendo
 * @param b sustraendo
 * @returns `a − b` con exactamente 2 decimales y signo `-` si es negativo (p. ej. "-13.00")
 */
export function restarMontos(a: string, b: string): string {
  return aDecimal(a).minus(aDecimal(b)).toFixed(2, Decimal.ROUND_HALF_UP);
}

/**
 * Formatea un monto que puede ser negativo (una diferencia Debe − Haber) como `-$13.00` o `$13.00`.
 * @param monto cadena decimal con signo opcional (`MontoConSigno` del contrato)
 */
export function formatearMonedaConSigno(monto: string): string {
  // 1. El signo se separa para reutilizar la validación y el formato de los montos sin signo
  const negativo = monto.startsWith('-');
  const formateado = formatearMoneda(negativo ? monto.slice(1) : monto);
  // 2. Un cero negativo ("-0.00") se muestra sin signo
  return negativo && formateado !== '$0.00' ? `-${formateado}` : formateado;
}
