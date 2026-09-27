import { expect, test, type Response } from '@playwright/test';
import { datosUsuarioNuevo, registrarUsuario, URL_API } from './soporte/registro';

/**
 * Aceptación F3 en el navegador (docs/plan-de-trabajo.md, F3): con una persona nueva recorre el entorno real
 * (Keycloak, Mailpit, backend y frontend) y comprueba lo que ve y hace quien registra el Libro Diario:
 *
 * - un asiento `CON_IVA` con una línea "lleva IVA" muestra la vista previa del backend (Ventas 100.00 + IVA débito
 *   fiscal 13.00), bloquea Guardar y anuncia la diferencia mientras no cuadra, y lo guarda al corregirse;
 * - un descuadre manual sin IVA bloquea Guardar con el mensaje de la diferencia exacta y no llega a la API;
 * - revertir un asiento navega a la reversión sin dejar ningún diálogo abierto (F3-04, corrección 2), con los
 *   lados intercambiados, y el original queda "Revertido" enlazado a su reversión y sin botón "Revertir";
 * - con movimientos reales, el catálogo rechaza desactivar una cuenta con saldo (`CON-012`) y cambiarle el
 *   código (`CON-011`), con el mensaje junto al campo.
 *
 * Ningún secreto (contraseña, semilla TOTP) se imprime ni se adjunta.
 */

/** Petición de la API que falló, para adjuntarla como evidencia. */
interface PeticionApi {
  url: string;
  metodo: string;
  estado: number;
}

