import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO, cuenta } from '../compartido/datosPrueba';
import { conEtag, llamadas, montarContabilidad, problema } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Manejador base: el catálogo de ejemplo y, opcionalmente, un manejador propio que se consulta primero. */
const conCatalogo =
  (propio?: (url: string, init: RequestInit) => Response | undefined) => (url: string, init: RequestInit) => {
    const r = propio?.(url, init);
    if (r) return r;
    if (url.endsWith('/contabilidad/cuentas') && (init.method ?? 'GET') === 'GET') return json(CATALOGO);
    return undefined;
  };

/** Texto de cada elemento de lista del árbol. */
const filas = () => within(screen.getByLabelText('Árbol de cuentas')).getAllByRole('listitem');

describe('pantalla Catálogo: árbol y búsqueda', () => {
  // Regla del encargo: expandido por defecto hasta el nivel 2; los nodos se expanden con aria-expanded
  it('muestra el árbol expandido hasta el nivel 2 y permite expandir un nodo con el teclado', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    expect(await screen.findByText('Efectivo y equivalentes')).toBeInTheDocument();
    // Nivel 4 (subcuenta) aún colapsado
    expect(screen.queryByText('Caja general')).not.toBeInTheDocument();

    const boton = screen.getByRole('button', { name: /Expandir 1101/ });
    expect(boton).toHaveAttribute('aria-expanded', 'false');
    boton.focus();
    await userEvent.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: /Contraer 1101/ })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Caja')).toBeInTheDocument();
  });

  // Regla: cada fila informa naturaleza (letra D/A neutra, spec F4.5 §7.2), si acepta movimientos y si está inactiva
  it('cada fila muestra la naturaleza D/A, "Acepta movimientos" e insignia "Inactiva"', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    await screen.findByText('Activo corriente');
    // Con la búsqueda se despliegan todas las ramas
    await userEvent.type(screen.getByLabelText('Buscar por código o nombre'), '110101');
    const inactiva = (await screen.findByText('Caja chica antigua')).closest('li')!;
    expect(within(inactiva).getByText('Inactiva')).toBeInTheDocument();
    expect(within(inactiva).getByText('D')).toHaveAttribute('aria-label', 'Deudora');
    expect(within(inactiva).getByText('Acepta movimientos')).toBeInTheDocument();
  });

  // Regla: la búsqueda local muestra coincidencias con sus ancestros y resalta el texto
  it('buscar "iva" muestra las cuentas de IVA con sus ancestros y resalta la coincidencia', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    await screen.findByText('Activo corriente');
    await userEvent.type(screen.getByLabelText('Buscar por código o nombre'), 'iva');

    const textos = filas().map((f) => f.querySelector('div')!.textContent);
    // Ancestros de "IVA débito fiscal" presentes; las ventas (clase 5) fuera
    expect(textos.some((t) => t?.includes('21010101') && t.includes('IVA débito fiscal'))).toBe(true);
    expect(textos.some((t) => t?.includes('Pasivo corriente'))).toBe(true);
    expect(screen.queryByText('Ventas gravadas')).not.toBeInTheDocument();
    // Resalta la coincidencia
    expect(document.querySelectorAll('mark').length).toBeGreaterThan(0);
    expect(document.querySelector('mark')!.textContent!.toLowerCase()).toBe('iva');
  });

  // Regla: buscar por código muestra esa rama
  it('buscar "1101" muestra esa rama y oculta las demás clases', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    await screen.findByText('Activo corriente');
    await userEvent.type(screen.getByLabelText('Buscar por código o nombre'), '1101');
    expect(await screen.findByText('Caja general')).toBeInTheDocument();
    expect(screen.queryByText('Pasivo')).not.toBeInTheDocument();
  });

  // Regla: estados de error visibles
  it('muestra un aviso si no se puede cargar el catálogo', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', (url) =>
      url.endsWith('/contabilidad/cuentas') ? problema(403, 'PLT-010') : undefined,
    );
    expect(await screen.findByText('No pudimos cargar el catálogo de cuentas.')).toBeInTheDocument();
  });
});

