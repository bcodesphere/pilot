import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import {
  actualizarConfiguracionContable,
  getObtenerConfiguracionContableQueryKey,
  useObtenerConfiguracionContable,
} from '@/api/configuracion-contable/configuracion-contable';
import type { ConfiguracionContable, ModoPrecio } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Label } from '@/compartido/ui/label';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { AvisoConflicto } from '../compartido/AvisoConflicto';
import { SelectorCuenta } from '../compartido/SelectorCuenta';
import { useCuentas } from '../compartido/useCuentas';
import { campoDeError, mensajeContabilidad } from '../mensajesContabilidad';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { esquemaConfiguracion, type ValoresConfiguracion } from './esquemaConfiguracion';

/** Respuesta con cuerpo y cabeceras, tal como la guarda la caché de la consulta de configuración. */
type RespuestaConfiguracion = { data: ConfiguracionContable; status: number; headers: Headers };

/** Etiqueta en español de cada modo de precio (ADR-015). */
const ETIQUETA_MODO: Record<ModoPrecio, string> = {
  CON_IVA: 'Precios con IVA incluido',
  SIN_IVA: 'Precios más IVA',
};

/** Aviso permanente: la configuración solo afecta a los asientos futuros y queda auditada (CLAUDE.md §11.3). */
const AVISO_CAMBIOS = 'Los cambios aplican a los asientos que se registren después; quedan en la auditoría.';

/**
 * Pantalla "Configuración" (`/contabilidad/configuracion`): modo de precio por defecto y cuentas de IVA
 * débito y crédito (CLAUDE.md §11.3). Quien escribe ve un formulario (`PUT` completo con `If-Match`);
 * el `auditor` ve solo los valores.
 */
