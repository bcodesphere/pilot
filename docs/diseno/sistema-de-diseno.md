# Sistema de diseño de Pilot

> Documento operativo de ADR-043. Fuente de la decisión: `docs/diseno/2026-09-27-automatizacion-y-rediseno.md` §7 y §8. Lo mantiene el agente de frontend en cada tarea que toque `frontend/src/compartido/ui`, `frontend/src/compartido/dominio` o `frontend/src/nucleo/estructura`.

## 1. Principios (spec §7.1)

1. **Primero el dato.** Densidad de sistema contable: filas de 36 px, bordes finos de 1 px, sin sombras decorativas.
2. **Lenguaje de negocio en la entrada, lenguaje contable en la evidencia.** Los formularios hablan de "venta" y "forma de cobro"; el panel del asiento, los libros y los reportes usan cuentas, Debe y Haber.
3. **Cada pantalla dice qué hacer después:** estados vacíos con acción, pendientes con acción, errores con la solución (nunca un código crudo).
4. **Teclado primero** en la captura: atajos, `Enter` para avanzar, foco visible de 2 px.
5. **Nada genérico:** sin degradados, sin tarjetas con bordes muy redondeados en todas partes, sin títulos gigantes centrados, sin íconos decorativos, sin sombras de colores, sin textos de relleno.

## 2. Tokens (`frontend/src/index.css`)

Todos los tokens se declaran como variables CSS dentro de `@theme` (Tailwind v4). **Ningún componente usa un color, radio o tamaño de letra fuera de esta lista** — se verifica con:

```bash
grep -rnE "#[0-9a-fA-F]{3,6}|bg-black|text-black" frontend/src/compartido frontend/src/nucleo
# Sin resultados fuera de index.css
```

| Token | Valor | Uso |
|---|---|---|
| `--color-primario` | `#1d4ed8` | Acción principal, enlaces, foco (igual que el login) |
| `--color-primario-oscuro` | `#1e3a8a` | Hover, elemento activo de la barra lateral |
| `--color-primario-suave` | `#dbeafe` | Selección de fila, fondo de pestaña/menú activo |
| `--color-lienzo` | `#f1f5f9` | Fondo de la app (igual que el login) |
| `--color-superficie` | `#ffffff` | Tablas, formularios, paneles, tarjetas |
| `--color-borde` | `#e2e8f0` | Bordes de 1 px |
| `--color-texto` | `#0f172a` | Texto principal |
| `--color-texto-suave` | `#475569` | Etiquetas, texto secundario (AA sobre blanco) |
| `--color-lateral` | `#26292d` | Barra lateral (ver nota) |
| `--color-lateral-texto` | `#cbd5e1` | Texto de la barra lateral |
| `--color-foco-lateral` | `#93c5fd` | Foco visible de los enlaces de la barra lateral y del logo "Pilot" (ver nota) |
| `--color-exito` | `#15803d` | Contabilizado, cuadra |
| `--color-alerta` | `#b45309` | Pendientes, saldo contrario a su naturaleza |
| `--color-error` | `#b91c1c` | Descuadre, rechazo |
| `--color-neutro-revertido` | `#64748b` | Revertido (con tachado sutil del monto) |
| `--color-velo` | `rgb(15 23 42 / 0.4)` | Fondo semitransparente detrás de un diálogo o del buscador (`Dialogo`, `CommandDialog`) |
| `--radius-control` | `4px` | Botones, campos, insignias |
| `--radius-panel` | `6px` | Tarjetas, paneles, menús, diálogos |

**Nota sobre `--color-lateral`:** el plan traía el valor provisional `#1b1d21`. El 2026-09-27 se confirmó contra la tarjeta real de inicio de sesión (Keycloak local, tema `pilot`, formulario "Acceder a tu cuenta"): `getComputedStyle(document.querySelector('.pf-v5-c-login__main')).backgroundColor` midió `rgb(38, 41, 45)` = **`#26292d`**. Es el valor que quedó en `index.css`.

