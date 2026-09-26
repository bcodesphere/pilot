import {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { obtenerUsuarioActual } from '@/api/usuario-actual/usuario-actual';
import type { UsuarioActual } from '@/api/modelos';
import { Button } from '@/compartido/ui/button';
import { ContextoSesion, type ValorSesion } from './contextoSesion';
import { fijarEmpresaActivaId, obtenerEmpresaActivaId, suscribirEmpresaActiva } from './almacenEmpresa';

/**
 * Carga `GET /me` tras autenticarse y mantiene la empresa activa en memoria (ADR-032:
 * en 1.0 hay una sola membresía; el mecanismo multiempresa se conserva).
 * Los hijos se remontan al cambiar de empresa para que no queden datos de la anterior.
 * @param props.children contenido que requiere usuario y empresa
 */
export function ProveedorSesion({ children }: { children: ReactNode }) {
  const clienteConsultas = useQueryClient();
  const navegar = useNavigate();
  const [usuario, setUsuario] = useState<UsuarioActual | null>(null);
  const [fallo, setFallo] = useState(false);
  const [intento, setIntento] = useState(0);
  const empresaId = useSyncExternalStore(suscribirEmpresaActiva, obtenerEmpresaActivaId);

  /** Carga `/me` y deja una empresa activa válida (la actual si sigue siendo miembro; si no, la primera). */
  const aplicar = useCallback((datos: UsuarioActual) => {
    const vigente = datos.membresias.some((m) => m.empresaId === obtenerEmpresaActivaId());
    // Se fija ANTES de publicar el usuario: los hijos consultan con la empresa ya definida
    if (!vigente) fijarEmpresaActivaId(datos.membresias[0]?.empresaId ?? null);
    setUsuario(datos);
  }, []);

  /** Vuelve a pedir `/me` y aplica el resultado (también lo usa el manejo de 403 PLT-003). */
  const cargar = useCallback(async () => {
    aplicar((await obtenerUsuarioActual()).data as UsuarioActual);
  }, [aplicar]);

  useEffect(() => {
    let activo = true;
    obtenerUsuarioActual()
      .then((r) => activo && aplicar(r.data as UsuarioActual))
      .catch(() => activo && setFallo(true));
    return () => {
      activo = false;
    };
  }, [aplicar, intento]);

  const cambiarEmpresa = useCallback(
    (nueva: string) => {
      if (nueva === obtenerEmpresaActivaId()) return;
      // 1. Nueva empresa en memoria; 2. caché vacía para no mezclar datos; 3. de vuelta al lanzador
      fijarEmpresaActivaId(nueva);
      clienteConsultas.clear();
      navegar('/');
    },
    [clienteConsultas, navegar],
  );

  const empresaActiva = usuario?.membresias.find((m) => m.empresaId === empresaId);
  const valor = useMemo<ValorSesion | null>(
    () =>
      usuario && empresaActiva ? { usuario, empresaActiva, cambiarEmpresa, recargarUsuario: cargar } : null,
    [usuario, empresaActiva, cambiarEmpresa, cargar],
  );

  if (fallo || (usuario && !empresaActiva)) {
    return (
      <div role="alert" className="mx-auto mt-24 max-w-md space-y-4 p-6 text-center">
        <h1 className="text-xl font-semibold">No pudimos cargar tu espacio de trabajo</h1>
        <p className="text-sm text-neutral-600">Verifica tu conexión e inténtalo de nuevo.</p>
        <Button
          onClick={() => {
            setUsuario(null);
            setFallo(false);
            setIntento((n) => n + 1);
          }}
        >
          Reintentar
        </Button>
      </div>
    );
  }
  if (!valor) {
    return (
      <p role="status" className="mt-24 text-center text-sm text-neutral-600">
        Cargando tu espacio de trabajo…
      </p>
    );
  }

  // `key` por empresa: al cambiar, todo el árbol se remonta y vuelve a consultar
  return (
    <ContextoSesion.Provider value={valor}>
      <Fragment key={valor.empresaActiva.empresaId}>{children}</Fragment>
    </ContextoSesion.Provider>
  );
}
