import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import type { User } from 'oidc-client-ts';
import { Button } from '@/compartido/ui/button';
import { configurarContextoHttp } from '@/nucleo/http/clienteHttp';
import { obtenerEmpresaActivaId } from '@/nucleo/sesion/almacenEmpresa';
import { ContextoAuth } from './contextoAuth';
import { RUTA_CALLBACK, sesionVigente, type EstadoLogin, type GestorSesion } from './gestorSesion';

/** Procesamiento del callback por gestor: evita canjear dos veces el `code` (StrictMode ejecuta los efectos dos veces). */
const callbacksEnCurso = new WeakMap<GestorSesion, Promise<User>>();

/**
 * Exige una sesión OIDC (Code + PKCE) antes de mostrar la aplicación.
 * - Sin sesión en memoria (incluido un F5) redirige a Keycloak guardando la ruta de destino;
 *   si el SSO sigue vivo, Keycloak devuelve al usuario sin pedir credenciales.
 * - En `/auth/callback` canjea el código y vuelve a la ruta de destino.
 * Configura además el cliente HTTP con el token de la sesión (renovación y reinicio de login incluidos).
 * @param props.gestor `UserManager` (o simulación en pruebas)
 * @param props.apiBaseUrl base de la API para el cliente HTTP
 */
export function ProveedorAuth({
  gestor,
  apiBaseUrl,
  children,
}: {
  gestor: GestorSesion;
  apiBaseUrl: string;
  children: ReactNode;
}) {
  const navegar = useNavigate();
  const ubicacion = useLocation();
  const [estado, setEstado] = useState<'cargando' | 'listo' | 'error'>('cargando');
  const [intento, setIntento] = useState(0);
  const esCallback = ubicacion.pathname === RUTA_CALLBACK;

  /** Redirige a Keycloak recordando la ruta actual para volver a ella. */
  const iniciarLogin = useCallback(
    (ruta: string) => gestor.signinRedirect({ state: { ruta } satisfies EstadoLogin }),
    [gestor],
  );

  useEffect(() => {
    let activo = true;
    // 1. El cliente HTTP lee el token de la sesión en memoria y sabe renovar o reiniciar el login
    configurarContextoHttp({
      baseUrl: apiBaseUrl,
      obtenerToken: async () => (await gestor.getUser())?.access_token ?? null,
      obtenerEmpresaId: obtenerEmpresaActivaId,
      renovarSesion: async () => (await gestor.signinSilent())?.access_token ?? null,
      iniciarLogin: () => iniciarLogin(window.location.pathname + window.location.search),
    });

    const arrancar = async () => {
      if (esCallback) {
        // 2. Callback: canjea el código una sola vez y vuelve a la ruta guardada
        let pendiente = callbacksEnCurso.get(gestor);
        if (!pendiente) {
          pendiente = gestor.signinRedirectCallback();
          callbacksEnCurso.set(gestor, pendiente);
        }
        const usuario = await pendiente;
        if (!activo) return;
        const destino = (usuario.state as EstadoLogin | undefined)?.ruta ?? '/';
        setEstado('listo');
        navegar(destino, { replace: true });
        return;
      }
      // 3. Ruta normal: usa la sesión en memoria o inicia el login
      const usuario = await gestor.getUser();
      if (!activo) return;
      if (sesionVigente(usuario)) {
        setEstado('listo');
      } else {
        await iniciarLogin(ubicacion.pathname + ubicacion.search + ubicacion.hash);
      }
    };

    arrancar()
      .catch(() => activo && setEstado('error'))
      .finally(() => callbacksEnCurso.delete(gestor));
    return () => {
      activo = false;
    };
    // Se re-ejecuta solo al reintentar o al pasar por el callback; no en cada navegación
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gestor, apiBaseUrl, esCallback, intento]);

  const cerrarSesion = useCallback(
    () => gestor.signoutRedirect({ post_logout_redirect_uri: window.location.origin }),
    [gestor],
  );
  const valor = useMemo(() => ({ cerrarSesion }), [cerrarSesion]);

  // 4. Error de autenticación: mensaje claro y reintento
  if (estado === 'error') {
    return (
      <div role="alert" className="mx-auto mt-24 max-w-md space-y-4 p-6 text-center">
        <h1 className="text-xl font-semibold">No pudimos iniciar tu sesión</h1>
        <p className="text-sm text-neutral-600">
          Ocurrió un problema al comunicarse con el servicio de identidad.
        </p>
        <Button
          onClick={() => {
            // Reintenta desde el inicio: si estamos en el callback, el código ya no sirve
            if (esCallback) navegar('/', { replace: true });
            setEstado('cargando');
            setIntento((n) => n + 1);
          }}
        >
          Reintentar
        </Button>
      </div>
    );
  }

  // 5. Hasta que la sesión esté lista, estado de carga (nunca se muestran datos sin autenticar)
  if (estado !== 'listo' || esCallback) {
    return (
      <p role="status" className="mt-24 text-center text-sm text-neutral-600">
        Iniciando sesión…
      </p>
    );
  }

  return <ContextoAuth.Provider value={valor}>{children}</ContextoAuth.Provider>;
}
