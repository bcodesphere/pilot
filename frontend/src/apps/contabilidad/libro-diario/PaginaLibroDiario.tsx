import { ChevronRight } from 'lucide-react';
import { Fragment, useState } from 'react';
import { Link } from 'react-router-dom';
import { useListarAsientos, useObtenerAsiento } from '@/api/asientos/asientos';
import { exportarLibroDiario } from '@/api/exportaciones-contables/exportaciones-contables';
import type {
  Asiento,
  EstadoAsiento,
  ListarAsientosParams,
  OrigenAsiento,
  PaginaAsientos,
  ResumenAsiento,
} from '@/api/modelos';
import { BarraFiltrosReporte } from '@/compartido/dominio/BarraFiltrosReporte';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { MenuExportar } from '@/compartido/dominio/MenuExportar';
import { Monto } from '@/compartido/dominio/Monto';
import { Button } from '@/compartido/ui/button';
import { Label } from '@/compartido/ui/label';
import { Select } from '@/compartido/ui/select';
import { Skeleton } from '@/compartido/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/compartido/ui/table';
import { EncabezadoInforme } from '../compartido/EncabezadoInforme';
import { descargarExportacion } from '../compartido/exportarReporte';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { ETIQUETA_ESTADO, ETIQUETA_ORIGEN, filasDeAsiento, numeroAsiento } from './etiquetas';
import { InsigniaEstado, TablaLineas } from './presentacion';

/** Asientos pedidos por página (paginación por cursor, CLAUDE.md §13). */
const TAMANO_PAGINA = 50;

/** Filtros del listado; una cadena vacía significa "sin filtro". */
interface Filtros {
  desde: string;
  hasta: string;
  origen: '' | OrigenAsiento;
  estado: '' | EstadoAsiento;
}

/**
 * Pantalla "Libro Diario" (`/contabilidad/libro-diario`): listado de asientos con filtros por fecha, origen y
 * estado y paginación por cursor con "Cargar más". El `auditor` la ve en solo lectura; el contador además
 * puede abrir el formulario de "Nuevo asiento" (CLAUDE.md §10.5, §13).
 *
 * La lista usa las tablas del sistema de diseño (`compartido/ui/table`, ADR-043) con filas expandibles a
 * las líneas del asiento; no usa `TablaContable` (que exige un arreglo `filas` plano) porque la paginación
 * por cursor acumula páginas independientes — ver la nota en `Listado` más abajo.
 */
export function PaginaLibroDiario() {
  const { puedeEscribir } = usePermisosContabilidad();
  const [filtros, setFiltros] = useState<Filtros>({ desde: '', hasta: '', origen: '', estado: '' });

  /** Actualiza un filtro; al cambiar la clave del listado se reinicia la paginación. */
  const cambiar = <K extends keyof Filtros>(campo: K, valor: Filtros[K]) =>
    setFiltros((previo) => ({ ...previo, [campo]: valor }));

  // Parámetros base de la consulta: solo se envían los filtros con valor
  const base: ListarAsientosParams = {
    limite: TAMANO_PAGINA,
    ...(filtros.desde ? { desde: filtros.desde } : {}),
    ...(filtros.hasta ? { hasta: filtros.hasta } : {}),
    ...(filtros.origen ? { origen: filtros.origen } : {}),
    ...(filtros.estado ? { estado: filtros.estado } : {}),
  };
  const filtrosCompletos = !!filtros.desde && !!filtros.hasta;

  return (
    <section aria-labelledby="titulo-libro-diario" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="titulo-libro-diario" className="text-xl font-semibold">
          Libro Diario
        </h2>
        {puedeEscribir && (
          <Button asChild>
            <Link to="/contabilidad/libro-diario/nuevo">Nuevo asiento</Link>
          </Button>
        )}
      </div>

      <BarraFiltrosReporte
        periodo={{ desde: filtros.desde, hasta: filtros.hasta }}
        onPeriodo={(rango) => setFiltros((p) => ({ ...p, ...rango }))}
        extras={
          <>
            <div className="space-y-1">
              <Label htmlFor="filtro-origen">Origen</Label>
              <Select
                id="filtro-origen"
                value={filtros.origen}
                onChange={(e) => cambiar('origen', e.target.value as Filtros['origen'])}
              >
                <option value="">Todos</option>
                {/* "Operación" (asientos del motor de operaciones guiadas, ADR-041) se agrega cuando el
                    contrato de C1 publique ese origen en OrigenAsiento; hasta entonces no se ofrece un
                    valor que la API todavía no acepta. */}
                {(Object.keys(ETIQUETA_ORIGEN) as OrigenAsiento[]).map((o) => (
                  <option key={o} value={o}>
                    {ETIQUETA_ORIGEN[o]}
                  </option>
                ))}
              </Select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="filtro-estado">Estado</Label>
              <Select
                id="filtro-estado"
                value={filtros.estado}
                onChange={(e) => cambiar('estado', e.target.value as Filtros['estado'])}
              >
                <option value="">Todos</option>
                {(Object.keys(ETIQUETA_ESTADO) as EstadoAsiento[]).map((e) => (
                  <option key={e} value={e}>
                    {ETIQUETA_ESTADO[e]}
                  </option>
                ))}
              </Select>
            </div>
          </>
        }
        exportar={
          // La exportación exige desde/hasta aunque el listado en pantalla no los requiera (ADR-038)
          <MenuExportar
            deshabilitado={!filtrosCompletos}
            onExportar={(formato) =>
              descargarExportacion(
                () =>
                  exportarLibroDiario({
                    formato,
                    desde: filtros.desde,
                    hasta: filtros.hasta,
                    ...(filtros.origen ? { origen: filtros.origen } : {}),
                    ...(filtros.estado ? { estado: filtros.estado } : {}),
                  }),
                'libro-diario',
                formato,
              )
            }
          />
        }
      />
      {filtrosCompletos && <EncabezadoInforme periodo={`Del ${filtros.desde} al ${filtros.hasta}`} />}

      {/* La clave con los filtros reinicia la paginación (y las líneas expandidas) cuando cambian */}
      <Listado key={JSON.stringify(base)} base={base} />
    </section>
  );
}

