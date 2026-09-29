import { useSearchParams } from 'react-router-dom';
import { exportarResumenIva } from '@/api/exportaciones-contables/exportaciones-contables';
import type { DesgloseIva, ResumenIva } from '@/api/modelos';
import { useObtenerResumenIva } from '@/api/reportes-contables/reportes-contables';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { MenuExportar } from '@/compartido/dominio/MenuExportar';
import { Monto } from '@/compartido/dominio/Monto';
import { TablaContable, type ColumnaContable } from '@/compartido/dominio/TablaContable';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { TableCell, TableRow } from '@/compartido/ui/table';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { EncabezadoInforme } from '../compartido/EncabezadoInforme';
import { descargarExportacion } from '../compartido/exportarReporte';
import { FiltroAnioMes } from './FiltroAnioMes';

/**
 * Texto propio del frontend (spec F4.5 §7.6: ningún texto visible cita un documento interno). El
 * backend guarda esta misma nota con una cita a CLAUDE.md §10.5 para su propio uso (`ResumenIva.NOTA`,
 * pensada para quien lea el JSON o la exportación); la pantalla no repite `resumen.nota` tal cual.
 */
const NOTA_RESUMEN_IVA = 'Es un punto de partida para preparar la declaración de IVA; no la reemplaza.';

/** Año y mes actuales (hora de El Salvador), como valores por defecto del filtro. */
function periodoActual(): { anio: number; mes: number } {
  const [anio, mes] = hoyElSalvador().split('-').map(Number);
  return { anio: anio!, mes: mes! };
}

/**
 * Pantalla "Resumen de IVA" (`/contabilidad/reportes/iva`): IVA débito y crédito fiscal del mes, cada
 * uno desglosado por origen (manual, n8n, reversión), y la diferencia estimada (CLAUDE.md §10.5, §11).
 *
 * El período es un año y un mes, no un rango: no usa `BarraFiltrosReporte` (pensada para `desde`/`hasta`),
 * pero comparte con el resto de los reportes el `MenuExportar` y el `EncabezadoInforme`.
 */
export function PaginaResumenIva() {
  const [parametros, fijarParametros] = useSearchParams();
  const porDefecto = periodoActual();
  const anio = Number(parametros.get('anio') ?? porDefecto.anio);
  const mes = Number(parametros.get('mes') ?? porDefecto.mes);

  const cambiarPeriodo = (p: { anio: number; mes: number }) =>
    fijarParametros((sp) => {
      sp.set('anio', String(p.anio));
      sp.set('mes', String(p.mes));
      return sp;
    });

  return (
    <section aria-labelledby="titulo-iva" className="space-y-4">
      <h2 id="titulo-iva" className="text-xl font-semibold">
        Resumen de IVA
      </h2>
      <div className="flex flex-wrap items-end gap-3 border-b border-[var(--color-borde)] pb-3">
        <FiltroAnioMes anio={anio} mes={mes} onCambiar={cambiarPeriodo} />
        <div className="ml-auto">
          <MenuExportar
            onExportar={(formato) =>
              descargarExportacion(
                () => exportarResumenIva({ formato, anio, mes }),
                `resumen-iva-${anio}-${mes}`,
                formato,
              )
            }
          />
        </div>
      </div>
      <ContenidoResumenIva key={`${anio}-${mes}`} anio={anio} mes={mes} />
    </section>
  );
}

/** Cuerpo del resumen: pide el reporte y dibuja los desgloses, o el estado de carga/error/sin configurar. */
function ContenidoResumenIva({ anio, mes }: { anio: number; mes: number }) {
  const consulta = useObtenerResumenIva({ anio, mes });
  const resumen = consulta.data?.data as ResumenIva | undefined;

  // 404 PLT-017: la empresa no tiene configuración contable (sin precarga), como en Configuración
  const sinConfiguracion = esErrorApi(consulta.error, 'PLT-017') && consulta.error.status === 404;

  if (consulta.isPending) {
    return (
      <p role="status" className="text-[var(--color-texto-suave)]">
        Cargando…
      </p>
    );
  }
  if (sinConfiguracion) {
    return (
      <Alert variant="error">
        Este espacio de trabajo no tiene configuración contable. Contacta al soporte de Pilot.
      </Alert>
    );
  }
  if (consulta.isError || !resumen) {
    return <Alert variant="error">No pudimos cargar el resumen de IVA.</Alert>;
  }

  return (
    <div className="space-y-4">
      <EncabezadoInforme periodo={`${String(mes).padStart(2, '0')}/${anio}`} />
      <TablaDesglose
        titulo={`IVA débito fiscal — ${resumen.cuentaIvaDebito.codigo} ${resumen.cuentaIvaDebito.nombre}`}
        desglose={resumen.ivaDebito}
      />
      <TablaDesglose
        titulo={`IVA crédito fiscal — ${resumen.cuentaIvaCredito.codigo} ${resumen.cuentaIvaCredito.nombre}`}
        desglose={resumen.ivaCredito}
      />
      <dl className="space-y-1 border-t border-[var(--color-borde)] pt-2 text-sm">
        <div className="flex justify-between font-semibold">
          <dt>Diferencia estimada (débito − crédito)</dt>
          <dd>
            <Monto valor={resumen.diferenciaEstimada} conSigno />
          </dd>
        </div>
      </dl>
      <p className="text-sm text-[var(--color-texto-suave)]">{NOTA_RESUMEN_IVA}</p>
    </div>
  );
}

/** Fila del desglose de un IVA (débito o crédito) por origen del asiento. */
interface FilaOrigen {
  origen: string;
  monto: string;
  esTotal?: boolean;
}

/** Tabla del desglose de un IVA (débito o crédito) por origen del asiento. */
function TablaDesglose({ titulo, desglose }: { titulo: string; desglose: DesgloseIva }) {
  const filas: FilaOrigen[] = [
    { origen: 'Manual', monto: desglose.manual },
    { origen: 'n8n', monto: desglose.n8n },
    { origen: 'Reversión', monto: desglose.reversion },
  ];
  const columnas: ColumnaContable<FilaOrigen>[] = [
    { clave: 'origen', encabezado: 'Origen', celda: (f) => f.origen },
    { clave: 'monto', encabezado: 'Monto', alineacion: 'derecha', celda: (f) => <Monto valor={f.monto} /> },
  ];
  return (
    <div className="space-y-1">
      <h3 className="text-base font-semibold text-[var(--color-texto)]">{titulo}</h3>
      <TablaContable
        columnas={columnas}
        filas={filas}
        cargando={false}
        obtenerClave={(f) => f.origen}
        vacio={
          <EstadoVacio titulo="Sin movimiento" descripcion="No hubo movimiento de este IVA en el período." />
        }
        totales={
          <TableRow>
            <TableCell>Total</TableCell>
            <TableCell className="text-right">
              <Monto valor={desglose.total} />
            </TableCell>
          </TableRow>
        }
      />
    </div>
  );
}
