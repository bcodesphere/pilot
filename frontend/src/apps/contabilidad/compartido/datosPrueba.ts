import type {
  Asiento,
  BalanzaComprobacion,
  CuentaContable,
  DesgloseIva,
  DiagnosticoMayorizacion,
  DiferenciaMayorizacion,
  EstadoResultados,
  EstadoSituacionFinanciera,
  FilaBalanza,
  FilaEstado,
  LadoSaldo,
  LibroMayor,
  LineaAsiento,
  MovimientoMayor,
  ReglaContabilizacion,
  ResumenAsiento,
  ResumenCuenta,
  RubroEstado,
  Saldo,
  ResumenIva,
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

/* ---------------------------------------------------------------------------------------------
 * Datos de ejemplo de los reportes de F4 (Mayor, Balanza, estados financieros, IVA y diagnóstico,
 * CLAUDE.md §10.3 a §10.5 y ADR-038). Las cuentas se toman del CATALOGO de arriba por su código.
 * ------------------------------------------------------------------------------------------- */

/** Referencia corta a una cuenta del CATALOGO por su código, para usarla en los reportes. */
export function resumenDeCatalogo(codigo: string): ResumenCuenta {
  const c = CATALOGO.find((x) => x.codigo === codigo)!;
  return { id: c.id, codigo: c.codigo, nombre: c.nombre };
}

/** Fabrica un `Saldo` de ejemplo (CLAUDE.md §10.3); `contrarioNaturaleza` en false salvo que se indique. */
export function saldo(monto: string, lado: LadoSaldo, contrarioNaturaleza = false): Saldo {
  return { monto, lado, contrarioNaturaleza };
}

/** Fabrica un movimiento del Mayor de ejemplo para la cuenta con ese código del catálogo. */
export function movimientoMayor(
  cuentaCodigo: string,
  debe: string,
  haber: string,
  saldoAcumulado: Saldo,
  extra: Partial<MovimientoMayor> = {},
): MovimientoMayor {
  return {
    fecha: '2026-09-20',
    asientoId: 'a-1',
    anio: 2026,
    numero: 7,
    concepto: 'Venta al contado',
    descripcion: null,
    cuenta: resumenDeCatalogo(cuentaCodigo),
    debe,
    haber,
    saldo: saldoAcumulado,
    ...extra,
  };
}

/** Libro Mayor de ejemplo: la cuenta 11010101 (Caja general) con un único movimiento de 113.00. */
export function libroMayor(extra: Partial<LibroMayor> = {}): LibroMayor {
  return {
    cuenta: resumenDeCatalogo('11010101'),
    naturaleza: 'DEUDORA',
    desde: '2026-09-01',
    hasta: '2026-09-30',
    saldoInicial: saldo('0.00', 'CERO'),
    movimientos: [movimientoMayor('11010101', '113.00', '0.00', saldo('113.00', 'DEUDOR'))],
    totalDebe: '113.00',
    totalHaber: '0.00',
    saldoFinal: saldo('113.00', 'DEUDOR'),
    ...extra,
  };
}

/** Fabrica una fila de la Balanza para la cuenta con ese código del catálogo. */
export function filaBalanza(
  cuentaCodigo: string,
  nivel: number,
  debe: string,
  haber: string,
  saldoFinal: Saldo,
  extra: Partial<FilaBalanza> = {},
): FilaBalanza {
  const c = CATALOGO.find((x) => x.codigo === cuentaCodigo)!;
  return {
    cuenta: resumenDeCatalogo(cuentaCodigo),
    nivel,
    esDetalle: c.aceptaMovimientos,
    saldoInicial: saldo('0.00', 'CERO'),
    debe,
    haber,
    saldoFinal,
    ...extra,
  };
}

/** Balanza de Comprobación de ejemplo: clase 1 con su cuenta de detalle, que cuadra. */
export function balanza(extra: Partial<BalanzaComprobacion> = {}): BalanzaComprobacion {
  return {
    desde: '2026-09-01',
    hasta: '2026-09-30',
    nivel: 5,
    filas: [
      filaBalanza('1', 1, '0.00', '0.00', saldo('113.00', 'DEUDOR')),
      filaBalanza('11', 2, '0.00', '0.00', saldo('113.00', 'DEUDOR')),
      filaBalanza('1101', 3, '0.00', '0.00', saldo('113.00', 'DEUDOR')),
      filaBalanza('110101', 4, '0.00', '0.00', saldo('113.00', 'DEUDOR')),
      filaBalanza('11010101', 5, '113.00', '0.00', saldo('113.00', 'DEUDOR')),
    ],
    totalDebe: '113.00',
    totalHaber: '113.00',
    totalSaldosDeudores: '113.00',
    totalSaldosAcreedores: '0.00',
    cuadra: true,
    ...extra,
  };
}

/** Fabrica una fila de un estado financiero para la cuenta con ese código del catálogo. */
export function filaEstado(cuentaCodigo: string, nivel: number, monto: string): FilaEstado {
  return { cuenta: resumenDeCatalogo(cuentaCodigo), nivel, monto };
}

/** Fabrica un rubro de un estado financiero (una clase de cuenta). */
export function rubroEstado(clase: number, nombre: string, filas: FilaEstado[], total: string): RubroEstado {
  return { clase, nombre, filas, total };
}

/** Leyenda de estado de gestión que exige ADR-037 §3 en ambos estados financieros. */
export const LEYENDA_ESTADO_GESTION =
  'Estado de gestión generado por Pilot; no constituye un juego completo de estados financieros conforme a NIIF para PYMES';

/** Estado de Situación Financiera de ejemplo, con la comprobación cuadrando (ADR-016, ADR-037). */
export function estadoSituacionFinanciera(
  extra: Partial<EstadoSituacionFinanciera> = {},
): EstadoSituacionFinanciera {
  return {
    fechaCorte: '2026-09-30',
    nivel: 5,
    activo: rubroEstado(1, 'Activo', [filaEstado('11010101', 5, '113.00')], '113.00'),
    pasivo: rubroEstado(2, 'Pasivo', [filaEstado('21010101', 5, '13.00')], '13.00'),
    patrimonio: rubroEstado(3, 'Patrimonio', [], '0.00'),
    resultadosEjerciciosAnteriores: '0.00',
    utilidadEjercicio: '100.00',
    totalPasivoPatrimonio: '113.00',
    comprobacion: { cuadra: true, diferencia: '0.00' },
    leyenda: LEYENDA_ESTADO_GESTION,
    ...extra,
  };
}

/** Estado de Resultados de ejemplo: una venta gravada de 100.00 sin costos ni impuesto sobre la renta. */
export function estadoResultados(extra: Partial<EstadoResultados> = {}): EstadoResultados {
  return {
    desde: '2026-09-01',
    hasta: '2026-09-30',
    nivel: 5,
    ingresos: rubroEstado(5, 'Ingresos', [filaEstado('51010101', 5, '100.00')], '100.00'),
    costosGastos: rubroEstado(4, 'Costos y gastos', [], '0.00'),
    utilidadAntesImpuesto: '100.00',
    impuestoSobreRenta: rubroEstado(4, 'Impuesto sobre la renta', [], '0.00'),
    utilidadEjercicio: '100.00',
    leyenda: LEYENDA_ESTADO_GESTION,
    ...extra,
  };
}

/** Fabrica un desglose de IVA (débito o crédito fiscal) de ejemplo. */
export function desgloseIva(manual: string, n8n: string, reversion: string, total: string): DesgloseIva {
  return { manual, n8n, reversion, total };
}

/** Resumen de IVA de ejemplo: septiembre de 2026, solo movimiento manual. */
export function resumenIva(extra: Partial<ResumenIva> = {}): ResumenIva {
  return {
    anio: 2026,
    mes: 9,
    cuentaIvaDebito: resumenDeCatalogo('21010101'),
    cuentaIvaCredito: resumenDeCatalogo('11030101'),
    ivaDebito: desgloseIva('13.00', '0.00', '0.00', '13.00'),
    ivaCredito: desgloseIva('0.00', '0.00', '0.00', '0.00'),
    diferenciaEstimada: '13.00',
    nota: 'Punto de partida para preparar la declaración de IVA; no la reemplaza.',
    ...extra,
  };
}

/** Fabrica una diferencia de mayorización de ejemplo (ADR-018). */
export function diferenciaMayorizacion(
  cuentaCodigo: string,
  extra: Partial<DiferenciaMayorizacion> = {},
): DiferenciaMayorizacion {
  return {
    cuenta: resumenDeCatalogo(cuentaCodigo),
    anio: 2026,
    mes: 9,
    saldoDebe: '113.00',
    saldoHaber: '0.00',
    lineasDebe: '100.00',
    lineasHaber: '0.00',
    ...extra,
  };
}

/** Diagnóstico de mayorización de ejemplo: consistente, sin diferencias. */
export function diagnosticoMayorizacion(
  extra: Partial<DiagnosticoMayorizacion> = {},
): DiagnosticoMayorizacion {
  return { consistente: true, cantidadCuentasRevisadas: 12, diferencias: [], ...extra };
}
