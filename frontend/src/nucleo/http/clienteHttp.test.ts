import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clienteHttp, configurarContextoHttp } from './clienteHttp';
import { ErrorApi } from './errorApi';

/** Respuesta JSON simulada del backend. */
function json(cuerpo: unknown, status = 200, tipo = 'application/json') {
  return new Response(JSON.stringify(cuerpo), { status, headers: { 'content-type': tipo } });
}

describe('cliente HTTP (mutator de Orval)', () => {
  const fetchMock = vi.fn();
  const renovarSesion = vi.fn();
  const iniciarLogin = vi.fn();
  let token = 'tok-1';
  let empresa: string | null = 'emp-1';

  beforeEach(() => {
    fetchMock.mockReset();
    renovarSesion.mockReset();
    iniciarLogin.mockReset().mockResolvedValue(undefined);
    token = 'tok-1';
    empresa = 'emp-1';
    vi.stubGlobal('fetch', fetchMock);
    configurarContextoHttp({
      baseUrl: 'http://api.test/api/v1',
      obtenerToken: async () => token,
      obtenerEmpresaId: () => empresa,
      renovarSesion,
      iniciarLogin,
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  // Regla: el contrato declara rutas relativas (`/aplicaciones`); la base viene de VITE_API_BASE_URL (tarea 2)
  it('antepone la base de la API a la ruta del contrato', async () => {
    fetchMock.mockResolvedValue(json([]));
    await clienteHttp('/aplicaciones', { method: 'GET' });
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://api.test/api/v1/aplicaciones');
  });

  // Regla: Authorization con el token en memoria y X-Empresa-Id de la empresa activa (CLAUDE.md §4.5)
  it('agrega Authorization y X-Empresa-Id', async () => {
    fetchMock.mockResolvedValue(json([]));
    await clienteHttp('/aplicaciones', { method: 'GET' });
    const h = new Headers(fetchMock.mock.calls[0]?.[1].headers);
    expect(h.get('Authorization')).toBe('Bearer tok-1');
    expect(h.get('X-Empresa-Id')).toBe('emp-1');
  });

  // Regla: /me sirve para elegir la empresa, así que no lleva X-Empresa-Id (CLAUDE.md §13)
  it('no agrega X-Empresa-Id en /me', async () => {
    fetchMock.mockResolvedValue(json({}));
    await clienteHttp('/me', { method: 'GET' });
    const h = new Headers(fetchMock.mock.calls[0]?.[1].headers);
    expect(h.has('X-Empresa-Id')).toBe(false);
    expect(h.get('Authorization')).toBe('Bearer tok-1');
  });

  // Regla: los headers del llamador se respetan (If-Match de la concurrencia optimista, CLAUDE.md §8.3)
  it('conserva el If-Match que pasa el llamador', async () => {
    fetchMock.mockResolvedValue(json({}));
    await clienteHttp('/empresas/1', { method: 'PATCH', headers: { 'If-Match': '"3"' } });
    expect(new Headers(fetchMock.mock.calls[0]?.[1].headers).get('If-Match')).toBe('"3"');
  });

  // Regla: errores en Problem Details (RFC 9457, CLAUDE.md §8.4) → ErrorApi con codigo y errores
  it('convierte un Problem Details 422 en ErrorApi con codigo y errores', async () => {
    fetchMock.mockResolvedValue(
      json(
        {
          status: 422,
          title: 'Validación',
          codigo: 'PLT-002',
          errores: [{ campo: 'nombre', mensaje: 'Obligatorio' }],
          diferencia: '1.00',
        },
        422,
        'application/problem+json',
      ),
    );
    const error = await clienteHttp('/empresas/1', { method: 'PATCH' }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ErrorApi);
    expect(error).toMatchObject({
      status: 422,
      codigo: 'PLT-002',
      errores: [{ campo: 'nombre', mensaje: 'Obligatorio' }],
      diferencia: '1.00',
    });
  });

  // Regla: ante 401 renueva el token una vez y reintenta una vez con el token nuevo
  it('ante 401 renueva el token y reintenta una sola vez', async () => {
    renovarSesion.mockResolvedValue('tok-2');
    fetchMock
      .mockResolvedValueOnce(json({ codigo: 'PLT-009' }, 401))
      .mockResolvedValueOnce(json([{ codigo: 'contabilidad' }]));
    const r = await clienteHttp<{ status: number }>('/aplicaciones', { method: 'GET' });
    expect(r.status).toBe(200);
    expect(renovarSesion).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(new Headers(fetchMock.mock.calls[1]?.[1].headers).get('Authorization')).toBe('Bearer tok-2');
    expect(iniciarLogin).not.toHaveBeenCalled();
  });

  // Regla: si tras renovar vuelve el 401, no hay más reintentos: se inicia el login
  it('con un segundo 401 inicia el login y lanza ErrorApi', async () => {
    renovarSesion.mockResolvedValue('tok-2');
    fetchMock.mockResolvedValue(json({ codigo: 'PLT-009' }, 401));
    await expect(clienteHttp('/aplicaciones', { method: 'GET' })).rejects.toMatchObject({
      status: 401,
      codigo: 'PLT-009',
    });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(iniciarLogin).toHaveBeenCalledTimes(1);
  });

  // Regla: si la renovación falla (refresh token vencido) se inicia el login sin reintentar la petición
  it('si no puede renovar inicia el login', async () => {
    renovarSesion.mockRejectedValue(new Error('sin refresh token'));
    fetchMock.mockResolvedValue(json({ codigo: 'PLT-009' }, 401));
    await expect(clienteHttp('/aplicaciones', { method: 'GET' })).rejects.toBeInstanceOf(ErrorApi);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(iniciarLogin).toHaveBeenCalledTimes(1);
  });
});
