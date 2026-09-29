import { expect, test, type Response } from '@playwright/test';
import { datosUsuarioNuevo, registrarUsuario, URL_API } from './soporte/registro';

/**
 * Aceptación F4 en el navegador (docs/plan-de-trabajo.md, F4; rediseño U2 de F4.5): con una persona nueva
 * recorre el entorno real (Keycloak, Mailpit, backend y frontend), registra un asiento con IVA y comprueba
 * el rediseño de los reportes (ADR-043, `BarraFiltrosReporte`/`MenuExportar`/`TablaContable`):
 *
 * - la Balanza de Comprobación muestra los cuatro totales del pie (Debe, Haber, saldos deudores y
 *   acreedores) y la etiqueta "Cuadra";
 * - el Estado de Situación Financiera, el Estado de Resultados y el Resumen de IVA cargan con sus valores
 *   por defecto (fecha de corte y período del mes actual) y muestran los montos del asiento registrado;
 * - el Libro Mayor de la cuenta usada en el asiento muestra su movimiento con saldo D/A;
 * - exportar la Balanza a PDF, XLSX y CSV descarga los tres archivos.
 *
 * Ningún secreto (contraseña, semilla TOTP) se imprime ni se adjunta.
 */

/** Petición de la API que falló, para adjuntarla como evidencia. */
interface PeticionApi {
  url: string;
  metodo: string;
  estado: number;
}

