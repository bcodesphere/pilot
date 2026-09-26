import type { ModuloApp } from '@/nucleo/apps/registro';
import { PaginaContabilidad } from './PaginaContabilidad';

/** Módulo de la app Contabilidad: el shell registra sus rutas bajo `/contabilidad/*` si está instalada (ADR-021). */
export const modulo: ModuloApp = {
  rutas: [{ index: true, element: <PaginaContabilidad /> }],
};
