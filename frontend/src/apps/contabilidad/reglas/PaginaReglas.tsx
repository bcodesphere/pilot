import { useQueryClient } from '@tanstack/react-query';
import {
  getListarReglasContabilizacionQueryKey,
  useListarReglasContabilizacion,
} from '@/api/reglas-contabilizacion/reglas-contabilizacion';
import type { CategoriaRegla, ReglaContabilizacion } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { useCuentas } from '../compartido/useCuentas';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { FilaRegla } from './FilaRegla';
import { reglasDeCategoria } from './etiquetasRegla';

/** Parámetros de la consulta: en 1.0 solo existe el cierre de ingresos diarios (ADR-017). */
const PARAMETROS = { tipoOperacion: 'CIERRE_INGRESOS_DIARIO' } as const;

/** Grupos en que se presentan las reglas (CLAUDE.md §9.3: categorías `INGRESO` y `COBRO`). */
const GRUPOS: { categoria: CategoriaRegla; titulo: string }[] = [
  { categoria: 'INGRESO', titulo: 'Conceptos de ingreso' },
  { categoria: 'COBRO', titulo: 'Formas de pago' },
];

/**
 * Pantalla "Reglas" (`/contabilidad/reglas`): a qué cuenta va cada concepto de ingreso y cada forma de pago
 * de un cierre de ingresos diario (ADR-020). Quien escribe edita por fila; el `auditor` solo las ve.
 */
export function PaginaReglas() {
  const { puedeEscribir } = usePermisosContabilidad();
  const clienteConsultas = useQueryClient();
  const consulta = useListarReglasContabilizacion(PARAMETROS);
  const { cuentas } = useCuentas();
  const reglas = consulta.data?.data as ReglaContabilizacion[] | undefined;

  /** Vuelve a leer las reglas (tras guardar o al resolver un conflicto de versión). */
  const recargar = () =>
    clienteConsultas.invalidateQueries({ queryKey: getListarReglasContabilizacionQueryKey(PARAMETROS) });

  // La regla OTRO inactiva rechaza los cierres que la usen hasta que se le asigne cuenta (ADR-035)
  // Consulta exitosa sin ninguna regla: la empresa no tiene precarga
  const sinReglas = reglas?.length === 0;
  const otroInactiva = reglas?.some((r) => r.codigo === 'OTRO' && !r.activa) ?? false;

  return (
    <section aria-labelledby="titulo-reglas" className="max-w-4xl space-y-4">
      <h2 id="titulo-reglas" className="text-xl font-semibold">
        Reglas de contabilización
      </h2>
      <p className="text-sm text-neutral-600">
        Cuenta que usa cada concepto y forma de pago cuando llega un cierre de ingresos diario.
      </p>

      {consulta.isPending && (
        <p role="status" className="text-sm text-neutral-600">
          Cargando…
        </p>
      )}
      {consulta.isError && <Alert variant="error">No pudimos cargar las reglas de contabilización.</Alert>}

      {sinReglas && (
        <p role="status" className="text-sm text-neutral-600">
          No hay reglas de contabilización configuradas. Contacta al soporte de Pilot.
        </p>
      )}

      {otroInactiva && (
        <Alert variant="warning">
          Los cierres que usen &quot;Otro&quot; se rechazarán hasta que le asignes una cuenta
        </Alert>
      )}

      {reglas &&
        !sinReglas &&
        GRUPOS.map(({ categoria, titulo }) => (
          <section key={categoria} aria-labelledby={`grupo-${categoria}`} className="space-y-1">
            <h3 id={`grupo-${categoria}`} className="text-base font-semibold">
              {titulo}
            </h3>
            <ul className="divide-y divide-neutral-200">
              {reglasDeCategoria(reglas, categoria).map((regla) => (
                <FilaRegla
                  key={`${regla.id}-${regla.version}`}
                  regla={regla}
                  cuentas={cuentas}
                  puedeEscribir={puedeEscribir}
                  onRecargar={recargar}
                />
              ))}
            </ul>
          </section>
        ))}
    </section>
  );
}
