import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { asiento, CATALOGO, CONFIGURACION, cuenta, vistaPrevia } from '../compartido/datosPrueba';
import { conEtag, llamadas, montarContabilidad, problema } from '../compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Catálogo de las pruebas: el de ejemplo más una cuenta de gasto (Compras) para el caso `SIN_IVA`. */
const CUENTAS = [...CATALOGO, cuenta('41010101', 'Compras')];

/** Manejador base: catálogo, configuración y detalle del asiento al que navega el guardado. */
const base =
  (propio?: (url: string, init: RequestInit) => Response | undefined) => (url: string, init: RequestInit) => {
    const r = propio?.(url, init);
    if (r) return r;
    const metodo = init.method ?? 'GET';
    if (url.endsWith('/contabilidad/cuentas')) return json(CUENTAS);
    if (url.endsWith('/contabilidad/configuracion') && metodo === 'GET') return conEtag(CONFIGURACION, 3);
    if (url.endsWith('/contabilidad/asientos/a-1') && metodo === 'GET') return json(asiento());
    return undefined;
  };

/** Elige en el selector de la línea `n` (desde 1) la cuenta con ese código. */
async function elegirCuenta(n: number, codigo: string) {
  const campo = await screen.findByLabelText(`Cuenta de la línea ${n}`);
  await userEvent.click(campo);
  await userEvent.type(campo, codigo);
  await userEvent.click(await screen.findByRole('option', { name: new RegExp(codigo) }));
}

/** Escribe un monto en el campo Debe o Haber de la línea `n`. */
async function escribir(campo: 'Debe' | 'Haber', n: number, monto: string) {
  await userEvent.type(screen.getByLabelText(`${campo} de la línea ${n}`), monto);
}

/** Botón Guardar del formulario. */
const guardar = () => screen.getByRole('button', { name: 'Guardar' });

/** Prepara el asiento Caja 100.00 / Ventas 100.00 (cuadrado, sin IVA) y espera el concepto para escribirlo. */
async function llenarAsientoCuadrado(concepto = 'Venta al contado') {
  await elegirCuenta(1, '11010101');
  await elegirCuenta(2, '51010101');
  await escribir('Debe', 1, '100.00');
  await escribir('Haber', 2, '100.00');
  await userEvent.type(screen.getByLabelText('Concepto'), concepto);
}

describe('formulario del Libro Diario: totales y partida doble', () => {
  // Criterio del plan de F3 / CLAUDE.md §10.1 CON-005: Guardar bloqueado y diferencia visible mientras no cuadre
  it('muestra los totales en vivo y mantiene Guardar deshabilitado con la diferencia hasta que cuadra', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta');
    await escribir('Debe', 1, '113.00');
    await escribir('Haber', 2, '100.00');

    // Σ Debe 113.00, Σ Haber 100.00: diferencia 13.00 y Guardar bloqueado
    expect(screen.getByText('Total Debe').nextElementSibling).toHaveTextContent('$113.00');
    expect(screen.getByText('Total Haber').nextElementSibling).toHaveTextContent('$100.00');
    expect(screen.getByText('Diferencia').nextElementSibling).toHaveTextContent('$13.00');
    expect(screen.getByText(/El asiento no cuadra: la diferencia es \$13\.00/)).toBeInTheDocument();
    expect(guardar()).toBeDisabled();

    // Al corregir el Haber el asiento cuadra y Guardar se habilita
    await userEvent.clear(screen.getByLabelText('Haber de la línea 2'));
    await escribir('Haber', 2, '113.00');
    expect(screen.getByText('Diferencia').nextElementSibling).toHaveTextContent('$0.00');
    expect(guardar()).toBeEnabled();
  });

  // Regla 1.2.2: 0.10 + 0.20 debe cuadrar contra 0.30 (con number daría 0.30000000000000004)
  it('suma con decimal.js: 0.10 + 0.20 contra 0.30 cuadra', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await elegirCuenta(3, '11010103');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Suma exacta');
    await escribir('Debe', 1, '0.10');
    await escribir('Debe', 3, '0.20');
    await escribir('Haber', 2, '0.30');
    expect(screen.getByText('Diferencia').nextElementSibling).toHaveTextContent('$0.00');
    expect(guardar()).toBeEnabled();
  });

  // CON-002: una línea con Debe y Haber a la vez bloquea Guardar y muestra el motivo junto a la línea
  it('bloquea Guardar y avisa si una línea lleva Debe y Haber a la vez (CON-002)', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await llenarAsientoCuadrado();
    await escribir('Haber', 1, '100.00');
    expect(await screen.findByText('Use solo Debe o solo Haber')).toBeInTheDocument();
    expect(guardar()).toBeDisabled();
  });

  // Corrección 2 (hallazgo 2): con el formulario vacío la diferencia es $0.00 y no es un descuadre real; solo
  // debe verse el motivo de CON-004 (totales en cero), nunca "El asiento no cuadra: la diferencia es $0.00."
  it('con el formulario vacío no anuncia un descuadre de $0.00, solo que los totales deben ser mayores que cero', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await screen.findByLabelText('Cuenta de la línea 1');
    expect(screen.getByText('Los totales deben ser mayores que cero')).toBeInTheDocument();
    expect(screen.queryByText(/El asiento no cuadra/)).not.toBeInTheDocument();
  });

  // Un descuadre real (diferencia distinta de cero) sí debe anunciarse, con el formato real de la moneda
  it('un descuadre real (Debe 10.00 / Haber 9.00) sí anuncia la diferencia', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await escribir('Debe', 1, '10.00');
    await escribir('Haber', 2, '9.00');
    expect(await screen.findByText('El asiento no cuadra: la diferencia es $1.00.')).toBeInTheDocument();
  });
});

