import { LayoutDashboard } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { EstadoVacio } from '@/compartido/dominio/EstadoVacio';
import { useCatalogoApps } from './apps/useCatalogoApps';

/**
 * Inicio (ADR-043, spec F4.5 §7.4 y §8): el lanzador de apps se movió a Configuración → Apps.
 * Sin Contabilidad instalada, invita a instalarla; con ella instalada muestra un estado vacío
 * provisional hasta que U4 entregue el tablero (efectivo, por cobrar, pendientes…).
 */
export function PaginaInicio() {
  const { apps, cargando, error } = useCatalogoApps();
  const contabilidadInstalada = apps.some((a) => a.codigo === 'contabilidad' && a.estado === 'INSTALADA');
  // Aviso que dejan otras rutas al redirigir aquí (app no instalada, PLT-004; ver RutaApp y ManejadorErroresGlobales)
  const aviso = (useLocation().state as { aviso?: string } | null)?.aviso;

  return (
    <section aria-labelledby="titulo-inicio" className="space-y-4">
      <h1
        id="titulo-inicio"
        className="font-[family-name:var(--font-titulo)] text-2xl font-bold text-[var(--color-texto)]"
      >
        Inicio
      </h1>

      {aviso && (
        <p
          role="alert"
          className="rounded-[var(--radius-panel)] border border-[var(--color-alerta)]/30 bg-[var(--color-alerta)]/10 p-3 text-sm text-[var(--color-alerta)]"
        >
          {aviso}
        </p>
      )}

      {cargando && (
        <p role="status" className="text-sm text-[var(--color-texto-suave)]">
          Cargando…
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-[var(--color-error)]">
          No pudimos cargar tus aplicaciones.
        </p>
      )}

      {!cargando && !error && !contabilidadInstalada && (
        <EstadoVacio
          titulo="Instala Contabilidad para empezar"
          descripcion="Activa la app desde el catálogo para registrar tus operaciones y ver tus reportes."
          accion={{ etiqueta: 'Ir a Apps', href: '/configuracion/apps' }}
          icono={LayoutDashboard}
        />
      )}

      {!cargando && !error && contabilidadInstalada && (
        <div className="space-y-3">
          <EstadoVacio
            titulo="El tablero llega en la siguiente entrega"
            descripcion="Mientras tanto, entra al Libro Diario o a Reportes desde la barra lateral."
            accion={{ etiqueta: 'Ir al Libro Diario', href: '/contabilidad/libro-diario' }}
            icono={LayoutDashboard}
          />
          <p className="text-center text-sm">
            <Link to="/contabilidad/reportes" className="text-[var(--color-primario)] hover:underline">
              Ir a Reportes
            </Link>
          </p>
        </div>
      )}
    </section>
  );
}
