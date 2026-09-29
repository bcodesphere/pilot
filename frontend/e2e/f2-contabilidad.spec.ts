import { expect, test, type Response } from '@playwright/test';
import { datosUsuarioNuevo, registrarUsuario, URL_API } from './soporte/registro';

/**
 * Aceptación F2 en el navegador (docs/plan-de-trabajo.md, F2): con una persona nueva recorre el entorno real
 * (Keycloak, Mailpit, backend y frontend) y comprueba lo que ve y hace quien instala Contabilidad:
 *
 * - criterio 1: la instalación deja catálogo, configuración y reglas precargados sin ninguna otra acción;
 * - criterio 2: el catálogo rechaza un código duplicado (`CON-014`) y uno de la clase 6 (`CON-010`), con el error en
 *   el campo del código;
 * - criterio 4: cambiar el modo de precio se guarda (con `If-Match` y el ETag nuevo); la auditoría se comprueba en
 *   el backend (`CambioModoPrecioAuditadoF2IT`), no aquí;
 * - reglas: `OTRO` nace inactiva con su aviso; activarla sin cuenta no envía nada; con cuenta se guarda y el aviso
 *   desaparece.
 *
 * El criterio 3 (saldo y movimientos) no tiene pantalla en F2. Ningún secreto se imprime.
 */

/** Petición de la API que falló, para adjuntarla como evidencia. */
interface PeticionApi {
  url: string;
  metodo: string;
  estado: number;
}

