import { randomBytes } from 'node:crypto';
import { expect, test, type Page } from '@playwright/test';
import { esperarCorreo, primerEnlace } from './mailpit';
import { codigoTotp, msHastaSiguienteVentana } from './totp';

/**
 * Soporte e2e: registro de una persona nueva en el Keycloak real (formulario de autorregistro, configuración del
 * TOTP y verificación del correo recibido en Mailpit). Lo comparten `f1-nucleo.spec.ts` y `f2-contabilidad.spec.ts`
 * para no duplicar el recorrido. Ningún secreto (contraseña, semilla TOTP) se imprime ni se adjunta.
 */

/** Dirección de Keycloak del entorno local; sobrescribible con `E2E_KEYCLOAK_URL`. */
export const URL_KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180';

/** Dirección de la API del entorno local; sobrescribible con `E2E_API_URL`. */
export const URL_API = process.env.E2E_API_URL ?? 'http://localhost:8080';

/** Asunto del correo de verificación del realm (infra/keycloak/temas/pilot/email/messages/messages_es.properties). */
export const ASUNTO_VERIFICACION = 'Verifica tu correo en Pilot';

/** Datos de una persona nueva, únicos por ejecución. */
export interface DatosUsuario {
  correo: string;
  /** Contraseña aleatoria; nunca se imprime. */
  contrasena: string;
  nombre: string;
  apellido: string;
  /** Nombre y apellido: el nombre del espacio de trabajo que muestra la cabecera de Pilot (ADR-029). */
  nombreCompleto: string;
  /** Teléfono salvadoreño de 8 dígitos (sin el prefijo +503). */
  telefono: string;
}

/** Lo que el resto del recorrido necesita del registro: para un segundo inicio de sesión con MFA. */
export interface RegistroCompletado {
  /** Semilla Base32 del TOTP, solo en memoria de la prueba. */
  semillaTotp: string;
  /** Ventana de 30 s del código de configuración: el realm no admite reutilizar un código (otpPolicyCodeReusable). */
  ventanaDelCodigoDeConfiguracion: number;
}

/**
 * Convierte una marca alfanumérica en solo letras (el validador de nombres de Keycloak rechaza símbolos y conviene
 * evitar dígitos en el apellido).
 *
 * @param marca texto alfanumérico
 * @returns el texto con cada dígito sustituido por una letra
 */
export const soloLetras = (marca: string): string =>
  [...marca].map((c) => (/\d/.test(c) ? String.fromCharCode(97 + Number(c)) : c)).join('');

/**
 * Genera los datos de una persona nueva: correo con marca de tiempo, contraseña aleatoria y teléfono al azar.
 *
 * @param prefijo prefijo del correo (por ejemplo `e2e-f2`), para reconocer de qué spec viene el usuario
 * @returns datos únicos por ejecución
 */
export function datosUsuarioNuevo(prefijo: string): DatosUsuario {
  // 1. Marca de tiempo en base 36: hace único el correo y el apellido
  const marca = Date.now().toString(36);
  const nombre = 'Elena';
  const apellido = `Prueba${soloLetras(marca)}`;
  return {
    correo: `${prefijo}-${marca}@pilot.test`,
    // 2. Contraseña aleatoria con mayúscula, minúscula, dígito y símbolo (política del realm)
    contrasena: `${randomBytes(18).toString('base64url')}Aa1!`,
    nombre,
    apellido,
    nombreCompleto: `${nombre} ${apellido}`,
    // 3. Teléfono de El Salvador: 7 y siete dígitos al azar
    telefono: `7${Math.floor(Math.random() * 10_000_000)
      .toString()
      .padStart(7, '0')}`,
  };
}

