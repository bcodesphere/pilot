import { useMemo, useState, type ReactNode } from 'react';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { ValorBloqueado } from '@/compartido/dominio/ValorBloqueado';
import { Alert } from '@/compartido/ui/alert';
import { Badge } from '@/compartido/ui/badge';
import { Button } from '@/compartido/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/compartido/ui/collapsible';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { useCuentas } from '../compartido/useCuentas';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import {
  buscarEnArbol,
  construirArbol,
  filtrarArbol,
  NIVEL_EXPANDIDO_POR_DEFECTO,
  type NodoCuenta,
  type ResultadoBusqueda,
} from './arbol';
import { DialogoEditarCuenta } from './DialogoEditarCuenta';
import { DialogoNuevaCuenta } from './DialogoNuevaCuenta';

/** Motivo del candado de una cuenta del catálogo base (ADR-042, CON-021). */
const MOTIVO_SISTEMA = 'Cuenta del catálogo base: agrega una subcuenta propia si necesitas más detalle';

/** Diálogo abierto en la pantalla del catálogo. */
type Dialogo = { tipo: 'nueva'; codigoInicial?: string } | { tipo: 'editar'; cuentaId: string } | null;

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
      <mark className="bg-[var(--color-alerta)]/20">{texto.slice(desde, desde + q.length)}</mark>
      {texto.slice(desde + q.length)}
    </>
  );
}

/**
 * Código y nombre de una cuenta con el resaltado de la búsqueda (`resaltar` devuelve nodos, no texto
 * plano, así que aquí no se puede usar `compartido/dominio/CodigoCuenta`, que solo acepta `string`;
 * reproduce su misma marca visual: `.codigo-cuenta` en Red Hat Mono, spec F4.5 §7.3).
 */
function CodigoNombre({ codigo, nombre, consulta }: { codigo: string; nombre: string; consulta: string }) {
  return (
    <span className="inline-flex items-baseline gap-2">
      <span className="codigo-cuenta cifra text-[var(--color-texto-suave)]">
        {resaltar(codigo, consulta)}
      </span>
      <span className="text-[var(--color-texto)]">{resaltar(nombre, consulta)}</span>
    </span>
  );
}

/** Propiedades de la lista recursiva de nodos. */
interface PropsLista {
  nodos: NodoCuenta[];
  busqueda: ResultadoBusqueda | null;
  filtro: ReadonlySet<string> | null;
  consulta: string;
  /** Devuelve si un nodo con hijas está expandido. */
  expandido: (nodo: NodoCuenta) => boolean;
  alternar: (id: string) => void;
  puedeEscribir: boolean;
  editar: (id: string) => void;
  agregarSubcuenta: (codigoPadre: string) => void;
}

