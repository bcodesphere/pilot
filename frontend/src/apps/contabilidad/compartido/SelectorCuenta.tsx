import { useId, useMemo, useState, type KeyboardEvent } from 'react';
import type { CuentaContable, ResumenCuenta } from '@/api/modelos';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';

/** Máximo de opciones dibujadas a la vez; el resto se alcanza escribiendo para filtrar. */
const MAX_OPCIONES = 50;

/** Propiedades del selector de cuenta. */
export interface PropsSelectorCuenta {
  /** Id del `<input>`, para asociarlo con su `<label htmlFor>`. */
  id: string;
  /** Catálogo completo; el selector solo ofrece las cuentas de detalle y activas. */
  cuentas: readonly CuentaContable[];
  /** Id de la cuenta elegida, o `null` si no hay. */
  valor: string | null;
  /** Se invoca con el id elegido (o `null` al quitar la cuenta). */
  onChange: (cuentaId: string | null) => void;
  /** Cuenta ya guardada, para mostrarla aunque ya no sea elegible (p. ej. se desactivó después). */
  valorActual?: ResumenCuenta | null;
  /** Marca el control como inválido (`aria-invalid`). */
  invalido?: boolean;
  /** Ids de los elementos que describen el control (mensaje de error). */
  describedBy?: string;
  /** Deshabilita el control. */
  disabled?: boolean;
  /** Texto del campo vacío; por defecto invita a buscar. */
  placeholder?: string;
  /** Muestra un botón "Quitar cuenta" (las reglas inactivas pueden quedar sin cuenta, ADR-035). */
  permitirQuitar?: boolean;
  /**
   * Ids de cuenta a mostrar primero, en este orden, antes del resto por código (spec F4.5 §8, ficha
   * "Asiento manual (avanzado)": "cuentas recientes primero"). Ausente o vacío: solo se ordena por código.
   */
  ordenPreferido?: readonly string[];
  /**
   * Prefijo de código al que se restringen las cuentas ofrecidas (`regla_contabilizacion.prefijo_permitido`,
   * ADR-042); asignar una cuenta fuera de este grupo responde 422 `CON-022`. `null` o ausente: sin restricción.
   */
  prefijoPermitido?: string | null;
}

/**
 * Selector de cuenta contable: combobox accesible (patrón ARIA combobox + listbox) que busca por código o
 * nombre entre las cuentas de detalle y activas (CLAUDE.md §10.1 CON-006). Se reutilizará en el Libro Diario (F3).
 * Teclado: escribir filtra, ↑/↓ mueven la opción activa, Enter elige y Escape cierra.
 * @param props ver {@link PropsSelectorCuenta}
 */
export function SelectorCuenta({
  id,
  cuentas,
  valor,
  onChange,
  valorActual = null,
  invalido,
  describedBy,
  disabled,
  placeholder = 'Busca por código o nombre',
  permitirQuitar,
  ordenPreferido,
  prefijoPermitido,
}: PropsSelectorCuenta) {
  const idLista = useId();
  const [abierto, setAbierto] = useState(false);
  const [consulta, setConsulta] = useState('');
  const [indice, setIndice] = useState(0);

  // 1. Solo las cuentas de detalle, activas y (si aplica) dentro del prefijo permitido (ADR-042, CON-022);
  //    se ordenan por código, salvo las de `ordenPreferido` (cuentas recientes), que van primero
  const elegibles = useMemo(() => {
    const activas = cuentas.filter(
      (c) => c.aceptaMovimientos && c.activa && (!prefijoPermitido || c.codigo.startsWith(prefijoPermitido)),
    );
    const porCodigo = [...activas].sort((a, b) => (a.codigo < b.codigo ? -1 : a.codigo > b.codigo ? 1 : 0));
    if (!ordenPreferido?.length) return porCodigo;
    const preferidas = ordenPreferido
      .map((id) => activas.find((c) => c.id === id))
      .filter((c): c is CuentaContable => c !== undefined);
    const idsPreferidos = new Set(preferidas.map((c) => c.id));
    return [...preferidas, ...porCodigo.filter((c) => !idsPreferidos.has(c.id))];
  }, [cuentas, ordenPreferido, prefijoPermitido]);

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
  const seleccionada =
    cuentas.find((c) => c.id === valor) ?? (valorActual?.id === valor ? valorActual : null);
  const etiqueta = seleccionada ? `${seleccionada.codigo} — ${seleccionada.nombre}` : '';

  /** Elige una cuenta y cierra la lista. */
  const elegir = (cuenta: CuentaContable) => {
    onChange(cuenta.id);
    setAbierto(false);
    setConsulta('');
  };

  /** Teclado del combobox (WAI-ARIA Authoring Practices). */
  const alPulsar = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!abierto) setAbierto(true);
      else setIndice(Math.min(activa + 1, opciones.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setIndice(Math.max(activa - 1, 0));
    } else if (e.key === 'Enter' && abierto) {
      // Enter elige la opción activa y no envía el formulario
      e.preventDefault();
      const opcion = opciones[activa];
      if (opcion) elegir(opcion);
    } else if (e.key === 'Escape' && abierto) {
      // Escape cierra solo la lista; no debe cerrar un diálogo que la contenga
      e.stopPropagation();
      setAbierto(false);
      setConsulta('');
    }
  };

  return (
    <div className="relative flex items-center gap-2">
      <div className="relative flex-1">
        <Input
          id={id}
          role="combobox"
          aria-expanded={abierto}
          aria-controls={idLista}
          aria-autocomplete="list"
          aria-activedescendant={abierto && opciones.length > 0 ? `${idLista}-${activa}` : undefined}
          aria-invalid={invalido ? true : undefined}
          aria-describedby={describedBy}
          autoComplete="off"
          disabled={disabled}
          placeholder={placeholder}
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
            aria-label="Cuentas de detalle activas"
            className="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-[var(--radius-panel)] border border-[var(--color-borde)] bg-[var(--color-superficie)] py-1 text-sm shadow-md"
          >
            {opciones.length === 0 && (
              <li
                role="option"
                aria-selected={false}
                aria-disabled="true"
                className="px-3 py-1.5 text-[var(--color-texto-suave)]"
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
                // mousedown no debe quitar el foco al input, o la lista se cerraría antes del clic
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => elegir(c)}
                className={`cursor-pointer px-3 py-1.5 ${i === activa ? 'bg-[var(--color-primario-suave)]' : ''}`}
              >
                {/* "código — nombre" (no el componente CodigoCuenta, sin separador): lo consumen catalogo/
                    configuracion/reglas (fuera de esta tarea) con pruebas que fijan este texto exacto */}
                <span className="codigo-cuenta cifra text-[var(--color-texto-suave)]">{c.codigo}</span> —{' '}
                {c.nombre}
              </li>
            ))}
          </ul>
        )}
      </div>
      {permitirQuitar && valor && !disabled && (
        <Button size="sm" variant="ghost" aria-label="Quitar cuenta" onClick={() => onChange(null)}>
          Quitar
        </Button>
      )}
    </div>
  );
}
