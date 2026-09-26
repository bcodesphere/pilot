import { randomBytes } from 'node:crypto';
import { expect, test, type Page, type Response } from '@playwright/test';
import { esperarCorreo, primerEnlace } from './soporte/mailpit';
import { codigoTotp, msHastaSiguienteVentana } from './soporte/totp';

/**
 * Aceptación F1, criterio 1 del plan (docs/plan-de-trabajo.md): "un usuario nuevo se registra, verifica su correo,
 * inicia sesión (con MFA), encuentra su empresa personal ya creada, instala Contabilidad desde Apps y la ve en el
 * lanzador". Recorre el entorno real (Keycloak, Mailpit, backend y frontend) con un usuario nuevo por ejecución.
 *
 * Además confirma: que Keycloak entrega `refresh_token` a `pilot-web`; que ningún token queda en `localStorage` ni
 * `sessionStorage` (CLAUDE.md 1.2.10); que la revocación de una API key responde 204; y que el recorrido no produce
 * errores de consola ni respuestas 5xx de la API. Ningún secreto ni token se imprime.
 */

/** Dirección de Keycloak y de la API del entorno local; sobrescribibles con `E2E_*`. */
const URL_KEYCLOAK = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180';
const URL_API = process.env.E2E_API_URL ?? 'http://localhost:8080';

/** Asunto del correo de verificación del realm (infra/keycloak/temas/pilot/email/messages/messages_es.properties). */
const ASUNTO_VERIFICACION = 'Verifica tu correo en Pilot';

/** Tokens que Keycloak entregó al navegador (solo para compararlos con el almacenamiento; nunca se imprimen). */
interface TokensVistos {
  access: string[];
  id: string[];
  refresh: string[];
}

/** Respuesta 5xx u otra petición fallida, para adjuntarla como evidencia. */
interface PeticionFallida {
  url: string;
  metodo: string;
  estado: number | string;
}

/**
 * Convierte una marca alfanumérica en solo letras (el validador de nombres de Keycloak rechaza símbolos y conviene
 * evitar dígitos en el apellido).
 */
const soloLetras = (marca: string) =>
  [...marca].map((c) => (/\d/.test(c) ? String.fromCharCode(97 + Number(c)) : c)).join('');

/**
 * Lee el contenido de `localStorage` y `sessionStorage` del navegador como texto. Se evalúa como cadena dentro de la
 * página: la regla de ESLint que prohíbe esos globales protege el código de la aplicación, y aquí se leen justamente
 * para comprobar que NO guardan tokens.
 */
const leerAlmacenamiento = (page: Page): Promise<string> =>
  page.evaluate('JSON.stringify({ local: { ...localStorage }, sesion: { ...sessionStorage } })');

