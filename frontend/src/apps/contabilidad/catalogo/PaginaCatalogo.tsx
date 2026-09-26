import { useMemo, useState, type ReactNode } from 'react';
import { Alert } from '@/compartido/ui/alert';
import { Badge } from '@/compartido/ui/badge';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { useCuentas } from '../compartido/useCuentas';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import {
  buscarEnArbol,
  construirArbol,
  NIVEL_EXPANDIDO_POR_DEFECTO,
  type NodoCuenta,
  type ResultadoBusqueda,
} from './arbol';
import { DialogoEditarCuenta } from './DialogoEditarCuenta';
import { DialogoNuevaCuenta } from './DialogoNuevaCuenta';

/** Diálogo abierto en la pantalla del catálogo. */
type Dialogo = { tipo: 'nueva' } | { tipo: 'editar'; cuentaId: string } | null;

/**
 * Resalta la coincidencia de la búsqueda dentro de un texto con `<mark>`.
 * @param texto código o nombre de la cuenta
 * @param consulta texto buscado (vacío no resalta)
 */
function resaltar(texto: string, consulta: string): ReactNode {
  const q = consulta.trim().toLowerCase();
  const desde = q ? texto.toLowerCase().indexOf(q) : -1;
  if (desde < 0) return texto;
  return (
    <>
      {texto.slice(0, desde)}
      <mark className="bg-yellow-200">{texto.slice(desde, desde + q.length)}</mark>
      {texto.slice(desde + q.length)}
    </>
  );
}

/** Propiedades de la lista recursiva de nodos. */
interface PropsLista {
  nodos: NodoCuenta[];
  busqueda: ResultadoBusqueda | null;
  consulta: string;
  /** Devuelve si un nodo con hijas está expandido. */
  expandido: (nodo: NodoCuenta) => boolean;
  alternar: (id: string) => void;
  puedeEscribir: boolean;
  editar: (id: string) => void;
}

