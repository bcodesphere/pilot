# ADR-043 — Sistema de diseño propio y navegación por procesos de negocio

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
El frontend de F0 a F4 no tiene sistema de diseño: `index.css` solo importa Tailwind, los botones usan el negro por defecto, no hay navegación lateral ni tablero, las tablas no siguen convenciones contables y hay textos internos visibles. El protocolo de revisión no tenía criterios de UI/UX. El usuario pidió el 2026-09-27 un rediseño profesional que no parezca genérico, con la paleta del login y tomando como referencia sistemas contables reales (spec, decisión D6).

## Decisión
1. **Tokens propios** en `frontend/src/index.css` (tema de Tailwind v4), basados en el tema de login: primario `#1d4ed8`, primario oscuro `#1e3a8a`, lienzo `#f1f5f9`, barra lateral con el tono de la tarjeta oscura del login; más los colores semánticos de éxito, alerta, error y revertido (spec §7.2). Deudor y Acreedor se distinguen con una etiqueta D/A neutra.
2. **Tipografía Red Hat** (Display, Text y Mono, las familias de PatternFly del login), servida localmente con `@fontsource`, y cifras tabulares en todos los montos.
3. **Navegación por procesos**, al estilo de QuickBooks, Xero, Odoo y Alegra: barra lateral (Inicio, Ingresos, Gastos, Bancos y efectivo, Activos fijos, Contabilidad, Reportes, Integraciones, Configuración), barra superior con migas de pan, buscador `Ctrl+K` y el botón "+ Registrar" con las operaciones guiadas. El lanzador de apps pasa a Configuración → Apps.
4. **Componentes de dominio** comunes (`Monto`, `CodigoCuenta`, `EtiquetaSaldo`, `EstadoDocumento`, `TablaContable`, `BarraFiltrosReporte`, `MenuExportar`, `EstadoVacio`, `Pendiente`, `ValorBloqueado`) y un catálogo único de mensajes de error con su acción.
5. **Documento operativo** `docs/diseno/sistema-de-diseno.md` y **sección G (UI/UX)** en el protocolo de revisión: un incumplimiento es un hallazgo Mayor, y el e2e en Chrome es la puerta de cada tarea de frontend.
6. El login y el registro de Keycloak no cambian. Solo se implementa el tema claro; los tokens quedan preparados para uno oscuro.

## Alternativas consideradas
- **Mantener shadcn/ui con sus valores por defecto:** es precisamente la apariencia genérica que se quiere evitar.
- **Una biblioteca de componentes empresarial (p. ej. PatternFly completo):** coincidiría con el login, pero duplicaría shadcn/ui, que ya está en el stack.

## Consecuencias
- Se agregan componentes shadcn (`sidebar`, `command`, `dropdown-menu`, `table`, `tabs`, `tooltip`, `sonner`, `skeleton`, `popover`, `calendar`, `toggle-group`, `breadcrumb`, `collapsible`, `chart`) y paquetes `@fontsource`, con versiones exactas verificadas en cada tarea.
- Los selectores de los e2e de Playwright cambian donde cambia la navegación, en la misma tarea que la cambia.
- Todo prompt de frontend incluye criterios de UX medibles.
