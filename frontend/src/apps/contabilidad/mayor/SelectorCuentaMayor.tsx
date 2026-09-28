import { useId, useMemo, useState, type KeyboardEvent } from 'react';
import type { CuentaContable } from '@/api/modelos';
import { Input } from '@/compartido/ui/input';

/** Máximo de opciones dibujadas a la vez; el resto se alcanza escribiendo para filtrar. */
const MAX_OPCIONES = 50;

/** Propiedades del buscador de cuenta del Mayor. */
export interface PropsSelectorCuentaMayor {
  /** Id del `<input>`, para asociarlo con su `<label htmlFor>`. */
  id: string;
  /** Catálogo completo; a diferencia de {@link import('../compartido/SelectorCuenta').SelectorCuenta}
   * (Libro Diario, solo cuentas de detalle) el Mayor acepta cualquier cuenta activa, también las
   * cuentas padre: sus movimientos son los de todas sus cuentas de detalle (ADR-038 §5). */
  cuentas: readonly CuentaContable[];
  /** Id de la cuenta elegida, o `null` si no hay. */
  valor: string | null;
  /** Se invoca con el id elegido. */
  onChange: (cuentaId: string) => void;
}

/**
 * Buscador de cuenta del Mayor: combobox accesible (patrón ARIA combobox + listbox) que filtra por
 * código o nombre entre todas las cuentas activas de la empresa, sean de detalle o padre.
 * Teclado: escribir filtra, ↑/↓ mueven la opción activa, Enter elige y Escape cierra.
 * @param props ver {@link PropsSelectorCuentaMayor}
 */
export function SelectorCuentaMayor({ id, cuentas, valor, onChange }: PropsSelectorCuentaMayor) {
  const idLista = useId();
  const [abierto, setAbierto] = useState(false);
  const [consulta, setConsulta] = useState('');
  const [indice, setIndice] = useState(0);

  // 1. Cualquier cuenta activa es elegible (a diferencia del Libro Diario, aquí sí valen las cuentas padre)
  const elegibles = useMemo(
    () =>
      cuentas
        .filter((c) => c.activa)
        .sort((a, b) => (a.codigo < b.codigo ? -1 : a.codigo > b.codigo ? 1 : 0)),
    [cuentas],
  );

  // 2. Filtro local por código o nombre, sin distinguir mayúsculas
  const opciones = useMemo(() => {
    const q = consulta.trim().toLowerCase();
    const filtradas = q
      ? elegibles.filter((c) => c.codigo.toLowerCase().includes(q) || c.nombre.toLowerCase().includes(q))
      : elegibles;
    return filtradas.slice(0, MAX_OPCIONES);
  }, [elegibles, consulta]);
  const activa = Math.min(indice, opciones.length - 1);

  // 3. Texto mostrado: lo que se escribe mientras está abierto; si no, la cuenta elegida
  const seleccionada = cuentas.find((c) => c.id === valor) ?? null;
  const etiqueta = seleccionada ? `${seleccionada.codigo} — ${seleccionada.nombre}` : '';

  /** Elige una cuenta y cierra la lista. */
  const elegir = (cuenta: CuentaContable) => {
    onChange(cuenta.id);
    setAbierto(false);
    setConsulta('');
  };

  /** Teclado del combobox (WAI-ARIA Authoring Practices), igual que el del Libro Diario. */
  const alPulsar = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!abierto) setAbierto(true);
      else setIndice(Math.min(activa + 1, opciones.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setIndice(Math.max(activa - 1, 0));
    } else if (e.key === 'Enter' && abierto) {
      e.preventDefault();
      const opcion = opciones[activa];
      if (opcion) elegir(opcion);
    } else if (e.key === 'Escape' && abierto) {
      e.stopPropagation();
      setAbierto(false);
      setConsulta('');
    }
  };

  return (
    <div className="relative">
      <Input
        id={id}
        role="combobox"
        aria-expanded={abierto}
        aria-controls={idLista}
        aria-autocomplete="list"
        aria-activedescendant={abierto && opciones.length > 0 ? `${idLista}-${activa}` : undefined}
        autoComplete="off"
        placeholder="Busca por código o nombre (cualquier cuenta)"
        value={abierto ? consulta : etiqueta}
        onChange={(e) => {
          setConsulta(e.target.value);
          setIndice(0);
          setAbierto(true);
        }}
        onFocus={() => setAbierto(true)}
        onBlur={() => {
          setAbierto(false);
          setConsulta('');
        }}
        onKeyDown={alPulsar}
      />
      {abierto && (
        <ul
          id={idLista}
          role="listbox"
          aria-label="Cuentas activas"
          className="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md border border-neutral-300 bg-white py-1 text-sm shadow-md"
        >
          {opciones.length === 0 && (
            <li
              role="option"
              aria-selected={false}
              aria-disabled="true"
              className="px-3 py-1.5 text-neutral-500"
            >
              Sin coincidencias
            </li>
          )}
          {opciones.map((c, i) => (
            <li
              key={c.id}
              id={`${idLista}-${i}`}
              role="option"
              aria-selected={c.id === valor}
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => elegir(c)}
              className={`cursor-pointer px-3 py-1.5 ${i === activa ? 'bg-neutral-100' : ''}`}
            >
              <span className="font-mono">{c.codigo}</span> — {c.nombre}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