test('catálogo, configuración y reglas de Contabilidad (criterios 1, 2 y 4 de F2)', async ({
  page,
}, testInfo) => {
  // 1. Una persona nueva por ejecución; la contraseña es aleatoria y nunca se imprime
  const usuario = datosUsuarioNuevo('e2e-f2');

  // -------------------------------------------------------------------------------------- observadores
  // 2. Respuestas de la API con error, consola y peticiones de escritura a reglas, recogidas todo el recorrido
  const erroresApi: PeticionApi[] = [];
  const erroresDeConsola: string[] = [];
  let putsDeReglas = 0;

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
    if (peticion.method() === 'PUT' && /\/contabilidad\/reglas-contabilizacion\//.test(peticion.url())) {
      putsDeReglas += 1;
    }
  });
  page.on('console', (mensaje) => {
    // 2.1 Solo cuentan los errores mientras se está en Pilot (las páginas de Keycloak no son del producto)
    if (mensaje.type() !== 'error' || !page.url().startsWith('http://localhost:5173')) return;
    // 2.2 Chrome registra en consola cada 4xx de la API. Los dos que la prueba provoca a propósito (409 y 422) se
    //     verifican con sus respuestas más abajo; cualquier otro error de consola sí hace fallar la prueba
    const esErrorHttpDeLaApi =
      mensaje.text().includes('Failed to load resource') && mensaje.location().url.startsWith(URL_API);
    if (!esErrorHttpDeLaApi) erroresDeConsola.push(mensaje.text());
  });

  // ------------------------------------------------------------------------- 1. registro e instalación
  await registrarUsuario(page, usuario);

  await test.step('Apps: instala Contabilidad y entra desde el lanzador', async () => {
    // 1. Espera a que el shell muestre el espacio de trabajo (la empresa personal ya existe)
    await expect(page.getByRole('banner')).toContainText(usuario.nombreCompleto);

    // 2. Instalar espera el POST y comprueba el 201: la precarga corre dentro de esa misma transacción (ADR-030)
    await page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: 'Apps' }).click();
    const [instalacion] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/aplicaciones/contabilidad/instalacion') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: 'Instalar Contabilidad' }).click(),
    ]);
    expect(instalacion.status()).toBe(201);

    // 3. Contabilidad ya aparece en la barra lateral (ADR-043: el lanzador de "Tus aplicaciones" en Inicio
    //    desapareció); desde ahí se entra directo al Catálogo
    await page.getByRole('link', { name: 'Pilot', exact: true }).click();
    await page
      .getByRole('navigation', { name: 'Principal' })
      // U2 (F4.5): BarraLateral ya no agrega ", barra lateral" al nombre accesible (el nav "Principal" ya lo distingue)
      .getByRole('link', { name: 'Catálogo' })
      .click();
    await expect(page).toHaveURL(/\/contabilidad\/catalogo$/);
  });

  // ------------------------------------------------------------------------------------ 2. catálogo
  const arbol = page.getByLabel('Árbol de cuentas');
  const busqueda = page.getByLabel('Buscar por código o nombre');
  const CODIGO_NUEVO = '11010104';

  await test.step('Catálogo: la precarga se ve en el árbol y la búsqueda encuentra las cuentas de IVA', async () => {
    // 1. El árbol muestra la precarga sin que nadie haya creado nada: hay varias cuentas y la clase 1 «ACTIVO»
    await expect(page.getByRole('heading', { name: 'Catálogo de cuentas' })).toBeVisible();
    await expect(arbol.getByText('ACTIVO', { exact: true })).toBeVisible();
    expect(await arbol.getByRole('listitem').count()).toBeGreaterThan(5);

    // 2. Buscar «iva» expande y muestra las cuentas de IVA de la configuración precargada
    await busqueda.fill('iva');
    await expect(arbol.getByText('IVA crédito fiscal', { exact: true })).toBeVisible();
    await expect(arbol.getByText('IVA débito fiscal', { exact: true })).toBeVisible();
    await busqueda.fill('');
  });

  await test.step('Catálogo: crea una cuenta de detalle bajo un padre existente', async () => {
    // 1. Abre «Nueva cuenta» (110101 «Efectivo» ya existe: es el padre deducido del código)
    await page.getByRole('button', { name: 'Nueva cuenta' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Nueva cuenta' });
    await dialogo.getByLabel('Código').fill(CODIGO_NUEVO);
    await dialogo.getByLabel('Nombre').fill('Caja de ventas e2e');
    await expect(dialogo.getByText(/Cuenta padre: 110101/)).toBeVisible();

    // 2. Crear espera el POST y comprueba el 201
    const [creacion] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/cuentas') && r.request().method() === 'POST',
      ),
      dialogo.getByRole('button', { name: 'Crear cuenta' }).click(),
    ]);
    expect(creacion.status()).toBe(201);
    await expect(page.getByText('Cuenta creada')).toBeVisible();

    // 3. La cuenta aparece en el árbol
    await busqueda.fill(CODIGO_NUEVO);
    await expect(arbol.getByText('Caja de ventas e2e', { exact: true })).toBeVisible();
    await busqueda.fill('');
  });

  await test.step('Catálogo: un código duplicado (CON-014) y uno de la clase 6 (CON-010) se rechazan en el campo', async () => {
    await page.getByRole('button', { name: 'Nueva cuenta' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Nueva cuenta' });
    const errorCodigo = dialogo.locator('#cuenta-codigo-error');
    await dialogo.getByLabel('Nombre').fill('Cuenta rechazada e2e');

    // 1. Duplicado: 409 CON-014 y el mensaje aparece junto al campo del código
    await dialogo.getByLabel('Código').fill(CODIGO_NUEVO);
    const [duplicada] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/cuentas') && r.request().method() === 'POST',
      ),
      dialogo.getByRole('button', { name: 'Crear cuenta' }).click(),
    ]);
    expect(duplicada.status()).toBe(409);
    expect(((await duplicada.json()) as { codigo: string }).codigo).toBe('CON-014');
    await expect(errorCodigo).toContainText(/ya existe/i);

    // 2. Clase 6: 422 CON-010 y el mensaje aparece en el mismo campo
    await dialogo.getByLabel('Código').fill('61');
    const [clase6] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().endsWith('/contabilidad/cuentas') && r.request().method() === 'POST',
      ),
      dialogo.getByRole('button', { name: 'Crear cuenta' }).click(),
    ]);
    expect(clase6.status()).toBe(422);
    expect(((await clase6.json()) as { codigo: string }).codigo).toBe('CON-010');
    await expect(errorCodigo).toContainText(/clase de 1 a 5/i);

    // 3. Cancelar cierra el diálogo sin crear nada
    await dialogo.getByRole('button', { name: 'Cancelar' }).click();
    await expect(dialogo).toBeHidden();
  });

  await test.step('Catálogo: edita el nombre de la cuenta creada (PATCH 200 con If-Match)', async () => {
    // 1. Localiza la cuenta con la búsqueda y abre su edición
    await busqueda.fill(CODIGO_NUEVO);
    await page.getByRole('button', { name: `Editar cuenta ${CODIGO_NUEVO}` }).click();
    const dialogo = page.getByRole('dialog', { name: 'Editar cuenta' });
    await dialogo.getByLabel('Nombre').fill('Caja de ventas e2e (editada)');

    // 2. Guardar espera el PATCH: 200 y la versión de la cuenta en If-Match (recién creada = "0")
    const [edicion] = await Promise.all([
      page.waitForResponse(
        (r) => /\/contabilidad\/cuentas\/[0-9a-f-]{36}$/.test(r.url()) && r.request().method() === 'PATCH',
      ),
      dialogo.getByRole('button', { name: 'Guardar' }).click(),
    ]);
    expect(edicion.status()).toBe(200);
    expect(await edicion.request().headerValue('if-match')).toBe('"0"');
    await expect(page.getByText('Cuenta actualizada')).toBeVisible();
    await expect(arbol.getByText('Caja de ventas e2e (editada)', { exact: true })).toBeVisible();
  });

  // ------------------------------------------------------------------------------- 3. configuración
  await test.step('Configuración: cambia a SIN_IVA y vuelve a CON_IVA (PUT 200 con el ETag nuevo)', async () => {
    await page
      .getByRole('navigation', { name: 'Secciones de Contabilidad' })
      .getByRole('link', { name: 'Configuración' })
      .click();
    await expect(page.getByRole('heading', { name: 'Configuración contable' })).toBeVisible();
    const conIva = page.getByRole('radio', { name: 'Precios con IVA incluido' });
    const sinIva = page.getByRole('radio', { name: 'Precios más IVA' });
    await expect(conIva).toBeChecked();

    /** Guarda y devuelve la respuesta del PUT. */
    const guardar = async () => {
      const [respuesta] = await Promise.all([
        page.waitForResponse(
          (r) => r.url().endsWith('/contabilidad/configuracion') && r.request().method() === 'PUT',
        ),
        page.getByRole('button', { name: 'Guardar', exact: true }).click(),
      ]);
      return respuesta;
    };

    // 1. CON_IVA → SIN_IVA con la versión de la precarga (0)
    await sinIva.check();
    const primera = await guardar();
    expect(primera.status()).toBe(200);
    expect(await primera.request().headerValue('if-match')).toBe('"0"');
    await expect(page.getByText('Configuración guardada')).toBeVisible();
    await expect(sinIva).toBeChecked();

    // 2. SIN_IVA → CON_IVA con el ETag que devolvió el primer PUT (1)
    await conIva.check();
    const segunda = await guardar();
    expect(segunda.status()).toBe(200);
    expect(await segunda.request().headerValue('if-match')).toBe('"1"');
    await expect(conIva).toBeChecked();
  });

  // ------------------------------------------------------------------------------------------ 4. reglas
  await test.step('Reglas: OTRO nace inactiva; sin cuenta no se activa; con cuenta se guarda y el aviso desaparece', async () => {
    await page
      .getByRole('navigation', { name: 'Secciones de Contabilidad' })
      .getByRole('link', { name: 'Reglas' })
      .click();
    await expect(page.getByRole('heading', { name: 'Reglas de contabilización' })).toBeVisible();

    // 1. OTRO aparece inactiva y con su aviso (ADR-035)
    const aviso = page.getByText(/se rechazarán hasta que le asignes una cuenta/);
    await expect(aviso).toBeVisible();
    const fila = page.getByRole('listitem').filter({ has: page.getByText('Otro', { exact: true }) });
    const activa = fila.getByRole('switch', { name: 'Regla activa: Otro' });
    await expect(activa).not.toBeChecked();

    // 2. Activarla sin cuenta queda bloqueado en la pantalla: mensaje en el campo y ninguna petición PUT
    await activa.click();
    await fila.getByRole('button', { name: 'Guardar Otro' }).click();
    await expect(fila.getByText('Asigna una cuenta antes de activarla')).toBeVisible();
    expect(putsDeReglas, 'PUT enviado con una regla activa sin cuenta').toBe(0);

    // 3. Con una cuenta de detalle (la recién creada) el PUT responde 200 y el aviso desaparece
    await fila.getByRole('combobox').fill(CODIGO_NUEVO);
    await fila.getByRole('option', { name: new RegExp(CODIGO_NUEVO) }).click();
    const [guardado] = await Promise.all([
      page.waitForResponse(
        (r) =>
          /\/contabilidad\/reglas-contabilizacion\/[0-9a-f-]{36}$/.test(r.url()) &&
          r.request().method() === 'PUT',
      ),
      fila.getByRole('button', { name: 'Guardar Otro' }).click(),
    ]);
    expect(guardado.status()).toBe(200);
    await expect(aviso).toBeHidden();
    await expect(
      page
        .getByRole('listitem')
        .filter({ has: page.getByText('Otro', { exact: true }) })
        .getByText('Activa', { exact: true }),
    ).toBeVisible();
  });

  // -------------------------------------------------------------------------------------- 5. sin errores
  await test.step('El recorrido no dejó errores de consola ni respuestas 5xx; solo los 4xx provocados', async () => {
    // Evidencia: todas las respuestas con error de la API (los 4xx provocados a propósito y cualquier otra)
    await testInfo.attach('respuestas-con-error.json', {
      body: JSON.stringify(erroresApi, null, 2),
      contentType: 'application/json',
    });
    expect(
      erroresApi.filter((e) => e.estado >= 500),
      'respuestas 5xx de la API',
    ).toEqual([]);
    // Solo el duplicado (409) y la clase 6 (422) provocados en el catálogo
    expect(erroresApi.map((e) => e.estado).sort()).toEqual([409, 422]);
    expect(erroresDeConsola, 'errores de consola en Pilot').toEqual([]);
  });
});
