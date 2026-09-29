import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  actualizarConfiguracionContable,
  getObtenerConfiguracionContableQueryKey,
  useObtenerConfiguracionContable,
} from '@/api/configuracion-contable/configuracion-contable';
import type { ConfiguracionContable, ModoPrecio } from '@/api/modelos';
import { CodigoCuenta } from '@/compartido/dominio/CodigoCuenta';
import { ValorBloqueado } from '@/compartido/dominio/ValorBloqueado';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Label } from '@/compartido/ui/label';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { AvisoConflicto } from '../compartido/AvisoConflicto';
import { mensajeContabilidad } from '../mensajesContabilidad';
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

/** Motivo del candado de las cuentas de IVA (ADR-042: fijas desde la plantilla, nunca editables). */
const MOTIVO_IVA_FIJA = 'Cuentas fijas del catálogo base';

/**
 * Pestaña "Contabilidad" de Configuración (`/configuracion/contabilidad`): solo el modo de precio por
 * defecto es editable (CLAUDE.md §11.3, ADR-042); las cuentas de IVA débito y crédito fiscal son fijas
 * desde la plantilla y se muestran con `ValorBloqueado`. Quien escribe ve el formulario del modo de
 * precio (`PUT` completo con `If-Match`); el `auditor` ve solo los valores.
 */
export function PaginaConfiguracion() {
  const { puedeEscribir } = usePermisosContabilidad();
  const clienteConsultas = useQueryClient();
  const consulta = useObtenerConfiguracionContable();
  const configuracion = consulta.data?.data as ConfiguracionContable | undefined;
  // El ETag de la versión leída vive en las cabeceras de la respuesta cacheada
  const etag = consulta.data?.headers.get('ETag') ?? null;

  // `values` sincroniza el formulario con la configuración vigente cada vez que se (re)lee
  const formulario = useForm<ValoresConfiguracion>({
    resolver: zodResolver(esquemaConfiguracion),
    values: { modoPrecioDefecto: configuracion?.modoPrecioDefecto ?? 'CON_IVA' },
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
  });

  // 404 PLT-017 al leer: la empresa no tiene fila de configuración (sin precarga); no es un fallo de red
  const sinConfiguracion = esErrorApi(consulta.error, 'PLT-017') && consulta.error.status === 404;
  const conflicto = esErrorApi(guardar.error, 'PLT-016');
  const errorGeneral = guardar.isError && !conflicto ? mensajeContabilidad(guardar.error) : null;

  return (
    <section aria-labelledby="titulo-configuracion" className="max-w-2xl space-y-4">
      <h2 id="titulo-configuracion" className="text-xl font-semibold">
        Configuración contable
      </h2>
      <p className="text-sm text-[var(--color-texto-suave)]">{AVISO_CAMBIOS}</p>

      {consulta.isPending && (
        <p role="status" className="text-sm text-[var(--color-texto-suave)]">
          Cargando…
        </p>
      )}
      {sinConfiguracion && (
        <Alert variant="error">
          Este espacio de trabajo no tiene configuración contable. Contacta al soporte de Pilot.
        </Alert>
      )}
      {/* Los demás fallos (red, 5xx) conservan el mensaje genérico */}
      {!sinConfiguracion && consulta.isError && (
        <Alert variant="error">No pudimos cargar la configuración contable.</Alert>
      )}

      {configuracion && (
        <dl className="space-y-2 text-sm">
          <div>
            <dt className="font-medium">Cuenta de IVA débito fiscal</dt>
            <dd>
              <ValorBloqueado
                valor={
                  <CodigoCuenta
                    codigo={configuracion.cuentaIvaDebito.codigo}
                    nombre={configuracion.cuentaIvaDebito.nombre}
                  />
                }
                motivo={MOTIVO_IVA_FIJA}
              />
            </dd>
          </div>
          <div>
            <dt className="font-medium">Cuenta de IVA crédito fiscal</dt>
            <dd>
              <ValorBloqueado
                valor={
                  <CodigoCuenta
                    codigo={configuracion.cuentaIvaCredito.codigo}
                    nombre={configuracion.cuentaIvaCredito.nombre}
                  />
                }
                motivo={MOTIVO_IVA_FIJA}
              />
            </dd>
          </div>
        </dl>
      )}

      {/* Solo lectura (auditor): el modo de precio sin controles */}
      {configuracion && !puedeEscribir && (
        <dl className="text-sm">
          <div>
            <dt className="font-medium">Modo de precio</dt>
            <dd>{ETIQUETA_MODO[configuracion.modoPrecioDefecto]}</dd>
          </div>
        </dl>
      )}

      {/* Edición (contador y admin_empresa): solo el modo de precio */}
      {configuracion && puedeEscribir && (
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