test('Libro Diario: IVA, descuadre, reversión y catálogo con movimientos reales (F3)', async ({
  page,
}, testInfo) => {
  // 1. Una persona nueva por ejecución; la contraseña es aleatoria y nunca se imprime
  const usuario = datosUsuarioNuevo('e2e-f3');

  // -------------------------------------------------------------------------------------- observadores
  const erroresApi: PeticionApi[] = [];
  const erroresDeConsola: string[] = [];
  let postsDeAsientos = 0;

  page.on('response', (respuesta: Response) => {
    if (respuesta.url().startsWith(URL_API) && respuesta.status() >= 400) {
      erroresApi.push({
        url: respuesta.url(),
        metodo: respuesta.request().method(),
        estado: respuesta.status(),
      });
    }
  });
  page.on('request', (peticion) => {
    if (peticion.method() === 'POST' && /\/contabilidad\/asientos$/.test(peticion.url())) {
      postsDeAsientos += 1;
    }
  });
  page.on('console', (mensaje) => {
    // Solo cuentan los errores mientras se está en Pilot (las páginas de Keycloak no son del producto)
    if (mensaje.type() !== 'error' || !page.url().startsWith('http://localhost:5173')) return;
    // Chrome registra en consola cada 4xx de la API; los que la prueba provoca a propósito se verifican con sus
    // respuestas más abajo, así que no deben hacer fallar la prueba por sí solos
    const esErrorHttpDeLaApi =
      mensaje.text().includes('Failed to load resource') && mensaje.location().url.startsWith(URL_API);
    if (!esErrorHttpDeLaApi) erroresDeConsola.push(mensaje.text());
  });

  // ------------------------------------------------------------------------- 1. registro e instalación
  await registrarUsuario(page, usuario);

  await test.step('Apps: instala Contabilidad y entra al Libro Diario', async () => {
    await expect(page.getByRole('banner')).toContainText(usuario.nombreCompleto);
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Apps' }).click();
    const [instalacion] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/aplicaciones/contabilidad/instalacion') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Instalar Contabilidad' }).click(),
    ]);
    expect(instalacion.status()).toBe(201);

    await page.getByRole('link', { name: 'Pilot', exact: true }).click();
    await page
      .getByRole('main')
      .getByRole('link', { name: /Contabilidad/ })
      .click();
    await page
      .getByRole('navigation', { name: 'Secciones de Contabilidad' })
      .getByRole('link', { name: 'Libro Diario' })
      .click();
    await expect(page).toHaveURL(/\/contabilidad\/libro-diario$/);
    await expect(page.getByRole('heading', { name: 'Libro Diario' })).toBeVisible();
  });

  // -------------------------------------------------------------- 2. nuevo asiento CON_IVA con vista previa
  let idAsientoOriginal = '';
  await test.step('Nuevo asiento CON_IVA: la vista previa expande el IVA y bloquea Guardar mientras no cuadra', async () => {
    await page.getByRole('link', { name: 'Nuevo asiento' }).click();
    await expect(page.getByRole('heading', { name: 'Nuevo asiento' })).toBeVisible();

    await page.getByLabel('Concepto').fill('Venta con IVA (e2e F3)');

    // 1. Línea 1: Caja general, 113.00 al Debe
    await page.getByLabel('Cuenta de la línea 1').fill('Caja general');
    await page.getByRole('option', { name: /Caja general/ }).click();
    await page.getByLabel('Debe de la línea 1').fill('113.00');

    // 2. Línea 2: Ventas gravadas, "lleva IVA", con un Haber deliberadamente distinto (50.00) para ver el descuadre
    await page.getByLabel('Cuenta de la línea 2').fill('Ventas gravadas');
    await page.getByRole('option', { name: /Ventas gravadas/ }).click();
    await page.getByRole('checkbox', { name: /Lleva IVA.*línea 2/ }).check();
    const [previaDescuadrada] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/asientos/vista-previa') && r.request().method() === 'POST',
      ),
      page.getByLabel('Haber de la línea 2').fill('50.00'),
    ]);
    expect(previaDescuadrada.status()).toBe(200);
    expect(((await previaDescuadrada.json()) as { cuadra: boolean }).cuadra).toBe(false);

    // 3. Mientras no cuadra: Guardar deshabilitado y la diferencia visible
    await expect(page.getByText(/El asiento no cuadra: la diferencia es/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Guardar' })).toBeDisabled();

    // 4. Se corrige el Haber a 113.00: la vista previa cuadra y muestra Ventas 100.00 + IVA débito fiscal 13.00
    const [previaCuadrada] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/asientos/vista-previa') && r.request().method() === 'POST',
      ),
      page.getByLabel('Haber de la línea 2').fill('113.00'),
    ]);
    expect(previaCuadrada.status()).toBe(200);
    expect(((await previaCuadrada.json()) as { cuadra: boolean }).cuadra).toBe(true);

    const tablaPrevia = page.getByRole('table', { name: 'Líneas del asiento con IVA' });
    await expect(tablaPrevia.getByRole('row').filter({ hasText: 'Ventas gravadas' })).toContainText('100.00');
    await expect(tablaPrevia.getByRole('row').filter({ hasText: 'IVA débito fiscal' })).toContainText(
      '13.00',
    );
    await expect(page.getByRole('button', { name: 'Guardar' })).toBeEnabled();

    // 5. Guardar registra el asiento (201) y navega al detalle "Asiento N.º 1/<año>"
    const [registro] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/asientos') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Guardar' }).click(),
    ]);
    expect(registro.status()).toBe(201);
    const asientoCreado = (await registro.json()) as { id: string; numero: number; anio: number };
    idAsientoOriginal = asientoCreado.id;
    await expect(page).toHaveURL(new RegExp(`/contabilidad/libro-diario/${asientoCreado.id}$`));
    await expect(
      page.getByRole('heading', { name: `Asiento N.º ${asientoCreado.numero}/${asientoCreado.anio}` }),
    ).toBeVisible();
  });

  // ---------------------------------------------------------------------------- 3. descuadre manual sin IVA
  await test.step('Descuadre manual sin IVA: Guardar deshabilitado con la diferencia exacta, sin llegar a la API', async () => {
    const postsAntes = postsDeAsientos;
    await page.getByRole('link', { name: 'Volver al Libro Diario' }).click();
    await page.getByRole('link', { name: 'Nuevo asiento' }).click();
    await expect(page.getByRole('heading', { name: 'Nuevo asiento' })).toBeVisible();

    await page.getByLabel('Concepto').fill('Descuadre manual (e2e F3)');
    await page.getByLabel('Cuenta de la línea 1').fill('Caja general');
    await page.getByRole('option', { name: /Caja general/ }).click();
    await page.getByLabel('Debe de la línea 1').fill('10.00');
    await page.getByLabel('Cuenta de la línea 2').fill('Ventas gravadas');
    await page.getByRole('option', { name: /Ventas gravadas/ }).click();
    await page.getByLabel('Haber de la línea 2').fill('9.00');

    // Sin IVA la validación es local (decimal.js): no hay POST de vista previa que esperar
    await expect(page.getByText('El asiento no cuadra: la diferencia es $1.00.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Guardar' })).toBeDisabled();
    // Un pequeño margen para descartar un POST tardío antes de seguir
    await page.waitForTimeout(500);
    expect(postsDeAsientos, 'ningún POST /contabilidad/asientos con el asiento descuadrado').toBe(postsAntes);

    await page.getByRole('button', { name: 'Cancelar' }).click();
    await expect(page).toHaveURL(/\/contabilidad\/libro-diario$/);
  });

  // ------------------------------------------------------------------------------------------- 4. reversión
  await test.step('Reversión: navega a la reversión sin diálogo abierto, con los lados intercambiados', async () => {
    await page.goto(`/contabilidad/libro-diario/${idAsientoOriginal}`);
    await page.getByRole('button', { name: 'Revertir' }).click();
    const dialogo = page.getByRole('dialog', { name: /Revertir el asiento/ });
    await expect(dialogo).toBeVisible();

    const [reversion] = await Promise.all([
      page.waitForResponse(
        (r) =>
          new RegExp(`/contabilidad/asientos/${idAsientoOriginal}/reversion$`).test(r.url()) &&
          r.request().method() === 'POST',
      ),
      dialogo.getByRole('button', { name: 'Revertir asiento' }).click(),
    ]);
    expect(reversion.status()).toBe(201);
    const asientoReversion = (await reversion.json()) as {
      id: string;
      numero: number;
      anio: number;
      lineas: Array<{ debe: string; haber: string }>;
    };

    // 1. Ningún diálogo queda abierto y la pantalla ya muestra la reversión
    await expect(dialogo).toBeHidden();
    await expect(page).toHaveURL(new RegExp(`/contabilidad/libro-diario/${asientoReversion.id}$`));
    await expect(
      page.getByRole('heading', { name: `Asiento N.º ${asientoReversion.numero}/${asientoReversion.anio}` }),
    ).toBeVisible();
    await expect(page.getByText(/revertido con el asiento/)).toBeVisible();

    // 2. Los lados están intercambiados respecto del original (Caja 113.00 Debe → 113.00 Haber en la reversión)
    expect(asientoReversion.lineas[0]?.haber).toBe('113.00');
    expect(asientoReversion.lineas[0]?.debe).toBe('0.00');
    const tablaReversion = page.getByRole('table', { name: 'Líneas del asiento' });
    await expect(tablaReversion.getByRole('row').filter({ hasText: 'Caja general' })).toContainText('113.00');

    // 3. El original aparece "Revertido", enlazado a su reversión y sin botón "Revertir"
    await page.getByRole('link', { name: 'ver el asiento revertido' }).click();
    await expect(page).toHaveURL(new RegExp(`/contabilidad/libro-diario/${idAsientoOriginal}$`));
    await expect(page.getByText('Revertido', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'ver la reversión' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Revertir' })).toHaveCount(0);
  });

  // ------------------------------------------------------------------ 5. cuenta con movimientos sin regla
  // Caja general y Ventas gravadas están precargadas con reglas de contabilización (EFECTIVO y VENTAS_GRAVADAS,
  // CLAUDE.md §12.5): desactivarlas siempre es CON-016 ("en uso por … una regla activa"), sin importar el saldo. Para
  // demostrar CON-012 (saldo distinto de cero) sin que CON-016 lo tape, se usa "Caja chica" (11010102), que el
  // catálogo base no liga a ninguna regla ni a la configuración.
  await test.step('Registra un asiento sobre Caja chica (11010102), sin regla de contabilización asociada', async () => {
    // El paso anterior deja la página en el detalle del asiento original: "Nuevo asiento" solo está en el listado
    await page
      .getByRole('navigation', { name: 'Secciones de Contabilidad' })
      .getByRole('link', { name: 'Libro Diario' })
      .click();
    await expect(page).toHaveURL(/\/contabilidad\/libro-diario$/);
    await page.getByRole('link', { name: 'Nuevo asiento' }).click();
    await expect(page.getByRole('heading', { name: 'Nuevo asiento' })).toBeVisible();

    await page.getByLabel('Concepto').fill('Movimiento sobre Caja chica (e2e F3)');
    await page.getByLabel('Cuenta de la línea 1').fill('Caja chica');
    await page.getByRole('option', { name: /Caja chica/ }).click();
    await page.getByLabel('Debe de la línea 1').fill('5.00');
    await page.getByLabel('Cuenta de la línea 2').fill('Ventas gravadas');
    await page.getByRole('option', { name: /Ventas gravadas/ }).click();
    await page.getByLabel('Haber de la línea 2').fill('5.00');

    const [registro] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/asientos') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Guardar' }).click(),
    ]);
    expect(registro.status()).toBe(201);
  });

  // ---------------------------------------------------------- 6. catálogo con movimientos reales (CON-011/012)
  await test.step('Catálogo: con movimientos reales, Caja chica rechaza desactivarse (CON-012) y cambiar de código (CON-011)', async () => {
    await page
      .getByRole('navigation', { name: 'Secciones de Contabilidad' })
      .getByRole('link', { name: 'Catálogo' })
      .click();
    await expect(page.getByRole('heading', { name: 'Catálogo de cuentas' })).toBeVisible();

    await page.getByLabel('Buscar por código o nombre').fill('11010102');
    await page.getByRole('button', { name: 'Editar cuenta 11010102' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Editar cuenta' });
    await expect(dialogo.getByLabel('Código')).toHaveValue('11010102');

    // 1. Desactivar una cuenta con saldo distinto de cero: 422 CON-012, mensaje junto a "Cuenta activa"
    await dialogo.getByLabel('Cuenta activa').uncheck();
    const [rechazoActiva] = await Promise.all([
      page.waitForResponse(
        (r) => /\/contabilidad\/cuentas\/[0-9a-f-]{36}$/.test(r.url()) && r.request().method() === 'PATCH',
      ),
      dialogo.getByRole('button', { name: 'Guardar' }).click(),
    ]);
    expect(rechazoActiva.status()).toBe(422);
    expect(((await rechazoActiva.json()) as { codigo: string }).codigo).toBe('CON-012');
    await expect(dialogo.locator('#editar-activa-error')).toContainText(
      'No se puede desactivar una cuenta con saldo distinto de cero.',
    );

    // 2. Se restaura "Cuenta activa" (para que el próximo envío solo cambie el código) y se cambia el código:
    //    422 CON-011, mensaje junto al campo "Código"
    await dialogo.getByLabel('Cuenta activa').check();
    await dialogo.getByLabel('Código').fill('11010199');
    const [rechazoCodigo] = await Promise.all([
      page.waitForResponse(
        (r) => /\/contabilidad\/cuentas\/[0-9a-f-]{36}$/.test(r.url()) && r.request().method() === 'PATCH',
      ),
      dialogo.getByRole('button', { name: 'Guardar' }).click(),
    ]);
    expect(rechazoCodigo.status()).toBe(422);
    expect(((await rechazoCodigo.json()) as { codigo: string }).codigo).toBe('CON-011');
    await expect(dialogo.locator('#editar-codigo-error')).toContainText(
      'No se puede cambiar el código de una cuenta con movimientos',
    );

    await dialogo.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialogo).toBeHidden();
  });

  // -------------------------------------------------------------------------------------- 6. sin errores
  await test.step('El recorrido no dejó errores de consola ni respuestas 5xx; solo los 4xx provocados', async () => {
    await testInfo.attach('respuestas-con-error.json', {
      body: JSON.stringify(erroresApi, null, 2),
      contentType: 'application/json',
    });
    expect(
      erroresApi.filter((e) => e.estado >= 500),
      'respuestas 5xx de la API',
    ).toEqual([]);
    // Los únicos 4xx provocados a propósito: el descuadre no llega a la API, así que solo quedan los dos del catálogo
    expect(erroresApi.map((e) => e.estado).sort()).toEqual([422, 422]);
    expect(erroresDeConsola, 'errores de consola en Pilot').toEqual([]);
  });
});