describe('pantalla Catálogo: nueva cuenta (contador)', () => {
  /** Abre el diálogo "Nueva cuenta". */
  const abrir = async () => {
    await screen.findByText('Activo corriente');
    await userEvent.click(screen.getByRole('button', { name: 'Nueva cuenta' }));
    return screen.findByRole('dialog', { name: 'Nueva cuenta' });
  };

  // Regla del contrato: el código son 1 a 8 dígitos; lo demás no se envía
  it('un código con letras o de 9 dígitos no se envía', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    const dialogo = await abrir();
    const codigo = within(dialogo).getByLabelText('Código');
    await userEvent.type(within(dialogo).getByLabelText('Nombre'), 'Caja nueva');

    await userEvent.type(codigo, '11a1');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cuenta' }));
    expect(await within(dialogo).findByText('Usa solo dígitos, de 1 a 8')).toBeInTheDocument();

    await userEvent.clear(codigo);
    await userEvent.type(codigo, '123456789');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cuenta' }));
    expect(await within(dialogo).findByText('Usa solo dígitos, de 1 a 8')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'POST', '/contabilidad/cuentas')).toHaveLength(0);
  });

  // Regla ADR-035: el padre se deduce del código; la vista previa es solo informativa
  it('muestra el padre que se deduciría del código', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    const dialogo = await abrir();
    await userEvent.type(within(dialogo).getByLabelText('Código'), '11010104');
    expect(await within(dialogo).findByText('Cuenta padre: 110101 — Caja')).toBeInTheDocument();
  });

  // Regla (paso 0 de U2 fase B): POST con el cuerpo correcto; con 201 se invalida el catálogo y se avisa.
  // El contrato de C1 quitó `naturaleza` de `NuevaCuentaContable` (ADR-042: siempre derivada); el
  // formulario deja de pedirla como parte del rediseño del catálogo de esta misma tarea.
  it('envía POST con código y nombre; con 201 recarga el catálogo y avisa', async () => {
    let creada = false;
    const { fetchMock } = montarContabilidad(
      '/contabilidad/catalogo',
      'contador',
      conCatalogo((url, init) => {
        if (!url.endsWith('/contabilidad/cuentas')) return;
        if (init.method === 'POST') {
          creada = true;
          return conEtag(cuenta('11010104', 'Caja nueva'), 1, 201);
        }
        if (creada)
          return json([...CATALOGO, cuenta('11010104', 'Caja nueva', { cuentaPadreId: 'c-110101' })]);
      }),
    );
    const dialogo = await abrir();
    await userEvent.type(within(dialogo).getByLabelText('Código'), '11010104');
    await userEvent.type(within(dialogo).getByLabelText('Nombre'), '  Caja nueva ');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cuenta' }));

    expect(await screen.findByText('Cuenta creada')).toBeInTheDocument();
    const post = llamadas(fetchMock, 'POST', '/contabilidad/cuentas')[0]!;
    // El nombre viaja sin espacios de los extremos
    expect(JSON.parse(String((post[1] as RequestInit).body))).toEqual({
      codigo: '11010104',
      nombre: 'Caja nueva',
    });
    // Se vuelve a leer el catálogo (invalidación)
    await waitFor(() =>
      expect(llamadas(fetchMock, 'GET', '/contabilidad/cuentas').length).toBeGreaterThan(1),
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  // Regla CLAUDE.md §10.2: 409 CON-014 (código repetido) se muestra en el campo código
  it('un 409 CON-014 muestra su mensaje junto al campo código', async () => {
    montarContabilidad(
      '/contabilidad/catalogo',
      'contador',
      conCatalogo((url, init) =>
        url.endsWith('/contabilidad/cuentas') && init.method === 'POST'
          ? problema(409, 'CON-014')
          : undefined,
      ),
    );
    const dialogo = await abrir();
    await userEvent.type(within(dialogo).getByLabelText('Código'), '11010101');
    await userEvent.type(within(dialogo).getByLabelText('Nombre'), 'Duplicada');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cuenta' }));
    const mensaje = await within(dialogo).findByText('El código ya existe en el catálogo de la empresa.');
    expect(mensaje).toHaveAttribute('id', 'cuenta-codigo-error');
  });

  // Regla CLAUDE.md §10.2: 422 CON-015 (longitud o padre) también junto al código
  it('un 422 CON-015 muestra su mensaje', async () => {
    montarContabilidad(
      '/contabilidad/catalogo',
      'contador',
      conCatalogo((url, init) =>
        url.endsWith('/contabilidad/cuentas') && init.method === 'POST'
          ? problema(422, 'CON-015')
          : undefined,
      ),
    );
    const dialogo = await abrir();
    await userEvent.type(within(dialogo).getByLabelText('Código'), '120101');
    await userEvent.type(within(dialogo).getByLabelText('Nombre'), 'Sin padre');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Crear cuenta' }));
    expect(
      await within(dialogo).findByText(
        'Longitud de código no válida (1, 2, 4, 6 u 8 dígitos) o no existe una cuenta padre activa cuyo código sea su prefijo.',
      ),
    ).toBeInTheDocument();
  });

  // Regla de accesibilidad: Escape cierra el diálogo y el foco vuelve al botón que lo abrió
  it('Escape cierra el diálogo y devuelve el foco a "Nueva cuenta"', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    await abrir();
    await userEvent.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Nueva cuenta' })).toHaveFocus();
  });
});