/** Dibuja los nodos de un nivel y, si están expandidos, sus hijas (recursivo). Con filtros, solo los visibles. */
function ListaNodos({
  nodos,
  busqueda,
  filtro,
  consulta,
  expandido,
  alternar,
  puedeEscribir,
  editar,
  agregarSubcuenta,
}: PropsLista) {
  const mostrados = nodos.filter(
    (n) => (!busqueda || busqueda.visibles.has(n.cuenta.id)) && (!filtro || filtro.has(n.cuenta.id)),
  );
  return (
    <ul className="space-y-0.5">
      {mostrados.map((nodo) => {
        const c = nodo.cuenta;
        const conHijas = nodo.hijos.length > 0;
        const abierto = conHijas && expandido(nodo);
        // Solo una cuenta de menos de 8 dígitos puede tener subcuentas (CLAUDE.md §10.2)
        const puedeAgregarSubcuenta = c.codigo.length < 8;
        return (
          <li key={c.id}>
            <Collapsible open={abierto} onOpenChange={() => conHijas && alternar(c.id)}>
              <div
                className="flex flex-wrap items-center gap-2 rounded-[var(--radius-control)] px-2 py-1 hover:bg-[var(--color-lienzo)]"
                style={{ paddingLeft: `${(c.nivel - 1) * 1.25 + 0.5}rem` }}
              >
                {/* Disparador de expandir: accesible por teclado; sin hijas, un espacio del mismo ancho */}
                {conHijas ? (
                  <CollapsibleTrigger asChild>
                    <button
                      type="button"
                      aria-label={`${abierto ? 'Contraer' : 'Expandir'} ${c.codigo} ${c.nombre}`}
                      className="flex h-5 w-5 items-center justify-center rounded-[var(--radius-control)] text-xs text-[var(--color-texto-suave)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]"
                    >
                      {abierto ? '▾' : '▸'}
                    </button>
                  </CollapsibleTrigger>
                ) : (
                  <span aria-hidden="true" className="inline-block h-5 w-5" />
                )}
                <span className="inline-flex items-center gap-1.5">
                  {c.sistema ? (
                    <ValorBloqueado
                      valor={<CodigoNombre codigo={c.codigo} nombre={c.nombre} consulta={consulta} />}
                      motivo={MOTIVO_SISTEMA}
                    />
                  ) : (
                    <CodigoNombre codigo={c.codigo} nombre={c.nombre} consulta={consulta} />
                  )}
                </span>
                {/* Naturaleza D/A neutra (nunca "buena"/"mala" por color, spec F4.5 §7.2); siempre derivada (ADR-042) */}
                <span
                  aria-label={c.naturaleza === 'DEUDORA' ? 'Deudora' : 'Acreedora'}
                  className="text-xs text-[var(--color-texto-suave)]"
                >
                  {c.naturaleza === 'DEUDORA' ? 'D' : 'A'}
                </span>
                {c.aceptaMovimientos && <Badge variant="success">Acepta movimientos</Badge>}
                {!c.activa && <Badge variant="warning">Inactiva</Badge>}
                {puedeEscribir && (
                  <div className="ml-auto flex gap-1">
                    {puedeAgregarSubcuenta && (
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label={`Agregar subcuenta bajo ${c.codigo} ${c.nombre}`}
                        onClick={() => agregarSubcuenta(c.codigo)}
                      >
                        Agregar subcuenta
                      </Button>
                    )}
                    {!c.sistema && (
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label={`Editar cuenta ${c.codigo}`}
                        onClick={() => editar(c.id)}
                      >
                        Editar
                      </Button>
                    )}
                  </div>
                )}
              </div>
              {conHijas && (
                <CollapsibleContent>
                  <ListaNodos
                    nodos={nodo.hijos}
                    busqueda={busqueda}
                    filtro={filtro}
                    consulta={consulta}
                    expandido={expandido}
                    alternar={alternar}
                    puedeEscribir={puedeEscribir}
                    editar={editar}
                    agregarSubcuenta={agregarSubcuenta}
                  />
                </CollapsibleContent>
              )}
            </Collapsible>
          </li>
        );
      })}
    </ul>
  );
}

/**
 * Pantalla "Catálogo" (`/contabilidad/catalogo`): árbol de cuentas con búsqueda, filtros, alta y edición.
 * Lee el catálogo completo una sola vez (ADR-035) y arma el árbol con {@link construirArbol}. Las cuentas
 * del catálogo base (`sistema`) se ven con `ValorBloqueado` (ADR-042, CON-021): su código, nombre y estado
 * no se editan; solo se les agregan subcuentas propias. El `auditor` solo lo ve; crear y editar es del
 * `contador` o `admin_empresa`.
 */
