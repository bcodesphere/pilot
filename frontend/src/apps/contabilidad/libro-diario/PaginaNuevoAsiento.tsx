import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { registrarAsiento } from '@/api/asientos/asientos';
import { useObtenerConfiguracionContable } from '@/api/configuracion-contable/configuracion-contable';
import type { Asiento, ConfiguracionContable, CuentaContable, NuevoAsiento } from '@/api/modelos';
import { formatearMoneda, formatearMonedaConSigno } from '@/compartido/dinero';
import { hoyElSalvador } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { Select } from '@/compartido/ui/select';
import { SelectorCuenta } from '../compartido/SelectorCuenta';
import { useCuentas } from '../compartido/useCuentas';
import { usePermisosContabilidad } from '../usePermisosContabilidad';
import { distribuirError } from './erroresAsiento';
import {
  construirNuevoAsiento,
  crearEsquemaAsiento,
  LINEA_VACIA,
  MAX_LINEAS,
  totalesLocales,
  type ValoresAsiento,
} from './esquemaAsiento';
import { filasDeVistaPrevia, invalidarLibroDiario, numeroAsiento } from './etiquetas';
import { TablaLineas } from './presentacion';
import { useClaveIdempotencia } from './useClaveIdempotencia';
import { useVistaPrevia } from './useVistaPrevia';

/**
 * Pantalla "Nuevo asiento" (`/contabilidad/libro-diario/nuevo`, rol contador): carga el catálogo y la
 * configuración contable y monta el formulario cuando ya conoce el modo de precio por defecto y las cuentas
 * de IVA. Sin permiso de escritura muestra un aviso (el backend responde 403 `PLT-010` de todos modos).
 */
export function PaginaNuevoAsiento() {
  const { puedeEscribir } = usePermisosContabilidad();
  const { cuentas, consulta: consultaCuentas } = useCuentas();
  const consultaConfiguracion = useObtenerConfiguracionContable();
  const configuracion = consultaConfiguracion.data?.data as ConfiguracionContable | undefined;

  if (!puedeEscribir) return <Alert variant="error">No tienes permiso para registrar asientos.</Alert>;
  if (consultaCuentas.isError)
    return <Alert variant="error">No pudimos cargar el catálogo de cuentas.</Alert>;
  if (!cuentas || consultaConfiguracion.isPending) {
    return (
      <p role="status" className="text-sm text-neutral-600">
        Cargando…
      </p>
    );
  }

  return (
    <section aria-labelledby="titulo-nuevo-asiento" className="max-w-5xl space-y-4">
      <h2 id="titulo-nuevo-asiento" className="text-xl font-semibold">
        Nuevo asiento
      </h2>
      {/* Sin configuración no se conocen las cuentas de IVA: se advierte y el backend valida CON-013 */}
      {consultaConfiguracion.isError && (
        <Alert variant="warning">
          No pudimos cargar la configuración contable; se usa el modo &quot;Precios con IVA incluido&quot; y
          el sistema validará las cuentas de IVA al guardar.
        </Alert>
      )}
      <FormularioAsiento
        cuentas={cuentas}
        modoDefecto={configuracion?.modoPrecioDefecto ?? 'CON_IVA'}
        idsCuentasIva={
          configuracion ? [configuracion.cuentaIvaDebito.id, configuracion.cuentaIvaCredito.id] : []
        }
      />
    </section>
  );
}

/** Propiedades del formulario. */
interface PropsFormulario {
  /** Catálogo completo (el selector filtra las cuentas de detalle activas). */
  cuentas: CuentaContable[];
  /** Modo de precio de la configuración contable (ADR-015). */
  modoDefecto: 'CON_IVA' | 'SIN_IVA';
  /** Cuentas de IVA de la configuración, sobre las que no se permite "lleva IVA" (CON-013). */
  idsCuentasIva: string[];
}

/**
 * Formulario del Libro Diario (CLAUDE.md §10.1): cabecera, líneas dinámicas, totales en vivo, vista previa del
 * backend cuando alguna línea lleva IVA y botón Guardar que solo se habilita cuando el asiento cuadra.
 *
 * - Sin líneas con IVA valida en local con decimal.js. Con IVA valida sobre las líneas expandidas que devuelve
 *   el backend en la vista previa, porque en modo `SIN_IVA` el IVA cambia los totales (ADR-036); el frontend
 *   nunca calcula IVA (regla 1.2.1).
 * - Guardar envía `Idempotency-Key`: la misma clave para reintentos del mismo cuerpo y otra si el cuerpo cambia
 *   (ver {@link useClaveIdempotencia}).
 */