**Nota sobre `--color-foco-lateral` (corrección 4 de U1, 2026-09-28):** el arquitecto encontró en Chrome que el anillo de foco de los enlaces de la barra lateral usaba `--color-primario` (`#1d4ed8`), que da solo **1.55:1** contra `--color-lateral` — por debajo del 3:1 que exige WCAG 2.2 §1.4.11 para indicadores de foco (sobre superficies claras el primario sí da 6.7:1 y sigue siendo el foco del resto de la app). Azul 300 (`#93c5fd`) da **8.10:1** contra `--color-lateral` (`#26292d`) y **5.75:1** contra el fondo del elemento activo de la barra (`--color-primario-oscuro`, `rgb(30 58 138)`); ambos superan el mínimo. Se usa únicamente en el `focus-visible` de `SidebarMenuButton` (`compartido/ui/sidebar.tsx`) y del logo "Pilot" (`BarraLateral.tsx`).

Deudor y Acreedor se muestran con una etiqueta **D/A** neutra (componente `EtiquetaSaldo`): nunca se les asigna "bueno" ni "malo" por color. Espaciado: escala de 4 px (Tailwind por defecto).

Las variables de shadcn/ui (`--color-primary`, `--color-background`, `--color-border`, `--color-ring`, `--color-sidebar*`…) están mapeadas a estos tokens en `index.css`, así que cualquier componente de `compartido/ui` generado con la convención de shadcn hereda la paleta automáticamente.

## 3. Tipografía (spec §7.3)

- **Red Hat Display** (`--font-titulo`): títulos (`h1`–`h6`).
- **Red Hat Text** (`--font-cuerpo`): cuerpo, por defecto en `body`, **y los montos**.
- **Red Hat Mono** (`--font-mono`): **solo** códigos de cuenta y números de asiento (clase `.codigo-cuenta`).
- Fuentes servidas localmente con `@fontsource-variable/red-hat-display`, `@fontsource-variable/red-hat-text` y `@fontsource/red-hat-mono` (versión exacta `5.3.0` de los tres). **Sin CDN**: la pestaña Red del navegador no debe mostrar peticiones a `fonts.googleapis.com` ni similares.
- **Dos clases, no una.** La spec §7.3 reserva Red Hat Mono para códigos de cuenta y números de asiento; los montos van en la fuente del cuerpo. Por eso hay dos clases:
  - `.cifra` — `font-variant-numeric: tabular-nums` únicamente (fuente del cuerpo, la hereda de `body`). La usa `Monto`.
  - `.codigo-cuenta` — `tabular-nums` **y** `font-family: var(--font-mono)`. La usa `CodigoCuenta` (y el buscador global al listar una cuenta).
- Los montos se alinean a la derecha y usan el formato `es-SV` (`$1,234.56`), nunca `Intl` (para no depender de datos del sistema) — ver `compartido/dinero`.

## 4. Componentes de dominio (`frontend/src/compartido/dominio`)

Cada uno reemplaza un patrón que antes se repetía a mano en las pantallas. **Uso correcto**: siempre a través de estos componentes cuando el dato es contable. **Uso incorrecto**: formatear un monto con `Intl.NumberFormat` o concatenar `"$" + valor`, mostrar un código de cuenta en la fuente del cuerpo, o escribir un estado vacío con un `<p>` suelto sin acción.

| Componente | Uso correcto | Uso incorrecto |
|---|---|---|
| `Monto` | Todo monto o diferencia (`valor` = cadena decimal del contrato) | `{"$" + monto}`, `toLocaleString()`, o mostrar el monto sin `.cifra` |
| `CodigoCuenta` | Código de cuenta en tablas, buscador, migas de pan | Mostrar el código en la fuente del cuerpo o sin agrupar con el nombre |
| `EtiquetaSaldo` | Saldo de Mayor/Balanza con su lado D/A | Colorear el saldo (verde/rojo) para indicar Deudor/Acreedor |
| `EstadoDocumento` | Barra de estado de un asiento u operación (Contabilizado/Revertido) | Un `Badge` genérico con el estado en mayúsculas crudas (`CONTABILIZADO`) |
| `TablaContable` | Toda tabla de datos contables (Libro Diario, Mayor, Balanza, reportes) | Una `<table>` hecha a mano sin esqueleto de carga ni estado vacío |
| `BarraFiltrosReporte` | Filtros de período + extras + `MenuExportar` de un reporte | Repetir los campos Desde/Hasta y los botones de atajo en cada pantalla |
| `MenuExportar` | El botón "Exportar ▾" de cualquier reporte | Tres botones sueltos "PDF"/"Excel"/"CSV" |
| `EstadoVacio` | Una lista o pantalla sin datos, siempre con `titulo` + `descripcion` + `accion` cuando aplica | Un `<p>` con "No hay datos" sin decir qué hacer |
| `Pendiente` | Una fila accionable del tablero de Inicio | Un aviso sin `accion.href` |
| `ValorBloqueado` | Un valor que ADR-042 no permite editar, con el motivo en el candado | Deshabilitar un `<input>` dejándolo visible como si se pudiera intentar escribir |
| `useAtajo` | Registrar un atajo de una tecla (`N`) o `Ctrl+tecla` (`Ctrl+K`) | Un `addEventListener('keydown', …)` a mano en un componente |
| `catalogoErrores` (`mensajeDeError`) | Traducir cualquier código `PLT-`/`CON-`/`INT-` a un mensaje | Mostrar `error.codigo` o `error.detail` directamente en la pantalla |

