import {
  BarChart3,
  Building2,
  Home,
  KeyRound,
  LayoutGrid,
  ListTree,
  NotebookText,
  Scale,
  Settings2,
  User,
  type LucideIcon,
} from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { cn } from '@/compartido/lib/utils';
import { useCatalogoApps } from '@/nucleo/apps/useCatalogoApps';
import { useSesion } from '@/nucleo/sesion/contextoSesion';
import { useEsAdmin } from '@/nucleo/sesion/useEsAdmin';
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from '@/compartido/ui/sidebar';
import { useSidebar } from '@/compartido/ui/useSidebar';

/**
 * Un enlace de la barra lateral. Repite el texto de la subnavegación propia de Contabilidad
 * (`LayoutContabilidad`, p. ej. "Libro Diario"), pero el `nav[aria-label="Principal"]` que envuelve la
 * barra ya distingue ambos por contexto para lectores de pantalla y para las pruebas
 * (`within(barraLateral).getByRole('link', …)`), así que aquí no hace falta un nombre accesible aparte
 * (corrección de U2, F4.5: antes se agregaba el sufijo ", barra lateral").
 */
interface EnlaceLateral {
  etiqueta: string;
  href: string;
  Icono: LucideIcon;
}

/** Un grupo con título ("Contabilidad", "Integraciones"…) y sus enlaces. */
interface GrupoLateral {
  titulo: string;
  enlaces: EnlaceLateral[];
}

/**
 * Barra lateral por procesos de negocio (ADR-043, spec F4.5 §7.4): "Inicio" y "Reportes" son
 * enlaces sueltos (solo con Contabilidad instalada, el segundo); el grupo "Contabilidad" agrupa
 * Libro Diario, Mayor y Catálogo; luego Integraciones y Configuración. Los grupos de pantallas que
 * aún no existen (Ingresos, Gastos, Bancos, Activos) no aparecen en 1.0 hasta que U3 y U5 agreguen
 * sus rutas — así ningún enlace queda roto.
 */
export function BarraLateral() {
  const { pathname } = useLocation();
  const { apps } = useCatalogoApps();
  const { empresaActiva } = useSesion();
  const { contraida } = useSidebar();
  const esAdmin = useEsAdmin();
  const contabilidadInstalada = apps.some((a) => a.codigo === 'contabilidad' && a.estado === 'INSTALADA');

  // "Inicio", "Bancos y efectivo", "Activos fijos" y "Reportes" son enlaces sueltos, sin grupo (spec §7.4);
  // en U1 solo existen Inicio y Reportes. Bancos y Activos llegan con U3/U5.
  const enlacesSueltos: EnlaceLateral[] = [
    { etiqueta: 'Inicio', href: '/', Icono: Home },
    ...(contabilidadInstalada
      ? [
          {
            etiqueta: 'Reportes',
            href: '/contabilidad/reportes',
            Icono: BarChart3,
          },
        ]
      : []),
  ];

  const grupos: GrupoLateral[] = [
    ...(contabilidadInstalada
      ? [
          {
            titulo: 'Contabilidad',
            enlaces: [
              {
                etiqueta: 'Libro Diario',
                href: '/contabilidad/libro-diario',
                Icono: NotebookText,
              },
              {
                etiqueta: 'Mayor',
                href: '/contabilidad/mayor',
                Icono: Scale,
              },
              {
                etiqueta: 'Catálogo',
                href: '/contabilidad/catalogo',
                Icono: ListTree,
              },
            ],
          },
        ]
      : []),
    {
      titulo: 'Integraciones',
      enlaces: [{ etiqueta: 'API keys', href: '/configuracion/api-keys', Icono: KeyRound }],
    },
    {
      titulo: 'Configuración',
      enlaces: [
        { etiqueta: 'Espacio de trabajo', href: '/configuracion/espacio', Icono: Building2 },
        ...(contabilidadInstalada
          ? [{ etiqueta: 'Contabilidad', href: '/contabilidad/configuracion', Icono: Settings2 }]
          : []),
        { etiqueta: 'Apps', href: '/configuracion/apps', Icono: LayoutGrid },
        { etiqueta: 'Mi perfil', href: '/perfil', Icono: User },
      ],
    },
  ]
    // "Espacio de trabajo" y "API keys" son de administración (CLAUDE.md §14.2); el resto es para todos
    .map((g) => ({
      ...g,
      enlaces: g.enlaces.filter(
        (e) => esAdmin || (e.href !== '/configuracion/espacio' && e.href !== '/configuracion/api-keys'),
      ),
    }))
    .filter((g) => g.enlaces.length > 0);

  return (
    <Sidebar aria-label="Principal">
      <SidebarHeader className="h-auto flex-col items-start gap-1 py-3">
        <Link
          to="/"
          className={cn(
            'flex items-center gap-2 rounded-[var(--radius-control)] font-[family-name:var(--font-titulo)] text-lg font-bold text-[var(--color-lateral-texto)]',
            // Corrección 4 de U1: mismo token de foco que los enlaces de la barra (WCAG 2.2 §1.4.11)
            'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-foco-lateral)]',
          )}
        >
          <span
            aria-hidden="true"
            className="flex size-7 shrink-0 items-center justify-center rounded-[var(--radius-control)] bg-[var(--color-primario)]"
          >
            P
          </span>
          {/*
           * Corrección 4 de U1 (punto 6): contraída (64 px), "Pilot" ya no cabe junto a la insignia
           * "P" y se desborda bajo la barra superior. Se oculta visualmente (sr-only) y se conserva
           * como nombre accesible del enlace, igual que el texto de cada enlace de SidebarMenuButton.
           */}
          <span className={contraida ? 'sr-only' : 'truncate'}>Pilot</span>
        </Link>
        {/* Espacio de trabajo activo (ADR-029); se oculta con la barra contraída, como los títulos de grupo */}
        {!contraida && (
          <span className="truncate pl-9 text-xs text-[var(--color-lateral-texto)]">
            {empresaActiva.nombreEmpresa}
          </span>
        )}
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarMenu>
            {enlacesSueltos.map((enlace) => (
              <SidebarMenuItem key={enlace.href}>
                <SidebarMenuButton
                  asChild
                  etiqueta={enlace.etiqueta}
                  activo={enlace.href === '/' ? pathname === '/' : pathname.startsWith(enlace.href)}
                >
                  <Link to={enlace.href}>
                    <enlace.Icono aria-hidden="true" className="size-4 shrink-0" />
                    {/* Contraída: el texto sigue en el DOM (nombre accesible) pero no se ve; lo muestra el tooltip */}
                    <span className={contraida ? 'sr-only' : 'truncate'}>{enlace.etiqueta}</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            ))}
          </SidebarMenu>
        </SidebarGroup>
        {grupos.map((grupo) => (
          <SidebarGroup key={grupo.titulo}>
            <SidebarGroupLabel>{grupo.titulo}</SidebarGroupLabel>
            <SidebarMenu>
              {grupo.enlaces.map((enlace) => (
                <SidebarMenuItem key={enlace.href}>
                  <SidebarMenuButton
                    asChild
                    etiqueta={enlace.etiqueta}
                    activo={pathname.startsWith(enlace.href)}
                  >
                    <Link to={enlace.href}>
                      <enlace.Icono aria-hidden="true" className="size-4 shrink-0" />
                      <span className={contraida ? 'sr-only' : 'truncate'}>{enlace.etiqueta}</span>
                    </Link>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroup>
        ))}
      </SidebarContent>
    </Sidebar>
  );
}
