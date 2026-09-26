import { ErrorApi } from './errorApi';

/**
 * Cliente HTTP del shell, usado como *mutator* de Orval (`orval.config.ts`).
 * Toda llamada del cliente generado pasa por aquí (CLAUDE.md §5.2, §8.4).
 */

/** Dependencias del cliente que aporta el shell (sesión, empresa activa y login). */
export interface ContextoHttp {
  /** Base de la API, sin barra final. */
  baseUrl: string;
  /** Token de acceso vigente de la sesión en memoria, o null si no hay. */
  obtenerToken: () => Promise<string | null>;
  /** Empresa activa (en memoria), o null si aún no se eligió. */
  obtenerEmpresaId: () => string | null;
  /** Renueva la sesión con el refresh token y devuelve el nuevo token, o null si no fue posible. */
  renovarSesion: () => Promise<string | null>;
  /** Inicia el login (redirección a Keycloak). */
  iniciarLogin: () => Promise<void>;
}

/** Contexto vigente; lo fija el shell al arrancar. */
let contexto: ContextoHttp | null = null;

/** Renovación en curso, compartida para que varias respuestas 401 simultáneas renueven una sola vez. */
let renovacionEnCurso: Promise<string | null> | null = null;

/**
 * Fija el contexto que usa el cliente HTTP.
 * @param nuevo dependencias del shell
 */
export function configurarContextoHttp(nuevo: ContextoHttp): void {
  contexto = nuevo;
  renovacionEnCurso = null;
}

/** Renueva el token sin duplicar renovaciones concurrentes. */
function renovarUnaVez(ctx: ContextoHttp): Promise<string | null> {
  renovacionEnCurso ??= ctx
    .renovarSesion()
    .catch(() => null)
    .finally(() => {
      renovacionEnCurso = null;
    });
  return renovacionEnCurso;
}

/**
 * Envía la petición con los headers de seguridad y de empresa.
 * @param url ruta del contrato (p. ej. `/aplicaciones`)
 * @param options opciones del llamador; sus headers tienen prioridad (p. ej. `If-Match`)
 * @param token token de acceso a enviar
 */
async function enviar(
  ctx: ContextoHttp,
  url: string,
  options: RequestInit,
  token: string | null,
): Promise<Response> {
  const headers = new Headers(options.headers);
  // 1. Credencial de la sesión en memoria
  if (token) headers.set('Authorization', `Bearer ${token}`);
  // 2. Empresa activa en todas las rutas salvo /me, que sirve justamente para elegirla (CLAUDE.md §13)
  const empresaId = ctx.obtenerEmpresaId();
  const esMe = url.split('?')[0] === '/me';
  if (empresaId && !esMe && !headers.has('X-Empresa-Id')) headers.set('X-Empresa-Id', empresaId);
  // 3. Base de la API antepuesta a la ruta relativa del contrato
  return fetch(`${ctx.baseUrl}${url}`, { ...options, headers });
}

/**
 * Lee el cuerpo de una respuesta: JSON si el tipo lo indica, texto en otro caso; vacío → `{}`.
 * Un cuerpo ilegible no debe ocultar el estado HTTP, por eso no lanza.
 */
async function leerCuerpo(res: Response): Promise<unknown> {
  if ([204, 205, 304].includes(res.status)) return {};
  const texto = await res.text();
  if (!texto) return {};
  if ((res.headers.get('content-type') ?? '').includes('json')) {
    try {
      return JSON.parse(texto);
    } catch {
      return {};
    }
  }
  return texto;
}

/**
 * Ejecuta una llamada a la API. Firma exigida por Orval: `(url, options) => Promise<T>`, con `T` = `{ data, status, headers }`.
 * @param url ruta relativa del contrato
 * @param options opciones de `fetch` generadas por Orval o pasadas por el llamador
 * @returns respuesta 2xx con `data`, `status` y `headers`
 * @throws ErrorApi en toda respuesta que no sea 2xx
 */
export async function clienteHttp<T>(url: string, options: RequestInit = {}): Promise<T> {
  if (!contexto) throw new Error('El cliente HTTP no está configurado');
  const ctx = contexto;

  // 1. Primer intento con el token actual
  let res = await enviar(ctx, url, options, await ctx.obtenerToken());

  // 2. Un 401 puede ser un token vencido: renueva una sola vez y reintenta una sola vez
  if (res.status === 401) {
    const nuevoToken = await renovarUnaVez(ctx);
    if (nuevoToken) res = await enviar(ctx, url, options, nuevoToken);
    // 3. Sin renovación posible o con un segundo 401, la sesión no sirve: inicia el login
    if (!nuevoToken || res.status === 401) await ctx.iniciarLogin();
  }

  const data = await leerCuerpo(res);

  // 4. Toda respuesta no 2xx se convierte en ErrorApi leyendo el Problem Details
  if (!res.ok) {
    const p = (typeof data === 'object' && data !== null ? data : {}) as Record<string, unknown>;
    throw new ErrorApi({
      status: res.status,
      codigo: typeof p.codigo === 'string' ? p.codigo : undefined,
      detail: typeof p.detail === 'string' ? p.detail : undefined,
      title: typeof p.title === 'string' ? p.title : undefined,
      errores: Array.isArray(p.errores) ? (p.errores as ErrorApi['errores']) : undefined,
      diferencia: typeof p.diferencia === 'string' ? p.diferencia : undefined,
    });
  }

  return { data, status: res.status, headers: res.headers } as T;
}

export default clienteHttp;
