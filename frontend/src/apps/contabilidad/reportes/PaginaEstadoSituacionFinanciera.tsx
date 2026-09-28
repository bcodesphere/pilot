import { Link, useSearchParams } from 'react-router-dom';
import { exportarEstadoSituacionFinanciera } from '@/api/exportaciones-contables/exportaciones-contables';
import type { EstadoSituacionFinanciera } from '@/api/modelos';
import { useObtenerEstadoSituacionFinanciera } from '@/api/reportes-contables/reportes-contables';
import { formatearMonedaConSigno } from '@/compartido/dinero';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { BotonesExportacion } from '../compartido/BotonesExportacion';
import { FiltroNivelCeros } from '../compartido/FiltroNivelCeros';
import { TablaRubro } from './TablaRubro';

/** Nivel del catálogo por defecto (CLAUDE.md §10.2). */
const NIVEL_POR_DEFECTO = 5;
/**
 * Fecha desde la que arranca el Mayor cuando se enlaza desde un estado a un corte (no un rango): el
 * catálogo base se carga con esa misma fecha técnica de vigencia (ADR-034), así que cubre todo movimiento.
 */
const DESDE_ORIGEN_MAYOR = '2000-01-01';

/**
 * Pantalla "Estado de Situación Financiera" (`/contabilidad/reportes/situacion-financiera`): Activo,
 * Pasivo, Patrimonio, resultados de ejercicios anteriores y utilidad del ejercicio a una fecha de corte,
 * con la comprobación Activo = Pasivo + Patrimonio + resultados anteriores + utilidad (ADR-016, ADR-037).
 */
export function PaginaEstadoSituacionFinanciera() {
  const [parametros, fijarParametros] = useSearchParams();
  const fechaCorte = parametros.get('fechaCorte') ?? hoyElSalvador();
  const nivel = Number(parametros.get('nivel') ?? NIVEL_POR_DEFECTO);
  const incluirCeros = parametros.get('incluirCeros') === '1';

  const cambiarFechaCorte = (v: string) =>
    fijarParametros((p) => {
      p.set('fechaCorte', v);
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

  return (
    <section aria-labelledby="titulo-esf" className="space-y-4">
      <h2 id="titulo-esf" className="text-xl font-semibold">
        Estado de Situación Financiera
      </h2>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="esf-fecha-corte">Fecha de corte</Label>
          <Input
            id="esf-fecha-corte"
            type="date"
            value={fechaCorte}
            onChange={(e) => cambiarFechaCorte(e.target.value)}
          />
        </div>
      </div>
      <FiltroNivelCeros
        idPrefijo="esf"
        nivel={nivel}
        onCambiarNivel={cambiarNivel}
        incluirCeros={incluirCeros}
        onCambiarIncluirCeros={cambiarIncluirCeros}
      />
      <ContenidoEstadoSituacionFinanciera
        key={`${fechaCorte}-${nivel}-${incluirCeros}`}
        fechaCorte={fechaCorte}
        nivel={nivel}
        incluirCeros={incluirCeros}
      />
    </section>
  );
}

/** Cuerpo del estado: pide el reporte y dibuja los rubros, o el estado de carga/error. */
function ContenidoEstadoSituacionFinanciera({
  fechaCorte,
  nivel,
  incluirCeros,
}: {
  fechaCorte: string;
  nivel: number;
  incluirCeros: boolean;
}) {
  const consulta = useObtenerEstadoSituacionFinanciera({ fechaCorte, nivel, incluirCeros });
  const estado = consulta.data?.data as EstadoSituacionFinanciera | undefined;

  if (consulta.isPending) {
    return (
      <p role="status" className="text-neutral-600">
        Cargando…
      </p>
    );
  }
  if (consulta.isError || !estado) {
    return <Alert variant="error">No pudimos cargar el Estado de Situación Financiera.</Alert>;
  }

  const parametrosMayor = () => `desde=${DESDE_ORIGEN_MAYOR}&hasta=${estado.fechaCorte}`;

  return (
    <div className="space-y-4">
      <p className="text-sm text-neutral-600">{estado.leyenda}</p>
      {!estado.comprobacion.cuadra && (
        <Alert variant="error">
          El Activo no coincide con Pasivo + Patrimonio + resultados anteriores + utilidad del ejercicio.
          Diferencia: {formatearMonedaConSigno(estado.comprobacion.diferencia)}. Revisa el{' '}
          <Link to="/contabilidad/reportes/diagnostico" className="underline">
            diagnóstico de mayorización
          </Link>
          .
        </Alert>
      )}
      <BotonesExportacion
        nombreArchivo="situacion-financiera"
        filtrosCompletos
        exportar={(formato) =>
          exportarEstadoSituacionFinanciera({ formato, fechaCorte, nivel, incluirCeros })
        }
      />
      <TablaRubro rubro={estado.activo} parametrosMayor={parametrosMayor} />
      <TablaRubro rubro={estado.pasivo} parametrosMayor={parametrosMayor} />
      <TablaRubro rubro={estado.patrimonio} parametrosMayor={parametrosMayor} />
      <dl className="space-y-1 border-t border-neutral-300 pt-2 text-sm">
        <div className="flex justify-between">
          <dt>Resultados de ejercicios anteriores</dt>
          <dd className="tabular-nums">{formatearMonedaConSigno(estado.resultadosEjerciciosAnteriores)}</dd>
        </div>
        <div className="flex justify-between">
          <dt>Utilidad del ejercicio</dt>
          <dd className="tabular-nums">{formatearMonedaConSigno(estado.utilidadEjercicio)}</dd>
        </div>
        <div className="flex justify-between font-medium">
          <dt>Total Pasivo + Patrimonio</dt>
          <dd className="tabular-nums">{formatearMonedaConSigno(estado.totalPasivoPatrimonio)}</dd>
        </div>
      </dl>
    </div>
  );
}