/**
 * Registra a la persona en Keycloak y la deja dentro de Pilot: abre Pilot (redirige al login OIDC), llena el
 * autorregistro (ADR-028: sin DUI, con teléfono y consentimiento opcional), configura el TOTP con la semilla en modo
 * texto y verifica el correo con el mensaje real de Mailpit. Orden real del realm: `CONFIGURE_TOTP` (prioridad 10)
 * precede a `VERIFY_EMAIL` (prioridad 50), así que el TOTP se pide antes que la verificación (ADR-027).
 *
 * @param page página de Playwright, aún sin sesión
 * @param datos datos de la persona nueva
 * @returns la semilla y la ventana del código de configuración, para un segundo inicio de sesión
 */
export async function registrarUsuario(page: Page, datos: DatosUsuario): Promise<RegistroCompletado> {
  // ----------------------------------------------------------------------------------- 1. registro
  await test.step('Pilot redirige a Keycloak y la persona se registra', async () => {
    // 1. Abrir Pilot sin sesión lleva al login de Keycloak (OIDC con PKCE)
    await page.goto('/');
    await page.waitForURL(`${URL_KEYCLOAK}/realms/pilot/**`);

    // 2. "Registrarse" abre el formulario de autorregistro (ADR-028: sin DUI, con teléfono y consentimiento opcional)
    await page.getByRole('link', { name: /registrarse/i }).click();
    await page.fill('#email', datos.correo);
    await page.fill('#password', datos.contrasena);
    await page.fill('#password-confirm', datos.contrasena);
    await page.fill('#firstName', datos.nombre);
    await page.fill('#lastName', datos.apellido);
    await page.fill('#telefono', datos.telefono);
    await page.check('#recomendaciones_correo-true');
    await page.locator('input[type="submit"]').click();
  });

  // --------------------------------------------------------------------------------------- 2. TOTP
  let ventanaDelCodigoDeConfiguracion = 0;
  let semillaTotp = '';
  await test.step('Configura el TOTP con la semilla en modo texto y un código RFC 6238', async () => {
    await page.waitForURL(/execution=CONFIGURE_TOTP/);

    // 1. Modo texto: "¿No consigues escanear?" muestra la semilla Base32
    await page.locator('#mode-manual').click();
    const semilla = await page.locator('#kc-totp-secret-key').innerText();
    expect(semilla.replace(/\s/g, '')).toMatch(/^[A-Z2-7]{16,}$/);

    // 2. Si la ventana de 30 s está por terminar, se espera a la siguiente para no enviar un código vencido
    if (msHastaSiguienteVentana() < 4_000) await page.waitForTimeout(msHastaSiguienteVentana() + 500);

    // 3. Código TOTP propio (node:crypto), nombre del dispositivo y confirmación
    ventanaDelCodigoDeConfiguracion = Math.floor(Date.now() / 30_000);
    await page.fill('#totp', codigoTotp(semilla));
    await page.fill('#userLabel', 'dispositivo e2e');
    await page.locator('#saveTOTPBtn').click();

    // 4. Guarda la semilla para un segundo inicio de sesión (solo en memoria de la prueba; no se imprime)
    semillaTotp = semilla;
  });

  // ------------------------------------------------------------------------ 3. correo de verificación
  await test.step('Verifica el correo con el mensaje real recibido en Mailpit', async () => {
    await page.waitForURL(/execution=VERIFY_EMAIL/);

    // 1. El correo llega a Mailpit con el asunto del realm y para la dirección registrada
    const correoRecibido = await esperarCorreo(datos.correo);
    expect(correoRecibido.asunto).toBe(ASUNTO_VERIFICACION);
    expect(correoRecibido.para).toContain(datos.correo);

    // 2. El enlace de verificación es el de Keycloak; abrirlo completa el registro y entra a Pilot
    const enlace = primerEnlace(correoRecibido.texto);
    expect(enlace).toContain('/login-actions/action-token');
    await page.goto(enlace);
    await page.waitForURL('http://localhost:5173/**');
  });

  return { semillaTotp, ventanaDelCodigoDeConfiguracion };
}