## 5. Estructura del shell (`frontend/src/nucleo/estructura`)

- **`EstructuraApp`** = `SidebarProvider` + `BarraLateral` + `BarraSuperior` + `<Outlet/>` dentro de `Suspense`. Es lo que renderiza `nucleo/Layout.tsx` para toda ruta autenticada.
- **Carga por ruta.** Las páginas del núcleo (`nucleo/paginasPerezosas.tsx`) y el módulo de cada app (`nucleo/apps/registro.ts`, `RutaApp`) se importan con `React.lazy` bajo demanda: ninguna entra al fragmento de entrada hasta que se navega a su ruta. `EstructuraApp` muestra un esqueleto (`Skeleton`) mientras se descarga el fragmento. Las bibliotecas grandes de terceros (React, React Router, Radix, `cmdk`…) se separan en sus propios fragmentos con `manualChunks` (`vite.config.ts`): ningún fragmento de producción supera 500 KB.
- **`BarraLateral`** (`compartido/ui/sidebar`): fija, con el tono de la tarjeta del login (`--color-lateral`). Por debajo de **1280 px** se contrae a íconos con `Tooltip`. Grupos (spec §7.4): enlaces sueltos "Inicio" y "Reportes"; grupo "Contabilidad" (Libro Diario, Mayor, Catálogo); grupo "Integraciones" (API keys); grupo "Configuración" (Espacio de trabajo, Contabilidad, Apps, Mi perfil). Los grupos de pantallas que aún no existen (Ingresos, Gastos, Bancos y efectivo, Activos fijos) no aparecen hasta que la tarea que los construye (U3, U5) agregue sus rutas — nunca un enlace roto.
  - Los enlaces que coinciden en texto con la subnavegación propia de una app (p. ej. "Libro Diario" también existe dentro de `LayoutContabilidad`) llevan un `aria-label` que añade "barra lateral" como contexto (WCAG 2.5.3: el texto visible es un prefijo del nombre accesible), para que ambos sigan siendo distinguibles por lectores de pantalla y por las pruebas.
- **`BarraSuperior`**: migas de pan (`MigasDePan`), `SelectorEmpresa` (solo con más de una membresía), `BuscadorGlobal`, `MenuRegistrar`, `MenuUsuario`.
- **`MigasDePan`**: función pura `migasDeRuta(pathname)` + el componente que la renderiza con `compartido/ui/breadcrumb`. El último nivel no es un enlace (`BreadcrumbPage`, `aria-current="page"`).
- **`BuscadorGlobal`** (`Ctrl+K`, `compartido/ui/command` sobre `cmdk`): pantallas conocidas del shell (siempre) y cuentas del catálogo (`GET /contabilidad/cuentas`, solo con Contabilidad instalada y el diálogo abierto). Una cuenta lleva a `/contabilidad/mayor?cuentaId=`.
- **`MenuRegistrar`** (`+ Registrar`, atajo `N`): lee `estructura/registroOperaciones.ts`, un registro que cada operación guiada agrega cuando su pantalla exista (U3). En 1.0-U1 solo tiene "Asiento manual (avanzado)". Se oculta si Contabilidad no está instalada.
- Ningún atajo de una sola letra (`N`) se dispara con el foco en un `input`, `textarea`, `select` o un elemento `contenteditable` (`compartido/dominio/useAtajo`); `Ctrl+K`/`Ctrl+letra` sí, sin importar el foco.

