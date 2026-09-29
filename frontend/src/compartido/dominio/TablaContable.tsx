import { ChevronRight } from 'lucide-react';
import { Fragment, useState, type Key, type ReactNode } from 'react';
import { Skeleton } from '@/compartido/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from '@/compartido/ui/table';

/** Una columna de {@link TablaContable}. */
export interface ColumnaContable<T> {
  /** Clave única de la columna (React `key` y accesibilidad). */
  clave: string;
  encabezado: ReactNode;
  /** Contenido de la celda para una fila; usar `Monto`/`CodigoCuenta` para datos contables. */
  celda: (fila: T) => ReactNode;
  /** Los montos y saldos van a la derecha (spec F4.5 §7.1). */
  alineacion?: 'izquierda' | 'derecha';
}

/** Propiedades de {@link TablaContable}. */
export interface PropsTablaContable<T> {
  columnas: ColumnaContable<T>[];
  filas: T[];
  /** Fila de totales, ya armada por quien la usa (`<TableRow><TableCell>…`); queda en un `<tfoot>` fijo. */
  totales?: ReactNode;
  /** Si se da, cada fila puede expandirse y muestra este contenido debajo (p. ej. las líneas de un asiento). */
  filaExpandible?: (fila: T) => ReactNode;
  /** `true` mientras se espera la respuesta: se muestra un esqueleto en vez de las filas. */
  cargando: boolean;
  /** Contenido cuando `filas` está vacío y no se está cargando (usar `EstadoVacio`). */
  vacio: ReactNode;
  /** Clave de React por fila; por defecto el índice. */
  obtenerClave?: (fila: T, indice: number) => Key;
}

/**
 * Tabla del Libro Diario, el Mayor, la Balanza y los reportes (spec F4.5 §7.1 y §7.5): filas densas
 * de sistema contable, encabezado y totales fijos, y filas expandibles opcionales.
 * @param props ver {@link PropsTablaContable}
 */
export function TablaContable<T>({
  columnas,
  filas,
  totales,
  filaExpandible,
  cargando,
  vacio,
  obtenerClave,
}: PropsTablaContable<T>) {
  const [expandidas, setExpandidas] = useState<Set<Key>>(new Set());
  const columnasTotales = columnas.length + (filaExpandible ? 1 : 0);

  /** Alterna la fila expandida sin afectar a las demás. */
  const alternar = (clave: Key) => {
    setExpandidas((actual) => {
      const siguiente = new Set(actual);
      if (siguiente.has(clave)) siguiente.delete(clave);
      else siguiente.add(clave);
      return siguiente;
    });
  };

  return (
    <Table>
      <TableHeader>
        <TableRow>
          {filaExpandible && <TableHead className="w-8" />}
          {columnas.map((columna) => (
            <TableHead
              key={columna.clave}
              className={columna.alineacion === 'derecha' ? 'text-right' : undefined}
            >
              {columna.encabezado}
            </TableHead>
          ))}
        </TableRow>
      </TableHeader>
      <TableBody>
        {cargando && (
          <TableRow>
            <TableCell colSpan={columnasTotales} className="py-3">
              <span role="status" className="flex flex-col gap-2">
                <span className="sr-only">Cargando…</span>
                <Skeleton className="h-5 w-full" />
                <Skeleton className="h-5 w-full" />
                <Skeleton className="h-5 w-2/3" />
              </span>
            </TableCell>
          </TableRow>
        )}
        {!cargando && filas.length === 0 && (
          <TableRow>
            <TableCell colSpan={columnasTotales}>{vacio}</TableCell>
          </TableRow>
        )}
        {!cargando &&
          filas.map((fila, indice) => {
            const clave = obtenerClave ? obtenerClave(fila, indice) : indice;
            const abierta = expandidas.has(clave);
            return (
              <Fragment key={clave}>
                <TableRow data-selected={abierta || undefined}>
                  {filaExpandible && (
                    <TableCell>
                      <button
                        type="button"
                        aria-expanded={abierta}
                        aria-label={abierta ? 'Ocultar detalle' : 'Ver detalle'}
                        onClick={() => alternar(clave)}
                        className="flex size-6 items-center justify-center rounded-[var(--radius-control)] text-[var(--color-texto-suave)] hover:bg-[var(--color-lienzo)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]"
                      >
                        <ChevronRight
                          aria-hidden="true"
                          className={abierta ? 'size-4 rotate-90' : 'size-4'}
                        />
                      </button>
                    </TableCell>
                  )}
                  {columnas.map((columna) => (
                    <TableCell
                      key={columna.clave}
                      className={columna.alineacion === 'derecha' ? 'text-right' : undefined}
                    >
                      {columna.celda(fila)}
                    </TableCell>
                  ))}
                </TableRow>
                {filaExpandible && abierta && (
                  <TableRow>
                    <TableCell colSpan={columnasTotales} className="bg-[var(--color-lienzo)]">
                      {filaExpandible(fila)}
                    </TableCell>
                  </TableRow>
                )}
              </Fragment>
            );
          })}
      </TableBody>
      {totales && <TableFooter>{totales}</TableFooter>}
    </Table>
  );
}
