import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import {
  actualizarCuentaContable,
  useObtenerCuentaContable,
} from '@/api/cuentas-contables/cuentas-contables';
import type { ActualizacionCuentaContable, CuentaContable } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Dialogo } from '@/compartido/ui/dialog';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { AvisoConflicto } from '../compartido/AvisoConflicto';
import { campoDeError, mensajeContabilidad } from '../mensajesContabilidad';
import { esquemaEditarCuenta, type ValoresEditarCuenta } from './esquemaCuenta';

/** Texto de la naturaleza derivada (ADR-042: nunca se captura ni se edita). */
const ETIQUETA_NATURALEZA = { DEUDORA: 'Deudora', ACREEDORA: 'Acreedora' } as const;

/** Propiedades del diálogo "Editar cuenta". */
interface Props {
  /** Cuenta a editar. */
  cuentaId: string;
  /** Cierra el diálogo. */
  onCerrar: () => void;
  /** Se invoca tras guardar. */
  onGuardada: () => void;
}

/**
 * Diálogo "Editar cuenta" (rol contador). Primero pide `GET /contabilidad/cuentas/{id}` para leer el `ETag`
 * y solo entonces muestra el formulario; el `PATCH` lleva `If-Match` y únicamente los campos cambiados
 * (CLAUDE.md §8.3). Un 412 `PLT-016` avisa del conflicto y ofrece "Recargar".
 */
export function DialogoEditarCuenta({ cuentaId, onCerrar, onGuardada }: Props) {
  // gcTime/staleTime en 0: el ETag debe ser el vigente al abrir, nunca uno cacheado
  const consulta = useObtenerCuentaContable(cuentaId, { query: { staleTime: 0, gcTime: 0 } });
  const cuenta = consulta.data?.data as CuentaContable | undefined;
  const etag = consulta.data?.headers.get('ETag') ?? null;

  return (
    <Dialogo titulo="Editar cuenta" onCerrar={onCerrar}>
      {consulta.isPending && (
        <p role="status" className="text-sm text-[var(--color-texto-suave)]">
          Cargando…
        </p>
      )}
      {consulta.isError && <Alert variant="error">No pudimos cargar la cuenta.</Alert>}
      {cuenta && etag && (
        // La clave es el ETag: al recargar con una versión nueva el formulario parte de los datos frescos
        <FormularioEditar
          key={etag}
          cuenta={cuenta}
          etag={etag}
          onCerrar={onCerrar}
          onGuardada={onGuardada}
          onRecargar={() => void consulta.refetch()}
        />
      )}
    </Dialogo>
  );
}

/** Formulario de edición, montado cuando ya se conoce la cuenta y su ETag. */
function FormularioEditar({
  cuenta,
  etag,
  onCerrar,
  onGuardada,
  onRecargar,
}: {
  cuenta: CuentaContable;
  etag: string;
  onCerrar: () => void;
  onGuardada: () => void;
  onRecargar: () => void;
}) {
  const clienteConsultas = useQueryClient();
  const formulario = useForm<ValoresEditarCuenta>({
    resolver: zodResolver(esquemaEditarCuenta),
    defaultValues: {
      codigo: cuenta.codigo,
      nombre: cuenta.nombre,
      activa: cuenta.activa,
    },
  });

  const guardar = useMutation({
    mutationFn: (cambios: ActualizacionCuentaContable) =>
      actualizarCuentaContable(cuenta.id, cambios, { headers: { 'If-Match': etag } }),
    onSuccess: async () => {
      await clienteConsultas.invalidateQueries({ queryKey: ['/contabilidad/cuentas'] });
      onGuardada();
    },
    onError: (error) => {
      // El error de negocio de un campo se muestra junto a ese campo
      const campo = campoDeError(error);
      if (campo === 'codigo' || campo === 'activa') {
        formulario.setError(campo, { message: mensajeContabilidad(error) });
      }
    },
  });

  /** Envía solo lo que cambió respecto de la cuenta leída; sin cambios no hace ninguna petición. */
  const alEnviar = (v: ValoresEditarCuenta) => {
    // 1. Compara campo por campo con la versión leída
    const cambios: ActualizacionCuentaContable = {};
    if (v.nombre !== cuenta.nombre) cambios.nombre = v.nombre;
    if (v.codigo !== cuenta.codigo) cambios.codigo = v.codigo;
    // La naturaleza ya no se envía (paso 0 de U2 fase B): el contrato de C1 la quitó de
    // `ActualizacionCuentaContable` (ADR-042: siempre derivada, nunca editable)
    if (v.activa !== cuenta.activa) cambios.activa = v.activa;
    // 2. El contrato exige al menos un campo
    if (Object.keys(cambios).length === 0) {
      formulario.setError('nombre', { message: 'No hay cambios que guardar' });
      return;
    }
    guardar.mutate(cambios);
  };

  const conflicto = esErrorApi(guardar.error, 'PLT-016');
  const errorGeneral =
    guardar.isError && !conflicto && campoDeError(guardar.error) === null
      ? mensajeContabilidad(guardar.error)
      : null;

  return (
    <form noValidate className="space-y-3" onSubmit={formulario.handleSubmit(alEnviar)}>
      <div className="space-y-1">
        <Label htmlFor="editar-codigo">Código</Label>
        <Input
          id="editar-codigo"
          inputMode="numeric"
          aria-invalid={formulario.formState.errors.codigo ? true : undefined}
          aria-describedby="editar-codigo-error"
          {...formulario.register('codigo')}
        />
        <p id="editar-codigo-error" role="alert" className="min-h-4 text-sm text-[var(--color-error)]">
          {formulario.formState.errors.codigo?.message}
        </p>
      </div>

      <div className="space-y-1">
        <Label htmlFor="editar-nombre">Nombre</Label>
        <Input
          id="editar-nombre"
          aria-invalid={formulario.formState.errors.nombre ? true : undefined}
          aria-describedby="editar-nombre-error"
          {...formulario.register('nombre')}
        />
        <p id="editar-nombre-error" role="alert" className="min-h-4 text-sm text-[var(--color-error)]">
          {formulario.formState.errors.nombre?.message}
        </p>
      </div>

      {/* La naturaleza no se pide: siempre derivada de la cuenta padre o de la clase (ADR-042) */}
      <div className="space-y-1 text-sm">
        <span className="font-medium text-[var(--color-texto)]">Naturaleza: </span>
        <span className="text-[var(--color-texto-suave)]">{ETIQUETA_NATURALEZA[cuenta.naturaleza]}</span>
      </div>

      <div className="space-y-1">
        <div className="flex items-center gap-2">
          <input id="editar-activa" type="checkbox" className="h-4 w-4" {...formulario.register('activa')} />
          <Label htmlFor="editar-activa">Cuenta activa</Label>
        </div>
        <p id="editar-activa-error" role="alert" className="min-h-4 text-sm text-[var(--color-error)]">
          {formulario.formState.errors.activa?.message}
        </p>
      </div>

      {conflicto && (
        <AvisoConflicto
          onRecargar={() => {
            guardar.reset();
            onRecargar();
          }}
        />
      )}
      {errorGeneral && <Alert variant="error">{errorGeneral}</Alert>}

      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onCerrar} disabled={guardar.isPending}>
          Cancelar
        </Button>
        <Button type="submit" disabled={guardar.isPending}>
          {guardar.isPending ? 'Guardando…' : 'Guardar'}
        </Button>
      </div>
    </form>
  );
}
