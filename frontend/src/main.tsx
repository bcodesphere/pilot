import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { crearGestorSesion } from '@/nucleo/auth/gestorSesion';
import { leerEntorno } from '@/nucleo/config/entorno';
import { crearClienteConsultas } from '@/nucleo/clienteConsultas';
import { Proveedores } from '@/nucleo/Proveedores';
import { crearRouter } from '@/nucleo/router';
import './index.css';

// Punto de entrada: valida la configuración, crea la sesión OIDC y monta proveedores y router en #root
const raiz = document.getElementById('root');
if (!raiz) throw new Error('No existe el elemento #root');
const arbol = createRoot(raiz);

try {
  // 1. Falla temprano y con un mensaje claro si falta alguna variable VITE_* (ver .env.example)
  const entorno = leerEntorno();
  const router = crearRouter(crearGestorSesion(entorno), entorno);
  arbol.render(
    <StrictMode>
      <Proveedores cliente={crearClienteConsultas()}>
        <RouterProvider router={router} />
      </Proveedores>
    </StrictMode>,
  );
} catch (error) {
  // 2. Error de configuración visible en pantalla
  arbol.render(
    <div role="alert" className="mx-auto mt-24 max-w-lg space-y-2 p-6">
      <h1 className="text-xl font-semibold">Configuración incompleta</h1>
      <p className="text-sm">{error instanceof Error ? error.message : 'Error desconocido'}</p>
      <p className="text-sm text-neutral-600">Revise el archivo .env.local (ver .env.example).</p>
    </div>,
  );
}
