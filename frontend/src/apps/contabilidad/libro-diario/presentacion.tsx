import type { EstadoAsiento } from '@/api/modelos';
import { esCero, formatearMoneda } from '@/compartido/dinero';
import { Badge } from '@/compartido/ui/badge';
import { ETIQUETA_ESTADO, type FilaLinea } from './etiquetas';

/**
 * Insignia del estado de un asiento: verde si está vigente, ámbar si fue revertido.
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
  /** Muestra un monto como moneda, o nada si es cero. */
  const monto = (m: string) => (esCero(m) ? '' : formatearMoneda(m));
  return (
    <div className="overflow-x-auto">
      <table aria-label={leyenda} className="w-full text-sm">
        <thead>
          <tr className="border-b border-neutral-300 text-left">
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
            <tr key={f.numero} className={`border-b border-neutral-100 ${f.esIva ? 'bg-neutral-50' : ''}`}>
              <td className="py-1 pr-2">{f.numero}</td>
              <td className="px-2 py-1">
                <span className="font-mono">{f.cuenta.codigo}</span> — {f.cuenta.nombre}
                {f.esIva && (
                  <Badge variant="default" className="ml-2">
                    IVA calculado{f.deLinea !== null ? ` · de la línea ${f.deLinea}` : ''}
                  </Badge>
                )}
              </td>
              <td className="px-2 py-1 text-neutral-600">{f.descripcion ?? ''}</td>
              <td className="px-2 py-1 text-right tabular-nums">{monto(f.debe)}</td>
              <td className="py-1 pl-2 text-right tabular-nums">{monto(f.haber)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
