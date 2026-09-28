import { useSearchParams } from 'react-router-dom';
import { exportarEstadoResultados } from '@/api/exportaciones-contables/exportaciones-contables';
import type { EstadoResultados } from '@/api/modelos';
import { useObtenerEstadoResultados } from '@/api/reportes-contables/reportes-contables';
import { formatearMonedaConSigno } from '@/compartido/dinero';
import type { RangoFechas } from '@/compartido/formato/rangoPeriodo';
import { Alert } from '@/compartido/ui/alert';
import { BotonesExportacion } from '../compartido/BotonesExportacion';
import { FiltroNivelCeros } from '../compartido/FiltroNivelCeros';
import { FiltroPeriodo } from '../compartido/FiltroPeriodo';
import { validarRangoPeriodo } from '../mensajesContabilidad';
import { TablaRubro } from './TablaRubro';

/** Nivel del catálogo por defecto (CLAUDE.md §10.2). */
const NIVEL_POR_DEFECTO = 5;

/**
 * Pantalla "Estado de Resultados" (`/contabilidad/reportes/resultados`): Ingresos, Costos y gastos,
 * utilidad antes de impuesto, el impuesto sobre la renta aparte y la utilidad del ejercicio (ADR-037).
 */
export function PaginaEstadoResultados() {
  const [parametros, fijarParametros] = useSearchParams();
  const desde = parametros.get('desde') ?? '';
  const hasta = parametros.get('hasta') ?? '';
  const nivel = Number(parametros.get('nivel') ?? NIVEL_POR_DEFECTO);
  const incluirCeros = parametros.get('incluirCeros') === '1';

  const cambiarPeriodo = (rango: RangoFechas) =>
    fijarParametros((p) => {
      p.set('desde', rango.desde);
      p.set('hasta', rango.hasta);
      return p;
    });
  const cambiarNivel = (n: number) =>
    fijarParametros((p) => {
      p.set('nivel', String(n));
      return p;
    });
  const cambiarIncluirCeros = (v: boolean) =>
    fijarParametros((p) => {
      p.set('incluirCeros', v ? '1' : '0');
      return p;
    });

  const errorPeriodo = validarRangoPeriodo(desde, hasta);
  const listo = !!desde && !!hasta && !errorPeriodo;

  return (
    <section aria-labelledby="titulo-resultados" className="space-y-4">
      <h2 id="titulo-resultados" className="text-xl font-semibold">
        Estado de Resultados
      </h2>
      <FiltroPeriodo
        idPrefijo="resultados"
        desde={desde}
        hasta={hasta}
        onCambiar={cambiarPeriodo}
        error={errorPeriodo}
      />
      <FiltroNivelCeros
        idPrefijo="resultados"
        nivel={nivel}
        onCambiarNivel={cambiarNivel}
        incluirCeros={incluirCeros}
        onCambiarIncluirCeros={cambiarIncluirCeros}
      />

      {!listo && (
        <p role="status" className="text-neutral-600">
          Elige un período para ver el Estado de Resultados.
        </p>
      )}
      {listo && (
        <ContenidoEstadoResultados
          key={`${desde}-${hasta}-${nivel}-${incluirCeros}`}
          desde={desde}
          hasta={hasta}
          nivel={nivel}
          incluirCeros={incluirCeros}
        />
      )}
    </section>
  );
}

/** Cuerpo del estado: pide el reporte y dibuja los rubros, o el estado de carga/error. */
function ContenidoEstadoResultados({
  desde,
  hasta,
  nivel,
  incluirCeros,
}: {
  desde: string;
  hasta: string;
  nivel: number;
  incluirCeros: boolean;
}) {
  const consulta = useObtenerEstadoResultados({ desde, hasta, nivel, incluirCeros });
  const estado = consulta.data?.data as EstadoResultados | undefined;

  if (consulta.isPending) {
    return (
      <p role="status" className="text-neutral-600">
        Cargando…
      </p>
    );
  }
  if (consulta.isError || !estado) {
    return <Alert variant="error">No pudimos cargar el Estado de Resultados.</Alert>;
  }

  const parametrosMayor = () => `desde=${estado.desde}&hasta=${estado.hasta}`;

  return (
    <div className="space-y-4">
      <p className="text-sm text-neutral-600">{estado.leyenda}</p>
      <BotonesExportacion
        nombreArchivo="estado-resultados"
        filtrosCompletos
        exportar={(formato) => exportarEstadoResultados({ formato, desde, hasta, nivel, incluirCeros })}
      />
      <TablaRubro rubro={estado.ingresos} parametrosMayor={parametrosMayor} />
      <TablaRubro rubro={estado.costosGastos} parametrosMayor={parametrosMayor} />
      <dl className="space-y-1 border-t border-neutral-300 pt-2 text-sm">
        <div className="flex justify-between font-medium">
          <dt>Utilidad antes de impuesto</dt>
          <dd className="tabular-nums">{formatearMonedaConSigno(estado.utilidadAntesImpuesto)}</dd>
        </div>
      </dl>
      <TablaRubro rubro={estado.impuestoSobreRenta} parametrosMayor={parametrosMayor} />
      <dl className="space-y-1 border-t border-neutral-300 pt-2 text-sm">
        <div className="flex justify-between font-semibold">
          <dt>Utilidad (pérdida) del ejercicio</dt>
          <dd className="tabular-nums">{formatearMonedaConSigno(estado.utilidadEjercicio)}</dd>
        </div>
      </dl>
    </div>
  );
}