/** Dibuja los nodos de un nivel y, si están expandidos, sus hijas (recursivo). Con búsqueda solo los visibles. */
function ListaNodos({ nodos, busqueda, consulta, expandido, alternar, puedeEscribir, editar }: PropsLista) {
  const mostrados = busqueda ? nodos.filter((n) => busqueda.visibles.has(n.cuenta.id)) : nodos;
  return (
    <ul className="space-y-0.5">
      {mostrados.map((nodo) => {
        const c = nodo.cuenta;
        const conHijas = nodo.hijos.length > 0;
        const abierto = conHijas && expandido(nodo);
        return (
          <li key={c.id}>
            <div
              className="flex flex-wrap items-center gap-2 rounded px-2 py-1 hover:bg-neutral-50"
              style={{ paddingLeft: `${(c.nivel - 1) * 1.25 + 0.5}rem` }}
            >
              {/* Botón de expandir: accesible por teclado con aria-expanded; sin hijas, un espacio del mismo ancho */}
              {conHijas ? (
                <button
                  type="button"
                  aria-expanded={abierto}
                  aria-label={`${abierto ? 'Contraer' : 'Expandir'} ${c.codigo} ${c.nombre}`}
                  onClick={() => alternar(c.id)}
                  className="h-5 w-5 rounded text-xs focus-visible:outline-2 focus-visible:outline-neutral-900"
                >
                  {abierto ? '▾' : '▸'}
                </button>
              ) : (
                <span aria-hidden="true" className="inline-block h-5 w-5" />
              )}
              <span className="font-mono text-sm">{resaltar(c.codigo, consulta)}</span>
              <span className="text-sm">{resaltar(c.nombre, consulta)}</span>
              <Badge>{c.naturaleza === 'DEUDORA' ? 'Deudora' : 'Acreedora'}</Badge>
              {c.aceptaMovimientos && <Badge variant="success">Acepta movimientos</Badge>}
              {!c.activa && <Badge variant="warning">Inactiva</Badge>}
              {puedeEscribir && (
                <Button
                  size="sm"
                  variant="ghost"
                  className="ml-auto"
                  aria-label={`Editar cuenta ${c.codigo}`}
                  onClick={() => editar(c.id)}
                >
                  Editar
                </Button>
              )}
            </div>
            {abierto && (
              <ListaNodos
                nodos={nodo.hijos}
                busqueda={busqueda}
                consulta={consulta}
                expandido={expandido}
                alternar={alternar}
                puedeEscribir={puedeEscribir}
                editar={editar}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Pantalla "Catálogo" (`/contabilidad/catalogo`): árbol de cuentas con búsqueda local, alta y edición.
 * Lee el catálogo completo una sola vez (ADR-035) y arma el árbol con {@link construirArbol}.
 * El `auditor` solo lo ve; crear y editar es del `contador` o `admin_empresa`.
 */
export function PaginaCatalogo() {
  const { puedeEscribir } = usePermisosContabilidad();
  const { cuentas, consulta } = useCuentas();
  const [texto, setTexto] = useState('');
  // Nodos cuyo estado se invirtió respecto del valor por defecto (expandidos hasta el nivel 2)
  const [alternados, setAlternados] = useState<ReadonlySet<string>>(new Set());
  const [dialogo, setDialogo] = useState<Dialogo>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const raices = useMemo(() => (cuentas ? construirArbol(cuentas) : []), [cuentas]);
  const busqueda = useMemo(() => buscarEnArbol(raices, texto), [raices, texto]);

  /** Con búsqueda, todo lo visible se muestra expandido; sin ella, rige el nivel por defecto y las alternancias. */
  const expandido = (nodo: NodoCuenta) =>
    busqueda ? true : nodo.cuenta.nivel <= NIVEL_EXPANDIDO_POR_DEFECTO !== alternados.has(nodo.cuenta.id);

  /** Invierte el estado de expansión de un nodo. */
  const alternar = (id: string) =>
    setAlternados((previo) => {
      const siguiente = new Set(previo);
      if (!siguiente.delete(id)) siguiente.add(id);
      return siguiente;
    });

  return (
    <section aria-labelledby="titulo-catalogo" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="titulo-catalogo" className="text-xl font-semibold">
          Catálogo de cuentas
        </h2>
        {puedeEscribir && <Button onClick={() => setDialogo({ tipo: 'nueva' })}>Nueva cuenta</Button>}
      </div>

      <div className="max-w-sm space-y-1">
        <Label htmlFor="catalogo-busqueda">Buscar por código o nombre</Label>
        <Input
          id="catalogo-busqueda"
          type="search"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
        />
      </div>

      {aviso && <Alert variant="success">{aviso}</Alert>}
      {consulta.isPending && (
        <p role="status" className="text-sm text-neutral-600">
          Cargando…
        </p>
      )}
      {consulta.isError && <Alert variant="error">No pudimos cargar el catálogo de cuentas.</Alert>}

      {cuentas && (
        <div aria-label="Árbol de cuentas">
          {busqueda && busqueda.visibles.size === 0 ? (
            <p role="status" className="text-sm text-neutral-600">
              Ninguna cuenta coincide con la búsqueda.
            </p>
          ) : (
            <ListaNodos
              nodos={raices}
              busqueda={busqueda}
              consulta={texto}
              expandido={expandido}
              alternar={alternar}
              puedeEscribir={puedeEscribir}
              editar={(cuentaId) => setDialogo({ tipo: 'editar', cuentaId })}
            />
          )}
        </div>
      )}

      {dialogo?.tipo === 'nueva' && cuentas && (
        <DialogoNuevaCuenta
          cuentas={cuentas}
          onCerrar={() => setDialogo(null)}
          onCreada={() => {
            setDialogo(null);
            setAviso('Cuenta creada');
          }}
        />
      )}
      {dialogo?.tipo === 'editar' && (
        <DialogoEditarCuenta
          cuentaId={dialogo.cuentaId}
          onCerrar={() => setDialogo(null)}
          onGuardada={() => {
            setDialogo(null);
            setAviso('Cuenta actualizada');
          }}
        />
      )}
    </section>
  );
}