## 6. Patrones

### 6.1 Tabla contable

`TablaContable` da el patrón único: encabezado fijo, fila de totales dentro de un `<tfoot>` (con `BarraFiltrosReporte`/`totales` propio de cada pantalla), filas de 36 px, filas expandibles opcionales (el detalle aparece en una fila adicional al hacer clic en el botón con `aria-expanded`). Mientras `cargando` es `true` se muestra un esqueleto (`Skeleton`) con `role="status"`; sin filas y sin cargar, el nodo `vacio` (normalmente un `EstadoVacio`).

### 6.2 Formulario (guía para U2/U3)

- Selectores segmentados (`compartido/ui/toggle-group`) para elegir entre pocas opciones excluyentes (forma de cobro, modo de precio).
- A la derecha, el panel fijo de vista previa que trae el backend (nunca calculado en el cliente, ADR-006).
- `Enter` avanza al siguiente campo o agrega una línea; `Ctrl+Enter` guarda.

### 6.3 Documento (asiento, operación)

- `EstadoDocumento` arriba, seguido de los datos capturados y del asiento generado (`TablaContable` de solo lectura). El historial de auditoría y la acción "Revertir" van al final.

### 6.4 Reporte

- `BarraFiltrosReporte` (período + filtros propios + `MenuExportar`) seguida del encabezado del informe (espacio de trabajo, período, fecha de generación) y la `TablaContable`. Toda pantalla de reporte lleva la leyenda de estado de gestión cuando aplica (Estado de Situación Financiera, Estado de Resultados).

## 7. Textos y tono (spec §7.6)

- Español de El Salvador, trato de "tú", verbos concretos ("Registrar venta", nunca "Enviar").
- **Ningún texto visible cita un documento interno** (CLAUDE.md, un ADR, un nombre de tabla), **ni un código de error crudo**, **ni un booleano** (`true`/`false`); todo pasa por `catalogoErrores` o se traduce a una frase ("Sí"/"No", "Contabilizado"/"Revertido").
- Cada código de error tiene un mensaje humano y, cuando resuelve algo, una acción con `href`. Ejemplos (`compartido/errores/catalogoErrores.ts`):

| Código | Mensaje | Acción |
|---|---|---|
| `CON-005` | "El asiento no cuadra por $13.00." | — |
| `CON-020` | "Falta configurar la cuenta para «Otro gasto»." | "Configurar" → `/configuracion/contabilidad#reglas` |
| `PLT-004` | "Esta aplicación no está instalada en tu espacio de trabajo." | "Ir a Apps" → `/configuracion/apps` |

## 8. Atajos de teclado

| Atajo | Acción | Dónde |
|---|---|---|
| `Ctrl+K` (`Cmd+K` en Mac) | Abre el buscador global | En cualquier pantalla del shell |
| `N` | Abre "+ Registrar" | En cualquier pantalla, salvo con el foco en un campo de texto |
| `Escape` | Cierra el diálogo o menú abierto | Buscador, menús, diálogos |
| `Enter` (en `TablaContable` expandible) | — | Activa el botón enfocado como cualquier botón nativo |

## 9. Fichas de pantalla (spec §8)

Estado de esta entrega (U1): **estructura y componentes**. Las pantallas de negocio (Ingresos, Gastos, Bancos, Activos, tablero de Inicio) las construyen U2 a U6; aquí se documenta lo que ya existe o queda listo para ellas.

