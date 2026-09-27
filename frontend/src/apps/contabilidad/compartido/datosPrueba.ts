import type {
  Asiento,
  CuentaContable,
  LineaAsiento,
  ReglaContabilizacion,
  ResumenAsiento,
  VistaPreviaAsiento,
} from '@/api/modelos';

/** Datos de ejemplo para las pruebas de las pantallas de Contabilidad (subconjunto del catálogo base). */

/** Fabrica una cuenta de ejemplo; el nivel se deduce de la longitud del código. */
export function cuenta(codigo: string, nombre: string, extra: Partial<CuentaContable> = {}): CuentaContable {
  const nivel = ([1, 2, 4, 6, 8] as const).indexOf(codigo.length as 1) + 1;
  return {
    id: `c-${codigo}`,
    codigo,
    nombre,
    clase: Number(codigo[0]),
    nivel,
    cuentaPadreId: null,
    naturaleza: Number(codigo[0]) === 1 || Number(codigo[0]) === 4 ? 'DEUDORA' : 'ACREEDORA',
    aceptaMovimientos: nivel === 5,
    activa: true,
    version: 1,
    ...extra,
  };
}

/** Catálogo de ejemplo con su jerarquía completa: clases 1, 2 y 5 con algunas cuentas de detalle. */
export const CATALOGO: CuentaContable[] = [
  cuenta('1', 'Activo'),
  cuenta('11', 'Activo corriente', { cuentaPadreId: 'c-1' }),
  cuenta('1101', 'Efectivo y equivalentes', { cuentaPadreId: 'c-11' }),
  cuenta('110101', 'Caja', { cuentaPadreId: 'c-1101' }),
  cuenta('11010101', 'Caja general', { cuentaPadreId: 'c-110101' }),
  cuenta('11010103', 'Bancos', { cuentaPadreId: 'c-110101' }),
  cuenta('11010199', 'Caja chica antigua', { cuentaPadreId: 'c-110101', activa: false }),
  cuenta('11030101', 'IVA crédito fiscal', { cuentaPadreId: 'c-110101' }),
  cuenta('2', 'Pasivo'),
  cuenta('21', 'Pasivo corriente', { cuentaPadreId: 'c-2' }),
  cuenta('2101', 'Impuestos por pagar', { cuentaPadreId: 'c-21' }),
  cuenta('210101', 'IVA', { cuentaPadreId: 'c-2101' }),
  cuenta('21010101', 'IVA débito fiscal', { cuentaPadreId: 'c-210101' }),
  cuenta('5', 'Ingresos'),
  cuenta('51', 'Ingresos de operación', { cuentaPadreId: 'c-5' }),
  cuenta('5101', 'Ventas', { cuentaPadreId: 'c-51' }),
  cuenta('510101', 'Ventas del giro', { cuentaPadreId: 'c-5101' }),
  cuenta('51010101', 'Ventas gravadas', { cuentaPadreId: 'c-510101' }),
];

/** Fabrica una regla de ejemplo. */
export function regla(
  codigo: string,
  categoria: 'INGRESO' | 'COBRO',
  cuentaCodigo: string | null,
  extra: Partial<ReglaContabilizacion> = {},
): ReglaContabilizacion {
  const c = CATALOGO.find((x) => x.codigo === cuentaCodigo);
  return {
    id: `r-${codigo}`,
    tipoOperacion: 'CIERRE_INGRESOS_DIARIO',
    categoria,
    codigo,
    cuenta: c ? { id: c.id, codigo: c.codigo, nombre: c.nombre } : null,
    activa: c !== undefined,
    version: 2,
    ...extra,
  };
}

/** Configuración contable de ejemplo: modo `CON_IVA`, IVA débito 21010101 e IVA crédito 11030101 (ETag "3"). */
export const CONFIGURACION = {
  modoPrecioDefecto: 'CON_IVA',
  cuentaIvaDebito: { id: 'c-21010101', codigo: '21010101', nombre: 'IVA débito fiscal' },
  cuentaIvaCredito: { id: 'c-11030101', codigo: '11030101', nombre: 'IVA crédito fiscal' },
  version: 3,
} as const;