export function PaginaCatalogo() {
  const { puedeEscribir } = usePermisosContabilidad();
  const { cuentas, consulta } = useCuentas();
  const [texto, setTexto] = useState('');
  const [conMovimiento, setConMovimiento] = useState(false);
  const [soloActivas, setSoloActivas] = useState(false);
  // Nodos cuyo estado se invirtió respecto del valor por defecto (expandidos hasta el nivel 2)
  const [alternados, setAlternados] = useState<ReadonlySet<string>>(new Set());
  const [dialogo, setDialogo] = useState<Dialogo>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const raices = useMemo(() => (cuentas ? construirArbol(cuentas) : []), [cuentas]);
  const busqueda = useMemo(() => buscarEnArbol(raices, texto), [raices, texto]);
  const filtro = useMemo(
    () => filtrarArbol(raices, { conMovimiento, soloActivas }),
    [raices, conMovimiento, soloActivas],
  );
  const hayFiltrosActivos = !!busqueda || !!filtro;

  /** Con búsqueda o filtros, todo lo visible se muestra expandido; si no, rige el nivel por defecto y las alternancias. */
  const expandido = (nodo: NodoCuenta) =>
    hayFiltrosActivos
      ? true
      : nodo.cuenta.nivel <= NIVEL_EXPANDIDO_POR_DEFECTO !== alternados.has(nodo.cuenta.id);

  /** Invierte el estado de expansión de un nodo. */
  const alternar = (id: string) =>
    setAlternados((previo) => {
      const siguiente = new Set(previo);
      if (!siguiente.delete(id)) siguiente.add(id);
      return siguiente;
    });

  // Ninguna fila coincide con los filtros vigentes (búsqueda y/o "Con movimiento"/"Activas")
  const sinCoincidencias =
    (!busqueda || busqueda.visibles.size === 0) && (!filtro || filtro.size === 0) && (busqueda || filtro);

  return (
    <section aria-labelledby="titulo-catalogo" className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="titulo-catalogo" className="text-xl font-semibold">
          Catálogo de cuentas
        </h2>
        {puedeEscribir && <Button onClick={() => setDialogo({ tipo: 'nueva' })}>Nueva cuenta</Button>}
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="max-w-sm flex-1 space-y-1">
          <Label htmlFor="catalogo-busqueda">Buscar por código o nombre</Label>
          <Input
            id="catalogo-busqueda"
            type="search"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
        </div>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input
            type="checkbox"
            checked={conMovimiento}
            onChange={(e) => setConMovimiento(e.target.checked)}
          />
          Con movimiento
        </label>
        <label className="flex items-center gap-2 pb-2 text-sm">
          <input type="checkbox" checked={soloActivas} onChange={(e) => setSoloActivas(e.target.checked)} />
          Activas
        </label>
      </div>

      {aviso && <Alert variant="success">{aviso}</Alert>}
      {consulta.isPending && (
        <p role="status" className="text-sm text-[var(--color-texto-suave)]">
          Cargando…
        </p>
      )}
      {consulta.isError && <Alert variant="error">No pudimos cargar el catálogo de cuentas.</Alert>}

      {/* Catálogo sin cuentas (consulta exitosa y sin filtros activos): estado vacío explicativo */}
      {cuentas && cuentas.length === 0 && !hayFiltrosActivos && (
        <div role="status">
          <EstadoVacio
            titulo="El catálogo de cuentas está vacío"
            descripcion={
              puedeEscribir
                ? 'Crea las cuentas de clase (1 a 5) con «Nueva cuenta».'
                : 'Contacta a quien administra la contabilidad para cargarlo.'
            }
          />
        </div>
      )}

      {/* Con filtros activos pero el catálogo vacío por completo: el mismo aviso de "ninguna coincide" */}
      {cuentas && cuentas.length === 0 && hayFiltrosActivos && (
        <div role="status">
          <EstadoVacio
            titulo="Ninguna cuenta coincide"
            descripcion="Ajusta la búsqueda o quita algún filtro."
          />
        </div>
      )}

      {cuentas && cuentas.length > 0 && (
        <div aria-label="Árbol de cuentas">
          {sinCoincidencias ? (
            <div role="status">
              <EstadoVacio
                titulo="Ninguna cuenta coincide"
                descripcion="Ajusta la búsqueda o quita algún filtro."
              />
            </div>
          ) : (
            <ListaNodos
              nodos={raices}
              busqueda={busqueda}
              filtro={filtro}
              consulta={texto}
              expandido={expandido}
              alternar={alternar}
              puedeEscribir={puedeEscribir}
              editar={(cuentaId) => setDialogo({ tipo: 'editar', cuentaId })}
              agregarSubcuenta={(codigoPadre) => setDialogo({ tipo: 'nueva', codigoInicial: codigoPadre })}
            />
          )}
        </div>
      )}

      {dialogo?.tipo === 'nueva' && cuentas && (
        <DialogoNuevaCuenta
          cuentas={cuentas}
          codigoInicial={dialogo.codigoInicial}
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
