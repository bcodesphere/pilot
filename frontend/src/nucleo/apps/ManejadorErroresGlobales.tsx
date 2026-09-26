import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getListarAplicacionesQueryKey } from '@/api/aplicaciones/aplicaciones';
import { esErrorApi } from '@/nucleo/http/errorApi';
import { useSesion } from '@/nucleo/sesion/contextoSesion';

/**
 * Reacciona a errores de la API que afectan a todo el shell, sin importar qué pantalla los provocó:
 * - 403 `PLT-004` (app no instalada): avisa, refresca el catálogo y vuelve al lanzador.
 * - 403 `PLT-003` (sin membresía en la empresa activa): recarga `/me` y elige otra membresía válida.
 * No renderiza nada.
 */
export function ManejadorErroresGlobales() {
  const clienteConsultas = useQueryClient();
  const navegar = useNavigate();
  const { recargarUsuario } = useSesion();

  useEffect(() => {
    /** Aplica la reacción que corresponde al error. */
    const procesar = (error: unknown) => {
      if (esErrorApi(error, 'PLT-004')) {
        void clienteConsultas.invalidateQueries({ queryKey: getListarAplicacionesQueryKey() });
        navegar('/', {
          replace: true,
          state: { aviso: 'Esa app no está instalada en tu espacio de trabajo.' },
        });
      } else if (esErrorApi(error, 'PLT-003')) {
        void recargarUsuario();
      }
    };
    // Escucha los fallos definitivos (tras los reintentos) de consultas y mutaciones
    const cancelarConsultas = clienteConsultas.getQueryCache().subscribe((e) => {
      if (e.type === 'updated' && e.action.type === 'error') procesar(e.action.error);
    });
    const cancelarMutaciones = clienteConsultas.getMutationCache().subscribe((e) => {
      if (e.type === 'updated' && e.action.type === 'error') procesar(e.action.error);
    });
    return () => {
      cancelarConsultas();
      cancelarMutaciones();
    };
  }, [clienteConsultas, navegar, recargarUsuario]);

  return null;
}
