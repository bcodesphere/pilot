import type { EstadoAsiento } from '@/api/modelos';
import { CodigoCuenta } from '@/compartido/dominio/CodigoCuenta';
import { Monto } from '@/compartido/dominio/Monto';
import { esCero } from '@/compartido/dinero';
import { Badge } from '@/compartido/ui/badge';
import { ETIQUETA_ESTADO, type FilaLinea } from './etiquetas';

/**
 * Insignia del estado de un asiento: verde si está vigente, ámbar si fue revertido. Se usa en las filas
 * del listado; el detalle de un asiento usa la barra de estado completa (`EstadoDocumento`, ADR-043).
 * @param props.estado estado del asiento
 */
export function InsigniaEstado({ estado }: { estado: EstadoAsiento }) {
  return (
    <Badge variant={estado === 'CONTABILIZADO' ? 'success' : 'warning'}>{ETIQUETA_ESTADO[estado]}</Badge>
  );
}

/**
 * Tabla de líneas de un asiento (expandidas o guardadas). Las de IVA calculado se marcan con una insignia y
 * indican de qué línea provienen. Un monto en cero se muestra vacío para que cada línea se lea como Debe o Haber.
 * @param props.filas líneas a mostrar
 * @param props.leyenda nombre accesible de la tabla
 */
export function TablaLineas({ filas, leyenda }: { filas: readonly FilaLinea[]; leyenda: string }) {
  return (
    <div className="overflow-x-auto">
      <table aria-label={leyenda} className="w-full text-sm">
        <thead>
          <tr className="border-b border-[var(--color-borde)] text-left">
            <th scope="col" className="py-1 pr-2">
              N.º
            </th>
            <th scope="col" className="px-2 py-1">
              Cuenta
            </th>
            <th scope="col" className="px-2 py-1">
              Descripción
            </th>
            <th scope="col" className="px-2 py-1 text-right">
              Debe
            </th>
            <th scope="col" className="py-1 pl-2 text-right">
              Haber
            </th>
          </tr>
        </thead>
        <tbody>
          {filas.map((f) => (
            <tr
              key={f.numero}
              className={`border-b border-[var(--color-borde)] ${f.esIva ? 'bg-[var(--color-lienzo)]' : ''}`}
            >
              <td className="py-1 pr-2">{f.numero}</td>
              <td className="px-2 py-1">
                <CodigoCuenta codigo={f.cuenta.codigo} nombre={f.cuenta.nombre} />
                {f.esIva && (
                  <Badge variant="default" className="ml-2">
                    IVA calculado{f.deLinea !== null ? ` · de la línea ${f.deLinea}` : ''}
                  </Badge>
                )}
              </td>
              <td className="px-2 py-1 text-[var(--color-texto-suave)]">{f.descripcion ?? ''}</td>
              <td className="px-2 py-1 text-right">{esCero(f.debe) ? '' : <Monto valor={f.debe} />}</td>
              <td className="py-1 pl-2 text-right">{esCero(f.haber) ? '' : <Monto valor={f.haber} />}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