describe('formulario del Libro Diario: líneas con IVA y vista previa del backend', () => {
  // ADR-036 / §10.1: un solo llamado tras 300 ms de inactividad y Guardar usa `cuadra` de la vista previa
  it('con "lleva IVA" pide la vista previa una sola vez tras 300 ms y muestra las líneas de IVA', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos/vista-previa') && init.method === 'POST'
          ? json(vistaPrevia())
          : undefined,
      ),
    );
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta con IVA');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 2'));
    await escribir('Haber', 2, '113.00');
    // Cada tecla del Debe deja el formulario válido con un cuerpo distinto: el retardo las agrupa en una
    await escribir('Debe', 1, '113.00');

    // Aparece la tabla expandida con la línea de IVA asociada a su línea de origen
    expect(await screen.findByText(/IVA calculado · de la línea 2/)).toBeInTheDocument();
    expect(llamadas(fetchMock, 'POST', '/contabilidad/asientos/vista-previa')).toHaveLength(1);
    const cuerpo = JSON.parse(
      String((llamadas(fetchMock, 'POST', '/contabilidad/asientos/vista-previa')[0]![1] as RequestInit).body),
    );
    expect(cuerpo.modoPrecio).toBe('CON_IVA');
    expect(cuerpo.lineas[0].debe).toBe('113.00');
    // Usa los totales del backend y Guardar se habilita porque `cuadra` es true
    await waitFor(() => expect(guardar()).toBeEnabled());
    expect(screen.getByText('Total Debe').nextElementSibling).toHaveTextContent('$113.00');
  });

  // §11.2 ejemplo SIN_IVA: Compras 100.00 con IVA / Caja 113.00 solo cuadra con la vista previa del backend
  it('caso SIN_IVA de §11.2: Compras 100.00 con IVA contra Caja 113.00 cuadra solo con la vista previa', async () => {
    montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) => {
        if (!url.endsWith('/contabilidad/asientos/vista-previa') || init.method !== 'POST') return undefined;
        const enviado = JSON.parse(String(init.body));
        // El backend respeta la base 100.00 y suma el IVA crédito de 13.00 al Debe (modo SIN_IVA)
        return enviado.modoPrecio === 'SIN_IVA'
          ? json(
              vistaPrevia({
                modoPrecio: 'SIN_IVA',
                lineas: [
                  {
                    numeroLinea: 1,
                    cuenta: { id: 'c-41010101', codigo: '41010101', nombre: 'Compras' },
                    descripcion: null,
                    debe: '100.00',
                    haber: '0.00',
                    origenLinea: 'USUARIO',
                    numeroLineaOrigen: null,
                  },
                  {
                    numeroLinea: 2,
                    cuenta: { id: 'c-11030101', codigo: '11030101', nombre: 'IVA crédito fiscal' },
                    descripcion: null,
                    debe: '13.00',
                    haber: '0.00',
                    origenLinea: 'IVA_CALCULADO',
                    numeroLineaOrigen: 1,
                  },
                  {
                    numeroLinea: 3,
                    cuenta: { id: 'c-11010101', codigo: '11010101', nombre: 'Caja general' },
                    descripcion: null,
                    debe: '0.00',
                    haber: '113.00',
                    origenLinea: 'USUARIO',
                    numeroLineaOrigen: null,
                  },
                ],
              }),
            )
          : json(
              vistaPrevia({ cuadra: false, totalDebe: '100.00', totalHaber: '113.00', diferencia: '-13.00' }),
            );
      }),
    );
    await elegirCuenta(1, '41010101');
    await elegirCuenta(2, '11010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Compra');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 1'));
    await userEvent.selectOptions(screen.getByLabelText('Modo de precio'), 'SIN_IVA');
    await escribir('Debe', 1, '100.00');
    await escribir('Haber', 2, '113.00');

    // Sin cálculo local: la validación llega de la vista previa y el asiento cuadra (100.00 + 13.00 = 113.00)
    expect(await screen.findByText(/IVA calculado · de la línea 1/)).toBeInTheDocument();
    await waitFor(() => expect(guardar()).toBeEnabled());
    expect(screen.queryByText(/El asiento no cuadra/)).not.toBeInTheDocument();
  });

  // ADR-036: la vista previa responde 200 aunque no cuadre; Guardar sigue bloqueado y se muestra su diferencia
  it('si la vista previa dice que no cuadra, Guardar sigue bloqueado y muestra la diferencia del backend', async () => {
    montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos/vista-previa') && init.method === 'POST'
          ? json(
              vistaPrevia({ cuadra: false, totalDebe: '113.00', totalHaber: '100.00', diferencia: '13.00' }),
            )
          : undefined,
      ),
    );
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 2'));
    await escribir('Haber', 2, '100.00');
    await escribir('Debe', 1, '113.00');
    expect(await screen.findByText(/El asiento no cuadra: la diferencia es \$13\.00/)).toBeInTheDocument();
    expect(guardar()).toBeDisabled();
  });

  // Hallazgo 1 (corrección 1): el retardo de 300 ms agrupa una ráfaga de teclas en una sola petición con el cuerpo final
  it('una ráfaga de teclas en un monto no llama a la vista previa antes de 300 ms y luego lo hace una sola vez con el cuerpo final', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos/vista-previa') && init.method === 'POST'
          ? json(vistaPrevia())
          : undefined,
      ),
    );
    const previas = () => llamadas(fetchMock, 'POST', '/contabilidad/asientos/vista-previa');
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta con IVA');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 2'));
    await escribir('Haber', 2, '113.00');
    // Con RETARDO 0 esta ráfaga ya habría disparado peticiones; con 300 ms no debe haber ninguna todavía
    await escribir('Debe', 1, '113.00');
    expect(previas()).toHaveLength(0);
    await new Promise((r) => setTimeout(r, 150));
    expect(previas()).toHaveLength(0);

    // Pasado el retardo llega exactamente una petición, con el monto final y no con los intermedios
    await waitFor(() => expect(previas()).toHaveLength(1));
    await new Promise((r) => setTimeout(r, 400));
    expect(previas()).toHaveLength(1);
    const cuerpo = JSON.parse(String((previas()[0]![1] as RequestInit).body));
    expect(cuerpo.lineas[0].debe).toBe('113.00');
  });

  // Hallazgo 2 (corrección 1): un cambio nuevo deja Guardar bloqueado hasta que llega la vista previa nueva
  it('Guardar se deshabilita con un cambio nuevo mientras la vista previa está pendiente y se habilita al llegar la nueva con cuadra', async () => {
    let solicitudes = 0;
    let liberar: (r: Response) => void = () => {};
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) => {
        if (!url.endsWith('/contabilidad/asientos/vista-previa') || init.method !== 'POST') return undefined;
        solicitudes += 1;
        // La primera responde de inmediato; la segunda no se resuelve hasta que la prueba lo decide
        return solicitudes === 1
          ? json(vistaPrevia())
          : (new Promise<Response>((resolver) => {
              liberar = resolver;
            }) as unknown as Response);
      }),
    );
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta con IVA');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 2'));
    await escribir('Haber', 2, '113.00');
    await escribir('Debe', 1, '113.00');
    await waitFor(() => expect(guardar()).toBeEnabled());

    // Cambio nuevo: durante el retardo el resultado anterior (que cuadraba) ya no es vigente
    await userEvent.type(screen.getByLabelText('Debe de la línea 1'), '{Backspace}9');
    expect(guardar()).toBeDisabled();

    // Con la petición en vuelo sigue bloqueado
    await waitFor(() => expect(solicitudes).toBe(2));
    expect(guardar()).toBeDisabled();
    const cuerpo = JSON.parse(
      String((llamadas(fetchMock, 'POST', '/contabilidad/asientos/vista-previa')[1]![1] as RequestInit).body),
    );
    expect(cuerpo.lineas[0].debe).toBe('113.09');

    // Solo al llegar la respuesta nueva con cuadra: true se habilita
    liberar(json(vistaPrevia()));
    await waitFor(() => expect(guardar()).toBeEnabled());
  });

  // Sin líneas con IVA no hay vista previa: se valida en local
  it('sin líneas con IVA no llama a la vista previa', async () => {
    const { fetchMock } = montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await llenarAsientoCuadrado();
    await new Promise((r) => setTimeout(r, 400));
    expect(llamadas(fetchMock, 'POST', '/contabilidad/asientos/vista-previa')).toHaveLength(0);
  });

  // CON-013: el esquema bloquea "lleva IVA" en una cuenta de IVA de la configuración
  it('avisa CON-013 si "lleva IVA" se marca en una cuenta de IVA', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '21010101');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 2'));
    expect(await screen.findByText(/No se permite "lleva IVA" en una cuenta de IVA/)).toBeInTheDocument();
    expect(guardar()).toBeDisabled();
  });

  // ADR-036: 422 de la vista previa (CON-006 con campo) se muestra junto a la línea
  it('un 422 CON-006 de la vista previa se muestra junto a la cuenta de la línea', async () => {
    montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos/vista-previa') && init.method === 'POST'
          ? problema(422, 'CON-006', {
              errores: [{ campo: 'lineas[1].cuentaId', mensaje: 'La cuenta está inactiva' }],
            })
          : undefined,
      ),
    );
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta');
    await userEvent.click(screen.getByLabelText('Lleva IVA de la línea 2'));
    await escribir('Haber', 2, '100.00');
    await escribir('Debe', 1, '100.00');
    expect(await screen.findByText('La cuenta está inactiva')).toBeInTheDocument();
    expect(guardar()).toBeDisabled();
  });
});

