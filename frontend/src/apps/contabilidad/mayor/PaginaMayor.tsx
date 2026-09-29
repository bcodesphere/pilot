import { Link, useSearchParams } from 'react-router-dom';
import { exportarLibroMayor } from '@/api/exportaciones-contables/exportaciones-contables';
import { useObtenerLibroMayor } from '@/api/reportes-contables/reportes-contables';
import type { LibroMayor, MovimientoMayor } from '@/api/modelos';
import { BarraFiltrosReporte } from '@/compartido/dominio/BarraFiltrosReporte';
import { CodigoCuenta } from '@/compartido/dominio/CodigoCuenta';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { EtiquetaSaldo } from '@/compartido/dominio/EtiquetaSaldo';
import { MenuExportar } from '@/compartido/dominio/MenuExportar';
import { Monto } from '@/compartido/dominio/Monto';
import { TablaContable, type ColumnaContable } from '@/compartido/dominio/TablaContable';
import type { RangoFechas } from '@/compartido/formato/rangoPeriodo';
import { Alert } from '@/compartido/ui/alert';
import { Label } from '@/compartido/ui/label';
import { TableCell, TableRow } from '@/compartido/ui/table';
import { EncabezadoInforme } from '../compartido/EncabezadoInforme';
import { descargarExportacion } from '../compartido/exportarReporte';
import { useCuentas } from '../compartido/useCuentas';
import { numeroAsiento } from '../libro-diario/etiquetas';
import { validarRangoPeriodo } from '../mensajesContabilidad';
import { SelectorCuentaMayor } from './SelectorCuentaMayor';

/**
 * Pantalla "Mayor" (`/contabilidad/mayor`): Libro Mayor / auxiliar de una cuenta cualquiera (también
 * cuentas padre, ADR-038 §5) en un rango de fechas, con saldo inicial, movimientos con saldo acumulado
 * línea a línea, totales y saldo final (CLAUDE.md §10.5). Los filtros viven en la URL para poder
 * compartir o recargar el reporte.
 */
export function PaginaMayor() {
  const [parametros, fijarParametros] = useSearchParams();
  const { cuentas, consulta: consultaCuentas } = useCuentas();

  const cuentaId = parametros.get('cuentaId') ?? '';
  const desde = parametros.get('desde') ?? '';
  const hasta = parametros.get('hasta') ?? '';

  /** Cambia la cuenta elegida, conservando el período. */
  const cambiarCuenta = (id: string) => {
    fijarParametros((p) => {
      p.set('cuentaId', id);
      return p;
    });
  };

  /** Cambia el período, conservando la cuenta. */
  const cambiarPeriodo = (rango: RangoFechas) => {
    fijarParametros((p) => {
      p.set('desde', rango.desde);
      p.set('hasta', rango.hasta);
      return p;
    });
  };

  const errorPeriodo = validarRangoPeriodo(desde, hasta);
  const listoParaConsultar = !!cuentaId && !!desde && !!hasta && !errorPeriodo;

  return (
    <section aria-labelledby="titulo-mayor" className="space-y-4">
      <h2 id="titulo-mayor" className="text-xl font-semibold">
        Libro Mayor
      </h2>

      <div className="flex flex-wrap items-end gap-3">
        <div className="w-80 space-y-1">
          <Label htmlFor="mayor-cuenta">Cuenta</Label>
          <SelectorCuentaMayor
            id="mayor-cuenta"
            cuentas={cuentas ?? []}
            valor={cuentaId || null}
            onChange={cambiarCuenta}
          />
        </div>
      </div>
      <BarraFiltrosReporte
        periodo={{ desde, hasta }}
        onPeriodo={cambiarPeriodo}
        error={errorPeriodo}
        exportar={
          <MenuExportar
            deshabilitado={!listoParaConsultar}
            onExportar={(formato) =>
              descargarExportacion(
                () => exportarLibroMayor({ formato, cuentaId, desde, hasta }),
                'mayor',
                formato,
              )
            }
          />
        }
      />

      {consultaCuentas.isError && <Alert variant="error">No pudimos cargar el catálogo de cuentas.</Alert>}
      {!listoParaConsultar && (
        <p role="status" className="text-sm text-[var(--color-texto-suave)]">
          Elige una cuenta y un período para ver el Mayor.
        </p>
      )}

      {/* La clave reinicia cualquier estado local del contenido cuando cambia el recurso consultado */}
      {listoParaConsultar && (
        <ContenidoMayor
          key={`${cuentaId}-${desde}-${hasta}`}
          cuentaId={cuentaId}
          desde={desde}
          hasta={hasta}
        />
      )}
    </section>
  );
}

