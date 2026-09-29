import { Link, useSearchParams } from 'react-router-dom';
import { exportarEstadoSituacionFinanciera } from '@/api/exportaciones-contables/exportaciones-contables';
import type { EstadoSituacionFinanciera } from '@/api/modelos';
import { useObtenerEstadoSituacionFinanciera } from '@/api/reportes-contables/reportes-contables';
import { MenuExportar } from '@/compartido/dominio/MenuExportar';
import { Monto } from '@/compartido/dominio/Monto';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { EncabezadoInforme } from '../compartido/EncabezadoInforme';
import { descargarExportacion } from '../compartido/exportarReporte';
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
 *
 * Es una fecha de corte, no un rango: no usa `BarraFiltrosReporte` (pensada para `desde`/`hasta`), pero
 * comparte con el resto de los reportes el `MenuExportar` y el `EncabezadoInforme`.
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
      <div className="flex flex-wrap items-end gap-3 border-b border-[var(--color-borde)] pb-3">
        <div className="space-y-1">
          <Label htmlFor="esf-fecha-corte">Fecha de corte</Label>
          <Input
            id="esf-fecha-corte"
            type="date"
            value={fechaCorte}
            onChange={(e) => cambiarFechaCorte(e.target.value)}
          />
        </div>
        <FiltroNivelCeros
          idPrefijo="esf"
          nivel={nivel}
          onCambiarNivel={cambiarNivel}
          incluirCeros={incluirCeros}
          onCambiarIncluirCeros={cambiarIncluirCeros}
        />
        <div className="ml-auto">
          <MenuExportar
            onExportar={(formato) =>
              descargarExportacion(
                () => exportarEstadoSituacionFinanciera({ formato, fechaCorte, nivel, incluirCeros }),
                'situacion-financiera',
                formato,
              )
            }
          />
        </div>
      </div>
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
      <p role="status" className="text-[var(--color-texto-suave)]">
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
      <EncabezadoInforme periodo={`Corte al ${estado.fechaCorte}`} />
      <p className="text-sm text-[var(--color-texto-suave)]">{estado.leyenda}</p>
      {!estado.comprobacion.cuadra && (
        <Alert variant="error">
          El Activo no coincide con Pasivo + Patrimonio + resultados anteriores + utilidad del ejercicio.
          Diferencia: <Monto valor={estado.comprobacion.diferencia} />. Revisa el{' '}
          <Link to="/contabilidad/reportes/diagnostico" className="underline">
            diagnóstico de mayorización
          </Link>
          .
        </Alert>
      )}
      {/* Escritorio ancho (spec F4.5 §8): Activo en una columna y Pasivo + Patrimonio en la otra, desde 1280 px */}
      <div className="grid gap-6 xl:grid-cols-2">
        <TablaRubro rubro={estado.activo} parametrosMayor={parametrosMayor} />
        <div className="space-y-4">
          <TablaRubro rubro={estado.pasivo} parametrosMayor={parametrosMayor} />
          <TablaRubro rubro={estado.patrimonio} parametrosMayor={parametrosMayor} />
        </div>
      </div>
      <dl className="space-y-1 border-t border-[var(--color-borde)] pt-2 text-sm">
        <div className="flex justify-between">
          <dt>Resultados de ejercicios anteriores</dt>
          <dd>
            <Monto valor={estado.resultadosEjerciciosAnteriores} />
          </dd>
        </div>
        <div className="flex justify-between">
          <dt>Utilidad del ejercicio</dt>
          <dd>
            <Monto valor={estado.utilidadEjercicio} />
          </dd>
        </div>
        <div className="flex justify-between font-medium">
          <dt>Total Pasivo + Patrimonio</dt>
          <dd>
            <Monto valor={estado.totalPasivoPatrimonio} />
          </dd>
        </div>
      </dl>
    </div>
  );
}