describe('formulario del Libro Diario: guardado e idempotencia', () => {
  /** Devuelve las llaves `Idempotency-Key` de los POST de guardado, en orden. */
  const claves = (fetchMock: { mock: { calls: unknown[][] } }) =>
    llamadas(fetchMock, 'POST', '/contabilidad/asientos').map((c) =>
      new Headers((c[1] as RequestInit).headers).get('Idempotency-Key'),
    );

  // §1.1.4 y política del encargo: misma clave al reintentar el mismo cuerpo, otra si el cuerpo cambia
  it('el POST lleva Idempotency-Key, la misma en un reintento del mismo cuerpo y otra si el cuerpo cambia', async () => {
    let intentos = 0;
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) => {
        if (!url.endsWith('/contabilidad/asientos') || init.method !== 'POST') return undefined;
        intentos += 1;
        // Los dos primeros intentos fallan con 409 PLT-008 (otra petición en proceso); el tercero se guarda
        return intentos < 3 ? problema(409, 'PLT-008') : json(asiento(), 201);
      }),
    );
    await llenarAsientoCuadrado();
    await userEvent.click(guardar());
    expect(await screen.findByText(/Ya hay una petición igual en proceso/)).toBeInTheDocument();
    await userEvent.click(guardar());
    await waitFor(() => expect(claves(fetchMock)).toHaveLength(2));
    const [primera, segunda] = claves(fetchMock);
    expect(primera).toBeTruthy();
    expect(segunda).toBe(primera);

    // Cambiar el cuerpo (otro concepto) es otra operación: clave nueva
    await userEvent.type(screen.getByLabelText('Concepto'), ' corregida');
    await userEvent.click(guardar());
    await waitFor(() => expect(claves(fetchMock)).toHaveLength(3));
    expect(claves(fetchMock)[2]).not.toBe(primera);

    // Al guardar (201) muestra el aviso con el número y navega al detalle
    expect(await screen.findByText('Asiento N.º 7/2026 registrado')).toBeInTheDocument();
  });

  // Contrato: el cuerpo lleva montos como cadena "0.00" en el lado vacío y no lleva `modoPrecio` sin IVA
  it('envía el cuerpo con montos como cadena y sin modoPrecio cuando ninguna línea lleva IVA', async () => {
    const { fetchMock } = montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos') && init.method === 'POST' ? json(asiento(), 201) : undefined,
      ),
    );
    await llenarAsientoCuadrado();
    await userEvent.click(guardar());
    await screen.findByText('Asiento N.º 7/2026 registrado');
    const cuerpo = JSON.parse(
      String((llamadas(fetchMock, 'POST', '/contabilidad/asientos')[0]![1] as RequestInit).body),
    );
    expect(cuerpo).toEqual({
      fecha: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
      concepto: 'Venta al contado',
      lineas: [
        { cuentaId: 'c-11010101', debe: '100.00', haber: '0.00', llevaIva: false },
        { cuentaId: 'c-51010101', debe: '0.00', haber: '100.00', llevaIva: false },
      ],
    });
  });

  // §10.1: CON-005 del backend informa la diferencia del Problem Details
  it('un 422 CON-005 del backend muestra la diferencia', async () => {
    montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos') && init.method === 'POST'
          ? problema(422, 'CON-005', { diferencia: '13.00' })
          : undefined,
      ),
    );
    await llenarAsientoCuadrado();
    await userEvent.click(guardar());
    expect(await screen.findByText(/El asiento no cuadra.*Diferencia: \$13\.00/)).toBeInTheDocument();
  });

  // §10.1: CON-006 con `errores[].campo` se muestra junto a la cuenta de esa línea
  it('un 422 CON-006 con campo se muestra junto a la línea', async () => {
    montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos') && init.method === 'POST'
          ? problema(422, 'CON-006', {
              errores: [{ campo: 'lineas[1].cuentaId', mensaje: 'La cuenta no es de detalle' }],
            })
          : undefined,
      ),
    );
    await llenarAsientoCuadrado();
    await userEvent.click(guardar());
    const mensaje = await screen.findByText('La cuenta no es de detalle');
    expect(mensaje).toHaveAttribute('id', 'error-linea-1');
  });

  // §10.1: CON-017 (sin tasa de IVA vigente) y CON-007 tienen su mensaje
  it.each([
    ['CON-017', 'No hay una tasa de IVA vigente a la fecha del asiento.'],
    ['CON-007', 'La fecha no puede ser futura.'],
    ['CON-001', 'El asiento debe tener al menos dos líneas.'],
  ])('un 422 %s del backend muestra su mensaje', async (codigo, mensaje) => {
    montarContabilidad(
      '/contabilidad/libro-diario/nuevo',
      'contador',
      base((url, init) =>
        url.endsWith('/contabilidad/asientos') && init.method === 'POST' ? problema(422, codigo) : undefined,
      ),
    );
    await llenarAsientoCuadrado();
    await userEvent.click(guardar());
    expect(await screen.findByText(mensaje)).toBeInTheDocument();
  });

  // §14.2: el auditor no puede registrar asientos
  it('el auditor no ve el formulario', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'auditor', base());
    expect(await screen.findByText('No tienes permiso para registrar asientos.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
  });
});

