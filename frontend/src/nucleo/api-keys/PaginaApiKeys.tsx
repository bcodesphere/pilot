import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { getListarApiKeysQueryKey, listarApiKeys, revocarApiKey } from '@/api/api-keys/api-keys';
import type { ApiKey, PaginaApiKeys as PaginaApiKeysDto } from '@/api/modelos';
import { formatearFechaHora } from '@/compartido/formato/fecha';
import { Alert } from '@/compartido/ui/alert';
import { Badge } from '@/compartido/ui/badge';
import { Button } from '@/compartido/ui/button';
import { Dialogo } from '@/compartido/ui/dialog';
import { mensajeDeError } from '@/nucleo/mensajesError';
import { DialogoCrearApiKey } from './DialogoCrearApiKey';

/** Tamaño de página de la lista. */
const LIMITE = 20;

/**
 * Estado legible de una API key: revocada > vencida > vigente.
 * @param clave API key de la lista
 * @param ahora instante de referencia
 */
function estadoDe(clave: ApiKey, ahora: Date): { texto: string; variante: 'success' | 'danger' | 'warning' } {
  if (clave.revocadaEn) return { texto: 'Revocada', variante: 'danger' };
  if (clave.expiraEn && new Date(clave.expiraEn) <= ahora) return { texto: 'Vencida', variante: 'warning' };
  return { texto: 'Vigente', variante: 'success' };
}

/**
 * Pantalla "API keys" (`/configuracion/api-keys`, solo `admin_empresa`): lista paginada por cursor,
 * creación (con el secreto mostrado una sola vez) y revocación con confirmación.
 */
export function PaginaApiKeys() {
  const clienteConsultas = useQueryClient();
  const [creando, setCreando] = useState(false);
  const [porRevocar, setPorRevocar] = useState<ApiKey | null>(null);

  // Lista con "Cargar más": cada página se pide con el cursor de la anterior
  const lista = useInfiniteQuery({
    queryKey: [...getListarApiKeysQueryKey(), 'paginas'],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam }) =>
      listarApiKeys({ limite: LIMITE, ...(pageParam ? { cursor: pageParam } : {}) }),
    getNextPageParam: (ultima) => (ultima.data as PaginaApiKeysDto).siguienteCursor ?? undefined,
  });
  const claves = (lista.data?.pages ?? []).flatMap((p) => (p.data as PaginaApiKeysDto).elementos);
  const ahora = new Date();

  /** Refresca la lista tras crear o revocar. */
  const refrescar = () => clienteConsultas.invalidateQueries({ queryKey: getListarApiKeysQueryKey() });

  const revocacion = useMutation({
    mutationFn: (id: string) => revocarApiKey(id),
    onSuccess: async () => {
      // 204: cierra la confirmación y refresca
      setPorRevocar(null);
      await refrescar();
    },
  });

  return (
    <section aria-labelledby="titulo-api-keys" className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <h1 id="titulo-api-keys" className="text-2xl font-bold">
          API keys
        </h1>
        <Button onClick={() => setCreando(true)}>Crear API key</Button>
      </div>

      {/* Ayuda de uso con n8n: solo texto de ejemplo, nunca una clave real */}
      <Alert>
        Usa la clave en n8n con el header <code>Authorization: Bearer &lt;clave&gt;</code> al llamar a{' '}
        <code>POST /api/v1/integraciones/n8n/operaciones</code>.
      </Alert>

      {lista.isPending && (
        <p role="status" className="text-sm text-neutral-600">
          Cargando API keys…
        </p>
      )}
      {lista.isError && <Alert variant="error">No pudimos cargar las API keys.</Alert>}
      {!lista.isPending && !lista.isError && claves.length === 0 && (
        <p className="text-neutral-600">Aún no has creado ninguna API key.</p>
      )}

      {claves.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b">
                <th className="py-2 pr-4">Nombre</th>
                <th className="py-2 pr-4">Prefijo</th>
                <th className="py-2 pr-4">Alcances</th>
                <th className="py-2 pr-4">Creada</th>
                <th className="py-2 pr-4">Vence</th>
                <th className="py-2 pr-4">Último uso</th>
                <th className="py-2 pr-4">Estado</th>
                <th className="py-2">
                  <span className="sr-only">Acciones</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {claves.map((c) => {
                const estado = estadoDe(c, ahora);
                return (
                  <tr key={c.id} className="border-b">
                    <td className="py-2 pr-4">{c.nombre}</td>
                    <td className="py-2 pr-4 font-mono">{c.prefijo}</td>
                    <td className="py-2 pr-4">{c.alcances.join(', ')}</td>
                    <td className="py-2 pr-4">{formatearFechaHora(c.creadaEn)}</td>
                    <td className="py-2 pr-4">{formatearFechaHora(c.expiraEn)}</td>
                    <td className="py-2 pr-4">{formatearFechaHora(c.ultimoUsoEn)}</td>
                    <td className="py-2 pr-4">
                      <Badge variant={estado.variante}>{estado.texto}</Badge>
                    </td>
                    <td className="py-2">
                      {!c.revocadaEn && (
                        <Button
                          size="sm"
                          variant="outline"
                          aria-label={`Revocar ${c.nombre}`}
                          onClick={() => {
                            revocacion.reset();
                            setPorRevocar(c);
                          }}
                        >
                          Revocar
                        </Button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {lista.hasNextPage && (
        <Button
          variant="outline"
          disabled={lista.isFetchingNextPage}
          onClick={() => void lista.fetchNextPage()}
        >
          {lista.isFetchingNextPage ? 'Cargando…' : 'Cargar más'}
        </Button>
      )}

      {/* Diálogo de creación (y del secreto) */}
      {creando && <DialogoCrearApiKey onCerrar={() => setCreando(false)} onCreada={() => void refrescar()} />}

      {/* Confirmación de revocación (diálogo accesible, no window.confirm) */}
      {porRevocar && (
        <Dialogo
          titulo="Revocar API key"
          onCerrar={() => setPorRevocar(null)}
          cerrable={!revocacion.isPending}
        >
          <p className="text-sm">
            ¿Revocar la API key <strong>{porRevocar.nombre}</strong>? Las integraciones que la usen dejarán de
            funcionar y no se puede deshacer.
          </p>
          {revocacion.isError && (
            <Alert variant="error">
              {mensajeDeError(revocacion.error, {
                'PLT-017': 'La API key ya no existe.',
                'PLT-010': 'No tienes permiso para revocar API keys.',
              })}
            </Alert>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setPorRevocar(null)} disabled={revocacion.isPending}>
              Cancelar
            </Button>
            <Button disabled={revocacion.isPending} onClick={() => revocacion.mutate(porRevocar.id)}>
              {revocacion.isPending ? 'Revocando…' : 'Revocar'}
            </Button>
          </div>
        </Dialogo>
      )}
    </section>
  );
}
