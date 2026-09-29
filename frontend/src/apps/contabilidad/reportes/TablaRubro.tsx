import { Link } from 'react-router-dom';
import type { RubroEstado } from '@/api/modelos';
import { CodigoCuenta } from '@/compartido/dominio/CodigoCuenta';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { Monto } from '@/compartido/dominio/Monto';
import { TablaContable, type ColumnaContable } from '@/compartido/dominio/TablaContable';
import { TableCell, TableRow } from '@/compartido/ui/table';

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
  const columnas: ColumnaContable<RubroEstado['filas'][number]>[] = [
    {
      clave: 'cuenta',
      encabezado: 'Cuenta',
      celda: (fila) => (
        <span className="inline-block" style={{ paddingLeft: `${(fila.nivel - 1) * 1}rem` }}>
          <Link
            to={`/contabilidad/mayor?cuentaId=${fila.cuenta.id}&${parametrosMayor(fila.cuenta.id)}`}
            className="hover:underline"
          >
            <CodigoCuenta codigo={fila.cuenta.codigo} nombre={fila.cuenta.nombre} />
          </Link>
        </span>
      ),
    },
    {
      clave: 'monto',
      encabezado: 'Monto',
      alineacion: 'derecha',
      celda: (fila) => <Monto valor={fila.monto} />,
    },
  ];

  return (
    <div className="space-y-1">
      <h3 className="text-base font-semibold text-[var(--color-texto)]">{rubro.nombre}</h3>
      <TablaContable
        columnas={columnas}
        filas={rubro.filas}
        cargando={false}
        obtenerClave={(fila) => fila.cuenta.id}
        vacio={
          <EstadoVacio
            titulo="Sin cuentas con movimiento"
            descripcion={`Ninguna cuenta de «${rubro.nombre.toLowerCase()}» tuvo movimiento en este período.`}
          />
        }
        totales={
          <TableRow>
            <TableCell>Total {rubro.nombre.toLowerCase()}</TableCell>
            <TableCell className="text-right">
              <Monto valor={rubro.total} />
            </TableCell>
          </TableRow>
        }
      />
    </div>
  );
}