export function PaginaConfiguracion() {
  const { puedeEscribir } = usePermisosContabilidad();
  const clienteConsultas = useQueryClient();
  const consulta = useObtenerConfiguracionContable();
  const { cuentas, consulta: consultaCuentas } = useCuentas();
  const configuracion = consulta.data?.data as ConfiguracionContable | undefined;
  // El ETag de la versión leída vive en las cabeceras de la respuesta cacheada
  const etag = consulta.data?.headers.get('ETag') ?? null;

  // `values` sincroniza el formulario con la configuración vigente cada vez que se (re)lee
  const formulario = useForm<ValoresConfiguracion>({
    resolver: zodResolver(esquemaConfiguracion),
    values: {
      modoPrecioDefecto: configuracion?.modoPrecioDefecto ?? 'CON_IVA',
      cuentaIvaDebitoId: configuracion?.cuentaIvaDebito.id ?? '',
      cuentaIvaCreditoId: configuracion?.cuentaIvaCredito.id ?? '',
    },
  });

  const guardar = useMutation({
    mutationFn: (v: ValoresConfiguracion) =>
      actualizarConfiguracionContable(v, { headers: { 'If-Match': etag ?? '' } }),
    onSuccess: async (respuesta) => {
      // 1. El PUT devuelve la configuración con su ETag nuevo: reemplaza la lectura cacheada
      clienteConsultas.setQueryData(
        getObtenerConfiguracionContableQueryKey(),
        respuesta as RespuestaConfiguracion,
      );
      // 2. Invalida para confirmar contra el servidor
      await clienteConsultas.invalidateQueries({ queryKey: getObtenerConfiguracionContableQueryKey() });
    },
    onError: (error) => {
      // CON-006 (cuenta inexistente, inactiva o no de detalle) va junto al campo de cuenta
      if (campoDeError(error) !== 'cuenta') return;
      const mensaje = mensajeContabilidad(error);
      // Si el servidor indica el campo se usa; si no, se marcan ambas cuentas de IVA
      const campos = (esErrorApi(error) ? error.errores : [])
        .map((e) => e.campo)
        .filter(
          (c): c is 'cuentaIvaDebitoId' | 'cuentaIvaCreditoId' =>
            c === 'cuentaIvaDebitoId' || c === 'cuentaIvaCreditoId',
        );
      (campos.length > 0 ? campos : (['cuentaIvaDebitoId', 'cuentaIvaCreditoId'] as const)).forEach((c) =>
        formulario.setError(c, { message: mensaje }),
      );
    },
  });

  const conflicto = esErrorApi(guardar.error, 'PLT-016');
  const errorGeneral =
    guardar.isError && !conflicto && campoDeError(guardar.error) !== 'cuenta'
      ? mensajeContabilidad(guardar.error)
      : null;
  const errores = formulario.formState.errors;

  return (
    <section aria-labelledby="titulo-configuracion" className="max-w-2xl space-y-4">
      <h2 id="titulo-configuracion" className="text-xl font-semibold">
        Configuración contable
      </h2>
      <p className="text-sm text-neutral-600">{AVISO_CAMBIOS}</p>

      {(consulta.isPending || consultaCuentas.isPending) && (
        <p role="status" className="text-sm text-neutral-600">
          Cargando…
        </p>
      )}
      {(consulta.isError || consultaCuentas.isError) && (
        <Alert variant="error">No pudimos cargar la configuración contable.</Alert>
      )}

      {/* Solo lectura (auditor): valores actuales sin controles */}
      {configuracion && !puedeEscribir && (
        <dl className="space-y-2 text-sm">
          <div>
            <dt className="font-medium">Modo de precio</dt>
            <dd>{ETIQUETA_MODO[configuracion.modoPrecioDefecto]}</dd>
          </div>
          <div>
            <dt className="font-medium">Cuenta de IVA débito fiscal</dt>
            <dd>
              {configuracion.cuentaIvaDebito.codigo} — {configuracion.cuentaIvaDebito.nombre}
            </dd>
          </div>
          <div>
            <dt className="font-medium">Cuenta de IVA crédito fiscal</dt>
            <dd>
              {configuracion.cuentaIvaCredito.codigo} — {configuracion.cuentaIvaCredito.nombre}
            </dd>
          </div>
        </dl>
      )}

      {/* Edición (contador y admin_empresa) */}
      {configuracion && cuentas && puedeEscribir && (
        <form noValidate className="space-y-4" onSubmit={formulario.handleSubmit((v) => guardar.mutate(v))}>
          <fieldset className="space-y-1">
            <legend className="text-sm font-medium">Modo de precio por defecto</legend>
            {(Object.keys(ETIQUETA_MODO) as ModoPrecio[]).map((modo) => (
              <div key={modo} className="flex items-center gap-2">
                <input
                  id={`modo-${modo}`}
                  type="radio"
                  value={modo}
                  {...formulario.register('modoPrecioDefecto')}
                />
                <Label htmlFor={`modo-${modo}`}>{ETIQUETA_MODO[modo]}</Label>
              </div>
            ))}
          </fieldset>

          <div className="space-y-1">
            <Label htmlFor="config-iva-debito">Cuenta de IVA débito fiscal</Label>
            <Controller
              control={formulario.control}
              name="cuentaIvaDebitoId"
              render={({ field }) => (
                <SelectorCuenta
                  id="config-iva-debito"
                  cuentas={cuentas}
                  valor={field.value || null}
                  valorActual={configuracion.cuentaIvaDebito}
                  onChange={(id) => field.onChange(id ?? '')}
                  invalido={!!errores.cuentaIvaDebitoId}
                  describedBy="config-iva-debito-error"
                />
              )}
            />
            <p id="config-iva-debito-error" role="alert" className="min-h-4 text-sm text-red-700">
              {errores.cuentaIvaDebitoId?.message}
            </p>
          </div>

          <div className="space-y-1">
            <Label htmlFor="config-iva-credito">Cuenta de IVA crédito fiscal</Label>
            <Controller
              control={formulario.control}
              name="cuentaIvaCreditoId"
              render={({ field }) => (
                <SelectorCuenta
                  id="config-iva-credito"
                  cuentas={cuentas}
                  valor={field.value || null}
                  valorActual={configuracion.cuentaIvaCredito}
                  onChange={(id) => field.onChange(id ?? '')}
                  invalido={!!errores.cuentaIvaCreditoId}
                  describedBy="config-iva-credito-error"
                />
              )}
            />
            <p id="config-iva-credito-error" role="alert" className="min-h-4 text-sm text-red-700">
              {errores.cuentaIvaCreditoId?.message}
            </p>
          </div>

          {conflicto && (
            <AvisoConflicto
              onRecargar={() => {
                guardar.reset();
                void consulta.refetch();
              }}
            />
          )}
          {errorGeneral && <Alert variant="error">{errorGeneral}</Alert>}
          {guardar.isSuccess && <Alert variant="success">Configuración guardada</Alert>}

          <Button type="submit" disabled={guardar.isPending || !etag}>
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </Button>
        </form>
      )}
    </section>
  );
}