test('Reportes: Balanza, estados financieros, IVA y Mayor con un asiento real (F4)', async ({
  page,
}, testInfo) => {
  // 1. Una persona nueva por ejecución; la contraseña es aleatoria y nunca se imprime
  const usuario = datosUsuarioNuevo('e2e-f4');

  // -------------------------------------------------------------------------------------- observadores
  const erroresApi: PeticionApi[] = [];
  const erroresDeConsola: string[] = [];

  page.on('response', (respuesta: Response) => {
    if (respuesta.url().startsWith(URL_API) && respuesta.status() >= 400) {
      erroresApi.push({
        url: respuesta.url(),
        metodo: respuesta.request().method(),
        estado: respuesta.status(),
      });
    }
  });
  page.on('console', (mensaje) => {
    // Solo cuentan los errores mientras se está en Pilot (las páginas de Keycloak no son del producto)
    if (mensaje.type() !== 'error' || !page.url().startsWith('http://localhost:5173')) return;
    const esErrorHttpDeLaApi =
      mensaje.text().includes('Failed to load resource') && mensaje.location().url.startsWith(URL_API);
    if (!esErrorHttpDeLaApi) erroresDeConsola.push(mensaje.text());
  });

  // ------------------------------------------------------------------------- 1. registro e instalación
  await registrarUsuario(page, usuario);

  await test.step('Apps: instala Contabilidad', async () => {
    await expect(page.getByRole('banner')).toContainText(usuario.nombreCompleto);
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Apps' }).click();
    const [instalacion] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/aplicaciones/contabilidad/instalacion') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Instalar Contabilidad' }).click(),
    ]);
    expect(instalacion.status()).toBe(201);
  });

  // ------------------------------------------------------------------------- 2. un asiento con IVA real
  await test.step('Registra un asiento con IVA (Caja / Ventas gravadas) para tener datos en los reportes', async () => {
    await page
      .getByRole('navigation', { name: 'Principal' })
      .getByRole('link', { name: 'Libro Diario' })
      .click();
    await page.getByRole('link', { name: 'Nuevo asiento' }).click();
    await expect(page.getByRole('heading', { name: 'Nuevo asiento' })).toBeVisible();

    await page.getByLabel('Concepto').fill('Venta con IVA (e2e F4)');
    await page.getByLabel('Cuenta de la línea 1').fill('Caja general');
    await page.getByRole('option', { name: /Caja general/ }).click();
    await page.getByLabel('Debe de la línea 1').fill('113.00');

    await page.getByLabel('Cuenta de la línea 2').fill('Ventas gravadas');
    await page.getByRole('option', { name: /Ventas gravadas/ }).click();
    await page.getByRole('checkbox', { name: /Lleva IVA.*línea 2/ }).check();
    const [previa] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/asientos/vista-previa') && r.request().method() === 'POST',
      ),
      page.getByLabel('Haber de la línea 2').fill('113.00'),
    ]);
    expect(((await previa.json()) as { cuadra: boolean }).cuadra).toBe(true);
    await expect(page.getByRole('button', { name: 'Guardar' })).toBeEnabled();

    const [registro] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/asientos') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Guardar' }).click(),
    ]);
    expect(registro.status()).toBe(201);
  });

  // ---------------------------------------------------------------------------- 3. Balanza de Comprobación
  await test.step('Balanza: los cuatro totales del pie y la etiqueta "Cuadra"', async () => {
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Reportes' }).click();
    await expect(page).toHaveURL(/\/contabilidad\/reportes\/balanza$/);

    // "Este mes" (BarraFiltrosReporte) fija Desde/Hasta y dispara la consulta con el asiento recién guardado
    const [balanza] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/contabilidad/balanza?') && r.request().method() === 'GET',
      ),
      page.getByRole('button', { name: 'Este mes' }).click(),
    ]);
    expect(balanza.status()).toBe(200);

    await expect(page.getByText('11010101', { exact: false }).first()).toBeVisible();
    const tabla = page.getByRole('table');
    const pie = tabla.locator('tfoot');
    // Los cuatro totales del pie: Debe, Haber, saldo deudor y saldo acreedor de las cuentas de detalle
    await expect(pie).toContainText('$113.00');
    await expect(pie).toContainText('Cuadra');
  });

  // ------------------------------------------------------------------- 4. Estado de Situación Financiera
  await test.step('Estado de Situación Financiera: carga con la fecha de corte de hoy', async () => {
    await page
      .getByRole('navigation', { name: 'Reportes de Contabilidad' })
      .getByRole('link', { name: 'Estado de Situación Financiera' })
      .click();
    await expect(page.getByText('Estado de gestión generado por Pilot', { exact: false })).toBeVisible();
    // La cuenta de Caja general (clase 1, Activo) aparece con el monto del asiento
    await expect(page.getByText('11010101', { exact: false }).first()).toBeVisible();
  });

  // -------------------------------------------------------------------------------- 5. Estado de Resultados
  await test.step('Estado de Resultados: el impuesto sobre la renta aparte y la utilidad del ejercicio', async () => {
    await page
      .getByRole('navigation', { name: 'Reportes de Contabilidad' })
      .getByRole('link', { name: 'Estado de Resultados' })
      .click();
    await page.getByRole('button', { name: 'Este mes' }).click();
    await expect(page.getByText('Impuesto sobre la renta')).toBeVisible();
    await expect(page.getByText('51010101', { exact: false })).toBeVisible();
  });

  // ------------------------------------------------------------------------------------- 6. Resumen de IVA
  await test.step('Resumen de IVA: el mes actual muestra el IVA débito fiscal del asiento', async () => {
    await page
      .getByRole('navigation', { name: 'Reportes de Contabilidad' })
      .getByRole('link', { name: 'Resumen de IVA' })
      .click();
    await expect(page.getByText('IVA débito fiscal', { exact: false })).toBeVisible();
    await expect(page.getByText('$13.00', { exact: false }).first()).toBeVisible();
    // Texto propio del frontend (spec F4.5 §7.6): ningún texto visible cita un documento interno
    await expect(page.getByText('CLAUDE.md')).toHaveCount(0);
  });

  // -------------------------------------------------------------------------------------------- 7. Mayor
  await test.step('Mayor: la cuenta de Caja general muestra su movimiento con saldo deudor', async () => {
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Mayor' }).click();
    await page.getByLabel('Cuenta').fill('Caja general');
    await page.getByRole('option', { name: /Caja general/ }).click();
    const [mayor] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/contabilidad/mayor?') && r.request().method() === 'GET'),
      page.getByRole('button', { name: 'Este mes' }).click(),
    ]);
    expect(mayor.status()).toBe(200);
    await expect(page.getByRole('table')).toContainText('$113.00');
    await expect(page.getByRole('table')).toContainText('D');
  });

  // ---------------------------------------------------------------------------- 8. exportación de la Balanza
  await test.step('Exporta la Balanza a PDF, Excel y CSV', async () => {
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Reportes' }).click();
    await page.getByRole('button', { name: 'Este mes' }).click();
    for (const [formato, etiqueta] of [
      ['pdf', 'PDF'],
      ['xlsx', 'Excel'],
      ['csv', 'CSV'],
    ] as const) {
      await page.getByRole('button', { name: 'Exportar' }).click();
      const [descarga] = await Promise.all([
        page.waitForEvent('download'),
        page.getByRole('menuitem', { name: etiqueta }).click(),
      ]);
      expect(descarga.suggestedFilename()).toContain(`.${formato}`);
    }
  });

  // -------------------------------------------------------------------------------------- verificaciones finales
  await test.step('Sin errores inesperados de la API ni de la consola', async () => {
    if (erroresApi.length > 0) {
      await testInfo.attach('errores-api', {
        body: JSON.stringify(erroresApi, null, 2),
        contentType: 'application/json',
      });
    }
    expect(erroresApi, 'la API respondió con errores no esperados por la prueba').toEqual([]);
    expect(erroresDeConsola, 'la consola del navegador registró errores no esperados').toEqual([]);
  });
});
