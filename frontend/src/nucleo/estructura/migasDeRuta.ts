/** Un nivel de la ruta: `href` ausente en el último (la pantalla actual, sin enlace). */
export interface Miga {
  etiqueta: string;
  href?: string;
}

/** Segmentos con nombre fijo, de más específico a más general (para no listar cada ruta dos veces). */
const SEGMENTOS: { patron: RegExp; migas: Miga[] }[] = [
  { patron: /^\/perfil$/, migas: [{ etiqueta: 'Mi perfil' }] },
  {
    patron: /^\/configuracion\/espacio$/,
    migas: [{ etiqueta: 'Configuración', href: '/configuracion/apps' }, { etiqueta: 'Espacio de trabajo' }],
  },
  {
    patron: /^\/configuracion\/api-keys$/,
    migas: [{ etiqueta: 'Configuración', href: '/configuracion/apps' }, { etiqueta: 'API keys' }],
  },
  {
    patron: /^\/configuracion\/apps$/,
    migas: [{ etiqueta: 'Configuración', href: '/configuracion/apps' }, { etiqueta: 'Apps' }],
  },
  {
    patron: /^\/contabilidad\/libro-diario\/nuevo$/,
    migas: [{ etiqueta: 'Libro Diario', href: '/contabilidad/libro-diario' }, { etiqueta: 'Asiento manual' }],
  },
  {
    patron: /^\/contabilidad\/libro-diario\/[^/]+$/,
    migas: [{ etiqueta: 'Libro Diario', href: '/contabilidad/libro-diario' }, { etiqueta: 'Asiento' }],
  },
  { patron: /^\/contabilidad\/libro-diario$/, migas: [{ etiqueta: 'Libro Diario' }] },
  { patron: /^\/contabilidad\/mayor$/, migas: [{ etiqueta: 'Mayor' }] },
  { patron: /^\/contabilidad\/catalogo$/, migas: [{ etiqueta: 'Catálogo de cuentas' }] },
  {
    patron: /^\/contabilidad\/configuracion$/,
    migas: [{ etiqueta: 'Configuración', href: '/configuracion/apps' }, { etiqueta: 'Contabilidad' }],
  },
  {
    patron: /^\/contabilidad\/reglas$/,
    migas: [
      { etiqueta: 'Configuración', href: '/configuracion/apps' },
      { etiqueta: 'Reglas de contabilización' },
    ],
  },
  {
    patron: /^\/contabilidad\/reportes\/balanza$/,
    migas: [
      { etiqueta: 'Reportes', href: '/contabilidad/reportes' },
      { etiqueta: 'Balanza de Comprobación' },
    ],
  },
  {
    patron: /^\/contabilidad\/reportes\/situacion-financiera$/,
    migas: [
      { etiqueta: 'Reportes', href: '/contabilidad/reportes' },
      { etiqueta: 'Estado de Situación Financiera' },
    ],
  },
  {
    patron: /^\/contabilidad\/reportes\/resultados$/,
    migas: [{ etiqueta: 'Reportes', href: '/contabilidad/reportes' }, { etiqueta: 'Estado de Resultados' }],
  },
  {
    patron: /^\/contabilidad\/reportes\/iva$/,
    migas: [{ etiqueta: 'Reportes', href: '/contabilidad/reportes' }, { etiqueta: 'Resumen de IVA' }],
  },
  {
    patron: /^\/contabilidad\/reportes\/diagnostico$/,
    migas: [
      { etiqueta: 'Reportes', href: '/contabilidad/reportes' },
      { etiqueta: 'Diagnóstico de mayorización' },
    ],
  },
  { patron: /^\/contabilidad\/reportes$/, migas: [{ etiqueta: 'Reportes' }] },
];

/**
 * Calcula las migas de una ruta del shell (función pura, se prueba sin renderizar). `/` (Inicio)
 * siempre encabeza la ruta, salvo que ya sea la propia raíz. Separada de `MigasDePan.tsx` (un
 * componente) para no mezclar una función exportada con un componente en el mismo archivo.
 * @param pathname `location.pathname` actual
 */
export function migasDeRuta(pathname: string): Miga[] {
  if (pathname === '/') return [{ etiqueta: 'Inicio' }];
  const conocida = SEGMENTOS.find((s) => s.patron.test(pathname));
  const resto = conocida?.migas ?? [{ etiqueta: pathname.split('/').filter(Boolean).pop() ?? pathname }];
  return [{ etiqueta: 'Inicio', href: '/' }, ...resto];
}
