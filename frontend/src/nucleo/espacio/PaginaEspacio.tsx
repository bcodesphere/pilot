import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { actualizarEmpresa, getObtenerEmpresaQueryKey, useObtenerEmpresa } from '@/api/empresas/empresas';
import type { Empresa } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { Button } from '@/compartido/ui/button';
import { Input } from '@/compartido/ui/input';
import { Label } from '@/compartido/ui/label';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { mensajeDeError } from '@/nucleo/mensajesError';
import { useSesion } from '@/nucleo/sesion/contextoSesion';
import { esquemaEspacio, type ValoresEspacio } from './esquemaEspacio';

/** Respuesta con cuerpo y cabeceras, tal como la guarda la caché de la consulta de la empresa. */
type RespuestaEmpresa = { data: Empresa; status: number; headers: Headers };

/**
 * Pantalla "Espacio de trabajo" (`/configuracion/espacio`): muestra y cambia el nombre (ADR-032).
 * Usa concurrencia optimista: el `PATCH` lleva `If-Match` con el `ETag` leído del `GET` (CLAUDE.md §8.3);
 * un 412 `PLT-016` avisa del conflicto y ofrece "Recargar".
 */
export function PaginaEspacio() {
  const { empresaActiva, recargarUsuario } = useSesion();
  const empresaId = empresaActiva.empresaId;
  const clienteConsultas = useQueryClient();
  const consulta = useObtenerEmpresa(empresaId);
  const empresa = consulta.data?.data as Empresa | undefined;
  // El ETag de la versión leída vive en las cabeceras de la respuesta cacheada
  const etag = consulta.data?.headers.get('ETag') ?? null;

  // `values` sincroniza el formulario con el nombre vigente cada vez que se (re)lee la empresa
  const formulario = useForm<ValoresEspacio>({
    resolver: zodResolver(esquemaEspacio),
    values: { nombre: empresa?.nombre ?? '' },
  });

  const guardar = useMutation({
    mutationFn: (valores: ValoresEspacio) =>
      actualizarEmpresa(empresaId, { nombre: valores.nombre }, { headers: { 'If-Match': etag ?? '' } }),
    onSuccess: async (respuesta) => {
      // 1. Guarda el nuevo ETag: la respuesta del PATCH (cuerpo y cabeceras) reemplaza la lectura cacheada
      clienteConsultas.setQueryData(getObtenerEmpresaQueryKey(empresaId), respuesta as RespuestaEmpresa);
      // 2. Invalida para confirmar contra el servidor
      await clienteConsultas.invalidateQueries({ queryKey: getObtenerEmpresaQueryKey(empresaId) });
      // 3. Recarga /me: la cabecera del shell muestra el nombre nuevo
      await recargarUsuario();
    },
    onError: (error) => {
      // 422: el mensaje del servidor se muestra en el campo
      if (esErrorApi(error, 'PLT-002')) {
        const detalle = error.errores.find((e) => e.campo === 'nombre') ?? error.errores[0];
        formulario.setError('nombre', { message: detalle?.mensaje ?? 'El nombre no es válido' });
      }
    },
  });

  /** Vuelve a leer la empresa (con su ETag nuevo) tras un conflicto 412. */
  const recargar = () => {
    guardar.reset();
    void consulta.refetch();
  };

  const conflicto = esErrorApi(guardar.error, 'PLT-016');
  const errorGeneral =
    guardar.isError && !conflicto && !esErrorApi(guardar.error, 'PLT-002')
      ? mensajeDeError(guardar.error, {
          'PLT-010': 'No tienes permiso para cambiar el nombre.',
          'PLT-015': 'No pudimos verificar la versión del dato. Recarga e inténtalo de nuevo.',
        })
      : null;

  return (
    <section aria-labelledby="titulo-espacio" className="max-w-lg space-y-4">
      <h1 id="titulo-espacio" className="text-2xl font-bold">
        Espacio de trabajo
      </h1>
      <p className="text-sm text-neutral-600">
        En la versión abierta de Pilot solo se edita el nombre de tu espacio de trabajo.
      </p>

      {consulta.isPending && (
        <p role="status" className="text-sm text-neutral-600">
          Cargando…
        </p>
      )}
      {consulta.isError && <Alert variant="error">No pudimos cargar tu espacio de trabajo.</Alert>}

      {empresa && (
        <form noValidate className="space-y-3" onSubmit={formulario.handleSubmit((v) => guardar.mutate(v))}>
          <div className="space-y-1">
            <Label htmlFor="espacio-nombre">Nombre del espacio</Label>
            <Input
              id="espacio-nombre"
              aria-invalid={formulario.formState.errors.nombre ? true : undefined}
              aria-describedby="espacio-nombre-error"
              {...formulario.register('nombre')}
            />
            <p id="espacio-nombre-error" role="alert" className="min-h-4 text-sm text-red-700">
              {formulario.formState.errors.nombre?.message}
            </p>
          </div>

          {/* Conflicto de versión: alguien más lo cambió */}
          {conflicto && (
            <Alert variant="warning" className="flex items-center justify-between gap-2">
              <span>Alguien cambió este dato mientras lo editabas</span>
              <Button size="sm" variant="outline" onClick={recargar}>
                Recargar
              </Button>
            </Alert>
          )}
          {errorGeneral && <Alert variant="error">{errorGeneral}</Alert>}
          {guardar.isSuccess && <Alert variant="success">Nombre actualizado</Alert>}

          <Button type="submit" disabled={guardar.isPending || !etag}>
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </Button>
        </form>
      )}
    </section>
  );
}
