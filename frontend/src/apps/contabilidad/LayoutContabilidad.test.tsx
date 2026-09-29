import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { json } from '@/nucleo/pruebas-arnes';
import { CATALOGO } from './compartido/datosPrueba';
import { montarContabilidad } from './compartido/montarContabilidad';

afterEach(() => vi.unstubAllGlobals());

/** Manejador mínimo: catálogo vacío-ish, configuración y reglas no se piden en estas pruebas. */
const manejador = (url: string) => (url.endsWith('/contabilidad/cuentas') ? json(CATALOGO) : undefined);

describe('layout de Contabilidad', () => {
  // Regla del encargo: el índice de /contabilidad redirige a "catalogo"
  it('/contabilidad redirige al catálogo', async () => {
    const { router } = montarContabilidad('/contabilidad', 'contador', manejador);
    expect(await screen.findByRole('heading', { name: 'Catálogo de cuentas' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/contabilidad/catalogo');
    // ADR-043 U2 fase B: la subnavegación propia de Contabilidad se quitó (la barra lateral ya la
    // reemplaza); Configuración y Reglas siguen siendo rutas propias de la app mientras se retoma su
    // unificación en /configuracion (ver el reporte de entrega).
    expect(screen.queryByRole('navigation', { name: 'Secciones de Contabilidad' })).not.toBeInTheDocument();
  });
});