describe('pantalla Catálogo: editar cuenta (contador)', () => {
  const caja = cuenta('11010101', 'Caja general', { cuentaPadreId: 'c-110101', version: 4 });

  /** Manejador con `GET /cuentas/c-11010101` (ETag "4") más el manejador propio. */
  const conCaja = (propio?: (url: string, init: RequestInit) => Response | undefined) =>
    conCatalogo((url, init) => {
      const r = propio?.(url, init);
      if (r) return r;
      if (url.endsWith('/contabilidad/cuentas/c-11010101') && (init.method ?? 'GET') === 'GET') {
        return conEtag(caja, 4);
      }
    });

  /** Abre el diálogo de edición de la Caja general (expandiendo su rama con la búsqueda). */
  const abrir = async () => {
    await screen.findByText('Activo corriente');
    await userEvent.type(screen.getByLabelText('Buscar por código o nombre'), '11010101');
    await userEvent.click(await screen.findByRole('button', { name: 'Editar cuenta 11010101' }));
    return screen.findByRole('dialog', { name: 'Editar cuenta' });
  };

  // Regla CLAUDE.md §8.3: el PATCH lleva If-Match con el ETag del GET y solo los campos cambiados
  it('el PATCH lleva If-Match con el ETag del GET y solo los campos cambiados', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/catalogo',
      'contador',
      conCaja((url, init) =>
        url.endsWith('/contabilidad/cuentas/c-11010101') && init.method === 'PATCH'
          ? conEtag({ ...caja, nombre: 'Caja principal', version: 5 }, 5)
          : undefined,
      ),
    );
    const dialogo = await abrir();
    const nombre = await within(dialogo).findByLabelText('Nombre');
    await waitFor(() => expect(nombre).toHaveValue('Caja general'));
    await userEvent.clear(nombre);
    await userEvent.type(nombre, 'Caja principal');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Cuenta actualizada')).toBeInTheDocument();
    const patch = llamadas(fetchMock, 'PATCH', '/contabilidad/cuentas/c-11010101')[0]!;
    const init = patch[1] as RequestInit;
    expect(new Headers(init.headers).get('If-Match')).toBe('"4"');
    expect(JSON.parse(String(init.body))).toEqual({ nombre: 'Caja principal' });
  });

  // Regla: sin cambios no se envía nada
  it('sin cambios no envía el PATCH', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/catalogo', 'contador', conCaja());
    const dialogo = await abrir();
    await within(dialogo).findByDisplayValue('Caja general');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }));
    expect(await within(dialogo).findByText('No hay cambios que guardar')).toBeInTheDocument();
    expect(llamadas(fetchMock, 'PATCH', '/contabilidad/cuentas/c-11010101')).toHaveLength(0);
  });

  // Regla CLAUDE.md §8.3 y §8.4: 412 PLT-016 avisa del conflicto y "Recargar" vuelve a leer la cuenta
  it('un 412 PLT-016 muestra el aviso y "Recargar" vuelve a pedir la cuenta', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/catalogo',
      'contador',
      conCaja((url, init) =>
        url.endsWith('/contabilidad/cuentas/c-11010101') && init.method === 'PATCH'
          ? problema(412, 'PLT-016')
          : undefined,
      ),
    );
    const dialogo = await abrir();
    const nombre = await within(dialogo).findByDisplayValue('Caja general');
    await userEvent.type(nombre, ' 2');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }));
    expect(
      await within(dialogo).findByText('Alguien cambió este dato mientras lo editabas'),
    ).toBeInTheDocument();

    const antes = llamadas(fetchMock, 'GET', '/contabilidad/cuentas/c-11010101').length;
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Recargar' }));
    await waitFor(() =>
      expect(llamadas(fetchMock, 'GET', '/contabilidad/cuentas/c-11010101').length).toBe(antes + 1),
    );
    await waitFor(() =>
      expect(screen.queryByText('Alguien cambió este dato mientras lo editabas')).not.toBeInTheDocument(),
    );
  });

  // Regla CLAUDE.md §10.2: 422 CON-016 al desactivar una cuenta en uso
  it('un 422 CON-016 al desactivar muestra su mensaje', async () => {
    montarContabilidad(
      '/contabilidad/catalogo',
      'contador',
      conCaja((url, init) =>
        url.endsWith('/contabilidad/cuentas/c-11010101') && init.method === 'PATCH'
          ? problema(422, 'CON-016')
          : undefined,
      ),
    );
    const dialogo = await abrir();
    await within(dialogo).findByDisplayValue('Caja general');
    await userEvent.click(within(dialogo).getByLabelText('Cuenta activa'));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }));
    expect(
      await within(dialogo).findByText(
        'La cuenta está en uso por la configuración contable o por una regla activa: no se puede desactivar ni dejar de ser de detalle.',
      ),
    ).toBeInTheDocument();
  });
});

