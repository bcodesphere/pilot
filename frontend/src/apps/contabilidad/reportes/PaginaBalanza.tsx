import { useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { exportarBalanza } from '@/api/exportaciones-contables/exportaciones-contables';
import type { BalanzaComprobacion, FilaBalanza } from '@/api/modelos';
import { useObtenerBalanza } from '@/api/reportes-contables/reportes-contables';
import { formatearMoneda } from '@/compartido/dinero';
import type { RangoFechas } from '@/compartido/formato/rangoPeriodo';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { BotonesExportacion } from '../compartido/BotonesExportacion';
import { FiltroNivelCeros } from '../compartido/FiltroNivelCeros';
import { FiltroPeriodo } from '../compartido/FiltroPeriodo';
import { TextoSaldo } from '../compartido/PresentacionSaldo';
import { validarRangoPeriodo } from '../mensajesContabilidad';

/** Nivel del catálogo por defecto: 5 (detalle), como en el resto de reportes (CLAUDE.md §10.2). */
const NIVEL_POR_DEFECTO = 5;

/**
 * Pantalla "Balanza de Comprobación" (`/contabilidad/reportes/balanza`): saldo inicial, Debe, Haber
 * y saldo final por cuenta hasta el nivel elegido, con la jerarquía expandible por clase y el enlace
 * de cada cuenta al Mayor con el mismo período (CLAUDE.md §10.5, ADR-038 §6).
 */
export function PaginaBalanza() {
  const [parametros, fijarParametros] = useSearchParams();
  const desde = parametros.get('desde') ?? '';
  const hasta = parametros.get('hasta') ?? '';
  const nivel = Number(parametros.get('nivel') ?? NIVEL_POR_DEFECTO);

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

  const errorPeriodo = validarRangoPeriodo(desde, hasta);
  const listo = !!desde && !!hasta && !errorPeriodo;

  return (
    <section aria-labelledby="titulo-balanza" className="space-y-4">
      <h2 id="titulo-balanza" className="text-xl font-semibold">
        Balanza de Comprobación
      </h2>
      <FiltroPeriodo
        idPrefijo="balanza"
        desde={desde}
        hasta={hasta}
        onCambiar={cambiarPeriodo}
        error={errorPeriodo}
      />
      <FiltroNivelCeros idPrefijo="balanza" nivel={nivel} onCambiarNivel={cambiarNivel} />

      {!listo && (
        <p role="status" className="text-neutral-600">
          Elige un período para ver la Balanza.
        </p>
      )}
      {listo && (
        <ContenidoBalanza key={`${desde}-${hasta}-${nivel}`} desde={desde} hasta={hasta} nivel={nivel} />
      )}
    </section>
  );
}

/** Cuerpo de la Balanza: pide el reporte y dibuja la tabla jerárquica, o el estado de carga/error. */
function ContenidoBalanza({ desde, hasta, nivel }: { desde: string; hasta: string; nivel: number }) {
  const consulta = useObtenerBalanza({ desde, hasta, nivel });
  const balanza = consulta.data?.data as BalanzaComprobacion | undefined;
  const [contraidas, setContraidas] = useState<Set<string>>(new Set());

  if (consulta.isPending) {
    return (
      <p role="status" className="text-neutral-600">
        Cargando…
      </p>
    );
  }
  if (consulta.isError || !balanza) {
    return <Alert variant="error">No pudimos cargar la Balanza de Comprobación.</Alert>;
  }

  /** Clases (nivel 1) que agrupan al resto de las filas por prefijo de código. */
  const raices = balanza.filas.filter((f) => f.nivel === 1);
  const parametrosMayor = `desde=${desde}&hasta=${hasta}`;

  return (
    <div className="space-y-4">
      {!balanza.cuadra && (
        <Alert variant="error">
          La Balanza no cuadra (Σ Debe = {formatearMoneda(balanza.totalDebe)}, Σ Haber ={' '}
          {formatearMoneda(balanza.totalHaber)}). Revisa el{' '}
          <Link to="/contabilidad/reportes/diagnostico" className="underline">
            diagnóstico de mayorización
          </Link>
          .
        </Alert>
      )}
      <BotonesExportacion
        nombreArchivo="balanza"
        filtrosCompletos
        exportar={(formato) => exportarBalanza({ formato, desde, hasta, nivel })}
      />
      <div className="overflow-x-auto">
        <table aria-label="Balanza de Comprobación" className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-300 text-left">
              <th scope="col" className="py-1 pr-2">
                Cuenta
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Saldo inicial
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Debe
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Haber
              </th>
              <th scope="col" className="py-1 pl-2 text-right">
                Saldo final
              </th>
            </tr>
          </thead>
          <tbody>
            {raices.map((raiz) => {
              const hijas = balanza.filas.filter(
                (f) => f !== raiz && f.cuenta.codigo.startsWith(raiz.cuenta.codigo),
              );
              const expandido = !contraidas.has(raiz.cuenta.codigo);
              return (
                <FragmentoClase
                  key={raiz.cuenta.id}
                  raiz={raiz}
                  hijas={hijas}
                  expandido={expandido}
                  parametrosMayor={parametrosMayor}
                  onToggle={() =>
                    setContraidas((prev) => {
                      const siguiente = new Set(prev);
                      if (siguiente.has(raiz.cuenta.codigo)) siguiente.delete(raiz.cuenta.codigo);
                      else siguiente.add(raiz.cuenta.codigo);
                      return siguiente;
                    })
                  }
                />
              );
            })}
            <tr className="border-t-2 border-neutral-400 font-medium">
              <td className="py-1 pr-2">Totales (cuentas de detalle)</td>
              <td className="px-2 py-1" />
              <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(balanza.totalDebe)}</td>
              <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(balanza.totalHaber)}</td>
              <td className="py-1 pl-2" />
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Fila de una clase (nivel 1) con su botón para expandir/contraer, y sus filas hijas si está expandida. */
function FragmentoClase({
  raiz,
  hijas,
  expandido,
  parametrosMayor,
  onToggle,
}: {
  raiz: FilaBalanza;
  hijas: FilaBalanza[];
  expandido: boolean;
  parametrosMayor: string;
  onToggle: () => void;
}) {
  return (
    <>
      <FilaTabla
        fila={raiz}
        parametrosMayor={parametrosMayor}
        controlExpandir={
          <BotonExpandir expandido={expandido} onToggle={onToggle} nombreClase={raiz.cuenta.nombre} />
        }
      />
      {expandido &&
        hijas.map((h) => <FilaTabla key={h.cuenta.id} fila={h} parametrosMayor={parametrosMayor} />)}
    </>
  );
}

/** Botón accesible para expandir/contraer las cuentas de una clase. */
function BotonExpandir({
  expandido,
  onToggle,
  nombreClase,
}: {
  expandido: boolean;
  onToggle: () => void;
  nombreClase: string;
}) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="sm"
      aria-expanded={expandido}
      onClick={onToggle}
      className="mr-1 h-6 px-1"
    >
      <span aria-hidden="true">{expandido ? '▾' : '▸'}</span>
      <span className="sr-only">{expandido ? `Contraer ${nombreClase}` : `Expandir ${nombreClase}`}</span>
    </Button>
  );
}

/** Fila de una cuenta de la Balanza, con sangría por nivel y las de detalle distinguibles en negrita. */
function FilaTabla({
  fila,
  parametrosMayor,
  controlExpandir,
}: {
  fila: FilaBalanza;
  parametrosMayor: string;
  controlExpandir?: ReactNode;
}) {
  return (
    <tr className={`border-b border-neutral-100 ${fila.esDetalle ? 'font-medium' : ''}`}>
      <td className="py-1 pr-2" style={{ paddingLeft: `${(fila.nivel - 1) * 1}rem` }}>
        {controlExpandir}
        <Link to={`/contabilidad/mayor?cuentaId=${fila.cuenta.id}&${parametrosMayor}`} className="underline">
          <span className="font-mono">{fila.cuenta.codigo}</span> — {fila.cuenta.nombre}
        </Link>
      </td>
      <td className="px-2 py-1 text-right">
        <TextoSaldo saldo={fila.saldoInicial} />
      </td>
      <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(fila.debe)}</td>
      <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(fila.haber)}</td>
      <td className="py-1 pl-2 text-right">
        <TextoSaldo saldo={fila.saldoFinal} />
      </td>
    </tr>
  );
}
