import { Search } from 'lucide-react';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useListarCuentasContables } from '@/api/cuentas-contables/cuentas-contables';
import type { CuentaContable } from '@/api/modelos';
import { useAtajo } from '@/compartido/dominio/useAtajo';
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/compartido/ui/command';
import { useCatalogoApps } from '@/nucleo/apps/useCatalogoApps';

/** Una pantalla del shell que el buscador puede ofrecer. */
interface PantallaBuscable {
  etiqueta: string;
  href: string;
  /** `true` si la pantalla es de la app Contabilidad (se omite sin la app instalada). */
  deContabilidad?: boolean;
}

/** Pantallas conocidas; agregar una nueva ruta aquí no exige tocar la barra lateral. */
const PANTALLAS: PantallaBuscable[] = [
  { etiqueta: 'Inicio', href: '/' },
  { etiqueta: 'Libro Diario', href: '/contabilidad/libro-diario', deContabilidad: true },
  { etiqueta: 'Mayor', href: '/contabilidad/mayor', deContabilidad: true },
  { etiqueta: 'Catálogo de cuentas', href: '/contabilidad/catalogo', deContabilidad: true },
  { etiqueta: 'Balanza de Comprobación', href: '/contabilidad/reportes/balanza', deContabilidad: true },
  {
    etiqueta: 'Estado de Situación Financiera',
    href: '/contabilidad/reportes/situacion-financiera',
    deContabilidad: true,
  },
  { etiqueta: 'Estado de Resultados', href: '/contabilidad/reportes/resultados', deContabilidad: true },
  { etiqueta: 'Resumen de IVA', href: '/contabilidad/reportes/iva', deContabilidad: true },
  {
    etiqueta: 'Diagnóstico de mayorización',
    href: '/contabilidad/reportes/diagnostico',
    deContabilidad: true,
  },
  { etiqueta: 'Configuración de Contabilidad', href: '/contabilidad/configuracion', deContabilidad: true },
  { etiqueta: 'Reglas de contabilización', href: '/contabilidad/reglas', deContabilidad: true },
  { etiqueta: 'Apps', href: '/configuracion/apps' },
  { etiqueta: 'Espacio de trabajo', href: '/configuracion/espacio' },
  { etiqueta: 'API keys', href: '/configuracion/api-keys' },
  { etiqueta: 'Mi perfil', href: '/perfil' },
];

/**
 * Buscador global `Ctrl+K` (spec F4.5 §7.4): encuentra pantallas del shell y, con Contabilidad
 * instalada, cuentas del catálogo por código o nombre (`GET /contabilidad/cuentas`, ADR-035). Una
 * cuenta lleva al Mayor filtrado por ella.
 */
export function BuscadorGlobal() {
  const [abierto, setAbierto] = useState(false);
  const navegar = useNavigate();
  const { apps } = useCatalogoApps();
  const contabilidadInstalada = apps.some((a) => a.codigo === 'contabilidad' && a.estado === 'INSTALADA');

  // El catálogo completo se pide solo mientras el buscador está abierto y hay algo que buscar
  const cuentas = useListarCuentasContables(undefined, {
    query: { enabled: abierto && contabilidadInstalada },
  });
  // El cliente HTTP lanza en no-2xx, así que un dato presente es siempre la respuesta 200 (como useCatalogoApps)
  const listaCuentas = (cuentas.data?.data as CuentaContable[] | undefined) ?? [];

  useAtajo('k', () => setAbierto(true), { ctrlOMeta: true });

  /** Navega y cierra el buscador. */
  const ir = (href: string) => {
    setAbierto(false);
    navegar(href);
  };

  return (
    <>
      {/* Disparador visible: un botón compacto con la pista del atajo, como en la barra superior */}
      <button
        type="button"
        onClick={() => setAbierto(true)}
        className="flex h-8 items-center gap-2 rounded-[var(--radius-control)] border border-[var(--color-borde)] bg-[var(--color-lienzo)] px-2.5 text-sm text-[var(--color-texto-suave)] hover:bg-[var(--color-primario-suave)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-primario)]"
      >
        <Search aria-hidden="true" className="size-4" />
        <span className="hidden sm:inline">Buscar</span>
        <kbd className="hidden rounded border border-[var(--color-borde)] bg-[var(--color-superficie)] px-1 font-mono text-xs sm:inline">
          Ctrl K
        </kbd>
      </button>
      <CommandDialog open={abierto} onOpenChange={setAbierto} label="Buscador global">
        <CommandInput placeholder="Buscar una pantalla o una cuenta…" />
        <CommandList>
          <CommandEmpty>Sin resultados.</CommandEmpty>
          <CommandGroup heading="Pantallas">
            {PANTALLAS.filter((p) => !p.deContabilidad || contabilidadInstalada).map((p) => (
              <CommandItem key={p.href} value={p.etiqueta} onSelect={() => ir(p.href)}>
                <Search aria-hidden="true" className="size-4 text-[var(--color-texto-suave)]" />
                {p.etiqueta}
              </CommandItem>
            ))}
          </CommandGroup>
          {listaCuentas.length > 0 && (
            <CommandGroup heading="Cuentas">
              {listaCuentas.map((cuenta) => (
                <CommandItem
                  key={cuenta.id}
                  value={`${cuenta.codigo} ${cuenta.nombre}`}
                  onSelect={() => ir(`/contabilidad/mayor?cuentaId=${cuenta.id}`)}
                >
                  <span className="codigo-cuenta cifra text-[var(--color-texto-suave)]">{cuenta.codigo}</span>
                  {cuenta.nombre}
                </CommandItem>
              ))}
            </CommandGroup>
          )}
        </CommandList>
      </CommandDialog>
    </>
  );
}