describe('pantalla Catálogo: rol auditor', () => {
  // Regla CLAUDE.md §14.2: el auditor solo lee
  it('el auditor ve el catálogo sin "Nueva cuenta" ni "Editar"', async () => {
    montarContabilidad('/contabilidad/catalogo', 'auditor', conCatalogo());
    expect(await screen.findByText('Activo corriente')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Nueva cuenta' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /^Editar/ })).not.toBeInTheDocument();
  });
});

describe('pantalla Catálogo: estado vacío', () => {
  /** Manejador que devuelve un catálogo sin cuentas. */
  const vacio = (url: string, init: RequestInit) =>
    url.endsWith('/contabilidad/cuentas') && (init.method ?? 'GET') === 'GET' ? json([]) : undefined;

  // Caso: empresa con Contabilidad instalada y sin precarga; quien escribe recibe la indicación de crear cuentas
  it('con catálogo vacío y permiso de escritura explica que está vacío y cómo crear cuentas', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', vacio);
    const estado = await screen.findByText('El catálogo de cuentas está vacío');
    expect(estado.closest('[role="status"]')).toBeInTheDocument();
    expect(screen.getByText('Crea las cuentas de clase (1 a 5) con «Nueva cuenta».')).toBeInTheDocument();
  });

  // Caso: el auditor no puede crear cuentas, así que no se le indica hacerlo
  it('con catálogo vacío y sin permiso de escritura no sugiere crear cuentas', async () => {
    montarContabilidad('/contabilidad/catalogo', 'auditor', vacio);
    expect(await screen.findByText('El catálogo de cuentas está vacío')).toBeInTheDocument();
    expect(screen.queryByText(/Crea las cuentas de clase/)).not.toBeInTheDocument();
  });

  // Caso (menor de F2-06): con el catálogo vacío, escribir en la búsqueda no debe dejar la pantalla sin mensaje
  it('con catálogo vacío y texto en la búsqueda dice que ninguna cuenta coincide', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', vacio);
    await screen.findByText('El catálogo de cuentas está vacío');
    await userEvent.type(screen.getByLabelText('Buscar por código o nombre'), 'caja');
    expect(await screen.findByText('Ninguna cuenta coincide')).toBeInTheDocument();
    expect(screen.queryByText('El catálogo de cuentas está vacío')).not.toBeInTheDocument();
  });
});

