import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { getListarAplicacionesQueryKey, instalarAplicacion } from '@/api/aplicaciones/aplicaciones';
import type { AplicacionCatalogo } from '@/api/modelos';
import { Alert } from '@/compartido/ui/alert';
import { Badge } from '@/compartido/ui/badge';
import { Button } from '@/compartido/ui/button';
import { Card } from '@/compartido/ui/card';
import { useCatalogoApps } from './apps/useCatalogoApps';
import { mensajeDeError } from './mensajesError';
import { useEsAdmin } from './sesion/useEsAdmin';

/** Mensajes de la instalación según el código de error (CLAUDE.md §8.4, ADR-030). */
const MENSAJES_INSTALACION: Record<string, string> = {
  'PLT-011': 'Esta app es de la edición Enterprise y no se puede instalar.',
  'PLT-010': 'No tienes permiso para instalar apps.',
  'PLT-017': 'La app no existe o no pertenece a tu espacio de trabajo.',
};

/**
 * Catálogo "Apps" (ADR-030): cada app con su estado para la empresa activa.
 * `INSTALADA` → enlace "Abrir"; `DISPONIBLE` → botón "Instalar" (solo `admin_empresa`);
 * `BLOQUEADA_ENTERPRISE` → botón deshabilitado "Disponible en Enterprise".
 */
export function PaginaApps() {
  const { apps, cargando, error } = useCatalogoApps();
  const esAdmin = useEsAdmin();
  const clienteConsultas = useQueryClient();
  const [exito, setExito] = useState<string | null>(null);

  // Instalación: 201 (recién instalada) y 200 (ya lo estaba) son éxito; el cliente lanza ErrorApi en el resto
  const instalacion = useMutation({
    mutationFn: (app: AplicacionCatalogo) => instalarAplicacion(app.codigo),
    onSuccess: async (_respuesta, app) => {
      // 1. Refresca el catálogo: el lanzador y esta pantalla muestran la app como instalada
      await clienteConsultas.invalidateQueries({ queryKey: getListarAplicacionesQueryKey() });
      // 2. Confirma al usuario
      setExito(`${app.nombre} instalada`);
    },
  });

  /** Inicia la instalación limpiando el aviso anterior. */
  const instalar = (app: AplicacionCatalogo) => {
    setExito(null);
    instalacion.reset();
    instalacion.mutate(app);
  };

  return (
    <section aria-labelledby="titulo-apps" className="space-y-4">
      <h1 id="titulo-apps" className="text-2xl font-bold">
        Apps
      </h1>

      {/* 1. Avisos de resultado */}
      {exito && <Alert variant="success">{exito}</Alert>}
      {instalacion.isError && (
        <Alert variant="error">{mensajeDeError(instalacion.error, MENSAJES_INSTALACION)}</Alert>
      )}

      {/* 2. Estados de carga y error del catálogo */}
      {cargando && (
        <p role="status" className="text-sm text-neutral-600">
          Cargando aplicaciones…
        </p>
      )}
      {error && <Alert variant="error">No pudimos cargar las aplicaciones.</Alert>}

      {/* 3. Catálogo en el orden que entrega la API */}
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {apps.map((app) => {
          const instalando = instalacion.isPending && instalacion.variables?.codigo === app.codigo;
          return (
            <li key={app.codigo}>
              <Card className="flex h-full flex-col gap-3">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="font-semibold">{app.nombre}</h2>
                  {app.estado === 'INSTALADA' && <Badge variant="success">Instalada</Badge>}
                  {app.estado === 'BLOQUEADA_ENTERPRISE' && <Badge variant="warning">Enterprise</Badge>}
                </div>
                {app.descripcion && <p className="text-sm text-neutral-600">{app.descripcion}</p>}
                <div className="mt-auto">
                  {app.estado === 'INSTALADA' && (
                    <Link
                      to={`/${app.codigo}`}
                      aria-label={`Abrir ${app.nombre}`}
                      className="text-sm underline underline-offset-4"
                    >
                      Abrir
                    </Link>
                  )}
                  {app.estado === 'DISPONIBLE' &&
                    (esAdmin ? (
                      <Button
                        size="sm"
                        aria-label={`Instalar ${app.nombre}`}
                        disabled={instalacion.isPending}
                        onClick={() => instalar(app)}
                      >
                        {instalando ? 'Instalando…' : 'Instalar'}
                      </Button>
                    ) : (
                      <span className="text-sm text-neutral-500">
                        Solo un administrador puede instalarla.
                      </span>
                    ))}
                  {app.estado === 'BLOQUEADA_ENTERPRISE' && (
                    <Button size="sm" variant="outline" disabled>
                      Disponible en Enterprise
                    </Button>
                  )}
                </div>
              </Card>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
