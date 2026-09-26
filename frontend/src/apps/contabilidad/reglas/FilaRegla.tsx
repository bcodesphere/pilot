import { useMutation } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { actualizarReglaContabilizacion } from '@/api/reglas-contabilizacion/reglas-contabilizacion';
import type { CuentaContable, ReglaContabilizacion } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { Badge } from '@/compartido/ui/badge';
import { Button } from '@/compartido/ui/button';
import { Switch } from '@/compartido/ui/switch';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { AvisoConflicto } from '../compartido/AvisoConflicto';
import { SelectorCuenta } from '../compartido/SelectorCuenta';
import { campoDeError, mensajeContabilidad } from '../mensajesContabilidad';
import { esquemaRegla } from './esquemaRegla';
import { etiquetaRegla } from './etiquetasRegla';

/** Propiedades de una fila de regla. */
interface Props {
  regla: ReglaContabilizacion;
  /** Catálogo para el selector; indefinido mientras carga. */
  cuentas: readonly CuentaContable[] | undefined;
  /** Con permiso de escritura la fila es editable; sin él (auditor) solo se muestra. */
  puedeEscribir: boolean;
  /** Vuelve a leer la lista de reglas (tras guardar o al recargar un conflicto). */
  onRecargar: () => Promise<unknown> | void;
}

/**
 * Fila de una regla de contabilización: código con su etiqueta, cuenta y estado. Con permiso permite elegir
 * la cuenta e interruptor "Activa" y guardar con `PUT` + `If-Match: "<version>"` (CLAUDE.md §8.3).
 * Activar sin cuenta no envía nada (esquema Zod); `CON-006` se muestra junto al campo de cuenta.
 */
export function FilaRegla({ regla, cuentas, puedeEscribir, onRecargar }: Props) {
  const idBase = useId();
  const etiqueta = etiquetaRegla(regla.codigo);
  const estadoServidor = { cuentaId: regla.cuenta?.id ?? null, activa: regla.activa };
  // Borrador local de la fila; la lista la monta con `key` = id + versión, así se descarta al guardar o recargar
  const [borrador, setBorrador] = useState(estadoServidor);
  const [errorCuenta, setErrorCuenta] = useState<string | null>(null);
  const cambiado = borrador.cuentaId !== estadoServidor.cuentaId || borrador.activa !== estadoServidor.activa;

  const guardar = useMutation({
    mutationFn: (v: { cuentaId: string | null; activa: boolean }) =>
      actualizarReglaContabilizacion(regla.id, v, { headers: { 'If-Match': `"${regla.version}"` } }),
    onSuccess: () => onRecargar(),
    onError: (error) => {
      if (campoDeError(error) === 'cuenta') setErrorCuenta(mensajeContabilidad(error));
    },
  });

  /** Valida con Zod y envía el `PUT` completo; si no cumple, muestra el error y no envía nada. */
  const alGuardar = () => {
    setErrorCuenta(null);
    const resultado = esquemaRegla.safeParse(borrador);
    if (!resultado.success) {
      setErrorCuenta(resultado.error.issues[0]?.message ?? 'Revisa los datos de la regla');
      return;
    }
    guardar.mutate(resultado.data);
  };

  const conflicto = esErrorApi(guardar.error, 'PLT-016');
  const errorGeneral =
    guardar.isError && !conflicto && campoDeError(guardar.error) !== 'cuenta'
      ? mensajeContabilidad(guardar.error)
      : null;

  // --- Solo lectura ---
  if (!puedeEscribir) {
    return (
      <li className="flex flex-wrap items-center gap-3 py-2">
        <span className="w-40 text-sm font-medium">{etiqueta}</span>
        <span className="text-sm">
          {regla.cuenta ? `${regla.cuenta.codigo} — ${regla.cuenta.nombre}` : 'Sin cuenta'}
        </span>
        <Badge variant={regla.activa ? 'success' : 'warning'}>{regla.activa ? 'Activa' : 'Inactiva'}</Badge>
      </li>
    );
  }

  // --- Edición ---
  return (
    <li className="space-y-1 py-2">
      <div className="flex flex-wrap items-center gap-3">
        <span id={`${idBase}-etiqueta`} className="w-40 text-sm font-medium">
          {etiqueta}
        </span>
        <div className="min-w-64 flex-1">
          {cuentas ? (
            <SelectorCuenta
              id={`${idBase}-cuenta`}
              cuentas={cuentas}
              valor={borrador.cuentaId}
              valorActual={regla.cuenta}
              onChange={(cuentaId) => {
                setErrorCuenta(null);
                // Una regla sin cuenta no puede quedar activa (ADR-035)
                setBorrador((b) => ({ cuentaId, activa: cuentaId ? b.activa : false }));
              }}
              invalido={!!errorCuenta}
              describedBy={`${idBase}-error`}
              placeholder="Sin cuenta"
              permitirQuitar
            />
          ) : (
            <span className="text-sm text-neutral-600">Cargando cuentas…</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Switch
            aria-label={`Regla activa: ${etiqueta}`}
            checked={borrador.activa}
            onCheckedChange={(activa) => {
              setErrorCuenta(null);
              setBorrador((b) => ({ ...b, activa }));
            }}
          />
          <span className="text-sm">{borrador.activa ? 'Activa' : 'Inactiva'}</span>
        </div>
        <Button
          size="sm"
          disabled={!cambiado || guardar.isPending}
          onClick={alGuardar}
          aria-label={`Guardar ${etiqueta}`}
        >
          {guardar.isPending ? 'Guardando…' : 'Guardar'}
        </Button>
      </div>
      <p id={`${idBase}-error`} role="alert" className="min-h-4 text-sm text-red-700">
        {errorCuenta}
      </p>
      {conflicto && (
        <AvisoConflicto
          onRecargar={() => {
            guardar.reset();
            // La versión nueva remonta la fila con los valores vigentes del servidor
            void onRecargar();
          }}
        />
      )}
      {errorGeneral && <Alert variant="error">{errorGeneral}</Alert>}
    </li>
  );
}