describe('pantalla Catálogo: cuentas del sistema y filtros (U2 fase B, paso 3)', () => {
  // "Caja" (110101, 6 dígitos: admite subcuenta) marcada como del sistema, para probar ambos casos a la vez
  const conSistema = () =>
    conCatalogo((url, init) =>
      url.endsWith('/contabilidad/cuentas') && (init.method ?? 'GET') === 'GET'
        ? json(CATALOGO.map((c) => (c.codigo === '110101' ? { ...c, sistema: true } : c)))
        : undefined,
    );

  // ADR-042/CON-021: código, nombre y estado de una cuenta del sistema no son un campo editable
  it('una cuenta del sistema se ve con candado y sin botón "Editar"', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conSistema());
    await userEvent.type(await screen.findByLabelText('Buscar por código o nombre'), '110101');
    await screen.findByText('Caja', { exact: true });
    // "Caja" (110101) es del sistema: nunca tiene su propio botón "Editar cuenta 110101" (a diferencia
    // de sus hijas, que sí lo tienen: por eso no se acota por `within(fila)`, que las incluiría)
    expect(screen.queryByRole('button', { name: 'Editar cuenta 110101' })).not.toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: 'Cuenta del catálogo base: agrega una subcuenta propia si necesitas más detalle',
      }),
    ).toBeInTheDocument();
  });

  // Una cuenta del sistema con menos de 8 dígitos igual permite agregar una subcuenta propia
  it('"Agregar subcuenta" abre "Nueva cuenta" con el código del padre precargado', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conSistema());
    await userEvent.type(await screen.findByLabelText('Buscar por código o nombre'), '110101');
    const fila = (await screen.findByText('Caja', { exact: true })).closest('li')!;
    await userEvent.click(within(fila).getByRole('button', { name: /Agregar subcuenta/ }));
    const dialogo = await screen.findByRole('dialog', { name: 'Agregar subcuenta' });
    expect(within(dialogo).getByLabelText('Código')).toHaveValue('110101');
  });

  // Filtro "Activas": oculta las inactivas (y sus ancestros, si ninguna otra hija cumple)
  it('el filtro "Activas" oculta las cuentas inactivas', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    await screen.findByText('Activo corriente');
    await userEvent.click(screen.getByLabelText('Activas'));
    expect(await screen.findByText('Caja general', { exact: false })).toBeInTheDocument();
    expect(screen.queryByText('Caja chica antigua')).not.toBeInTheDocument();
  });

  // Filtro "Con movimiento": solo cuentas de detalle (`aceptaMovimientos`)
  it('el filtro "Con movimiento" solo muestra cuentas que aceptan movimientos', async () => {
    montarContabilidad('/contabilidad/catalogo', 'contador', conCatalogo());
    await screen.findByText('Activo corriente');
    await userEvent.click(screen.getByLabelText('Con movimiento'));
    expect(await screen.findByText('Caja general', { exact: false })).toBeInTheDocument();
    // "Activo corriente" (grupo, no de detalle) desaparece de las filas propias, aunque siga como ancestro visible
    expect(screen.queryByText('Bancos', { exact: false })).toBeInTheDocument();
  });
});