function FormularioAsiento({ cuentas, modoDefecto, idsCuentasIva }: PropsFormulario) {
  const navegar = useNavigate();
  const clienteConsultas = useQueryClient();
  // Fecha de hoy fijada al abrir el formulario: es el valor por defecto y el límite de CON-007
  const [hoy] = useState(() => hoyElSalvador());
  const esquema = useMemo(() => crearEsquemaAsiento({ hoy, idsCuentasIva }), [hoy, idsCuentasIva]);
  const obtenerClave = useClaveIdempotencia();

  const formulario = useForm<ValoresAsiento>({
    resolver: zodResolver(esquema),
    mode: 'onChange',
    defaultValues: {
      fecha: hoy,
      concepto: '',
      modoPrecio: modoDefecto,
      lineas: [{ ...LINEA_VACIA }, { ...LINEA_VACIA }],
    },
  });
  const { fields, append, remove } = useFieldArray({ control: formulario.control, name: 'lineas' });
  const errores = formulario.formState.errors;

  // 1. Valores en vivo del formulario y su análisis con el mismo esquema Zod que usa el resolver
  const valores = useWatch({ control: formulario.control }) as ValoresAsiento;
  const analisis = useMemo(() => esquema.safeParse(valores), [esquema, valores]);
  const hayIva = valores.lineas.some((l) => l.llevaIva);

  // 2. Cuerpo de la vista previa: solo con líneas con IVA y con el formulario válido en su forma
  const cuerpoVistaPrevia: NuevoAsiento | null = useMemo(
    () => (hayIva && analisis.success ? construirNuevoAsiento(analisis.data) : null),
    [hayIva, analisis],
  );
  const previa = useVistaPrevia(cuerpoVistaPrevia);

  // 3. Totales mostrados: los del backend si hay IVA (líneas expandidas); los locales en caso contrario
  const local = totalesLocales(valores.lineas);
  const totales = hayIva
    ? previa.vista && {
        debe: previa.vista.totalDebe,
        haber: previa.vista.totalHaber,
        diferencia: previa.vista.diferencia,
      }
    : local;
  const cuadra = hayIva ? previa.vista?.cuadra === true : analisis.success;

  // 4. Guardado: una sola transacción en el backend; la clave de idempotencia depende del cuerpo
  const guardar = useMutation({
    mutationFn: (cuerpo: NuevoAsiento) =>
      registrarAsiento(cuerpo, { headers: { 'Idempotency-Key': obtenerClave(JSON.stringify(cuerpo)) } }),
    onSuccess: async (respuesta) => {
      const asiento = respuesta.data as Asiento;
      await invalidarLibroDiario(clienteConsultas);
      navegar(`/contabilidad/libro-diario/${asiento.id}`, {
        state: { aviso: `Asiento N.º ${numeroAsiento(asiento)} registrado` },
      });
    },
  });

  /** Guardar solo se habilita con el formulario válido, el asiento cuadrado y sin vista previa pendiente. */
  const puedeGuardar = analisis.success && cuadra && !previa.pendiente && !guardar.isPending;

  // 5. Errores del backend (guardado o vista previa) repartidos por línea y en general
  const deBackend = distribuirError(guardar.error ?? previa.error);

  /** Envía el asiento ya validado por el esquema (el botón solo se habilita si lo está). */
  const alEnviar = formulario.handleSubmit((v) => guardar.mutate(construirNuevoAsiento(v)));

  // 6. Mensaje del esquema por línea (CON-002, CON-003, CON-013), solo en las líneas que el usuario ya tocó
  //    (RHF solo informa el campo editado, y CON-002 se produce sobre "debe" aunque se edite "haber")
  const lineasTocadas = formulario.formState.dirtyFields.lineas;
  const mensajePorLinea = new Map<number, string[]>();
  if (!analisis.success) {
    for (const i of analisis.error.issues) {
      const [raiz, indice, campo] = i.path;
      if (raiz !== 'lineas' || typeof indice !== 'number' || campo === 'cuentaId') continue;
      if (lineasTocadas?.[indice])
        mensajePorLinea.set(indice, [...(mensajePorLinea.get(indice) ?? []), i.message]);
    }
  }

  // 7. Motivos por los que Guardar está bloqueado (CON-001, CON-002, CON-004…), sin repetir el descuadre
  const motivos: string[] = analisis.success
    ? []
    : analisis.error.issues
        .filter((i) => i.path.length === 1 && i.path[0] === 'lineas')
        .filter((i) => (i as { params?: { codigo?: string } }).params?.codigo !== 'CON-005')
        .map((i) => i.message);
  // Sin cuenta en alguna línea no se puede guardar; el motivo se explica una sola vez
  if (!analisis.success && analisis.error.issues.some((i) => i.path[2] === 'cuentaId')) {
    motivos.push('Elige la cuenta de cada línea.');
  }

  return (
    <form onSubmit={alEnviar} noValidate className="space-y-4">
      {/* Cabecera: fecha, concepto y modo de precio (este último solo si alguna línea lleva IVA) */}
      <div className="grid gap-3 sm:grid-cols-[10rem_1fr_14rem]">
        <div className="space-y-1">
          <Label htmlFor="asiento-fecha">Fecha</Label>
          <Input
            id="asiento-fecha"
            type="date"
            max={hoy}
            aria-invalid={errores.fecha ? true : undefined}
            aria-describedby={errores.fecha ? 'error-fecha' : undefined}
            {...formulario.register('fecha')}
          />
          {errores.fecha && (
            <p id="error-fecha" className="text-sm text-red-700">
              {errores.fecha.message}
            </p>
          )}
        </div>
        <div className="space-y-1">
          <Label htmlFor="asiento-concepto">Concepto</Label>
          <Input
            id="asiento-concepto"
            maxLength={500}
            aria-invalid={errores.concepto ? true : undefined}
            aria-describedby={errores.concepto ? 'error-concepto' : undefined}
            {...formulario.register('concepto')}
          />
          {errores.concepto && (
            <p id="error-concepto" className="text-sm text-red-700">
              {errores.concepto.message}
            </p>
          )}
        </div>
        {hayIva && (
          <div className="space-y-1">
            <Label htmlFor="asiento-modo">Modo de precio</Label>
            <Select id="asiento-modo" {...formulario.register('modoPrecio')}>
              <option value="CON_IVA">Precios con IVA incluido</option>
              <option value="SIN_IVA">Precios más IVA</option>
            </Select>
          </div>
        )}
      </div>

      {/* Líneas dinámicas: cuenta, descripción, Debe, Haber y "lleva IVA" */}
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium">Líneas del asiento</legend>
        {fields.map((campo, i) => {
          const errorEsquema = mensajePorLinea.get(i)?.join(' · ');
          const errorBackend = deBackend.porLinea.get(i);
          const idError = `error-linea-${i}`;
          return (
            <div key={campo.id} className="rounded-md border border-neutral-200 p-2">
              <div className="grid items-start gap-2 md:grid-cols-[minmax(0,2fr)_minmax(0,1.5fr)_7rem_7rem_auto_auto]">
                <Controller
                  control={formulario.control}
                  name={`lineas.${i}.cuentaId`}
                  render={({ field }) => (
                    <div>
                      <Label htmlFor={`linea-${i}-cuenta`} className="sr-only">
                        Cuenta de la línea {i + 1}
                      </Label>
                      <SelectorCuenta
                        id={`linea-${i}-cuenta`}
                        cuentas={cuentas}
                        valor={field.value || null}
                        onChange={(id) => field.onChange(id ?? '')}
                        invalido={Boolean(errorBackend)}
                        describedBy={errorBackend ? idError : undefined}
                      />
                    </div>
                  )}
                />
                <div>
                  <Label htmlFor={`linea-${i}-descripcion`} className="sr-only">
                    Descripción de la línea {i + 1}
                  </Label>
                  <Input
                    id={`linea-${i}-descripcion`}
                    placeholder="Descripción (opcional)"
                    maxLength={300}
                    {...formulario.register(`lineas.${i}.descripcion`)}
                  />
                </div>
                <div>
                  <Label htmlFor={`linea-${i}-debe`} className="sr-only">
                    Debe de la línea {i + 1}
                  </Label>
                  <Input
                    id={`linea-${i}-debe`}
                    inputMode="decimal"
                    placeholder="Debe"
                    className="text-right tabular-nums"
                    aria-invalid={errorEsquema ? true : undefined}
                    {...formulario.register(`lineas.${i}.debe`)}
                  />
                </div>
                <div>
                  <Label htmlFor={`linea-${i}-haber`} className="sr-only">
                    Haber de la línea {i + 1}
                  </Label>
                  <Input
                    id={`linea-${i}-haber`}
                    inputMode="decimal"
                    placeholder="Haber"
                    className="text-right tabular-nums"
                    aria-invalid={errorEsquema ? true : undefined}
                    {...formulario.register(`lineas.${i}.haber`)}
                  />
                </div>
                <label className="flex h-9 items-center gap-1 text-sm">
                  <input type="checkbox" {...formulario.register(`lineas.${i}.llevaIva`)} />
                  <span>
                    Lleva IVA<span className="sr-only"> de la línea {i + 1}</span>
                  </span>
                </label>
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label={`Quitar la línea ${i + 1}`}
                  disabled={fields.length <= 2}
                  onClick={() => remove(i)}
                >
                  Quitar
                </Button>
              </div>
              {/* Errores de la línea: los del esquema (CON-002, CON-003, CON-013) y los del backend (CON-006…) */}
              {(errorBackend ?? errorEsquema) && (
                <p id={idError} className="mt-1 text-sm text-red-700">
                  {errorBackend ?? errorEsquema}
                </p>
              )}
            </div>
          );
        })}
        <Button
          variant="outline"
          size="sm"
          disabled={fields.length >= MAX_LINEAS}
          onClick={() => append({ ...LINEA_VACIA })}
        >
          Agregar línea
        </Button>
      </fieldset>

      {/* Vista previa del backend: líneas expandidas con el IVA calculado (ADR-036) */}
      {hayIva && (
        <div className="space-y-2">
          <h3 className="text-sm font-medium">Vista previa con IVA</h3>
          {previa.pendiente && (
            <p role="status" className="text-sm text-neutral-600">
              Calculando el IVA…
            </p>
          )}
          {!previa.pendiente && !cuerpoVistaPrevia && (
            <p className="text-sm text-neutral-600">
              Completa las líneas para ver el asiento con el IVA calculado.
            </p>
          )}
          {previa.vista && !previa.pendiente && (
            <TablaLineas
              leyenda="Líneas del asiento con IVA"
              filas={filasDeVistaPrevia(previa.vista.lineas)}
            />
          )}
        </div>
      )}

      {/* Totales en vivo y diferencia; se anuncian a lectores de pantalla al cambiar */}
      <div aria-live="polite" className="space-y-1 rounded-md bg-neutral-50 p-3 text-sm">
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 sm:grid-cols-[auto_auto_auto_auto_auto_auto] sm:justify-start">
          <dt>Total Debe</dt>
          <dd className="tabular-nums">{totales ? formatearMoneda(totales.debe) : '—'}</dd>
          <dt>Total Haber</dt>
          <dd className="tabular-nums">{totales ? formatearMoneda(totales.haber) : '—'}</dd>
          <dt>Diferencia</dt>
          <dd className="tabular-nums">{totales ? formatearMonedaConSigno(totales.diferencia) : '—'}</dd>
        </dl>
        {totales && !cuadra && !previa.pendiente && (
          <p className="text-red-700">
            El asiento no cuadra: la diferencia es {formatearMonedaConSigno(totales.diferencia)}.
          </p>
        )}
        {motivos.map((m) => (
          <p key={m} className="text-red-700">
            {m}
          </p>
        ))}
      </div>

      {deBackend.general && <Alert variant="error">{deBackend.general}</Alert>}

      <div className="flex gap-2">
        <Button type="submit" disabled={!puedeGuardar}>
          {guardar.isPending ? 'Guardando…' : 'Guardar'}
        </Button>
        <Button variant="outline" onClick={() => navegar('/contabilidad/libro-diario')}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
