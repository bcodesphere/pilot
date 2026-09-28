import { Link } from 'react-router-dom';
import type { RubroEstado } from '@/api/modelos';
import { formatearMonedaConSigno } from '@/compartido/dinero';

/** Propiedades de la tabla de un rubro de un estado financiero. */
export interface PropsTablaRubro {
  rubro: RubroEstado;
  /** Período a propagar al enlace de cada cuenta hacia el Mayor (`desde`/`hasta` o solo `hasta` en un corte). */
  parametrosMayor: (cuentaId: string) => string;
}

/**
 * Tabla de un rubro de un estado financiero (CLAUDE.md §10.4): sus filas ya vienen jerárquicas y con
 * el nivel resuelto por el backend, aquí solo se indenta por nivel y se formatea el monto. Cada cuenta
 * enlaza al Mayor con el mismo período.
 * @param props ver {@link PropsTablaRubro}
 */
export function TablaRubro({ rubro, parametrosMayor }: PropsTablaRubro) {
  return (
    <table aria-label={rubro.nombre} className="w-full text-sm">
      <caption className="mb-1 text-left text-base font-semibold">{rubro.nombre}</caption>
      <thead>
        <tr className="border-b border-neutral-300 text-left">
          <th scope="col" className="py-1 pr-2">
            Cuenta
          </th>
          <th scope="col" className="py-1 pl-2 text-right">
            Monto
          </th>
        </tr>
      </thead>
      <tbody>
        {rubro.filas.length === 0 && (
          <tr>
            <td colSpan={2} className="py-2 text-neutral-600">
              Sin cuentas con movimiento en este rubro.
            </td>
          </tr>
        )}
        {rubro.filas.map((fila) => (
          <tr key={fila.cuenta.id} className="border-b border-neutral-100">
            <td className="py-1 pr-2" style={{ paddingLeft: `${(fila.nivel - 1) * 1}rem` }}>
              <Link
                to={`/contabilidad/mayor?cuentaId=${fila.cuenta.id}&${parametrosMayor(fila.cuenta.id)}`}
                className="underline"
              >
                <span className="font-mono">{fila.cuenta.codigo}</span> — {fila.cuenta.nombre}
              </Link>
            </td>
            <td className="py-1 pl-2 text-right tabular-nums">{formatearMonedaConSigno(fila.monto)}</td>
          </tr>
        ))}
        <tr className="border-t-2 border-neutral-400 font-medium">
          <td className="py-1 pr-2">Total {rubro.nombre.toLowerCase()}</td>
          <td className="py-1 pl-2 text-right tabular-nums">{formatearMonedaConSigno(rubro.total)}</td>
        </tr>
      </tbody>
    </table>
  );
}
