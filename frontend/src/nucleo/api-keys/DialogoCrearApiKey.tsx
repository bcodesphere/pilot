import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { crearApiKey } from '@/api/api-keys/api-keys';
import type { ApiKeyCreada } from '@/api/modelos';
import { finDeDiaElSalvadorEnUtc } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Dialogo } from '@/compartido/ui/dialog';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { mensajeDeError } from '@/nucleo/mensajesError';
import { ALCANCE_INTEGRACION, esquemaApiKey, type ValoresApiKey } from './esquemaApiKey';

/** Propiedades del flujo de creación. */
interface Props {
  /** Se invoca al cerrar el flujo (con o sin haber creado la clave). */
  onCerrar: () => void;
  /** Se invoca cuando se creó una clave, para refrescar la lista. */
  onCreada: () => void;
}

/**
 * Flujo de creación de una API key en dos diálogos: (1) formulario; (2) el secreto, una sola vez.
 *
 * Tratamiento del secreto (CLAUDE.md §14.1): vive únicamente en el estado local de este componente
 * mientras el segundo diálogo está abierto. La mutación se descarta (`reset`) apenas responde y usa
 * `gcTime: 0`, de modo que ni la caché de mutaciones ni la de consultas lo retienen; tampoco se
 * escribe en ningún almacenamiento del navegador. Al cerrar, el componente se desmonta y el secreto desaparece.
 * @param props ver {@link Props}
 */
export function DialogoCrearApiKey({ onCerrar, onCreada }: Props) {
  const [creada, setCreada] = useState<ApiKeyCreada | null>(null);
  const [copiado, setCopiado] = useState<'si' | 'no' | null>(null);

  const formulario = useForm<ValoresApiKey>({
    resolver: zodResolver(esquemaApiKey),
    defaultValues: { nombre: '', alcances: [ALCANCE_INTEGRACION], vencimiento: '' },
  });

  const creacion = useMutation({
    // gcTime 0: la mutación (y su respuesta con el secreto) se elimina en cuanto no tiene observadores
    gcTime: 0,
    mutationFn: (v: ValoresApiKey) =>
      crearApiKey({
        nombre: v.nombre,
        alcances: v.alcances,
        // El vencimiento viaja como instante UTC; sin fecha, la clave no vence
        expiraEn: v.vencimiento ? finDeDiaElSalvadorEnUtc(v.vencimiento) : null,
      }),
    onError: (error) => {
      if (esErrorApi(error, 'PLT-002')) {
        const detalle = error.errores[0];
        formulario.setError(detalle?.campo === 'nombre' ? 'nombre' : 'root', {
          message: detalle?.mensaje ?? 'Los datos no son válidos',
        });
      }
    },
  });

  /** Envía el formulario y, con 201, pasa el secreto al estado local y limpia la mutación. */
  const enviar = formulario.handleSubmit((valores) => {
    creacion.mutate(valores, {
      onSuccess: (respuesta) => {
        setCreada(respuesta.data as ApiKeyCreada);
        onCreada();
        // El secreto ya está en el estado local: la mutación no necesita conservarlo
        creacion.reset();
      },
    });
  });

  /** Copia el secreto al portapapeles; informa si el navegador lo impide. */
  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(creada?.secreto ?? '');
      setCopiado('si');
    } catch {
      setCopiado('no');
    }
  };

  /** Cierra descartando todo: mutación y secreto. */
  const cerrar = () => {
    creacion.reset();
    setCreada(null);
    onCerrar();
  };

  // Diálogo 2: el secreto, una sola vez
  if (creada) {
    return (
      <Dialogo titulo="API key creada" onCerrar={cerrar}>
        <Alert variant="warning">Copia tu clave ahora. No podrás volver a verlo.</Alert>
        <div className="space-y-1">
          <Label htmlFor="secreto-api-key">Clave secreta</Label>
          <Input id="secreto-api-key" readOnly value={creada.secreto} className="font-mono" />
        </div>
        {copiado === 'si' && <Alert variant="success">Copiada al portapapeles</Alert>}
        {copiado === 'no' && (
          <Alert variant="error">No se pudo copiar; selecciona el texto y cópialo a mano.</Alert>
        )}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => void copiar()}>
            Copiar
          </Button>
          <Button onClick={cerrar}>Cerrar</Button>
        </div>
      </Dialogo>
    );
  }

  // Diálogo 1: formulario de creación
  const errores = formulario.formState.errors;
  return (
    <Dialogo titulo="Crear API key" onCerrar={cerrar} cerrable={!creacion.isPending}>
      <form noValidate className="space-y-3" onSubmit={enviar}>
        <div className="space-y-1">
          <Label htmlFor="apikey-nombre">Nombre</Label>
          <Input
            id="apikey-nombre"
            placeholder="n8n producción"
            aria-invalid={errores.nombre ? true : undefined}
            aria-describedby="apikey-nombre-error"
            {...formulario.register('nombre')}
          />
          <p id="apikey-nombre-error" role="alert" className="min-h-4 text-sm text-red-700">
            {errores.nombre?.message}
          </p>
        </div>

        {/* Alcance: único en 1.0, marcado y obligatorio */}
        <fieldset className="space-y-1">
          <legend className="text-sm font-medium">Alcances</legend>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked disabled readOnly />
            {ALCANCE_INTEGRACION}
          </label>
        </fieldset>

        <div className="space-y-1">
          <Label htmlFor="apikey-vencimiento">Vencimiento (opcional)</Label>
          <Input
            id="apikey-vencimiento"
            type="date"
            aria-invalid={errores.vencimiento ? true : undefined}
            aria-describedby="apikey-vencimiento-error"
            {...formulario.register('vencimiento')}
          />
          <p id="apikey-vencimiento-error" role="alert" className="min-h-4 text-sm text-red-700">
            {errores.vencimiento?.message}
          </p>
        </div>

        {(errores.root?.message || (creacion.isError && !esErrorApi(creacion.error, 'PLT-002'))) && (
          <Alert variant="error">
            {errores.root?.message ??
              mensajeDeError(creacion.error, { 'PLT-010': 'No tienes permiso para crear API keys.' })}
          </Alert>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={cerrar} disabled={creacion.isPending}>
            Cancelar
          </Button>
          <Button type="submit" disabled={creacion.isPending}>
            {creacion.isPending ? 'Creando…' : 'Crear'}
          </Button>
        </div>
      </form>
    </Dialogo>
  );
}
