import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { Proveedores } from '@/nucleo/Proveedores';
import { router } from '@/nucleo/router';
import './index.css';

// Punto de entrada: monta los proveedores y el router en #root
const raiz = document.getElementById('root');
if (!raiz) throw new Error('No existe el elemento #root');
createRoot(raiz).render(
  <StrictMode>
    <Proveedores>
      <RouterProvider router={router} />
    </Proveedores>
  </StrictMode>,
);
