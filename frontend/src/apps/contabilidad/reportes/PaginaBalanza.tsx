import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { exportarBalanza } from '@/api/exportaciones-contables/exportaciones-contables';
import type { BalanzaComprobacion, FilaBalanza } from '@/api/modelos';
import { useObtenerBalanza } from '@/api/reportes-contables/reportes-contables';
import { restarMontos } from '@/compartido/dinero';
import type { RangoFechas } from '@/compartido/formato/rangoPeriodo';
import { BarraFiltrosReporte } from '@/compartido/dominio/BarraFiltrosReporte';
import { CodigoCuenta } from '@/compartido/dominio/CodigoCuenta';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { EtiquetaSaldo } from '@/compartido/dominio/EtiquetaSaldo';
import { MenuExportar } from '@/compartido/dominio/MenuExportar';
import { Monto } from '@/compartido/dominio/Monto';
import { TablaContable, type ColumnaContable } from '@/compartido/dominio/TablaContable';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { TableCell, TableRow } from '@/compartido/ui/table';
import { EncabezadoInforme } from '../compartido/EncabezadoInforme';
import { descargarExportacion } from '../compartido/exportarReporte';
import { FiltroNivelCeros } from '../compartido/FiltroNivelCeros';
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
      <BarraFiltrosReporte
        periodo={{ desde, hasta }}
        onPeriodo={cambiarPeriodo}
        error={errorPeriodo}
        extras={<FiltroNivelCeros idPrefijo="balanza" nivel={nivel} onCambiarNivel={cambiarNivel} />}
        exportar={
          <MenuExportar
            deshabilitado={!listo}
            onExportar={(formato) =>
              descargarExportacion(
                () => exportarBalanza({ formato, desde, hasta, nivel }),
                'balanza',
                formato,
              )
            }
          />
        }
      />

      {!listo && (
        <p role="status" className="text-[var(--color-texto-suave)]">
          Elige un período para ver la Balanza.
        </p>
      )}
      {listo && (
        <ContenidoBalanza key={`${desde}-${hasta}-${nivel}`} desde={desde} hasta={hasta} nivel={nivel} />
      )}
    </section>
  );
}

/** Fila que dibuja `TablaContable`: la cuenta de la Balanza y, si es una raíz de clase, su control de expandir. */
interface FilaVisible {
  fila: FilaBalanza;
  controlExpandir?: { expandido: boolean; onToggle: () => void };
}

