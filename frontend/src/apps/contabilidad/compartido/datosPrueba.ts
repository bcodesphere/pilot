import type { CuentaContable, ReglaContabilizacion } from '@/api/modelos';

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