// U2 (F4.5, paso 4): "Asiento manual (avanzado)" — atajos de teclado y cuentas recientes
describe('Asiento manual (avanzado): atajos y cuentas recientes', () => {
  // Ficha "Asiento manual (avanzado)": Enter en el Debe o el Haber de la última línea agrega una línea
  it('Enter en el Debe o el Haber de la última línea agrega una línea nueva', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await screen.findByLabelText('Cuenta de la línea 1');
    expect(screen.queryByLabelText('Cuenta de la línea 3')).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Haber de la línea 2'), '{Enter}');
    expect(await screen.findByLabelText('Cuenta de la línea 3')).toBeInTheDocument();

    // También desde el Debe de la línea que ahora es la última
    await userEvent.type(screen.getByLabelText('Debe de la línea 3'), '{Enter}');
    expect(await screen.findByLabelText('Cuenta de la línea 4')).toBeInTheDocument();
  });

  // Enter en una línea que NO es la última no agrega una línea (evita agregar de más al corregir un monto anterior)
  it('Enter en una línea que no es la última no agrega ninguna línea', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await screen.findByLabelText('Cuenta de la línea 1');
    await userEvent.type(screen.getByLabelText('Debe de la línea 1'), '{Enter}');
    expect(screen.queryByLabelText('Cuenta de la línea 3')).not.toBeInTheDocument();
  });

  // Ficha "Asiento manual (avanzado)": "Cuadrar con esta línea" pone la diferencia en una línea en blanco
  it('"Cuadrar con esta línea" completa una línea en blanco con la diferencia exacta', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await elegirCuenta(1, '11010101');
    await elegirCuenta(2, '51010101');
    await escribir('Debe', 1, '113.00');
    await escribir('Haber', 2, '100.00');
    await userEvent.type(screen.getByLabelText('Concepto'), 'Venta con vuelto a bancos');
    expect(screen.getByText('Diferencia').nextElementSibling).toHaveTextContent('$13.00');

    // Las líneas 1 y 2 ya tienen su cuenta y su lado decididos: "Cuadrar" solo tiene sentido en una línea
    // en blanco, agregada para absorber la diferencia (aquí, la línea 3)
    expect(screen.getAllByRole('button', { name: 'Cuadrar con esta línea' })[0]).toBeDisabled();
    expect(screen.getAllByRole('button', { name: 'Cuadrar con esta línea' })[1]).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await elegirCuenta(3, '11010103');
    const botonLinea3 = screen.getAllByRole('button', { name: 'Cuadrar con esta línea' })[2]!;
    expect(botonLinea3).toBeEnabled();
    await userEvent.click(botonLinea3);

    // Σ Debe 113.00 > Σ Haber 100.00: la diferencia (13.00) se completa en el Haber de la línea en blanco
    expect(screen.getByLabelText('Haber de la línea 3')).toHaveValue('13.00');
    await waitFor(() => expect(screen.getByText('Diferencia').nextElementSibling).toHaveTextContent('$0.00'));
    await waitFor(() => expect(guardar()).toBeEnabled());
  });

  // El botón se deshabilita cuando el asiento ya cuadra: no hay diferencia que repartir
  it('"Cuadrar con esta línea" se deshabilita cuando el asiento ya cuadra', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await llenarAsientoCuadrado();
    expect(screen.getAllByRole('button', { name: 'Cuadrar con esta línea' })[0]).toBeDisabled();
  });

  // Ficha "Asiento manual (avanzado)": la cuenta recién usada aparece primero la próxima vez que se abre el selector
  it('recuerda la última cuenta usada y la ofrece primero en la siguiente línea', async () => {
    montarContabilidad('/contabilidad/libro-diario/nuevo', 'contador', base());
    await elegirCuenta(1, '11010101');

    // Al abrir el selector de otra línea sin escribir nada, la cuenta recién usada encabeza la lista
    const campoLinea2 = await screen.findByLabelText('Cuenta de la línea 2');
    await userEvent.click(campoLinea2);
    const opciones = await screen.findAllByRole('option');
    expect(opciones[0]).toHaveTextContent('11010101');
  });
});