/** Cuerpo de la Balanza: pide el reporte y arma la jerarquía expandible por clase, o el estado de carga/error. */
function ContenidoBalanza({ desde, hasta, nivel }: { desde: string; hasta: string; nivel: number }) {
  const consulta = useObtenerBalanza({ desde, hasta, nivel });
  const balanza = consulta.data?.data as BalanzaComprobacion | undefined;
  const [contraidas, setContraidas] = useState<Set<string>>(new Set());

  if (consulta.isError) {
    return <Alert variant="error">No pudimos cargar la Balanza de Comprobación.</Alert>;
  }

  /** Alterna si una clase (nivel 1) muestra sus cuentas hijas. */
  const alternar = (codigoClase: string) =>
    setContraidas((previo) => {
      const siguiente = new Set(previo);
      if (siguiente.has(codigoClase)) siguiente.delete(codigoClase);
      else siguiente.add(codigoClase);
      return siguiente;
    });

  // Aplana la jerarquía a las filas visibles según qué clases están expandidas
  const filasVisibles: FilaVisible[] = [];
  if (balanza) {
    const raices = balanza.filas.filter((f) => f.nivel === 1);
    for (const raiz of raices) {
      const expandido = !contraidas.has(raiz.cuenta.codigo);
      filasVisibles.push({
        fila: raiz,
        controlExpandir: { expandido, onToggle: () => alternar(raiz.cuenta.codigo) },
      });
      if (expandido) {
        const hijas = balanza.filas.filter(
          (f) => f !== raiz && f.cuenta.codigo.startsWith(raiz.cuenta.codigo),
        );
        for (const hija of hijas) filasVisibles.push({ fila: hija });
      }
    }
  }

  const parametrosMayor = `desde=${desde}&hasta=${hasta}`;
  const columnas: ColumnaContable<FilaVisible>[] = [
    {
      clave: 'cuenta',
      encabezado: 'Cuenta',
      celda: ({ fila, controlExpandir }) => (
        <span
          className="inline-flex items-center gap-1"
          style={{ paddingLeft: `${(fila.nivel - 1) * 1}rem` }}
        >
          {controlExpandir && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-expanded={controlExpandir.expandido}
              onClick={controlExpandir.onToggle}
              className="mr-1 size-6 p-0"
            >
              <span aria-hidden="true">{controlExpandir.expandido ? '▾' : '▸'}</span>
              <span className="sr-only">
                {controlExpandir.expandido
                  ? `Contraer ${fila.cuenta.nombre}`
                  : `Expandir ${fila.cuenta.nombre}`}
              </span>
            </Button>
          )}
          <Link
            to={`/contabilidad/mayor?cuentaId=${fila.cuenta.id}&${parametrosMayor}`}
            className="hover:underline"
          >
            <CodigoCuenta codigo={fila.cuenta.codigo} nombre={fila.cuenta.nombre} />
          </Link>
        </span>
      ),
    },
    {
      clave: 'saldoInicial',
      encabezado: 'Saldo inicial',
      alineacion: 'derecha',
      celda: ({ fila }) => <EtiquetaSaldo saldo={fila.saldoInicial} />,
    },
    {
      clave: 'debe',
      encabezado: 'Debe',
      alineacion: 'derecha',
      celda: ({ fila }) => <Monto valor={fila.debe} />,
    },
    {
      clave: 'haber',
      encabezado: 'Haber',
      alineacion: 'derecha',
      celda: ({ fila }) => <Monto valor={fila.haber} />,
    },
    {
      clave: 'saldoFinal',
      encabezado: 'Saldo final',
      alineacion: 'derecha',
      celda: ({ fila }) => <EtiquetaSaldo saldo={fila.saldoFinal} />,
    },
  ];

  // La alerta de descuadre y el enlace al Diagnóstico solo se muestran con el reporte ya cargado
  const diferenciaDebeHaber = balanza ? restarMontos(balanza.totalDebe, balanza.totalHaber) : '0.00';

  return (
    <div className="space-y-4">
      {balanza && <EncabezadoInforme periodo={`Del ${balanza.desde} al ${balanza.hasta}`} />}
      {balanza && !balanza.cuadra && (
        <Alert variant="error">
          La Balanza no cuadra (Σ Debe = <Monto valor={balanza.totalDebe} />, Σ Haber ={' '}
          <Monto valor={balanza.totalHaber} />
          ). Revisa el{' '}
          <Link to="/contabilidad/reportes/diagnostico" className="underline">
            diagnóstico de mayorización
          </Link>
          .
        </Alert>
      )}
      <TablaContable
        columnas={columnas}
        filas={filasVisibles}
        cargando={consulta.isPending}
        obtenerClave={(f) => f.fila.cuenta.id}
        vacio={
          <EstadoVacio
            titulo="Sin cuentas para este período"
            descripcion="Ajusta el rango de fechas o el nivel del catálogo."
          />
        }
        totales={
          balanza && (
            <>
              <TableRow>
                <TableCell>Totales (cuentas de detalle)</TableCell>
                <TableCell />
                <TableCell className="text-right">
                  <Monto valor={balanza.totalDebe} />
                </TableCell>
                <TableCell className="text-right">
                  <Monto valor={balanza.totalHaber} />
                </TableCell>
                <TableCell />
              </TableRow>
              <TableRow>
                <TableCell colSpan={2}>Saldos de las cuentas de detalle</TableCell>
                <TableCell className="text-right">
                  <EtiquetaSaldo
                    saldo={{ monto: balanza.totalSaldosDeudores, lado: 'DEUDOR', contrarioNaturaleza: false }}
                  />
                </TableCell>
                <TableCell className="text-right">
                  <EtiquetaSaldo
                    saldo={{
                      monto: balanza.totalSaldosAcreedores,
                      lado: 'ACREEDOR',
                      contrarioNaturaleza: false,
                    }}
                  />
                </TableCell>
                <TableCell className="text-right">
                  {balanza.cuadra ? (
                    <span className="text-[var(--color-exito)]">Cuadra</span>
                  ) : (
                    <span className="text-[var(--color-error)]">
                      Diferencia: <Monto valor={diferenciaDebeHaber} conSigno />
                    </span>
                  )}
                </TableCell>
              </TableRow>
            </>
          )
        }
      />
    </div>
  );
}
