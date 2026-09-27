import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useListarAsientos } from '@/api/asientos/asientos';
import type {
  EstadoAsiento,
  ListarAsientosParams,
  OrigenAsiento,
  PaginaAsientos,
  ResumenAsiento,
} from '@/api/modelos';
import { formatearMoneda } from '@/compartido/dinero';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { Select } from '@/compartido/ui/select';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { ETIQUETA_ESTADO, ETIQUETA_ORIGEN, numeroAsiento } from './etiquetas';
import { InsigniaEstado } from './presentacion';

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

  return (
    <section aria-labelledby="titulo-libro-diario" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="titulo-libro-diario" className="text-xl font-semibold">
          Libro Diario
        </h2>
        {puedeEscribir && (
          <Link
            to="/contabilidad/libro-diario/nuevo"
            className="inline-flex h-9 items-center rounded-md bg-neutral-900 px-4 text-sm font-medium text-white hover:bg-neutral-800"
          >
            Nuevo asiento
          </Link>
        )}
      </div>

      {/* Filtros: fecha contable (ambos extremos incluidos), origen y estado */}
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="filtro-desde">Desde</Label>
          <Input
            id="filtro-desde"
            type="date"
            value={filtros.desde}
            onChange={(e) => cambiar('desde', e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="filtro-hasta">Hasta</Label>
          <Input
            id="filtro-hasta"
            type="date"
            value={filtros.hasta}
            onChange={(e) => cambiar('hasta', e.target.value)}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="filtro-origen">Origen</Label>
          <Select
            id="filtro-origen"
            value={filtros.origen}
            onChange={(e) => cambiar('origen', e.target.value as Filtros['origen'])}
          >
            <option value="">Todos</option>
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
      </div>

      <div className="overflow-x-auto">
        <table aria-label="Asientos del Libro Diario" className="w-full text-sm">
          <thead>
            <tr className="border-b border-neutral-300 text-left">
              <th scope="col" className="py-1 pr-2">
                N.º
              </th>
              <th scope="col" className="px-2 py-1">
                Fecha
              </th>
              <th scope="col" className="px-2 py-1">
                Concepto
              </th>
              <th scope="col" className="px-2 py-1">
                Origen
              </th>
              <th scope="col" className="px-2 py-1">
                Estado
              </th>
              <th scope="col" className="px-2 py-1 text-right">
                Total Debe
              </th>
              <th scope="col" className="py-1 pl-2 text-right">
                Total Haber
              </th>
            </tr>
          </thead>
          <tbody>
            {/* La clave con los filtros reinicia la paginación cuando cambian */}
            <TramoAsientos key={JSON.stringify(base)} params={base} primero />
          </tbody>
        </table>
      </div>
    </section>
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
}

/**
 * Una página del listado: pide sus asientos con su cursor y dibuja sus filas. Si hay más resultados ofrece
 * "Cargar más", que monta el tramo siguiente debajo (las páginas ya cargadas se conservan).
 */
function TramoAsientos({ params, cursor, primero }: PropsTramo) {
  const [verMas, setVerMas] = useState(false);
  const consulta = useListarAsientos({ ...params, ...(cursor ? { cursor } : {}) });
  const pagina = consulta.data?.data as PaginaAsientos | undefined;

  if (consulta.isPending) {
    return (
      <tr>
        <td colSpan={7} className="py-3">
          <p role="status" className="text-neutral-600">
            Cargando…
          </p>
        </td>
      </tr>
    );
  }
  if (consulta.isError || !pagina) {
    return (
      <tr>
        <td colSpan={7} className="py-3">
          <Alert variant="error">No pudimos cargar el Libro Diario.</Alert>
        </td>
      </tr>
    );
  }
  // Estado vacío de la primera página: ningún asiento cumple los filtros
  if (primero && pagina.elementos.length === 0) {
    return (
      <tr>
        <td colSpan={7} className="py-3">
          <p role="status" className="text-neutral-600">
            No hay asientos que coincidan con los filtros.
          </p>
        </td>
      </tr>
    );
  }

  return (
    <>
      {pagina.elementos.map((a: ResumenAsiento) => (
        <tr key={a.id} className="border-b border-neutral-100">
          <td className="py-1 pr-2">
            <Link
              to={`/contabilidad/libro-diario/${a.id}`}
              className="font-medium underline"
              aria-label={`Asiento ${numeroAsiento(a)}`}
            >
              {numeroAsiento(a)}
            </Link>
          </td>
          <td className="px-2 py-1">{a.fecha}</td>
          <td className="px-2 py-1">{a.concepto}</td>
          <td className="px-2 py-1">{ETIQUETA_ORIGEN[a.origenTipo]}</td>
          <td className="px-2 py-1">
            <InsigniaEstado estado={a.estado} />
          </td>
          <td className="px-2 py-1 text-right tabular-nums">{formatearMoneda(a.totalDebe)}</td>
          <td className="py-1 pl-2 text-right tabular-nums">{formatearMoneda(a.totalHaber)}</td>
        </tr>
      ))}
      {/* "Cargar más" solo si el backend indica otra página y aún no se pidió */}
      {pagina.siguienteCursor && !verMas && (
        <tr>
          <td colSpan={7} className="py-2">
            <Button variant="outline" size="sm" onClick={() => setVerMas(true)}>
              Cargar más
            </Button>
          </td>
        </tr>
      )}
      {pagina.siguienteCursor && verMas && <TramoAsientos params={params} cursor={pagina.siguienteCursor} />}
    </>
  );
}