| Pantalla | Estado en U1 | Notas |
|---|---|---|
| **Inicio** | Estado vacío | Sin Contabilidad instalada: `EstadoVacio` "Instala Contabilidad para empezar" → Apps. Con ella instalada: estado vacío provisional "El tablero llega en la siguiente entrega" con enlaces a Libro Diario y Reportes; lo reemplaza U4. Muestra también el aviso de redirección de `RutaApp` (app no instalada, `PLT-004`) |
| **+ Registrar** | Construido (menú) | Agrupado por `grupo` de `registroOperaciones`; en U1 solo "Asiento manual (avanzado)", sin grupo, con el atajo `N` visible en el ítem |
| **Formulario de operación** | Pendiente (U3) | Usará `TablaContable`, `Monto`, `toggle-group` y el panel de vista previa del backend |
| **Listas de Ingresos, Gastos y Bancos** | Pendientes (U3/U5) | — |
| **Detalle de operación o asiento** | Pendiente (U3); `EstadoDocumento` ya disponible | — |
| **Bancos y efectivo** | Pendiente (U5) | — |
| **Activos fijos** | Pendiente (U5) | — |
| **Libro Diario** | Rediseñada (U2, F4.5 paso 4) | Lista con filas expandibles a las líneas del asiento (se piden solo al expandir) y filtro de Origen (Manual, n8n, Reversión; "Operación" queda anotada para cuando el contrato de C1 lo agregue a `OrigenAsiento`). El detalle usa `EstadoDocumento` y una sección "Historial" al final con `creadoEn` (sin `creadoPor`: no está en el contrato, ver "Solicitudes" del reporte de U2) y ahí vive el botón "Revertir" |
| **Asiento manual (avanzado)** | Rediseñada (U2, F4.5 paso 4) | Cuentas recientes primero en el selector de cada línea (preferencia local en `localStorage`, `useCuentasRecientes`); `Enter` en el Debe o el Haber de la última línea agrega una línea; pie fijo (`sticky`) con los totales, la etiqueta Cuadra/No cuadra y Guardar/Cancelar; botón "Cuadrar con esta línea" en una línea en blanco, que completa el lado que corresponde con la diferencia exacta (`decimal.js`) |
| **Mayor** | Rediseñada (U2, F4.5 paso 3) | `BarraFiltrosReporte`, `MenuExportar`, `CodigoCuenta`, `Monto` y `EtiquetaSaldo` (saldo inicial junto al nombre de la cuenta; saldo final y totales en el pie de `TablaContable`) |
| **Catálogo** | Existente (F2) | Alcanzable desde la barra lateral; su rediseño (árbol, `ValorBloqueado`, naturaleza derivada) es la fase B de U2 (paso 3), después del contrato de C1 |
| **Reportes** | Rediseñada (U2, F4.5 paso 1 y 2) | Los seis reportes usan `MenuExportar`; Balanza, Estado de Resultados y Mayor además usan `BarraFiltrosReporte` (rango `desde`/`hasta`). El Estado de Situación Financiera (fecha de corte) y el Resumen de IVA (año y mes) no encajan en `BarraFiltrosReporte` — pensado para un rango — así que conservan su propio control de período junto a `MenuExportar`. Todos muestran `EncabezadoInforme` (espacio de trabajo, período, "Generado el …" en hora de El Salvador). Balanza: los cuatro totales (Debe, Haber, saldo deudor, saldo acreedor) en el `<tfoot>` de `TablaContable` y la etiqueta "Cuadra" o la diferencia exacta. ESF en dos columnas desde 1280 px (`xl:grid-cols-2`). Diagnóstico usa el texto "combinaciones de cuenta, año y mes" en ambos resultados. El Resumen de IVA ya no repite la nota del backend con la cita a CLAUDE.md: usa su propio texto en el frontend |
| **Integraciones** | Solo API keys (existente) | La bitácora de n8n llega con F5-03/U6 |
| **Configuración** | Existente (Espacio de trabajo, Apps, Perfil); Contabilidad (modo de precio y reglas) sigue con su pantalla de F2 | El agrupamiento por tipo de operación en la pantalla de reglas es trabajo de U2/B3 |

**Diseño adaptable:** escritorio primero (1440 px y 1280 px, quiebre de la barra en 1280 px); 1024 px sin desbordes horizontales. El teléfono (390 px, solo Inicio y reportes de lectura) queda para cuando existan esas pantallas de negocio.

**Accesibilidad:** contraste AA en la barra lateral y la superior; foco visible de 2 px en todo control interactivo, con el color primario (`--color-primario`) salvo dentro de la barra lateral, que usa `--color-foco-lateral` para cumplir el 3:1 de WCAG 2.2 §1.4.11 sobre su fondo oscuro (ver nota del token en la sección 2); navegación completa por teclado (`Tab`, `Escape`, los atajos de la sección 8).