test('registro, correo real, MFA, empresa personal, apps y API keys (criterio 1 de F1)', async ({
  page,
}, testInfo) => {
  // ------------------------------------------------------------------------------------------ datos únicos
  // 1. Un usuario nuevo por ejecución: correo con marca de tiempo y contraseña aleatoria que nunca se imprime
  const marca = Date.now().toString(36);
  const correo = `e2e-f1-${marca}@pilot.test`;
  const contrasena = `${randomBytes(18).toString('base64url')}Aa1!`;
  const nombre = 'Elena';
  const apellido = `Prueba${soloLetras(marca)}`;
  const nombreCompleto = `${nombre} ${apellido}`;
  const telefono = `7${Math.floor(Math.random() * 10_000_000)
    .toString()
    .padStart(7, '0')}`;

  // -------------------------------------------------------------------------------------- observadores
  // 2. Tokens de Keycloak, respuestas de la API y errores, recogidos durante todo el recorrido
  const tokens: TokensVistos = { access: [], id: [], refresh: [] };
  const fallidas: PeticionFallida[] = [];
  const erroresDeConsola: string[] = [];
  const respuestasApi5xx: PeticionFallida[] = [];
  let sinRefreshToken = 0;
  const catalogoVisto: Array<Array<{ codigo: string; nombre: string; estado: string }>> = [];

  page.on('response', async (respuesta: Response) => {
    const url = respuesta.url();
    const peticion = respuesta.request();
    // 2.1 Respuestas del endpoint de tokens: se guardan los valores para compararlos con el almacenamiento
    if (url.includes('/protocol/openid-connect/token') && peticion.method() === 'POST' && respuesta.ok()) {
      const cuerpo = (await respuesta.json().catch(() => ({}))) as Record<string, string>;
      if (cuerpo.access_token) tokens.access.push(cuerpo.access_token);
      if (cuerpo.id_token) tokens.id.push(cuerpo.id_token);
      if (cuerpo.refresh_token) tokens.refresh.push(cuerpo.refresh_token);
      else sinRefreshToken += 1;
    }
    // 2.1b Catálogo de apps que devuelve la API (para leer las apps Enterprise de la respuesta real)
    if (url.endsWith('/aplicaciones') && peticion.method() === 'GET' && respuesta.ok()) {
      catalogoVisto.push(await respuesta.json().catch(() => []));
    }
    // 2.2 Peticiones a la API o a Keycloak que fallan (4xx/5xx); solo las 5xx de la API hacen fallar la prueba
    if (respuesta.status() >= 400 && (url.startsWith(URL_API) || url.startsWith(URL_KEYCLOAK))) {
      const registro = { url, metodo: peticion.method(), estado: respuesta.status() };
      fallidas.push(registro);
      if (url.startsWith(URL_API) && respuesta.status() >= 500) respuestasApi5xx.push(registro);
    }
  });
  page.on('requestfailed', (peticion) =>
    fallidas.push({
      url: peticion.url(),
      metodo: peticion.method(),
      estado: peticion.failure()?.errorText ?? '',
    }),
  );
  // 2.3 Errores de consola mientras se está en Pilot (los de las páginas de Keycloak no son del producto)
  page.on('console', (mensaje) => {
    if (mensaje.type() === 'error' && page.url().startsWith('http://localhost:5173')) {
      erroresDeConsola.push(mensaje.text());
    }
  });

  /** Comprueba que ningún token de los recibidos aparece en `localStorage` ni en `sessionStorage`. */
  const verificarSinTokensEnAlmacenamiento = async () => {
    const almacenamiento = await leerAlmacenamiento(page);
    for (const token of [...tokens.access, ...tokens.id, ...tokens.refresh]) {
      expect(
        almacenamiento.includes(token),
        'un token quedó guardado en el almacenamiento del navegador',
      ).toBe(false);
    }
  };

  // ----------------------------------------------------------------------------------- 1. registro
  await test.step('Pilot redirige a Keycloak y la persona se registra', async () => {
    // 1. Abrir Pilot sin sesión lleva al login de Keycloak (OIDC con PKCE)
    await page.goto('/');
    await page.waitForURL(`${URL_KEYCLOAK}/realms/pilot/**`);

    // 2. "Registrarse" abre el formulario de autorregistro (ADR-028: sin DUI, con teléfono y consentimiento opcional)
    await page.getByRole('link', { name: /registrarse/i }).click();
    await page.fill('#email', correo);
    await page.fill('#password', contrasena);
    await page.fill('#password-confirm', contrasena);
    await page.fill('#firstName', nombre);
    await page.fill('#lastName', apellido);
    await page.fill('#telefono', telefono);
    await page.check('#recomendaciones_correo-true');
    await page.locator('input[type="submit"]').click();
  });

  // --------------------------------------------------------------------------------------- 2. TOTP
  // Orden real del realm: la acción CONFIGURE_TOTP (prioridad 10) precede a VERIFY_EMAIL (prioridad 50), así que
  // Keycloak pide primero el TOTP y solo después envía el correo de verificación.
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

    // 4. Guarda la semilla para el segundo inicio de sesión (solo en memoria de la prueba; no se imprime)
    semillaTotp = semilla;
  });

  // ------------------------------------------------------------------------ 3. correo de verificación
  await test.step('Verifica el correo con el mensaje real recibido en Mailpit', async () => {
    await page.waitForURL(/execution=VERIFY_EMAIL/);

    // 1. El correo llega a Mailpit con el asunto del realm y para la dirección registrada
    const correoRecibido = await esperarCorreo(correo);
    expect(correoRecibido.asunto).toBe(ASUNTO_VERIFICACION);
    expect(correoRecibido.para).toContain(correo);

    // 2. El enlace de verificación es el de Keycloak; abrirlo completa el registro y entra a Pilot
    const enlace = primerEnlace(correoRecibido.texto);
    expect(enlace).toContain('/login-actions/action-token');
    await page.goto(enlace);
    await page.waitForURL('http://localhost:5173/**');
  });

  // --------------------------------------------------------------------- 4. token y almacenamiento
  await test.step('Keycloak entrega refresh_token y ningún token queda en el almacenamiento', async () => {
    // Espera a que el shell cargue (la cabecera con el nombre del espacio de trabajo)
    await expect(page.getByRole('banner')).toContainText(nombreCompleto);

    // 1. El primer intercambio del código por tokens trae refresh_token (pendiente de F1-07)
    expect(tokens.access.length, 'no se interceptó ninguna respuesta del endpoint de tokens').toBeGreaterThan(
      0,
    );
    expect(sinRefreshToken, 'respuestas del endpoint de tokens sin refresh_token').toBe(0);
    expect(tokens.refresh.length).toBeGreaterThan(0);

    // 2. Ni el access, ni el id, ni el refresh token están en localStorage ni sessionStorage
    await verificarSinTokensEnAlmacenamiento();
  });

  // ----------------------------------------------------------------------- 5. empresa personal
  await test.step('La empresa personal ya existe y no aparece el selector de empresa', async () => {
    const cabecera = page.getByRole('banner');
    // 1. El espacio de trabajo lleva el nombre del usuario (ADR-029)
    await expect(cabecera.locator('span', { hasText: nombreCompleto }).first()).toBeVisible();
    // 2. Con una sola membresía no hay selector (ADR-032)
    await expect(cabecera.getByRole('combobox')).toHaveCount(0);
    await expect(cabecera.getByText('Empresa', { exact: true })).toHaveCount(0);
  });

  // ------------------------------------------------------------------------------------ 6. apps
  await test.step('Apps: Contabilidad disponible, Enterprise bloqueadas e instalación sin recargar', async () => {
    // 1. Entra a "Apps". El catálogo ya pudo cargarse en el lanzador (TanStack Query lo reutiliza), así que se toma
    //    del último GET /aplicaciones observado en todo el recorrido, no de una petición nueva
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Apps' }).click();
    await expect
      .poll(() => catalogoVisto.length, { message: 'no se vio GET /aplicaciones' })
      .toBeGreaterThan(0);
    const catalogo = catalogoVisto[catalogoVisto.length - 1]!;
    await expect(page.getByRole('heading', { name: 'Apps', level: 1 })).toBeVisible();

    /** Tarjeta de una app, localizada por su encabezado. */
    const tarjeta = (nombreApp: string) =>
      page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: nombreApp, exact: true }) });

    // 2. Contabilidad se puede instalar
    expect(catalogo.find((a) => a.codigo === 'contabilidad')?.estado).toBe('DISPONIBLE');
    await expect(page.getByRole('button', { name: 'Instalar Contabilidad' })).toBeEnabled();

    // 3. Cada app Enterprise (leída del catálogo, no de una lista fija) tiene la insignia y el botón deshabilitado
    const enterprise = catalogo.filter((a) => a.estado === 'BLOQUEADA_ENTERPRISE');
    expect(enterprise.length).toBeGreaterThan(0);
    for (const appEnterprise of enterprise) {
      const t = tarjeta(appEnterprise.nombre);
      await expect(t.getByText('Enterprise', { exact: true })).toBeVisible();
      await expect(t.getByRole('button', { name: 'Disponible en Enterprise' })).toBeDisabled();
    }

    // 4. Marca de "sin recarga": si la página se recargara, esta variable desaparecería
    await page.evaluate('window.marcaSinRecarga = 1');

    // 5. Instalar espera el POST y comprueba el 201
    const [instalacion] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/aplicaciones/contabilidad/instalacion') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Instalar Contabilidad' }).click(),
    ]);
    expect(instalacion.status()).toBe(201);
    await expect(page.getByText('Contabilidad instalada')).toBeVisible();

    // 6. Contabilidad aparece en el lanzador (inicio) sin recargar la página
    await page.getByRole('link', { name: 'Pilot', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Tus aplicaciones' })).toBeVisible();
    await expect(page.getByRole('main').getByRole('link', { name: /Contabilidad/ })).toBeVisible();
    expect(await page.evaluate('window.marcaSinRecarga'), 'la página se recargó').toBe(1);
  });

  // -------------------------------------------------------------------------------------- 7. API keys
  await test.step('API keys: el secreto se muestra una vez y la revocación responde 204', async () => {
    // 1. Menú de usuario → API keys
    await page.getByRole('button', { name: nombreCompleto }).click();
    await page.getByRole('menuitem', { name: 'API keys' }).click();
    await expect(page.getByRole('heading', { name: 'API keys', level: 1 })).toBeVisible();

    // 2. Crear: el diálogo muestra el secreto una sola vez
    await page.getByRole('button', { name: 'Crear API key' }).click();
    await page.fill('#apikey-nombre', 'e2e n8n');
    await page.getByRole('button', { name: 'Crear', exact: true }).click();
    const dialogo = page.getByRole('dialog', { name: 'API key creada' });
    await expect(dialogo).toBeVisible();
    const secreto = await dialogo.locator('#secreto-api-key').inputValue();
    expect(secreto).toMatch(/^pk_[a-z0-9]{4,13}\..{20,}$/);

    // 3. Cerrar el diálogo: la página ya no contiene el secreto (ni en el texto ni en atributos)
    await dialogo.getByRole('button', { name: 'Cerrar' }).click();
    await expect(dialogo).toBeHidden();
    const parteSecreta = secreto.slice(secreto.indexOf('.') + 1);
    expect((await page.content()).includes(parteSecreta), 'el secreto sigue en la página').toBe(false);
    expect(await leerAlmacenamiento(page)).not.toContain(parteSecreta);

    // 4. La fila aparece con su nombre; revocar pide confirmación en un diálogo
    const fila = page.getByRole('row', { name: /e2e n8n/ });
    await expect(fila).toBeVisible();
    await page.getByRole('button', { name: 'Revocar e2e n8n' }).click();
    const confirmar = page.getByRole('dialog', { name: 'Revocar API key' });
    await expect(confirmar).toBeVisible();

    // 5. Confirmar espera el DELETE y comprueba 204 (en la prueba de Chrome de F1-08 se vio como "503")
    const [revocacion] = await Promise.all([
      page.waitForResponse(
        (r) => /\/api-keys\/[0-9a-f-]{36}$/.test(r.url()) && r.request().method() === 'DELETE',
      ),
      confirmar.getByRole('button', { name: 'Revocar', exact: true }).click(),
    ]);
    expect(revocacion.status()).toBe(204);

    // 6. La fila muestra "Revocada" y ya no ofrece el botón de revocar
    await expect(fila.getByText('Revocada')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Revocar e2e n8n' })).toHaveCount(0);
  });

  // ------------------------------------------------------------------ 8. segundo inicio con MFA
  await test.step('Cierra la sesión y vuelve a entrar con correo, contraseña y un código TOTP nuevo', async () => {
    // 1. Cerrar sesión desde el menú: Keycloak cierra la sesión y Pilot vuelve a pedir el login
    await page.getByRole('button', { name: nombreCompleto }).click();
    await page.getByRole('menuitem', { name: 'Cerrar sesión' }).click();
    // Keycloak puede pedir confirmar el cierre de sesión si no recibe el id_token_hint
    const confirmarCierre = page.locator('#kc-logout');
    await Promise.race([
      page.waitForSelector('#username', { timeout: 20_000 }),
      confirmarCierre.waitFor({ timeout: 20_000 }).then(() => confirmarCierre.click()),
    ]).catch(() => undefined);
    await page.waitForSelector('#username');

    // 2. Correo y contraseña
    await page.fill('#username', correo);
    await page.fill('#password', contrasena);
    await page.locator('#kc-login').click();

    // 3. Keycloak pide el código OTP. El realm no admite reutilizar un código (otpPolicyCodeReusable = false):
    //    se espera a la siguiente ventana de 30 s respecto de la del código de configuración
    await page.waitForSelector('#otp');
    while (
      Math.floor(Date.now() / 30_000) <= ventanaDelCodigoDeConfiguracion ||
      msHastaSiguienteVentana() < 4_000
    ) {
      await page.waitForTimeout(1_000);
    }
    await page.fill('#otp', codigoTotp(semillaTotp));
    await page.locator('#kc-login').click();

    // 4. De vuelta en Pilot, Contabilidad sigue instalada en el lanzador
    await page.waitForURL('http://localhost:5173/**');
    await expect(page.getByRole('heading', { name: 'Tus aplicaciones' })).toBeVisible();
    await expect(page.getByRole('main').getByRole('link', { name: /Contabilidad/ })).toBeVisible();
    await expect(page.getByRole('banner')).toContainText(nombreCompleto);
    await verificarSinTokensEnAlmacenamiento();
  });

  // -------------------------------------------------------------------------- 9. sin errores
  await test.step('El recorrido no dejó errores de consola ni respuestas 5xx de la API', async () => {
    // Evidencia: registro de las peticiones fallidas (incluidos los 4xx esperados, si los hubo)
    if (fallidas.length > 0) {
      await testInfo.attach('peticiones-fallidas.json', {
        body: JSON.stringify(fallidas, null, 2),
        contentType: 'application/json',
      });
    }
    expect(respuestasApi5xx, 'respuestas 5xx de la API').toEqual([]);
    expect(erroresDeConsola, 'errores de consola en Pilot').toEqual([]);
  });
});