/**
 * Acumula las páginas ya pedidas del listado. Cada página se pide con su propio cursor; no se usa
 * `TablaContable` (pensada para un arreglo `filas` ya completo) porque aquí las páginas se acumulan de
 * forma incremental con "Cargar más", conservando las filas expandidas de las páginas anteriores.
 */
function Listado({ base }: { base: ListarAsientosParams }) {
  const [cursoresExtra, setCursoresExtra] = useState<string[]>([]);
  const cursores = [undefined, ...cursoresExtra];

  return (
    <div className="overflow-x-auto">
      <Table aria-label="Asientos del Libro Diario">
        <TableHeader>
          <TableRow>
            <TableHead className="w-8" />
            <TableHead>N.º</TableHead>
            <TableHead>Fecha</TableHead>
            <TableHead>Concepto</TableHead>
            <TableHead>Origen</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead className="text-right">Total Debe</TableHead>
            <TableHead className="text-right">Total Haber</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {cursores.map((cursor, i) => (
            <TramoAsientos
              key={cursor ?? 'primera'}
              params={base}
              cursor={cursor}
              primero={i === 0}
              ultimo={i === cursores.length - 1}
              onCargarMas={(siguienteCursor) => setCursoresExtra((previo) => [...previo, siguienteCursor])}
            />
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

/** Propiedades de un tramo (página) del listado. */
interface PropsTramo {
  /** Filtros y límite; el cursor de este tramo se agrega aquí. */
  params: ListarAsientosParams;
  /** Cursor de la página que pide este tramo; ausente en la primera. */
  cursor?: string;
  /** `true` en la primera página: solo ella muestra el estado de carga, de error y el vacío. */
  primero?: boolean;
  /** `true` en la última página ya pedida: solo ella ofrece "Cargar más" si hay una página siguiente. */
  ultimo?: boolean;
  /** Pide la página siguiente cuando el usuario pulsa "Cargar más". */
  onCargarMas: (cursor: string) => void;
}

/**
 * Una página del listado: pide sus asientos con su cursor y dibuja sus filas, cada una expandible a sus
 * líneas. Si es la última página cargada y hay más resultados, ofrece "Cargar más".
 */
function TramoAsientos({ params, cursor, primero, ultimo, onCargarMas }: PropsTramo) {
  const consulta = useListarAsientos({ ...params, ...(cursor ? { cursor } : {}) });
  const pagina = consulta.data?.data as PaginaAsientos | undefined;

  if (consulta.isPending) {
    if (!primero) return null;
    return (
      <TableRow>
        <TableCell colSpan={8} className="py-3">
          <span role="status" className="flex flex-col gap-2">
            <span className="sr-only">Cargando…</span>
            <Skeleton className="h-5 w-full" />
            <Skeleton className="h-5 w-2/3" />
          </span>
        </TableCell>
      </TableRow>
    );
  }
  if (consulta.isError || !pagina) {
    if (!primero) return null;
    return (
      <TableRow>
        <TableCell colSpan={8} className="py-3">
          No pudimos cargar el Libro Diario.
        </TableCell>
      </TableRow>
    );
  }
  // Estado vacío de la primera página: ningún asiento cumple los filtros
  if (primero && pagina.elementos.length === 0) {
    return (
      <TableRow>
        <TableCell colSpan={8} className="py-3">
          <div role="status">
            <EstadoVacio
              titulo="Sin asientos que coincidan"
              descripcion="No hay asientos que coincidan con los filtros."
            />
          </div>
        </TableCell>
      </TableRow>
    );
  }

  return (
    <>
      {pagina.elementos.map((a: ResumenAsiento) => (
        <FilaAsiento key={a.id} asiento={a} />
      ))}
      {/* "Cargar más" solo en la última página cargada, y solo si el backend indica otra página */}
      {ultimo && pagina.siguienteCursor && (
        <TableRow>
          <TableCell colSpan={8} className="py-2">
            <Button variant="outline" size="sm" onClick={() => onCargarMas(pagina.siguienteCursor!)}>
              Cargar más
            </Button>
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

/** Fila de un asiento del listado, expandible a sus líneas (se piden solo al expandir). */
function FilaAsiento({ asiento: a }: { asiento: ResumenAsiento }) {
  const [abierta, setAbierta] = useState(false);
  return (
    <Fragment>
      <TableRow data-selected={abierta || undefined}>
        <TableCell>
          <button
            type="button"
            aria-expanded={abierta}
            aria-label={
              abierta
                ? `Ocultar las líneas del asiento ${numeroAsiento(a)}`
                : `Ver las líneas del asiento ${numeroAsiento(a)}`
            }
            onClick={() => setAbierta((v) => !v)}
            className="flex size-6 items-center justify-center rounded-[var(--radius-control)] text-[var(--color-texto-suave)] hover:bg-[var(--color-lienzo)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]"
          >
            <ChevronRight aria-hidden="true" className={abierta ? 'size-4 rotate-90' : 'size-4'} />
          </button>
        </TableCell>
        <TableCell>
          <Link
            to={`/contabilidad/libro-diario/${a.id}`}
            className="font-medium hover:underline"
            aria-label={`Asiento ${numeroAsiento(a)}`}
          >
            {numeroAsiento(a)}
          </Link>
        </TableCell>
        <TableCell>{a.fecha}</TableCell>
        <TableCell>{a.concepto}</TableCell>
        <TableCell>{ETIQUETA_ORIGEN[a.origenTipo]}</TableCell>
        <TableCell>
          <InsigniaEstado estado={a.estado} />
        </TableCell>
        <TableCell className="text-right">
          <Monto valor={a.totalDebe} />
        </TableCell>
        <TableCell className="text-right">
          <Monto valor={a.totalHaber} />
        </TableCell>
      </TableRow>
      {abierta && (
        <TableRow>
          <TableCell colSpan={8} className="bg-[var(--color-lienzo)]">
            <DetalleLineasAsiento asientoId={a.id} />
          </TableCell>
        </TableRow>
      )}
    </Fragment>
  );
}

/** Líneas de un asiento, pedidas solo mientras su fila está expandida. */
function DetalleLineasAsiento({ asientoId }: { asientoId: string }) {
  const consulta = useObtenerAsiento(asientoId);
  const asientoCompleto = consulta.data?.data as Asiento | undefined;

  if (consulta.isPending) {
    return (
      <span role="status" className="text-sm text-[var(--color-texto-suave)]">
        Cargando las líneas…
      </span>
    );
  }
  if (consulta.isError || !asientoCompleto) {
    return (
      <span className="text-sm text-[var(--color-error)]">No pudimos cargar las líneas del asiento.</span>
    );
  }
  return (
    <TablaLineas
      leyenda={`Líneas del asiento ${numeroAsiento(asientoCompleto)}`}
      filas={filasDeAsiento(asientoCompleto)}
    />
  );
}
