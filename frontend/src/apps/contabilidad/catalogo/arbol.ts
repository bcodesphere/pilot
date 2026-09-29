import type { CuentaContable } from '@/api/modelos';

/**
 * Lógica pura del árbol del catálogo de cuentas (CLAUDE.md §10.2, ADR-035).
 * No calcula nada contable: solo organiza y filtra lo que devuelve el backend.
 */

/** Nodo del árbol: una cuenta y sus subcuentas ordenadas por código. */
export interface NodoCuenta {
  cuenta: CuentaContable;
  hijos: NodoCuenta[];
}

/** Longitudes de código válidas por nivel: clase, grupo, cuenta, subcuenta y detalle (CLAUDE.md §10.2). */
export const LONGITUDES_CODIGO = [1, 2, 4, 6, 8] as const;

/** Nivel hasta el cual los nodos aparecen expandidos por defecto. */
export const NIVEL_EXPANDIDO_POR_DEFECTO = 2;

/** Compara dos cuentas por código; el orden lexicográfico deja cada padre antes que sus hijas. */
function porCodigo(a: NodoCuenta, b: NodoCuenta): number {
  return a.cuenta.codigo < b.cuenta.codigo ? -1 : a.cuenta.codigo > b.cuenta.codigo ? 1 : 0;
}

/**
 * Arma el árbol del catálogo a partir de la lista plana, usando `cuentaPadreId`.
 * Una cuenta huérfana (su padre no está en la lista) o que se apunta a sí misma sube a la raíz,
 * y las cuentas inactivas se conservan: el árbol nunca se rompe por datos inesperados.
 * @param cuentas catálogo completo tal como lo devuelve `GET /contabilidad/cuentas`
 * @returns raíces (normalmente las clases 1 a 5) ordenadas por código
 */
export function construirArbol(cuentas: readonly CuentaContable[]): NodoCuenta[] {
  // 1. Un nodo por cuenta, indexado por id para enlazar padres e hijas en una sola pasada
  const nodos = new Map<string, NodoCuenta>(cuentas.map((c) => [c.id, { cuenta: c, hijos: [] }]));
  const raices: NodoCuenta[] = [];

  // 2. Cada nodo se cuelga de su padre; sin padre localizable pasa a ser raíz
  for (const nodo of nodos.values()) {
    const idPadre = nodo.cuenta.cuentaPadreId;
    const padre = idPadre && idPadre !== nodo.cuenta.id ? nodos.get(idPadre) : undefined;
    (padre ? padre.hijos : raices).push(nodo);
  }

  // 3. Orden por código en todos los niveles
  const ordenar = (lista: NodoCuenta[]): NodoCuenta[] => {
    lista.sort(porCodigo);
    lista.forEach((n) => ordenar(n.hijos));
    return lista;
  };
  return ordenar(raices);
}

/** Resultado de una búsqueda: qué nodos se muestran y cuáles coinciden con el texto. */
export interface ResultadoBusqueda {
  /** Ids de las coincidencias y de todos sus ancestros. */
  visibles: ReadonlySet<string>;
  /** Ids de las cuentas cuyo código o nombre contiene el texto. */
  coincidencias: ReadonlySet<string>;
}

/**
 * Busca por código o nombre (sin distinguir mayúsculas) y conserva los ancestros de cada coincidencia.
 * @param raices árbol devuelto por {@link construirArbol}
 * @param texto texto buscado; vacío o solo espacios no filtra
 * @returns el resultado, o `null` si no hay texto que buscar
 */
export function buscarEnArbol(raices: readonly NodoCuenta[], texto: string): ResultadoBusqueda | null {
  const consulta = texto.trim().toLowerCase();
  if (!consulta) return null;
  const visibles = new Set<string>();
  const coincidencias = new Set<string>();

  /** Recorre el subárbol; devuelve true si el nodo o algún descendiente coincide. */
  const recorrer = (nodo: NodoCuenta): boolean => {
    const { codigo, nombre, id } = nodo.cuenta;
    const propia = codigo.toLowerCase().includes(consulta) || nombre.toLowerCase().includes(consulta);
    // Se recorren todos los hijos (sin cortocircuito) para marcar todas las coincidencias
    const enHijos = nodo.hijos.map(recorrer).some(Boolean);
    if (propia) coincidencias.add(id);
    if (propia || enHijos) visibles.add(id);
    return propia || enHijos;
  };
  raices.forEach(recorrer);
  return { visibles, coincidencias };
}

/** Filtros del árbol del catálogo (ficha "Catálogo", paso 3 de U2 fase B). */
export interface FiltrosArbol {
  /** Solo cuentas que aceptan movimientos (cuentas de detalle); `aceptaMovimientos` es lo único que el
   * contrato expone hasta que exista un campo de "tiene movimientos" (no hay tal endpoint en 1.0). */
  conMovimiento: boolean;
  /** Solo cuentas activas. */
  soloActivas: boolean;
}

/**
 * Ids visibles con los filtros de la pantalla: una cuenta que no cumple el filtro sigue visible si
 * alguna descendiente sí lo cumple (para no perder la jerarquía), igual que la búsqueda.
 * @param raices árbol devuelto por {@link construirArbol}
 * @param filtros filtros activos; ambos en `false` no filtra nada (`null`)
 */
export function filtrarArbol(
  raices: readonly NodoCuenta[],
  filtros: FiltrosArbol,
): ReadonlySet<string> | null {
  if (!filtros.conMovimiento && !filtros.soloActivas) return null;
  const visibles = new Set<string>();

  const cumple = (c: CuentaContable) =>
    (!filtros.conMovimiento || c.aceptaMovimientos) && (!filtros.soloActivas || c.activa);

  const recorrer = (nodo: NodoCuenta): boolean => {
    const propia = cumple(nodo.cuenta);
    const enHijos = nodo.hijos.map(recorrer).some(Boolean);
    if (propia || enHijos) visibles.add(nodo.cuenta.id);
    return propia || enHijos;
  };
  raices.forEach(recorrer);
  return visibles;
}

/**
 * Deduce, solo a modo informativo, la cuenta padre que tendría un código nuevo: la cuenta existente
 * cuyo código es el prefijo del código con la longitud del nivel anterior (ADR-035).
 * El backend es quien decide y valida (`CON-015`); esto solo orienta a la persona que escribe.
 * @param codigo código escrito (solo dígitos)
 * @param cuentas catálogo cargado
 * @returns la cuenta padre, o `null` si el código no tiene padre (clase) o este no existe
 */
export function deducirPadre(codigo: string, cuentas: readonly CuentaContable[]): CuentaContable | null {
  const nivel = LONGITUDES_CODIGO.indexOf(codigo.length as (typeof LONGITUDES_CODIGO)[number]);
  // Una clase (nivel 0) no tiene padre; una longitud inválida (nivel -1) tampoco se puede deducir
  if (nivel < 1) return null;
  const prefijo = codigo.slice(0, LONGITUDES_CODIGO[nivel - 1]);
  return cuentas.find((c) => c.codigo === prefijo) ?? null;
}
