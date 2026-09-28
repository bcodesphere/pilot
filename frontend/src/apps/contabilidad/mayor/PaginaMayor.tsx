import { Link, useSearchParams } from 'react-router-dom';
import { exportarLibroMayor } from '@/api/exportaciones-contables/exportaciones-contables';
import { useObtenerLibroMayor } from '@/api/reportes-contables/reportes-contables';
import type { LibroMayor, MovimientoMayor } from '@/api/modelos';
import type { RangoFechas } from '@/compartido/formato/rangoPeriodo';
import { formatearMoneda } from '@/compartido/dinero';
import { Alert } from '@/compartido/ui/alert';
import { Label } from '@/compartido/ui/label';
import { BotonesExportacion } from '../compartido/BotonesExportacion';
import { FiltroPeriodo } from '../compartido/FiltroPeriodo';
import { TextoSaldo } from '../compartido/PresentacionSaldo';
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
      <FiltroPeriodo
        idPrefijo="mayor"
        desde={desde}
        hasta={hasta}
        onCambiar={cambiarPeriodo}
        error={errorPeriodo}
      />

      {consultaCuentas.isError && <Alert variant="error">No pudimos cargar el catálogo de cuentas.</Alert>}
      {!listoParaConsultar && (
        <p role="status" className="text-sm text-neutral-600">
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

/** Cuerpo del Mayor: pide el reporte y dibuja la tabla, o el estado de carga/error. */
function ContenidoMayor({ cuentaId, desde, hasta }: { cuentaId: string; desde: string; hasta: string }) {
  const consulta = useObtenerLibroMayor({ cuentaId, desde, hasta });
  const mayor = consulta.data?.data as LibroMayor | undefined;

  if (consulta.isPending) {
    return (
      <p role="status" className="text-neutral-600">
        Cargando…
      </p>
    );
  }
  if (consulta.isError || !mayor) {
    return <Alert variant="error">No pudimos cargar el Libro Mayor.</Alert>;
  }

  return (
    <div className="space-y-4">
      <BotonesExportacion
        nombreArchivo={`mayor-${mayor.cuenta.codigo}`}
        filtrosCompletos
        exportar={(formato) => exportarLibroMayor({ formato, cuentaId, desde, hasta })}
      />
      <div className="overflow-x-auto">
        <table
          aria-label={`Movimientos de ${mayor.cuenta.codigo} — ${mayor.cuenta.nombre}`}
          className="w-full text-sm"
        >
          <thead>
            <tr className="border-b border-neutral-300 text-left">
              <th scope="col" className="py-1 pr-2">
                Fecha
              </th>
              <th scope="col" className="px-2 py-1">
                Asiento
              </th>
              <th scope="col" className="px-2 py-1">
                Cuenta
              </th>
              <th scope="col" className="px-2 py-1">
                Concepto
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Debe
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Haber
              </th>
              <th scope="col" className="py-1 pl-2 text-right">
                Saldo
              </th>
            </tr>
          </thead>
          <tbody>
            <tr className="border-b border-neutral-200 bg-neutral-50 font-medium">
              <td colSpan={6} className="py-1 pr-2">
                Saldo inicial
              </td>
              <td className="py-1 pl-2 text-right">
                <TextoSaldo saldo={mayor.saldoInicial} />
              </td>
            </tr>
            {mayor.movimientos.length === 0 && (
              <tr>
                <td colSpan={7} className="py-3">
                  <p role="status" className="text-neutral-600">
                    No hay movimientos en este período.
                  </p>
                </td>
              </tr>
            )}
            {mayor.movimientos.map((m: MovimientoMayor, i: number) => (
              <tr key={`${m.asientoId}-${i}`} className="border-b border-neutral-100">
                <td className="py-1 pr-2">{m.fecha}</td>
                <td className="px-2 py-1">
                  <Link to={`/contabilidad/libro-diario/${m.asientoId}`} className="underline">
                    {numeroAsiento(m)}
                  </Link>
                </td>
                <td className="px-2 py-1">
                  <span className="font-mono">{m.cuenta.codigo}</span> — {m.cuenta.nombre}
                </td>
                <td className="px-2 py-1">{m.descripcion ?? m.concepto}</td>
                <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(m.debe)}</td>
                <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(m.haber)}</td>
                <td className="py-1 pl-2 text-right">
                  <TextoSaldo saldo={m.saldo} />
                </td>
              </tr>
            ))}
            <tr className="border-t-2 border-neutral-400 font-medium">
              <td colSpan={4} className="py-1 pr-2">
                Totales
              </td>
              <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(mayor.totalDebe)}</td>
              <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(mayor.totalHaber)}</td>
              <td className="py-1 pl-2 text-right" />
            </tr>
            <tr className="bg-neutral-50 font-medium">
              <td colSpan={6} className="py-1 pr-2">
                Saldo final
              </td>
              <td className="py-1 pl-2 text-right">
                <TextoSaldo saldo={mayor.saldoFinal} />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