/** Columnas del Mayor sobre un movimiento (el saldo inicial y el final van en el pie, no como fila). */
const columnas: ColumnaContable<MovimientoMayor>[] = [
  { clave: 'fecha', encabezado: 'Fecha', celda: (m) => m.fecha },
  {
    clave: 'asiento',
    encabezado: 'Asiento',
    celda: (m) => (
      <Link to={`/contabilidad/libro-diario/${m.asientoId}`} className="hover:underline">
        {numeroAsiento(m)}
      </Link>
    ),
  },
  {
    clave: 'cuenta',
    encabezado: 'Cuenta',
    celda: (m) => <CodigoCuenta codigo={m.cuenta.codigo} nombre={m.cuenta.nombre} />,
  },
  { clave: 'concepto', encabezado: 'Concepto', celda: (m) => m.descripcion ?? m.concepto },
  { clave: 'debe', encabezado: 'Debe', alineacion: 'derecha', celda: (m) => <Monto valor={m.debe} /> },
  { clave: 'haber', encabezado: 'Haber', alineacion: 'derecha', celda: (m) => <Monto valor={m.haber} /> },
  {
    clave: 'saldo',
    encabezado: 'Saldo',
    alineacion: 'derecha',
    celda: (m) => <EtiquetaSaldo saldo={m.saldo} />,
  },
];

/** Cuerpo del Mayor: pide el reporte y dibuja la tabla, o el estado de carga/error. */
function ContenidoMayor({ cuentaId, desde, hasta }: { cuentaId: string; desde: string; hasta: string }) {
  const consulta = useObtenerLibroMayor({ cuentaId, desde, hasta });
  const mayor = consulta.data?.data as LibroMayor | undefined;

  if (consulta.isError) {
    return <Alert variant="error">No pudimos cargar el Libro Mayor.</Alert>;
  }

  return (
    <div className="space-y-4">
      {mayor && <EncabezadoInforme periodo={`Del ${mayor.desde} al ${mayor.hasta}`} />}
      {mayor && (
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 className="text-base font-semibold text-[var(--color-texto)]">
            <CodigoCuenta codigo={mayor.cuenta.codigo} nombre={mayor.cuenta.nombre} />
          </h3>
          <p className="text-sm text-[var(--color-texto-suave)]">
            Saldo inicial: <EtiquetaSaldo saldo={mayor.saldoInicial} />
          </p>
        </div>
      )}
      <TablaContable
        columnas={columnas}
        filas={mayor?.movimientos ?? []}
        cargando={consulta.isPending}
        obtenerClave={(m, i) => `${m.asientoId}-${i}`}
        vacio={
          <EstadoVacio
            titulo="Sin movimientos en este período"
            descripcion="Ajusta el rango de fechas o elige otra cuenta."
          />
        }
        totales={
          mayor && (
            <>
              <TableRow>
                <TableCell colSpan={4}>Totales</TableCell>
                <TableCell className="text-right">
                  <Monto valor={mayor.totalDebe} />
                </TableCell>
                <TableCell className="text-right">
                  <Monto valor={mayor.totalHaber} />
                </TableCell>
                <TableCell />
              </TableRow>
              <TableRow>
                <TableCell colSpan={6}>Saldo final</TableCell>
                <TableCell className="text-right">
                  <EtiquetaSaldo saldo={mayor.saldoFinal} />
                </TableCell>
              </TableRow>
            </>
          )
        }
      />
    </div>
  );
}
