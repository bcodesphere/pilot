import { createHmac } from 'node:crypto';

/**
 * Cálculo de códigos TOTP (RFC 6238) con `node:crypto`, sin dependencias nuevas. Sirve al e2e de F1 para completar
 * la configuración de MFA de Keycloak (ADR-027): el realm usa TOTP, HmacSHA1, 6 dígitos y período de 30 s.
 */

/** Alfabeto Base32 de RFC 4648 (el que usa Keycloak para mostrar la semilla en modo texto). */
const ALFABETO_BASE32 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';

/** Parámetros del TOTP; los valores por defecto son los del realm `pilot` (`otpPolicy`). */
export interface OpcionesTotp {
  /** Número de dígitos del código (6 en el realm; 8 en los vectores de prueba de RFC 6238). */
  digitos?: number;
  /** Duración de cada ventana en segundos (30 en el realm). */
  periodo?: number;
  /** Algoritmo HMAC (`sha1` en el realm). */
  algoritmo?: 'sha1' | 'sha256' | 'sha512';
}

/**
 * Decodifica una semilla Base32 (RFC 4648). Tolera los espacios con que Keycloak agrupa la clave, las minúsculas y
 * el relleno `=`.
 * @param texto semilla en Base32 (p. ej. `JBSW Y3DP EHPK 3PXP`)
 * @returns los bytes de la semilla
 * @throws Error si aparece un carácter fuera del alfabeto Base32
 */
export function decodificarBase32(texto: string): Buffer {
  // 1. Normaliza: sin espacios ni relleno y en mayúsculas
  const limpio = texto.replace(/[\s=]/g, '').toUpperCase();
  const bytes: number[] = [];
  let acumulado = 0;
  let bits = 0;
  for (const caracter of limpio) {
    // 2. Cada carácter aporta 5 bits
    const valor = ALFABETO_BASE32.indexOf(caracter);
    if (valor < 0) throw new Error(`Carácter Base32 inválido: ${caracter}`);
    acumulado = (acumulado << 5) | valor;
    bits += 5;
    // 3. Cada vez que hay 8 bits o más se emite un byte
    if (bits >= 8) {
      bits -= 8;
      bytes.push((acumulado >> bits) & 0xff);
    }
  }
  return Buffer.from(bytes);
}

/**
 * Calcula el código TOTP de una semilla en bytes (RFC 6238, que aplica HOTP de RFC 4226 al contador de tiempo).
 * @param semilla bytes de la clave compartida
 * @param instanteMs instante en milisegundos desde la época Unix (por defecto, ahora)
 * @param opciones dígitos, período y algoritmo
 * @returns el código con ceros a la izquierda hasta completar los dígitos
 */
export function codigoTotpDesdeBytes(
  semilla: Buffer,
  instanteMs: number = Date.now(),
  opciones: OpcionesTotp = {},
): string {
  const { digitos = 6, periodo = 30, algoritmo = 'sha1' } = opciones;

  // 1. Contador = número de períodos completos desde la época, como entero de 8 bytes big-endian
  const contador = Math.floor(instanteMs / 1000 / periodo);
  const mensaje = Buffer.alloc(8);
  mensaje.writeBigUInt64BE(BigInt(contador));

  // 2. HMAC del contador con la semilla
  const hmac = createHmac(algoritmo, semilla).update(mensaje).digest();

  // 3. Truncado dinámico (RFC 4226 §5.3): 31 bits a partir del desplazamiento que marca el último nibble
  const desplazamiento = hmac[hmac.length - 1]! & 0x0f;
  const binario = hmac.readUInt32BE(desplazamiento) & 0x7fffffff;

  // 4. Los últimos `digitos` dígitos decimales, con ceros a la izquierda
  return String(binario % 10 ** digitos).padStart(digitos, '0');
}

/**
 * Calcula el código TOTP de una semilla Base32, como la que Keycloak muestra en su modo texto.
 * @param semillaBase32 semilla en Base32
 * @param instanteMs instante en milisegundos desde la época Unix (por defecto, ahora)
 * @param opciones dígitos, período y algoritmo
 * @returns el código de `digitos` dígitos
 */
export function codigoTotp(
  semillaBase32: string,
  instanteMs: number = Date.now(),
  opciones: OpcionesTotp = {},
): string {
  return codigoTotpDesdeBytes(decodificarBase32(semillaBase32), instanteMs, opciones);
}

/**
 * Milisegundos que faltan para que empiece la siguiente ventana TOTP. El realm no admite reutilizar un código
 * (`otpPolicyCodeReusable = false`), así que el segundo inicio de sesión espera este tiempo antes de enviar el suyo.
 * @param instanteMs instante actual en milisegundos desde la época Unix
 * @param periodo duración de la ventana en segundos
 * @returns milisegundos hasta el siguiente múltiplo del período
 */
export function msHastaSiguienteVentana(instanteMs: number = Date.now(), periodo = 30): number {
  const ventanaMs = periodo * 1000;
  return ventanaMs - (instanteMs % ventanaMs);
}