/** Fabrica una línea de asiento guardado de ejemplo para la cuenta con ese código del catálogo. */
export function lineaAsiento(
  numeroLinea: number,
  cuentaCodigo: string,
  debe: string,
  haber: string,
  extra: Partial<LineaAsiento> = {},
): LineaAsiento {
  const c = [...CATALOGO, cuenta('41010101', 'Compras')].find((x) => x.codigo === cuentaCodigo)!;
  return {
    id: `l-${numeroLinea}`,
    numeroLinea,
    cuenta: { id: c.id, codigo: c.codigo, nombre: c.nombre },
    descripcion: null,
    debe,
    haber,
    origenLinea: 'USUARIO',
    lineaBaseId: null,
    ...extra,
  };
}

/** Asiento guardado de ejemplo: N.º 7/2026, manual y contabilizado, Caja 113.00 / Ventas 100.00 + IVA débito 13.00. */
export function asiento(extra: Partial<Asiento> = {}): Asiento {
  return {
    id: 'a-1',
    anio: 2026,
    numero: 7,
    fecha: '2026-09-20',
    concepto: 'Venta al contado',
    estado: 'CONTABILIZADO',
    origenTipo: 'MANUAL',
    origenId: null,
    modoPrecio: 'CON_IVA',
    asientoRevertidoId: null,
    asientoReversionId: null,
    totalDebe: '113.00',
    totalHaber: '113.00',
    creadoEn: '2026-09-20T16:00:00Z',
    lineas: [
      lineaAsiento(1, '11010101', '113.00', '0.00'),
      lineaAsiento(2, '51010101', '0.00', '100.00'),
      lineaAsiento(3, '21010101', '0.00', '13.00', { origenLinea: 'IVA_CALCULADO', lineaBaseId: 'l-2' }),
    ],
    ...extra,
  };
}

/** Resumen de asiento (fila del listado) de ejemplo. */
export function resumenAsiento(numero: number, extra: Partial<ResumenAsiento> = {}): ResumenAsiento {
  return {
    id: `a-${numero}`,
    anio: 2026,
    numero,
    fecha: '2026-09-20',
    concepto: `Asiento de prueba ${numero}`,
    estado: 'CONTABILIZADO',
    origenTipo: 'MANUAL',
    totalDebe: '100.00',
    totalHaber: '100.00',
    ...extra,
  };
}

/** Vista previa de ejemplo: Caja 113.00 / Ventas 100.00 + IVA débito 13.00 (modo `CON_IVA`), que cuadra. */
export function vistaPrevia(extra: Partial<VistaPreviaAsiento> = {}): VistaPreviaAsiento {
  const c = (codigo: string) => {
    const x = CATALOGO.find((k) => k.codigo === codigo)!;
    return { id: x.id, codigo: x.codigo, nombre: x.nombre };
  };
  return {
    modoPrecio: 'CON_IVA',
    tasaIva: '0.1300',
    lineas: [
      {
        numeroLinea: 1,
        cuenta: c('11010101'),
        descripcion: null,
        debe: '113.00',
        haber: '0.00',
        origenLinea: 'USUARIO',
        numeroLineaOrigen: null,
      },
      {
        numeroLinea: 2,
        cuenta: c('51010101'),
        descripcion: null,
        debe: '0.00',
        haber: '100.00',
        origenLinea: 'USUARIO',
        numeroLineaOrigen: null,
      },
      {
        numeroLinea: 3,
        cuenta: c('21010101'),
        descripcion: null,
        debe: '0.00',
        haber: '13.00',
        origenLinea: 'IVA_CALCULADO',
        numeroLineaOrigen: 2,
      },
    ],
    totalDebe: '113.00',
    totalHaber: '113.00',
    diferencia: '0.00',
    cuadra: true,
    ...extra,
  };
}
