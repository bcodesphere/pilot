import { useSearchParams } from 'react-router-dom';
import { exportarResumenIva } from '@/api/exportaciones-contables/exportaciones-contables';
import type { DesgloseIva, ResumenIva } from '@/api/modelos';
import { useObtenerResumenIva } from '@/api/reportes-contables/reportes-contables';
import { formatearMonedaConSigno } from '@/compartido/dinero';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { BotonesExportacion } from '../compartido/BotonesExportacion';
import { FiltroAnioMes } from './FiltroAnioMes';

/** Año y mes actuales (hora de El Salvador), como valores por defecto del filtro. */
function periodoActual(): { anio: number; mes: number } {
  const [anio, mes] = hoyElSalvador().split('-').map(Number);
  return { anio: anio!, mes: mes! };
}

/**
 * Pantalla "Resumen de IVA" (`/contabilidad/reportes/iva`): IVA débito y crédito fiscal del mes, cada
 * uno desglosado por origen (manual, n8n, reversión), y la diferencia estimada (CLAUDE.md §10.5, §11).
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
      <FiltroAnioMes anio={anio} mes={mes} onCambiar={cambiarPeriodo} />
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
      <p role="status" className="text-neutral-600">
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
      <BotonesExportacion
        nombreArchivo={`resumen-iva-${anio}-${mes}`}
        filtrosCompletos
        exportar={(formato) => exportarResumenIva({ formato, anio, mes })}
      />
      <TablaDesglose
        titulo={`IVA débito fiscal — ${resumen.cuentaIvaDebito.codigo} ${resumen.cuentaIvaDebito.nombre}`}
        desglose={resumen.ivaDebito}
      />
      <TablaDesglose
        titulo={`IVA crédito fiscal — ${resumen.cuentaIvaCredito.codigo} ${resumen.cuentaIvaCredito.nombre}`}
        desglose={resumen.ivaCredito}
      />
      <dl className="space-y-1 border-t border-neutral-300 pt-2 text-sm">
        <div className="flex justify-between font-semibold">
          <dt>Diferencia estimada (débito − crédito)</dt>
          <dd className="tabular-nums">{formatearMonedaConSigno(resumen.diferenciaEstimada)}</dd>
        </div>
      </dl>
      <p className="text-sm text-neutral-600">{resumen.nota}</p>
    </div>
  );
}

/** Tabla del desglose de un IVA (débito o crédito) por origen del asiento. */
function TablaDesglose({ titulo, desglose }: { titulo: string; desglose: DesgloseIva }) {
  return (
    <table aria-label={titulo} className="w-full text-sm">
      <caption className="mb-1 text-left text-base font-semibold">{titulo}</caption>
      <thead>
        <tr className="border-b border-neutral-300 text-left">
          <th scope="col" className="py-1 pr-2">
            Origen
          </th>
          <th scope="col" className="py-1 pl-2 text-right">
            Monto
          </th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td className="py-1 pr-2">Manual</td>
          <td className="py-1 pl-2 text-right tabular-nums">{formatearMonedaConSigno(desglose.manual)}</td>
        </tr>
        <tr>
          <td className="py-1 pr-2">n8n</td>
          <td className="py-1 pl-2 text-right tabular-nums">{formatearMonedaConSigno(desglose.n8n)}</td>
        </tr>
        <tr>
          <td className="py-1 pr-2">Reversión</td>
          <td className="py-1 pl-2 text-right tabular-nums">{formatearMonedaConSigno(desglose.reversion)}</td>
        </tr>
        <tr className="border-t-2 border-neutral-400 font-medium">
          <td className="py-1 pr-2">Total</td>
          <td className="py-1 pl-2 text-right tabular-nums">{formatearMonedaConSigno(desglose.total)}</td>
        </tr>
      </tbody>
    </table>
  );
}
