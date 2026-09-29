# F4.5 — Automatización y experiencia: plan de implementación

> **Para quien ejecuta:** este plan lo ejecuta el flujo de Pilot. El arquitecto (sesión `arquitecto`) convierte cada tarea en un prompt con la plantilla de `.orquestacion/prompts/plantilla.md`, lo envía a una sesión Sonnet nombrada con el ID de la tarea y revisa la entrega con `protocolo-revision.md` (incluida la nueva sección G). Los pasos usan casillas (`- [ ]`) para el seguimiento. Los commits los hace el arquitecto **solo cuando el usuario lo pide**, con autor y committer `Harpin067 <stevenbeltran067@gmail.com>`, sin `Co-authored-by`.

**Objetivo:** el usuario registra hechos de negocio (vender, comprar, cobrar, pagar, depreciar) y Pilot genera, valida, numera, mayoriza y audita el asiento. Además se bloquea lo que define cómo contabiliza Pilot y se rediseña todo el frontend con un sistema de diseño propio.

**Arquitectura:**
- Un motor único `ContabilizarOperacion` en `contabilidad`, con una estrategia (armador) por tipo de operación que produce un `AsientoExpandido`. Ese asiento pasa por las mismas validaciones y por el mismo `GuardarAsiento` que un asiento manual (ADR-018).
- Las cuentas salen de reglas precargadas, restringidas por grupo (`prefijo_permitido`).
- El frontend solo captura datos de negocio y muestra la vista previa que calcula el backend.

**Tecnología:** Java 21, Spring Boot 4.1, Spring Modulith, JdbcClient, Flyway, PostgreSQL 17 con RLS, OpenAPI 3.1 contract-first; React 19, TypeScript strict, Tailwind v4, shadcn/ui, TanStack Query, Orval, decimal.js, Vitest y Playwright.

**Spec:** `docs/diseno/2026-09-27-automatizacion-y-rediseno.md` (aprobado el 2026-09-27). Todo ejecutor lee el spec y este plan.

## Restricciones globales

- Reglas críticas de la guía técnica §1.1 y §1.2 sin excepción: `BigDecimal`/`NUMERIC(19,2)`/`decimal.js`, `HALF_UP` a 2 decimales, RLS forzado **y** `empresa_id` en cada sentencia (regla 1.1.3), `Idempotency-Key` en cada `POST` que guarda, contract-first, migraciones nuevas (nunca editar V1–V15), comentarios según §8.2.
- Ninguna lógica contable en el frontend (ADR-006). La vista previa siempre viene del backend.
- Nunca dos tareas de `backend/` a la vez. En los prompts de tareas en paralelo quedan prohibidos `git stash`, `checkout`, `reset` y `restore`.
- Códigos de error nuevos: solo `CON-021`, `CON-022` y `CON-023`, registrados en la guía técnica §10.2 **antes** de usarlos.
- Versiones exactas de toda dependencia nueva, verificadas en Maven Central o npm el día de la tarea.
- Español de El Salvador en la interfaz, trato de "tú"; ningún texto visible cita documentos internos, booleanos crudos ni códigos sin traducir.
- Colores, radios y tamaños solo desde los tokens de `frontend/src/index.css` (spec §7.2). Login y registro de Keycloak intactos.
- Valores `[VERIFICAR]` (vidas útiles, residual, mes de inicio, cuentas por defecto) se cargan como borrador. La depreciación queda bloqueada con `CON-023` mientras `plantilla_vida_util.confirmada = false`.

## Foco de revisión

Situaciones que el spec implica y que ninguna prueba natural cubriría. Cada una tiene su prueba en la tarea indicada.

1. **Doble clic en "Guardar" o reintento de red:** debe quedar una sola operación y un solo asiento. Prueba en B2 (misma `Idempotency-Key` en paralelo → un asiento; otra clave con el mismo cuerpo → dos operaciones legítimas).
2. **Depreciación del mismo mes lanzada dos veces a la vez:** debe quedar una sola fila por activo y mes. Prueba en B4 (dos hilos → uno 201 y el otro 409 `PLT-008` por el índice único `uq_depreciacion_vigente`; sin código nuevo).
3. **Empresa creada antes de V16 (como "tester beltran"):** las operaciones guiadas deben funcionar sin reinstalar la app. Prueba en B1 con Flyway `target=15` → datos → migración completa.
4. **Regla desactivada o apuntando a una cuenta que luego se desactiva:** debe responder `CON-020` en la vista previa y al guardar, sin filas nuevas. Prueba en B2.
5. **Pantalla abierta con datos viejos:** el usuario cambia el tipo de operación o el modo de precio mientras la vista previa anterior sigue en vuelo; debe mostrarse siempre la respuesta de los últimos datos. Prueba en U3 (se descarta la respuesta de una petición anterior).

---

## Mapa de archivos

### Backend: `backend/src/main/java/com/bcodesphere/pilot/contabilidad/`

| Ruta | Responsabilidad | Tarea |
|---|---|---|
| `ContabilizarOperacion.java` (raíz, pública) | Interfaz del motor | B2 |
| `OperacionContable.java`, `OperacionContabilizada.java`, `CobrosNoCuadranException.java` (raíz) | Tipos públicos del motor (los usa `integracion`) | B2 |
| `dominio/operaciones/TipoOperacion.java` | Enum de los 11 tipos | B2 |
| `dominio/operaciones/DatosOperacion.java` | `sealed interface` con un `record` por tipo | B2 |
| `dominio/operaciones/ArmadorAsiento.java` | `AsientoExpandido armar(D datos, ContextoArmado ctx)` | B2 |
| `dominio/operaciones/Armador*.java` | Un armador por tipo | B2, B4 |
| `dominio/operaciones/ContextoArmado.java` | Fecha, tasa, modo, resolutor de cuentas, cuentas de IVA | B2 |
| `dominio/operaciones/ResolutorCuentas.java` | Interfaz (tipo, categoría, código) → `ResumenCuenta` con `CON-020` | B2 |
| `dominio/operaciones/LineasBuilder.java` | Arma `LineaExpandida` numeradas y omite las líneas en cero | B2 |
| `dominio/activos/*` | `CategoriaActivo`, `ActivoFijo`, `CalculadoraDepreciacion` | B4 |
| `dominio/tablero/*` | Registros del tablero | B5 |
| `aplicacion/ServicioContabilizarOperacion.java` | Implementa `ContabilizarOperacion` | B2 |
| `aplicacion/RegistrarOperacion.java`, `PrevisualizarOperacion.java` | Casos de uso de la API manual | B2 |
| `aplicacion/RepositorioOperaciones.java`, `ConsultarOperaciones.java` | Guardado y listado | B2 |
| `aplicacion/OyenteReversionOperacion.java` | `AsientoRevertido` → operación `REVERTIDA` (y la depreciación) | B2, B4 |
| `aplicacion/RepositorioActivos.java`, `RegistrarDepreciacion.java` | Activos | B4 |
| `aplicacion/ConsultarTablero.java` | Tablero | B5 |
| `api/ControladorOperacionesIngresos.java`, `…Gastos`, `…Bancos`, `…Activos`, `ControladorOperaciones.java`, `ControladorTablero.java` | Implementan las interfaces generadas | B2, B4, B5 |
| `infraestructura/RepositorioOperacionesJdbc.java`, `ResolutorCuentasJdbc.java`, `RepositorioActivosJdbc.java`, `ConsultaTableroJdbc.java` | SQL con `empresa_id` | B2, B4, B5 |

### Migraciones (`backend/src/main/resources/db/migration/`)

| Archivo | Contenido | Tarea |
|---|---|---|
| `V16__operaciones_guiadas.sql` | Tabla `operacion`, CHECK de `asiento.origen_tipo`, tipos y categorías de reglas ampliados, `prefijo_permitido`, reglas nuevas, copia a las empresas existentes | B1 |
| `V17__cuentas_del_sistema.sql` | `cuenta_contable.sistema`, marcado en las empresas existentes, permisos | B1 |
| `V18__activos_fijos.sql` | `activo_fijo`, `depreciacion_registrada`, `plantilla_vida_util` | B1 |
| `V19__operaciones_externas.sql` | `operacion_externa`, `intento_operacion_externa` | F5-03 |

### Frontend (`frontend/src/`)

| Ruta | Responsabilidad | Tarea |
|---|---|---|
| `index.css` | Tokens (spec §7.2), fuentes, base | U1 |
| `nucleo/estructura/BarraLateral.tsx`, `BarraSuperior.tsx`, `MigasDePan.tsx`, `BuscadorGlobal.tsx`, `MenuRegistrar.tsx`, `EstructuraApp.tsx` | Estructura de la app | U1 |
| `compartido/dominio/Monto.tsx`, `CodigoCuenta.tsx`, `EtiquetaSaldo.tsx`, `EstadoDocumento.tsx`, `TablaContable.tsx`, `BarraFiltrosReporte.tsx`, `MenuExportar.tsx`, `EstadoVacio.tsx`, `Pendiente.tsx`, `ValorBloqueado.tsx` | Componentes de dominio | U1 |
| `compartido/errores/catalogoErrores.ts` | Código → mensaje y acción | U1 |
| `compartido/ui/*` | Componentes shadcn nuevos | U1 |
| `apps/contabilidad/**` | Rediseño de lo existente | U2 |
| `apps/contabilidad/operaciones/**` | Formularios, panel del asiento, listas y detalle | U3 |
| `apps/contabilidad/inicio/**` | Tablero | U4 |
| `apps/contabilidad/activos/**` | Activos fijos | U5 |
| `apps/contabilidad/operaciones-n8n/**` | Bitácora | U6 |
| `docs/diseno/sistema-de-diseno.md` | Documento operativo del sistema de diseño | U1 |

---

## Oleada 0 — Arquitecto

### Tarea A0: decisiones, documentos, protocolo y prueba del generador

**Archivos:**
- Crear: `docs/adr/ADR-041-motor-de-operaciones-guiadas.md`, `ADR-042-bloqueos-de-edicion.md`, `ADR-043-sistema-de-diseno-y-navegacion.md`
- Modificar: `docs/adr/README.md`, `CLAUDE.md` (§2.2 filas nuevas, §9.3 tablas nuevas, §10.2 `CON-021`/`CON-022`/`CON-023`, §11.3 configuración solo con el modo, §13 rutas nuevas, §17 fase F4.5, §18 ADR, §19, fecha), `docs/plan-de-trabajo.md` (fase F4.5 con los criterios de la spec §2), `AGENTS.md` (§3.7 criterios de UX, §3.4 motor de operaciones)
- Modificar: `.orquestacion/protocolo-revision.md` (sección G de la spec §9.1), `.orquestacion/prompts/plantilla.md` (bloque "Criterios de UX" obligatorio en las tareas de frontend)
- Probar en una copia (`git clone --no-hardlinks`): el generador con la forma de C1

- [ ] **Paso 1:** escribir ADR-041, ADR-042 y ADR-043 a partir de la spec §5, §6 y §7 (contexto, decisión, alternativas, consecuencias).
- [ ] **Paso 2:** registrar `CON-021` (422, cuenta del sistema), `CON-022` (422, cuenta fuera del grupo permitido) y `CON-023` (422, vida útil sin confirmar) en la tabla de §10.2 de la guía técnica.
- [ ] **Paso 3:** agregar la sección G al protocolo y el bloque "Criterios de UX" a la plantilla.
- [ ] **Paso 4:** en la copia, agregar al contrato una operación `POST /contabilidad/operaciones/ventas` con la etiqueta `operacionesIngresos`, un cuerpo `VentaSolicitud` tipado y la respuesta `OperacionRegistrada`. Ejecutar `./mvnw -q generate-sources compile` y revisar la interfaz generada en `target/generated-sources`: métodos, tipos y ningún import de Jackson 2.

  Esperado: `ResponseEntity<OperacionRegistrada> registrarVenta(String idempotencyKey, VentaSolicitud venta)`.
- [ ] **Paso 5:** registrar en `.orquestacion/estado.md` la fase F4.5 con las tareas de este plan en estado PENDIENTE.

---

## Oleada 1

### Tarea F4-07: corrección del e2e de F4 (backend)
El prompt ya está escrito en `.orquestacion/prompts/F4-07-correccion-e2e-backend.md` (conteo de combinaciones del diagnóstico y "Sí/No" en los PDF). Se ejecuta tal cual.

### Tarea F4-06: aceptación de F4
El prompt ya está escrito en `.orquestacion/prompts/F4-06-aceptacion-f4.md`. Corre después de F4-07. Cierra F4.

### Tarea U1: sistema de diseño y estructura de la app (Frontend, en paralelo con F4-07/F4-06)

**Archivos:**
- Modificar: `frontend/src/index.css`, `frontend/package.json` (fuentes y componentes; versiones exactas)
- Crear: `frontend/src/compartido/ui/{sidebar,command,dropdown-menu,table,tabs,tooltip,sonner,skeleton,popover,calendar,toggle-group,breadcrumb,collapsible}.tsx` (con `pnpm dlx shadcn@<versión fijada> add …`)
- Crear: `frontend/src/nucleo/estructura/{EstructuraApp,BarraLateral,BarraSuperior,MigasDePan,BuscadorGlobal,MenuRegistrar}.tsx` y sus pruebas
- Crear: `frontend/src/compartido/dominio/*.tsx` (lista del mapa) y sus pruebas
- Crear: `frontend/src/compartido/errores/catalogoErrores.ts` y su prueba
- Modificar: `frontend/src/nucleo/router.tsx`, `Layout.tsx`, `PaginaApps.tsx` (pasa a Configuración → Apps), `PaginaInicio.tsx` (queda como estado vacío hasta U4)
- Modificar: `frontend/e2e/soporte/**` y los selectores de `e2e/f1..f3` **solo** donde la navegación cambió
- Crear: `docs/diseno/sistema-de-diseno.md`

**Interfaces:**
- Produce:
  - `Monto({ valor: string; conSigno?: boolean; tachado?: boolean })`;
  - `CodigoCuenta({ codigo: string; nombre?: string })`;
  - `EtiquetaSaldo({ saldo: Saldo })` (usa el `Saldo` generado por Orval);
  - `EstadoDocumento({ estado: 'CONTABILIZADO' | 'REVERTIDO'; enlaceReversion?: string })`;
  - `TablaContable<T>({ columnas: ColumnaContable<T>[]; filas: T[]; totales?: ReactNode; filaExpandible?: (f: T) => ReactNode; cargando: boolean; vacio: ReactNode })`;
  - `BarraFiltrosReporte({ periodo, onPeriodo, extras?: ReactNode, exportar?: ReactNode })`;
  - `MenuExportar({ onExportar: (f: 'pdf' | 'xlsx' | 'csv') => Promise<void> })`;
  - `EstadoVacio({ titulo: string; descripcion: string; accion?: { etiqueta: string; href?: string; onClick?: () => void } })`;
  - `Pendiente({ severidad: 'alerta' | 'error'; texto: string; accion: { etiqueta: string; href: string } })`;
  - `ValorBloqueado({ valor: ReactNode; motivo: string })`;
  - `mensajeDeError(problema: ProblemDetails): { mensaje: string; accion?: { etiqueta: string; href: string } }`;
  - `registrarAtajo(tecla: string, accion: () => void)` (hook `useAtajo`).

- [ ] **Paso 1: tokens.** Escribir en `index.css` los tokens de la spec §7.2 como `@theme` de Tailwind v4:

```css
@import 'tailwindcss';
@import 'tw-animate-css';
@import '@fontsource-variable/red-hat-display';
@import '@fontsource-variable/red-hat-text';
@import '@fontsource/red-hat-mono';

/* Tokens de Pilot (spec F4.5 §7.2): la paleta sale del tema de login (infra/keycloak/temas/pilot) */
@theme {
  --color-primario: #1d4ed8;
  --color-primario-oscuro: #1e3a8a;
  --color-primario-suave: #dbeafe;
  --color-lienzo: #f1f5f9;
  --color-superficie: #ffffff;
  --color-borde: #e2e8f0;
  --color-texto: #0f172a;
  --color-texto-suave: #475569;
  --color-lateral: #1b1d21;
  --color-lateral-texto: #cbd5e1;
  --color-exito: #15803d;
  --color-alerta: #b45309;
  --color-error: #b91c1c;
  --color-neutro-revertido: #64748b;
  --font-titulo: 'Red Hat Display Variable', sans-serif;
  --font-cuerpo: 'Red Hat Text Variable', sans-serif;
  --font-mono: 'Red Hat Mono', monospace;
  --radius-control: 4px;
  --radius-panel: 6px;
}

/* Base: lienzo, tipografía del cuerpo y cifras tabulares en toda la app */
body { background: var(--color-lienzo); color: var(--color-texto); font-family: var(--font-cuerpo); }
.cifra { font-variant-numeric: tabular-nums; }
```

  Mapear las variables de shadcn (`--primary`, `--background`, `--border`, `--ring`…) a estos tokens para que los componentes generados los usen.
- [ ] **Paso 2: confirmar `--color-lateral`.** Hacer una captura de la tarjeta del login (Keycloak en 8180) y tomar el color con el cuentagotas del navegador. Si difiere de `#1b1d21`, usar el medido y anotarlo en `sistema-de-diseno.md`.
- [ ] **Paso 3: prueba del catálogo de errores, en rojo.**

```ts
// catalogoErrores.test.ts — cada código de la guía técnica §8.4 y §10 tiene un mensaje humano
it('traduce CON-020 con la acción de configurar reglas', () => {
  expect(mensajeDeError({ codigo: 'CON-020', detail: 'No hay una regla activa con cuenta para GASTO/OTRO_GASTO' }))
    .toEqual({ mensaje: 'Falta configurar la cuenta para «Otro gasto».', accion: { etiqueta: 'Configurar', href: '/configuracion/contabilidad#reglas' } });
});
it('nunca devuelve el código crudo como mensaje', () => {
  for (const codigo of CODIGOS_CONOCIDOS) expect(mensajeDeError({ codigo }).mensaje).not.toMatch(/^[A-Z]{3}-\d{3}$/);
});
```

  Ejecutar `pnpm test catalogoErrores` → FALLA (el módulo no existe). Implementar `catalogoErrores.ts` con todos los códigos `PLT-`, `CON-` e `INT-` de la guía técnica. Ejecutar de nuevo → PASA.
- [ ] **Paso 4: componentes de dominio con pruebas.** Casos mínimos por componente:
  - `Monto` formatea `"1234.5"` como `$1,234.50`, alinea a la derecha y usa `.cifra`;
  - `EtiquetaSaldo` muestra `$13.00 A` para `{monto:'13.00', lado:'ACREEDOR'}` y un ícono de alerta con texto accesible si `contrarioNaturaleza`;
  - `TablaContable` muestra el esqueleto con `cargando`, el nodo `vacio` sin filas y la fila de totales en un `<tfoot>` fijo;
  - `ValorBloqueado` muestra el candado y el `motivo` en el tooltip, sin ningún `<input>`.
- [ ] **Paso 5: estructura.**
  - `EstructuraApp` = `BarraLateral` + `BarraSuperior` + `<Outlet/>`.
  - `BarraLateral` con los grupos de la spec §7.4. Los enlaces de pantallas que aún no existen (Ingresos, Gastos, Bancos, Activos) se muestran solo cuando la ruta está registrada, así U1 no deja enlaces rotos.
  - `MenuRegistrar` lee sus entradas de un registro que U3 completa. En U1 solo tiene "Asiento manual (avanzado)".
  - `BuscadorGlobal` (`Ctrl+K`) busca pantallas y cuentas (`GET /contabilidad/cuentas`, ya existente).
  - Prueba: `Ctrl+K` abre el buscador; `N` abre "+ Registrar"; con foco en un `input` los atajos de letra no se disparan.
- [ ] **Paso 6:** mover el lanzador a `/configuracion/apps`. `/` muestra `PaginaInicio` con un `EstadoVacio` ("Instala Contabilidad para empezar") si Contabilidad no está instalada. Actualizar los selectores de `e2e/f1-nucleo.spec.ts` que cambien y comentar el motivo.
- [ ] **Paso 7:** escribir `docs/diseno/sistema-de-diseno.md`: tokens, tipografía, componentes con su uso correcto e incorrecto, patrones de tabla, formulario, documento y reporte, textos, atajos y fichas de pantalla (spec §8).
- [ ] **Paso 8: verificación.**

```bash
cd frontend && pnpm install --frozen-lockfile && pnpm api:generate && git diff --exit-code -- src/api && pnpm lint && pnpm test && pnpm build
```

  Esperado: todo en verde. Con el backend levantado por el usuario: `pnpm e2e:f1 && pnpm e2e:f2 && pnpm e2e:f3` en verde.
- [ ] **Paso 9: criterios de UX** (el revisor los comprueba en Chrome, en 1440 px y 1024 px):
  - la barra lateral usa el tono de la tarjeta del login;
  - ningún botón negro por defecto;
  - `Ctrl+K` encuentra "Balanza" y la cuenta "11010103";
  - por debajo de 1280 px la barra se contrae a íconos con tooltip.

---

## Oleada 2

### Tarea C1: contrato de operaciones, tablero, bloqueos y activos (Contratos)

**Archivos:**
- Modificar: `api-spec/openapi/pilot-v1.yaml`
- Prueba: `npx @stoplight/spectral-cli lint …` y compilación del backend

**Interfaces producidas (esquemas del contrato):**
- `FormaCobro`: `EFECTIVO | BANCO | TARJETA | CREDITO`. `FormaPago`: `EFECTIVO | BANCO | CREDITO`. `DestinoFondos`: `EFECTIVO | BANCO`. `DocumentoCompra`: `CREDITO_FISCAL | FACTURA`. `PlazoPrestamo`: `CORTO | LARGO`.
- `DestinoGasto`: `MERCADERIA | ALQUILER | SERVICIOS_BASICOS | PAPELERIA | SUELDOS_ADMINISTRACION | SUELDOS_VENTAS | HONORARIOS | PUBLICIDAD | COMISIONES_BANCARIAS | OTRO_GASTO`.
- `CategoriaActivo`: `MOBILIARIO_EQUIPO | EQUIPO_COMPUTO | VEHICULO | EDIFICIO | TERRENO`.
- Solicitudes (todas con `fecha` y `descripcion` hasta 300 caracteres; montos con `MontoEntrada`):

| Esquema | Campos propios |
|---|---|
| `VentaSolicitud` | `gravado`, `exento`, `noSujeto`, `modoPrecio?`, `formaCobro` |
| `CompraGastoSolicitud` | `destino`, `documento`, `monto`, `modoPrecio?`, `formaPago` |
| `CobroClienteSolicitud` | `origen: CLIENTES \| TARJETAS`, `monto` (bruto), `destino: DestinoFondos`, `comision?`, `anticipoIva?` |
| `PagoProveedorSolicitud` | `monto`, `origen: DestinoFondos` |
| `AporteSolicitud` | `monto`, `destino: DestinoFondos` |
| `PrestamoSolicitud` | `monto`, `plazo`, `destino: DestinoFondos` |
| `CuotaPrestamoSolicitud` | `plazo`, `capital`, `intereses`, `comision?`, `origen: DestinoFondos` |
| `TrasladoSolicitud` | `monto`, `cuentaOrigenId`, `cuentaDestinoId` |
| `ActivoFijoSolicitud` | `categoria`, `documento`, `monto`, `modoPrecio?`, `formaPago` |
| `DepreciacionSolicitud` | `anio`, `mes` (sin `fecha` ni `descripcion`) |

- Respuestas:
  - `VistaPreviaOperacion` { `lineas: LineaAsientoVista[]`, `totalDebe`, `totalHaber`, `diferencia`, `cuadra`, `resumen: ResumenOperacion` };
  - `OperacionRegistrada` { `operacionId`, `estado`, `asiento: {id, numero, anio, fecha}`, `resumen` };
  - `ResumenOperacion` { `modoPrecio?`, `base`, `iva`, `exento`, `noSujeto`, `total` };
  - `Operacion` (lista y detalle) { `id`, `tipo`, `fecha`, `descripcion`, `total`, `estado`, `asiento`, `datos` (objeto libre de solo lectura), `resumen`, `creadoEn`, `creadoPor` };
  - `PaginaOperaciones`, `ActivoFijo` { `id`, `descripcion`, `categoria`, `fechaAdquisicion`, `costo`, `valorResidual`, `vidaUtilMeses`, `depreciacionAcumulada`, `valorEnLibros`, `estado` };
  - `Tablero` { `efectivo: [{cuenta, saldo}]`, `porCobrar`, `porPagar`, `mes: {ingresos, costosGastos, utilidad}`, `mesAnterior: {…}`, `ivaEstimado`, `serie: [{anio, mes, ingresos, gastos}]` (6), `pendientes: [{tipo, severidad, detalle, cantidad}]` }.
- Rutas, etiquetas y roles: spec §5.7. Cada `POST` que guarda lleva el header `Idempotency-Key` (requerido) y responde 201. Cada `…/vista-previa` responde 200. Ambos documentan 422 con `CON-003`, `CON-006`, `CON-007`, `CON-017`, `CON-020`, `CON-022` y `CON-023` según corresponda.
- Bloqueos (ADR-042):
  - `CuentaNueva` y `CuentaCambios` **sin** `naturaleza`;
  - `Cuenta` agrega `sistema: boolean` (solo lectura);
  - `ConfiguracionContableCambios` solo con `modoPrecioDefecto`;
  - `ReglaContabilizacion` agrega `prefijoPermitido` (solo lectura);
  - el filtro de tipo de `GET /contabilidad/reglas-contabilizacion` acepta los tipos nuevos.
- `GET /contabilidad/tablero?anio&mes` con la etiqueta `tablero`, rol `auditor`. `GET /contabilidad/operaciones?tipo&desde&hasta&estado&limite&cursor` y `GET /contabilidad/operaciones/{id}` con la etiqueta `operaciones`. `GET /contabilidad/activos-fijos` con la etiqueta `operacionesActivos`.

- [ ] **Paso 1:** escribir los esquemas y las rutas con `description` en cada operación, parámetro, propiedad y error (§8.2).
- [ ] **Paso 2:** `npx @stoplight/spectral-cli lint api-spec/openapi/pilot-v1.yaml --ruleset api-spec/.spectral.yaml` → 0 errores.
- [ ] **Paso 3:** `cd backend && ./mvnw -q -DskipTests compile`. Esperado: **falla** solo porque los controladores existentes de catálogo y configuración ya no coinciden con los esquemas cambiados (naturaleza, IVA). Anotar los archivos exactos en el reporte: los arregla B3. Para que el árbol compile entre C1 y B3, **C1 incluye el ajuste mínimo de esos dos controladores y sus mapeadores**: ignorar los campos eliminados sin cambiar el dominio. Las pruebas que envían `naturaleza` se marcan `@Disabled("B3: naturaleza derivada, ADR-042")`, con la lista en el reporte.
- [ ] **Paso 4:** `./mvnw -q clean verify` en verde y `cd ../frontend && pnpm api:generate && pnpm build`. Esperado: el build falla solo en los formularios que envían `naturaleza` o las cuentas de IVA. Se anotan y los corrige U2 (la tarea de frontend que corre en paralelo se coordina por el revisor).

### Tarea B1: migraciones V16–V18 (Base de datos)

**Archivos:**
- Crear: `backend/src/main/resources/db/migration/V16__operaciones_guiadas.sql`, `V17__cuentas_del_sistema.sql`, `V18__activos_fijos.sql`
- Prueba: `backend/src/test/java/com/bcodesphere/pilot/contabilidad/infraestructura/MigracionesOperacionesIT.java`

**Interfaces producidas:** tablas y columnas del mapa; reglas con `prefijo_permitido`; `cuenta_contable.sistema`.

- [ ] **Paso 1: prueba de migración sobre datos anteriores, en rojo.** Con Testcontainers y la API de Flyway:

```java
/** Una empresa que instaló Contabilidad antes de V16 recibe las reglas nuevas y sus cuentas base quedan marcadas como sistema. */
@Test
void unaEmpresaAnteriorAV16QuedaCompletaTrasMigrar() {
    // 1. Migra solo hasta V15 y siembra, como dueño, una empresa con el catálogo y las reglas de F2
    Flyway.configure().dataSource(duenio()).target("15").load().migrate();
    UUID empresa = sembrarEmpresaConContabilidadV15();
    // 2. Migra hasta la última versión
    Flyway.configure().dataSource(duenio()).load().migrate();
    // 3. Reglas nuevas copiadas con su cuenta, y cuentas base marcadas como sistema (spec §5.6 y §6)
    assertThat(contar("SELECT count(*) FROM regla_contabilizacion WHERE empresa_id = ? AND tipo_operacion = 'VENTA'", empresa)).isEqualTo(7);
    assertThat(contar("SELECT count(*) FROM cuenta_contable WHERE empresa_id = ? AND sistema", empresa))
        .isEqualTo(contar("SELECT count(*) FROM plantilla_cuenta"));
}
```

  Ejecutar `./mvnw -q -Dit.test=MigracionesOperacionesIT verify` → FALLA (no existe V16).
- [ ] **Paso 2: escribir `V16__operaciones_guiadas.sql`.**

```sql
-- 1. OPERACION: hecho de negocio registrado desde la interfaz (spec F4.5 §5.6, ADR-041)
CREATE TABLE operacion (
    id           UUID PRIMARY KEY,
    empresa_id   UUID NOT NULL REFERENCES empresa(id),
    tipo         VARCHAR(30) NOT NULL,
    fecha        DATE NOT NULL,
    descripcion  VARCHAR(300),
    datos        JSONB NOT NULL,
    resumen      JSONB NOT NULL,
    total        NUMERIC(19,2) NOT NULL CHECK (total > 0),
    asiento_id   UUID NOT NULL,
    estado       VARCHAR(12) NOT NULL DEFAULT 'CONTABILIZADA',
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por   VARCHAR(64) NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_operacion_asiento FOREIGN KEY (empresa_id, asiento_id) REFERENCES asiento (empresa_id, id),
    CONSTRAINT ck_operacion_estado CHECK (estado IN ('CONTABILIZADA', 'REVERTIDA')),
    CONSTRAINT ck_operacion_tipo CHECK (tipo IN ('VENTA','COMPRA_GASTO','COBRO_CLIENTE','PAGO_PROVEEDOR','APORTE_CAPITAL',
        'PRESTAMO_RECIBIDO','PAGO_CUOTA','TRASLADO_FONDOS','COMPRA_ACTIVO_FIJO','DEPRECIACION_MENSUAL'))
);
CREATE INDEX idx_operacion_listado ON operacion (empresa_id, fecha DESC, id DESC);
ALTER TABLE operacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE operacion FORCE ROW LEVEL SECURITY;
CREATE POLICY aislamiento_empresa ON operacion TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
GRANT SELECT, INSERT ON operacion TO pilot_app;
GRANT UPDATE (estado, version) ON operacion TO pilot_app;
-- COMMENT ON TABLE/COLUMN para cada columna no obvia (§8.2)

-- 2. El asiento admite el origen OPERACION (origen_id = operacion.id)
ALTER TABLE asiento DROP CONSTRAINT ck_asiento_origen_tipo;
ALTER TABLE asiento ADD CONSTRAINT ck_asiento_origen_tipo CHECK (origen_tipo IN ('MANUAL','N8N','REVERSION','OPERACION'));

-- 3. Reglas: tipos y categorías nuevos y grupo permitido (ADR-042)
ALTER TABLE plantilla_regla_contabilizacion DROP CONSTRAINT ck_plantilla_regla_categoria;
ALTER TABLE plantilla_regla_contabilizacion ADD CONSTRAINT ck_plantilla_regla_categoria
    CHECK (categoria IN ('INGRESO','COBRO','PAGO','GASTO','CONTRAPARTIDA','ACTIVO','DEPRECIACION'));
ALTER TABLE plantilla_regla_contabilizacion ADD COLUMN prefijo_permitido VARCHAR(8);
ALTER TABLE regla_contabilizacion DROP CONSTRAINT ck_regla_contabilizacion_tipo;
ALTER TABLE regla_contabilizacion ADD CONSTRAINT ck_regla_contabilizacion_tipo CHECK (tipo_operacion IN
    ('CIERRE_INGRESOS_DIARIO','VENTA','COMPRA_GASTO','COBRO_CLIENTE','PAGO_PROVEEDOR','APORTE_CAPITAL',
     'PRESTAMO_RECIBIDO','PAGO_CUOTA','COMPRA_ACTIVO_FIJO','DEPRECIACION_MENSUAL'));
ALTER TABLE regla_contabilizacion DROP CONSTRAINT ck_regla_contabilizacion_categoria;
ALTER TABLE regla_contabilizacion ADD CONSTRAINT ck_regla_contabilizacion_categoria
    CHECK (categoria IN ('INGRESO','COBRO','PAGO','GASTO','CONTRAPARTIDA','ACTIVO','DEPRECIACION'));
ALTER TABLE regla_contabilizacion ADD COLUMN prefijo_permitido VARCHAR(8);
```

  **Antes** de escribir los `DROP CONSTRAINT`, leer el nombre real de cada CHECK en V10, V11 y V13 (`ck_plantilla_regla_categoria`, `ck_regla_contabilizacion_tipo`, `ck_regla_contabilizacion_categoria`, `ck_asiento_origen_tipo`). La columna `tipo_operacion` de la plantilla no tiene CHECK en V10: se agrega el mismo CHECK de tipos.
- [ ] **Paso 3: reglas nuevas y prefijos** (borrador `[VERIFICAR]`, ADR-034). Mismo archivo:

```sql
-- 4. Prefijo de las reglas del cierre existentes
UPDATE plantilla_regla_contabilizacion SET prefijo_permitido = CASE
    WHEN categoria = 'INGRESO' THEN '5101'
    WHEN codigo IN ('EFECTIVO','TRANSFERENCIA','CHEQUE') THEN '1101'
    WHEN codigo IN ('TARJETA','CREDITO') THEN '1102'
    ELSE '11' END
 WHERE tipo_operacion = 'CIERRE_INGRESOS_DIARIO';

-- 5. Reglas de las operaciones guiadas (spec §5.3 a §5.5): tipo, categoría, código, cuenta por defecto, grupo permitido
INSERT INTO plantilla_regla_contabilizacion (tipo_operacion, categoria, codigo, cuenta_codigo, activa, prefijo_permitido) VALUES
 ('VENTA','INGRESO','GRAVADO','51010101',true,'5101'), ('VENTA','INGRESO','EXENTO','51010102',true,'5101'),
 ('VENTA','INGRESO','NO_SUJETO','51010103',true,'5101'),
 ('VENTA','COBRO','EFECTIVO','11010101',true,'1101'), ('VENTA','COBRO','BANCO','11010103',true,'1101'),
 ('VENTA','COBRO','TARJETA','11020102',true,'1102'), ('VENTA','COBRO','CREDITO','11020101',true,'1102'),
 ('COMPRA_GASTO','GASTO','MERCADERIA','41020101',true,'4102'), ('COMPRA_GASTO','GASTO','ALQUILER','42020102',true,'4202'),
 ('COMPRA_GASTO','GASTO','SERVICIOS_BASICOS','42020103',true,'4202'), ('COMPRA_GASTO','GASTO','PAPELERIA','42020104',true,'4202'),
 ('COMPRA_GASTO','GASTO','SUELDOS_ADMINISTRACION','42020101',true,'4202'), ('COMPRA_GASTO','GASTO','SUELDOS_VENTAS','42010101',true,'4201'),
 ('COMPRA_GASTO','GASTO','HONORARIOS','42020106',true,'4202'), ('COMPRA_GASTO','GASTO','PUBLICIDAD','42010102',true,'4201'),
 ('COMPRA_GASTO','GASTO','COMISIONES_BANCARIAS','43010102',true,'4301'), ('COMPRA_GASTO','GASTO','OTRO_GASTO',NULL,false,'42'),
 ('COMPRA_GASTO','PAGO','EFECTIVO','11010101',true,'1101'), ('COMPRA_GASTO','PAGO','BANCO','11010103',true,'1101'),
 ('COMPRA_GASTO','PAGO','CREDITO','21010101',true,'2101'),
 ('COBRO_CLIENTE','CONTRAPARTIDA','CLIENTES','11020101',true,'1102'), ('COBRO_CLIENTE','CONTRAPARTIDA','TARJETAS','11020102',true,'1102'),
 ('COBRO_CLIENTE','CONTRAPARTIDA','COMISION_TARJETA','42010103',true,'4201'), ('COBRO_CLIENTE','CONTRAPARTIDA','ANTICIPO_IVA','11040104',true,'1104'),
 ('COBRO_CLIENTE','COBRO','EFECTIVO','11010101',true,'1101'), ('COBRO_CLIENTE','COBRO','BANCO','11010103',true,'1101'),
 ('PAGO_PROVEEDOR','CONTRAPARTIDA','PROVEEDORES','21010101',true,'2101'),
 ('PAGO_PROVEEDOR','PAGO','EFECTIVO','11010101',true,'1101'), ('PAGO_PROVEEDOR','PAGO','BANCO','11010103',true,'1101'),
 ('APORTE_CAPITAL','CONTRAPARTIDA','CAPITAL','31010101',true,'3101'),
 ('APORTE_CAPITAL','COBRO','EFECTIVO','11010101',true,'1101'), ('APORTE_CAPITAL','COBRO','BANCO','11010103',true,'1101'),
 ('PRESTAMO_RECIBIDO','CONTRAPARTIDA','CORTO','21040101',true,'2104'), ('PRESTAMO_RECIBIDO','CONTRAPARTIDA','LARGO','22010101',true,'2201'),
 ('PRESTAMO_RECIBIDO','COBRO','EFECTIVO','11010101',true,'1101'), ('PRESTAMO_RECIBIDO','COBRO','BANCO','11010103',true,'1101'),
 ('PAGO_CUOTA','CONTRAPARTIDA','CORTO','21040101',true,'2104'), ('PAGO_CUOTA','CONTRAPARTIDA','LARGO','22010101',true,'2201'),
 ('PAGO_CUOTA','CONTRAPARTIDA','INTERESES','43010101',true,'4301'), ('PAGO_CUOTA','CONTRAPARTIDA','COMISION','43010102',true,'4301'),
 ('PAGO_CUOTA','PAGO','EFECTIVO','11010101',true,'1101'), ('PAGO_CUOTA','PAGO','BANCO','11010103',true,'1101'),
 ('COMPRA_ACTIVO_FIJO','ACTIVO','MOBILIARIO_EQUIPO','12010101',true,'1201'), ('COMPRA_ACTIVO_FIJO','ACTIVO','EQUIPO_COMPUTO','12010102',true,'1201'),
 ('COMPRA_ACTIVO_FIJO','ACTIVO','VEHICULO','12010103',true,'1201'), ('COMPRA_ACTIVO_FIJO','ACTIVO','EDIFICIO','12010202',true,'1201'),
 ('COMPRA_ACTIVO_FIJO','ACTIVO','TERRENO','12010201',true,'1201'),
 ('COMPRA_ACTIVO_FIJO','PAGO','EFECTIVO','11010101',true,'1101'), ('COMPRA_ACTIVO_FIJO','PAGO','BANCO','11010103',true,'1101'),
 ('COMPRA_ACTIVO_FIJO','PAGO','CREDITO','21010101',true,'2101'),
 ('DEPRECIACION_MENSUAL','DEPRECIACION','MOBILIARIO_EQUIPO','12020101',true,'1202'), ('DEPRECIACION_MENSUAL','DEPRECIACION','EQUIPO_COMPUTO','12020102',true,'1202'),
 ('DEPRECIACION_MENSUAL','DEPRECIACION','VEHICULO','12020103',true,'1202'), ('DEPRECIACION_MENSUAL','DEPRECIACION','EDIFICIO','12020201',true,'1202'),
 ('DEPRECIACION_MENSUAL','GASTO','DEPRECIACION','42020105',true,'4202');
ALTER TABLE plantilla_regla_contabilizacion ALTER COLUMN prefijo_permitido SET NOT NULL;
```

  `VENTA` tiene 7 reglas (3 de ingreso y 4 de cobro): es el valor que afirma la prueba del paso 1.
- [ ] **Paso 4: copia a las empresas existentes.** RLS forzado aplica también al dueño y las políticas son `TO pilot_app` (ADR-026), así que el dueño no ve filas. Dentro de la transacción de la migración se hace así:

```sql
-- 6. Completar las empresas que ya instalaron Contabilidad (ADR-042). Se levanta el FORCE solo durante esta sentencia,
--    dentro de la transacción de Flyway, y se restablece a continuación; si algo falla, todo se revierte.
ALTER TABLE regla_contabilizacion NO FORCE ROW LEVEL SECURITY;
ALTER TABLE cuenta_contable NO FORCE ROW LEVEL SECURITY;
UPDATE regla_contabilizacion r SET prefijo_permitido = p.prefijo_permitido
  FROM plantilla_regla_contabilizacion p
 WHERE p.tipo_operacion = r.tipo_operacion AND p.categoria = r.categoria AND p.codigo = r.codigo;
INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id, activa, prefijo_permitido)
SELECT gen_random_uuid(), ea.empresa_id, p.tipo_operacion, p.categoria, p.codigo, c.id, p.activa AND c.id IS NOT NULL, p.prefijo_permitido
  FROM empresa_aplicacion ea
  JOIN plantilla_regla_contabilizacion p ON p.tipo_operacion <> 'CIERRE_INGRESOS_DIARIO'
  LEFT JOIN cuenta_contable c ON c.empresa_id = ea.empresa_id AND c.codigo = p.cuenta_codigo
 WHERE ea.aplicacion_codigo = 'contabilidad'
ON CONFLICT (empresa_id, tipo_operacion, categoria, codigo) DO NOTHING;
ALTER TABLE regla_contabilizacion ALTER COLUMN prefijo_permitido SET NOT NULL;
ALTER TABLE regla_contabilizacion FORCE ROW LEVEL SECURITY;
ALTER TABLE cuenta_contable FORCE ROW LEVEL SECURITY;
```

  Si la cuenta por defecto no existe en esa empresa (catálogo anterior a V15), la regla queda inactiva y sin cuenta (`p.activa AND c.id IS NOT NULL` en el `SELECT`), lo que respeta `CHECK (NOT activa OR cuenta_id IS NOT NULL)`. El tablero lo mostrará como pendiente. La prueba del paso 1 agrega una segunda empresa sin la cuenta 11040104 y afirma que su regla `ANTICIPO_IVA` queda inactiva. `gen_random_uuid()` genera UUID v4: se acepta **solo** en esta migración de datos y queda comentado (ADR-010 aplica a los ids que genera la aplicación).
- [ ] **Paso 5: `V17__cuentas_del_sistema.sql`.**

```sql
-- Cuentas del catálogo base: solo lectura para el usuario (ADR-042)
ALTER TABLE cuenta_contable ADD COLUMN sistema BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE cuenta_contable NO FORCE ROW LEVEL SECURITY;
UPDATE cuenta_contable c SET sistema = true FROM plantilla_cuenta p WHERE p.codigo = c.codigo;
ALTER TABLE cuenta_contable FORCE ROW LEVEL SECURITY;
COMMENT ON COLUMN cuenta_contable.sistema IS 'true si la cuenta viene del catálogo base: código, nombre, naturaleza y estado no se editan (CON-021).';
```

  `pilot_app` no recibe `UPDATE (sistema)`: la columna solo la fija la precarga (INSERT) y esta migración.
- [ ] **Paso 6: `V18__activos_fijos.sql`**, con `plantilla_vida_util(categoria PK, vida_util_meses, valor_residual_porcentaje NUMERIC(5,4), inicia_mes_siguiente BOOLEAN, confirmada BOOLEAN)`. Filas `[VERIFICAR]` con `confirmada = false` y meses provisionales, comentados como no confirmados:

| Categoría | Meses provisionales |
|---|---|
| `MOBILIARIO_EQUIPO` | 60 |
| `EQUIPO_COMPUTO` | 24 |
| `VEHICULO` | 48 |
| `EDIFICIO` | 240 |

  Residual 0, mes siguiente `true`. Además, `activo_fijo` y `depreciacion_registrada` con las columnas de la spec §5.6, RLS forzado, `TO pilot_app`, `SELECT, INSERT` y `UPDATE (estado, version)`, y `CREATE UNIQUE INDEX uq_depreciacion_vigente ON depreciacion_registrada (empresa_id, activo_id, anio, mes) WHERE estado = 'VIGENTE'`.
- [ ] **Paso 7:** ejecutar `./mvnw -q -Dit.test=MigracionesOperacionesIT verify` → PASA. Luego `./mvnw -q clean verify` → todo en verde (las pruebas existentes de V13 y de la precarga no cambian).
- [ ] **Paso 8: prueba de permisos.** Como `pilot_app`, `UPDATE operacion SET total = 1` y `UPDATE cuenta_contable SET sistema = false` fallan con SQLState `42501`; `DELETE` en `operacion`, `activo_fijo` y `depreciacion_registrada` también.

### Tarea B2: motor genérico y los ocho tipos sin activos (Contable)

Reemplaza el prompt `F5-02-contabilizar-operacion.md` anterior, que queda archivado sin enviar.

**Archivos:** los del mapa marcados B2, sus pruebas en `backend/src/test/java/com/bcodesphere/pilot/contabilidad/{dominio/operaciones,api}/` y los casos dorados en `backend/src/test/resources/casos/operaciones/<tipo>/*.json`.

**Interfaces:**
- Consume:
  - `AsientoExpandido(LocalDate fecha, String concepto, ModoPrecio modoPrecio, BigDecimal tasaIva, List<LineaExpandida> lineas, int lineasCapturadas)`;
  - `LineaExpandida(int numeroLinea, ResumenCuenta cuenta, String descripcion, Dinero debe, Dinero haber, OrigenLinea origenLinea, Integer numeroLineaOrigen)`;
  - `ReglasAsiento.validar(AsientoExpandido)` y `ReglasAsiento.validarFecha(LocalDate, LocalDate)`;
  - `CalculadoraIva.separar(Dinero, ModoPrecio, BigDecimal) → SeparacionIva(base, iva)`;
  - `ConsultaTasaIva.vigenteA(LocalDate) → Optional<BigDecimal>`;
  - `GuardarAsiento` (paquete `aplicacion`);
  - `ServicioIdempotencia.ejecutar(clave, cuerpo, Supplier<RespuestaIdempotente>)`.
- Produce (paquete raíz `contabilidad`):

```java
/** Motor único de contabilización de operaciones (ADR-041). Exige la transacción del llamador. */
public interface ContabilizarOperacion {
    /** Arma, valida, numera, guarda, mayoriza y audita el asiento de la operación. */
    OperacionContabilizada contabilizar(OperacionContable operacion);
    /** Arma y devuelve las líneas sin guardar nada (vista previa, ADR-036: no rechaza descuadres). */
    AsientoPrevisto previsualizar(OperacionContable operacion);
}
public record OperacionContable(UUID operacionId, String tipo, LocalDate fecha, String descripcion,
        String modoPrecio, Map<String, Object> datos, String origen) {}   // origen: OPERACION | N8N
public record OperacionContabilizada(UUID asientoId, long numero, int anio, LocalDate fecha, String modoPrecio,
        Dinero base, Dinero iva, Dinero exento, Dinero noSujeto, Dinero total) {}
public record AsientoPrevisto(List<LineaPrevista> lineas, Dinero totalDebe, Dinero totalHaber, Dinero diferencia,
        boolean cuadra, OperacionContabilizada resumen) {}
public record LineaPrevista(int numero, String cuentaCodigo, String cuentaNombre, String descripcion,
        Dinero debe, Dinero haber, String origenLinea) {}
public final class CobrosNoCuadranException extends RuntimeException { public Dinero diferencia() { … } }
```

  `datos` viaja como `Map` en la API pública para que `integracion` no dependa de los `record` internos. `ServicioContabilizarOperacion` lo convierte al `record` tipado de `dominio/operaciones/DatosOperacion` y valida montos con `Dinero.de`.
- Dominio:

```java
public sealed interface DatosOperacion permits DatosVenta, DatosCompraGasto, DatosCobroCliente, DatosPagoProveedor,
        DatosAporte, DatosPrestamo, DatosCuotaPrestamo, DatosTraslado, DatosActivoFijo, DatosDepreciacion, DatosCierreIngresos {}
public interface ArmadorAsiento<D extends DatosOperacion> { AsientoExpandido armar(D datos, ContextoArmado ctx); }
public record ContextoArmado(LocalDate fecha, String concepto, ModoPrecio modo, BigDecimal tasa,
        ResolutorCuentas cuentas, ResumenCuenta ivaDebito, ResumenCuenta ivaCredito) {}
public interface ResolutorCuentas { ResumenCuenta cuenta(TipoOperacion tipo, String categoria, String codigo); } // CON-020
```

- [ ] **Paso 1: casos dorados de dominio** (en rojo), un `@ParameterizedTest` que lee `casos/operaciones/**` y compara las líneas exactas (cuenta, lado, monto, origen). Fuente: spec §5.3 y §11.1 de la guía técnica; tasa 13 %; `[VERIFICAR]` con contador. Casos mínimos:

| Caso | Entrada | Líneas esperadas (D = Debe, H = Haber) |
|---|---|---|
| venta-contado-con-iva | gravado 113.00, CON_IVA, EFECTIVO | D 11010101 113.00 · H 51010101 100.00 · H 21020101 13.00 (IVA_CALCULADO, base = línea 2) |
| venta-credito-sin-iva-mixta | gravado 100.00, exento 50.00, noSujeto 20.00, SIN_IVA, CREDITO | D 11020101 183.00 · H 51010101 100.00 · H 21020101 13.00 · H 51010102 50.00 · H 51010103 20.00 |
| venta-redondeo | gravado 5.00, CON_IVA, TARJETA | D 11020102 5.00 · H 51010101 4.42 · H 21020101 0.58 |
| venta-solo-exenta | exento 80.00, BANCO | D 11010103 80.00 · H 51010102 80.00 (sin línea de IVA) |
| compra-ccf | ALQUILER, CREDITO_FISCAL, 113.00, CON_IVA, BANCO | D 42020102 100.00 · D 11040101 13.00 · H 11010103 113.00 |
| compra-factura | PAPELERIA, FACTURA, 22.60, EFECTIVO | D 42020104 22.60 · H 11010101 22.60 |
| compra-ccf-sin-iva-a-credito | MERCADERIA, CREDITO_FISCAL, 4.42, SIN_IVA, CREDITO | D 41020101 4.42 · D 11040101 0.57 · H 21010101 4.99 |
| cobro-cliente | CLIENTES, 500.00, BANCO | D 11010103 500.00 · H 11020101 500.00 |
| cobro-tarjetas | TARJETAS, bruto 1000.00, comisión 30.00, anticipoIva 20.00, BANCO | D 11010103 950.00 · D 42010103 30.00 · D 11040104 20.00 · H 11020102 1000.00 |
| cobro-tarjetas-sin-extras | TARJETAS, 400.00, BANCO | D 11010103 400.00 · H 11020102 400.00 |
| pago-proveedor | 250.00, EFECTIVO | D 21010101 250.00 · H 11010101 250.00 |
| aporte | 5000.00, BANCO | D 11010103 5000.00 · H 31010101 5000.00 |
| prestamo-largo | 10000.00, LARGO, BANCO | D 11010103 10000.00 · H 22010101 10000.00 |
| cuota | CORTO, capital 800.00, intereses 95.50, comisión 2.00, BANCO | D 21040101 800.00 · D 43010101 95.50 · D 43010102 2.00 · H 11010103 897.50 |
| traslado | 300.00, origen 11010101, destino 11010103 | D 11010103 300.00 · H 11010101 300.00 |

  Inválidos (sin guardar nada):
  - venta con los tres montos en 0 → 422 `PLT-002` (errores `gravado`/`exento`/`noSujeto`);
  - cobro de tarjetas con comisión + anticipo ≥ bruto → 422 `PLT-002` (`comision`);
  - traslado con origen = destino, o con una cuenta fuera de 1101 → 422 `CON-022`;
  - `OTRO_GASTO` sin configurar → 422 `CON-020`;
  - fecha futura → `CON-007`;
  - tipo con IVA sin tasa vigente → `CON-017`.

  Ejecutar `./mvnw -q -Dtest='*Armador*' test` → FALLA (no existen).
- [ ] **Paso 2: `LineasBuilder`.** Numera desde 1, omite los montos en cero y enlaza la línea de IVA con `numeroLineaOrigen` = la de su base.

```java
/** Construye las líneas de un asiento armado: numera en orden, omite montos en cero y enlaza el IVA con su base. */
final class LineasBuilder {
    private final List<LineaExpandida> lineas = new ArrayList<>();
    /** Agrega una línea al Debe si el monto es mayor que cero; devuelve su número o null si se omitió. */
    Integer debe(ResumenCuenta cuenta, String descripcion, Dinero monto, OrigenLinea origen, Integer base) { return agregar(cuenta, descripcion, monto, Dinero.CERO, origen, base); }
    /** Agrega una línea al Haber si el monto es mayor que cero; devuelve su número o null si se omitió. */
    Integer haber(ResumenCuenta cuenta, String descripcion, Dinero monto, OrigenLinea origen, Integer base) { return agregar(cuenta, descripcion, Dinero.CERO, monto, origen, base); }
    List<LineaExpandida> lineas() { return List.copyOf(lineas); }
    private Integer agregar(ResumenCuenta c, String d, Dinero debe, Dinero haber, OrigenLinea o, Integer base) {
        // 1. Una línea en cero no representa movimiento (spec §5.3): se omite
        if (debe.esCero() && haber.esCero()) return null;
        int numero = lineas.size() + 1;
        lineas.add(new LineaExpandida(numero, c, d, debe, haber, o, base));
        return numero;
    }
}
```

- [ ] **Paso 3: `ArmadorVenta`** (el patrón para el resto):

```java
/** Venta (spec §5.3): Debe la forma de cobro por el total; Haber ventas gravadas, IVA débito, exentas y no sujetas. */
final class ArmadorVenta implements ArmadorAsiento<DatosVenta> {
    @Override
    public AsientoExpandido armar(DatosVenta d, ContextoArmado ctx) {
        // 1. Separa base e IVA de lo gravado con la tasa vigente y el modo elegido (guía técnica §11.1)
        SeparacionIva s = d.gravado().esCero() ? new SeparacionIva(Dinero.CERO, Dinero.CERO)
                : CalculadoraIva.separar(d.gravado(), ctx.modo(), ctx.tasa());
        Dinero total = s.base().sumar(s.iva()).sumar(d.exento()).sumar(d.noSujeto());
        // 2. Resuelve las cuentas por regla (CON-020 si falta alguna usada)
        ResolutorCuentas r = ctx.cuentas();
        LineasBuilder b = new LineasBuilder();
        b.debe(r.cuenta(TipoOperacion.VENTA, "COBRO", d.formaCobro()), "Cobro " + d.formaCobro(), total, OrigenLinea.OPERACION, null);
        Integer base = s.base().esCero() ? null
                : b.haber(r.cuenta(TipoOperacion.VENTA, "INGRESO", "GRAVADO"), "Ventas gravadas", s.base(), OrigenLinea.OPERACION, null);
        b.haber(ctx.ivaDebito(), "IVA débito fiscal", s.iva(), OrigenLinea.IVA_CALCULADO, base);
        if (d.exento().esPositivo()) b.haber(r.cuenta(TipoOperacion.VENTA, "INGRESO", "EXENTO"), "Ventas exentas", d.exento(), OrigenLinea.OPERACION, null);
        if (d.noSujeto().esPositivo()) b.haber(r.cuenta(TipoOperacion.VENTA, "INGRESO", "NO_SUJETO"), "Ventas no sujetas", d.noSujeto(), OrigenLinea.OPERACION, null);
        // 3. Asiento armado; la partida doble la valida ReglasAsiento en el servicio
        return new AsientoExpandido(ctx.fecha(), ctx.concepto(), ctx.modo(), ctx.tasa(), b.lineas(), 0);
    }
}
```

  Una regla solo se resuelve cuando su monto es mayor que cero. Así un `OTRO_GASTO` inactivo no bloquea otros destinos.
- [ ] **Paso 4:** implementar `ArmadorCompraGasto`, `ArmadorCobroCliente`, `ArmadorPagoProveedor`, `ArmadorAporte`, `ArmadorPrestamo`, `ArmadorCuotaPrestamo` y `ArmadorTraslado`, uno por uno, hasta que su fila dorada pase. `ArmadorCierreIngresos` implementa la guía técnica §12.5 con las pruebas del prompt anterior de F5-02 (CON_IVA 1,180.00 exacto; SIN_IVA → `CobrosNoCuadranException(146.90)`). Ejecutar `./mvnw -q -Dtest='*Armador*' test` → PASA.
- [ ] **Paso 5: `ServicioContabilizarOperacion`**, con `@Transactional(propagation = MANDATORY)`:
  1. valida la fecha (`CON-007`);
  2. toma la tasa si el tipo usa IVA (`CON-017`);
  3. el modo es el de la solicitud o el de la configuración;
  4. el concepto es `<Nombre del tipo> — <descripción>`, recortado a 500;
  5. llama al armador;
  6. aplica `ReglasAsiento.validar`;
  7. `GuardarAsiento.guardarOperacion(expandido, origen, operacionId)`.

  Agregar en `Asiento` la fábrica `deOperacion(AsientoExpandido, long numero, Instant creadoEn, OrigenAsiento origen, UUID origenId)`, con la misma lógica de ids y enlaces que `manual`, extraída a un método privado común (sin duplicar código).
- [ ] **Paso 6: `ResolutorCuentasJdbc`.**

```sql
SELECT c.id, c.codigo, c.nombre
  FROM regla_contabilizacion r
  JOIN cuenta_contable c ON c.id = r.cuenta_id AND c.empresa_id = r.empresa_id
 WHERE r.empresa_id = :empresa AND r.tipo_operacion = :tipo AND r.categoria = :categoria AND r.codigo = :codigo
   AND r.activa AND c.activa AND c.acepta_movimientos AND c.codigo LIKE r.prefijo_permitido || '%'
```

  Sin fila → `ExcepcionContabilidad.sinRegla(categoria, codigo)`, que es 422 `CON-020` con el detalle "No hay una regla activa con cuenta para <categoría>/<código>". Las cuentas del traslado se validan aparte: detalle, activa y código con prefijo `1101`; si no, `CON-022`.
- [ ] **Paso 7: casos de uso de la API.**
  - `RegistrarOperacion.registrar(clave, cuerpoCanonico, OperacionContable, Function<…, String> serializador)`, con `@PreAuthorize("hasRole('CONTADOR')")` y `@Transactional`, dentro de `idempotencia.ejecutar`:
    1. generar `operacionId` (`GeneradorId.nuevo()`);
    2. `contabilizar`;
    3. insertar `operacion` (datos, resumen, total, `asiento_id`);
    4. auditar `operacion CREAR`;
    5. devolver 201.
  - `PrevisualizarOperacion`: `readOnly`, sin idempotencia.
  - `@PreAuthorize` del motor: `hasRole('CONTADOR') or hasAuthority('SCOPE_integracion:operaciones')`.
- [ ] **Paso 8: controladores y mapeadores.** Uno por etiqueta, sin lógica: DTO → `OperacionContable` → caso de uso → DTO. `ControladorOperaciones` lista por cursor (`fecha DESC, id DESC`, patrón `CursorAsiento`) con filtros de tipo, desde, hasta y estado.
- [ ] **Paso 9: `OyenteReversionOperacion`.** Con `@EventListener` sobre `AsientoRevertido` (no transaccional, ADR-036): si `origenTipo = OPERACION`, `UPDATE operacion SET estado = 'REVERTIDA', version = version + 1 WHERE empresa_id = :e AND id = :origenId`. Se audita.
- [ ] **Paso 10: pruebas de integración** (`OperacionesIT`, Testcontainers, `pilot_app`):
  - cada tipo por la API → 201 y, como dueño, las líneas de su caso dorado, `asiento.origen_tipo = 'OPERACION'` con `origen_id` y la fila en `operacion`;
  - la vista previa devuelve las mismas líneas que se guardan;
  - **foco 1:** 10 hilos con la misma `Idempotency-Key` y el mismo cuerpo → un asiento, una operación; los demás reciben la respuesta repetida o 409 `PLT-008`;
  - **foco 4:** desactivar la regla `COMPRA_GASTO/GASTO/ALQUILER` por la API → vista previa y registro con 422 `CON-020`, cero filas nuevas (asiento, línea, saldo, correlativo, operación, auditoría, idempotencia);
  - revertir el asiento de una venta → la operación queda `REVERTIDA` y los saldos vuelven;
  - aislamiento: la empresa B no ve ni revierte las operaciones de A (404 `PLT-017`);
  - `auditor` → 403 `PLT-010` en los `POST`; lista y detalle, 200.
- [ ] **Paso 11: mutación del revisor** (en el prompt, como criterio): usar el total en lugar de la base en `ArmadorVenta` → falla `venta-contado-con-iva`; quitar el `if` de líneas en cero de `LineasBuilder` → falla `venta-solo-exenta`.
- [ ] **Paso 12:** `./mvnw -q clean verify` en verde, con el conteo de surefire más failsafe.

### Tarea U2: rediseño de las pantallas existentes (Frontend, en paralelo con C1/B1/B2)

**Archivos:** `frontend/src/apps/contabilidad/{catalogo,libro-diario,mayor,reportes,configuracion,reglas,compartido}/**`, `frontend/src/nucleo/{api-keys,espacio,perfil}/**` y los selectores de `e2e/f2..f4` que cambien.

**Interfaces:** consume los componentes de U1 y el cliente regenerado tras C1.

- [ ] **Paso 1:** Balanza (hallazgo del e2e de F4, antes F4-08):
  - totales de Debe/Haber y de saldos deudores/acreedores en el `<tfoot>` de `TablaContable`;
  - la etiqueta "Cuadra" con `--color-exito`, o una alerta con la diferencia calculada con `decimal.js`.

  La prueba usa datos con los cuatro totales distintos entre sí, afirmado en la prueba.
- [ ] **Paso 2: reportes.**
  - `BarraFiltrosReporte` y `MenuExportar` en los seis reportes;
  - encabezado de informe (espacio, período, "Generado el …");
  - ESF en dos columnas a partir de 1280 px;
  - quitar todo texto visible que cite documentos internos;
  - Diagnóstico con "combinaciones de cuenta, año y mes".
- [ ] **Paso 3: Catálogo.**
  - árbol con `collapsible`, búsqueda y filtros;
  - `ValorBloqueado` en las cuentas con `sistema`: nombre, código y estado sin editar, con el motivo "Cuenta del catálogo base NIIF: agrega una subcuenta propia si necesitas más detalle";
  - "Agregar subcuenta" solo en filas sin movimientos que no son de 8 dígitos;
  - el formulario ya no pide naturaleza: la muestra derivada, como texto.
- [ ] **Paso 4: Libro Diario.**
  - lista con filas expandibles y filtro de origen (Operación, Manual, n8n, Reversión);
  - el detalle usa `EstadoDocumento` y la sección Historial (auditoría, si hay endpoint; si no, `creadoPor` y `creadoEn`);
  - el asiento manual pasa a "Asiento manual (avanzado)": cuentas recientes primero (preferencia local), `Enter` en el último monto agrega una línea, totales en un pie fijo y el botón "Cuadrar con esta línea", que pone la diferencia en el lado que falta.
- [ ] **Paso 5: Configuración** con pestañas:
  - Espacio de trabajo;
  - Contabilidad: solo el modo de precio; las cuentas de IVA como `ValorBloqueado`;
  - Reglas, agrupadas por tipo de operación, con un selector de cuenta filtrado por `prefijoPermitido`;
  - Apps y Perfil.

  API keys pasa a Integraciones.
- [ ] **Paso 6: pruebas por pantalla** (Vitest): estados de carga, vacío y error; ningún `input` en una cuenta del sistema; el selector de reglas solo ofrece cuentas con el prefijo; en la Balanza, la etiqueta Cuadra o la alerta con la diferencia exacta.
- [ ] **Paso 7: verificación.** `pnpm lint && pnpm test && pnpm build`; con el backend, `pnpm e2e:f2 && pnpm e2e:f3 && pnpm e2e:f4` en verde (selectores actualizados y comentados).
- [ ] **Paso 8: criterios de UX** (el revisor en Chrome, 1440 px y 1024 px): sección G completa, y un asiento manual de 4 líneas cuadrado **sin tocar el mouse**.

---

## Oleada 3

### Tarea B3: bloqueos de edición en el backend (Contable)

**Archivos:** `contabilidad/aplicacion/GestionarCatalogo.java`, `GestionarConfiguracionContable.java`, `GestionarReglas.java`, `dominio/catalogo/ReglasCatalogo.java`, `dominio/ExcepcionContabilidad.java`, `PrecargarContabilidad.java` (marca `sistema = true` al copiar), `infraestructura/*Jdbc.java` correspondientes y sus pruebas. Se reactivan las pruebas marcadas `@Disabled` en C1.

- [ ] **Paso 1: pruebas en rojo** (`BloqueosIT`):

```java
@Test void editarUnaCuentaDelSistemaResponde422Con021() { /* PATCH nombre de 11010101 → 422 CON-021; fila sin cambios */ }
@Test void unaSubcuentaPropiaSiSeEdita() { /* POST 11010104 bajo 110101 → 201, sistema=false; PATCH nombre → 200 */ }
@Test void laNaturalezaSeDerivaDelPadre() { /* POST 12020104 bajo 120201 (ACREEDORA) → naturaleza ACREEDORA */ }
@Test void enviarNaturalezaSeIgnora() { /* Spring Boot no falla ante propiedades desconocidas: POST con naturaleza DEUDORA bajo 120201 → 201 con naturaleza ACREEDORA (derivada) */ }
@Test void laConfiguracionSoloCambiaElModoDePrecio() { /* PUT con modo SIN_IVA → 200; las cuentas de IVA siguen siendo 21020101/11040101 */ }
@Test void unaReglaNoAceptaUnaCuentaFueraDeSuGrupo() { /* PUT VENTA/COBRO/EFECTIVO → 51010101: 422 CON-022; → 11010104 propia: 200 */ }
```

- [ ] **Paso 2:**
  - `ReglasCatalogo.exigirEditable(Cuenta)` lanza `CON-021` si `sistema`;
  - la naturaleza de una cuenta nueva es la del padre (nivel > 1) o la de la clase;
  - la precarga inserta con `sistema = true`;
  - `GestionarReglas` valida `cuenta.codigo().startsWith(regla.prefijoPermitido())` o lanza `CON-022`;
  - `GestionarConfiguracionContable` solo actualiza `modo_precio_defecto`.
- [ ] **Paso 3:** `./mvnw -q clean verify` en verde, con las pruebas que desactivó C1 reactivadas y adaptadas (listadas en el reporte).
- [ ] **Paso 4:** mutación: quitar la comprobación de `sistema` → falla `editarUnaCuentaDelSistemaResponde422Con021`.

### Tarea B4: activos fijos y depreciación (Contable)

**Archivos:** `dominio/activos/{CategoriaActivo,ActivoFijo,CalculadoraDepreciacion}.java`, `dominio/operaciones/{ArmadorActivoFijo,ArmadorDepreciacion}.java`, `aplicacion/{RepositorioActivos,RegistrarDepreciacion}.java`, `infraestructura/RepositorioActivosJdbc.java`, `api/ControladorOperacionesActivos.java` y sus pruebas y casos dorados.

**Interfaces:**

```java
/** Cuota mensual en línea recta; la última cuota ajusta el redondeo para que el acumulado sea costo − residual. */
public final class CalculadoraDepreciacion {
    public static Dinero cuota(Dinero costo, Dinero residual, int vidaMeses, int cuotasPrevias, Dinero acumuladoPrevio) { … }
    public static boolean corresponde(LocalDate adquisicion, YearMonth mes, boolean iniciaMesSiguiente, int vidaMeses) { … }
}
```

- [ ] **Paso 1: casos dorados, en rojo** (con vidas **de prueba** inyectadas, no las de `plantilla_vida_util`):

| Caso | Datos | Esperado |
|---|---|---|
| cuota normal | costo 1200.00, residual 0, 24 meses | 50.00 cada mes |
| redondeo | costo 1000.00, 0, 3 meses | 333.33, 333.33, **333.34** |
| con residual | costo 1000.00, residual 100.00, 36 meses | 25.00 |
| terreno | TERRENO | no genera cuota |
| inicio | adquirido el 15/03, mes siguiente | marzo no, abril sí |
| fin de vida | 24 cuotas registradas | el mes 25 no genera cuota |
| compra con Crédito Fiscal | EQUIPO_COMPUTO, 1130.00 CON_IVA, BANCO | D 12010102 1000.00 · D 11040101 130.00 · H 11010103 1130.00; activo con costo 1000.00 |
| compra con Factura | MOBILIARIO_EQUIPO, 500.00, CREDITO | D 12010101 500.00 · H 21010101 500.00; costo 500.00 |
| asiento de depreciación | dos equipos de cómputo (50.00 y 20.00) y un vehículo (100.00) | D 42020105 170.00 · H 12020102 70.00 · H 12020103 100.00 |

- [ ] **Paso 2:** implementar la calculadora y los armadores. `COMPRA_ACTIVO_FIJO` también inserta `activo_fijo`, con la vida y el residual de `plantilla_vida_util` en ese momento.
- [ ] **Paso 3: `RegistrarDepreciacion`.**
  1. Si alguna categoría con activos en uso tiene `confirmada = false` → 422 `CON-023` "Las vidas útiles de <categorías> no están confirmadas por el contador".
  2. El mes no puede ser futuro (`CON-007` sobre el último día del mes).
  3. Calcula por activo, arma un solo asiento con `origen_tipo = OPERACION` e inserta una fila `VIGENTE` por activo en `depreciacion_registrada`.
  4. Si no hay nada que depreciar → 422 `PLT-002` "No hay activos que depreciar en <mes>".
- [ ] **Paso 4: reversión.** El oyente de B2 también marca `REVERTIDA` las filas de `depreciacion_registrada` de ese asiento.
- [ ] **Paso 5: pruebas de integración.**
  - con `confirmada = false` → 422 `CON-023` y cero filas;
  - con `confirmada = true` (la prueba la cambia como dueño y la restaura en `finally`) → 201 y las líneas del caso dorado;
  - **foco 2:** dos hilos para el mismo mes → un 201 y el otro 409 `PLT-008` o la violación de `uq_depreciacion_vigente` traducida a 409 `PLT-008`, **sin código nuevo**; una sola fila por activo;
  - revertir y volver a registrar el mes → 201.
- [ ] **Paso 6:** `./mvnw -q clean verify` en verde. Mutación: quitar el ajuste de la última cuota → falla el caso de redondeo.

### Tarea B5: tablero (Reportes)

**Archivos:** `dominio/tablero/*.java`, `aplicacion/ConsultarTablero.java`, `infraestructura/ConsultaTableroJdbc.java`, `api/ControladorTablero.java` y `TableroIT.java`.

- [ ] **Paso 1: prueba en rojo** con un escenario de dos meses (datos con totales distintos entre sí):
  - efectivo por cuenta de 1101 (incluida una subcuenta propia);
  - por cobrar = saldo de 1102;
  - por pagar = saldo de 2101;
  - ingresos, costos y gastos y utilidad del mes y del anterior, iguales a los del Estado de Resultados de esos rangos;
  - IVA estimado = el del Resumen de IVA del mes;
  - serie de 6 meses con ceros en los meses sin datos;
  - pendientes:
    - `REGLA_SIN_CUENTA` (cuenta las reglas inactivas sin cuenta que el usuario puede necesitar: `OTRO`, `OTRO_GASTO`);
    - `DEPRECIACION_PENDIENTE` (hay activos que corresponden al mes y no hay depreciación vigente) o `VIDA_UTIL_SIN_CONFIRMAR`;
    - `SALDO_CONTRARIO` (cuentas de detalle con saldo contrario a su naturaleza);
    - `MAYORIZACION_INCONSISTENTE` (el diagnóstico existente no es consistente).
- [ ] **Paso 2:** implementarlo **reutilizando** `ConsultaReportes` (saldo a una fecha, estado de resultados, IVA) y el diagnóstico, sin duplicar SQL. Cada consulta nueva lleva `empresa_id`.
- [ ] **Paso 3:** rendimiento con los 10,000 asientos de `ReportesRendimientoIT` → p95 < 1 s en 20 ejecuciones (el tablero es la primera pantalla).
- [ ] **Paso 4:** `./mvnw -q clean verify` en verde.

### Tarea U3: formularios de operación y listas (Frontend, después de C1; el backend de B2 hace falta para el e2e)

**Archivos:** `frontend/src/apps/contabilidad/operaciones/{formularios/Formulario*.tsx, PanelAsientoGenerado.tsx, useVistaPreviaOperacion.ts, registroOperaciones.ts, PaginaListaOperaciones.tsx, PaginaDetalleOperacion.tsx}` y sus pruebas; `frontend/e2e/f45-operaciones.spec.ts`; el script `e2e:f45`.

**Interfaces:**
- Produce: `registroOperaciones: EntradaRegistrar[]` (grupo, etiqueta, ruta, ícono, atajo) que consume `MenuRegistrar` de U1.
- Produce: `useVistaPreviaOperacion(tipo, datos)` con una espera de 300 ms y cancelación de la respuesta anterior.

- [ ] **Paso 1: prueba del foco 5, en rojo.**

```ts
it('muestra siempre la vista previa de los últimos datos aunque una respuesta anterior llegue tarde', async () => {
  // 1. La primera petición (113.00) responde después que la segunda (226.00)
  servidor.use(vistaPreviaConRetardo({ '113.00': 500, '226.00': 10 }));
  render(<FormularioVenta />);
  await escribir('Monto gravado', '113.00');
  await escribir('Monto gravado', '226.00');
  // 2. El panel termina mostrando 226.00, nunca 113.00
  await waitFor(() => expect(panel()).toHaveTextContent('$226.00'));
  await esperar(600);
  expect(panel()).not.toHaveTextContent('$113.00');
});
```

- [ ] **Paso 2:** implementar `useVistaPreviaOperacion` con `AbortController`, o comparando la clave de la petición, hasta que la prueba pase.
- [ ] **Paso 3: formularios.** Uno por tipo, con React Hook Form + Zod (esquemas de forma, sin cálculo contable):
  - `toggle-group` para forma de cobro o pago, documento y plazo;
  - montos con `MontoEntrada`;
  - fecha por defecto hoy;
  - el modo de precio por defecto viene de `GET /configuracion`;
  - la forma de pago usada la última vez queda en una preferencia local, con `try/catch`.

  `PanelAsientoGenerado` fijo a la derecha, con `TablaContable`, los totales y la etiqueta Cuadra; si la vista previa responde 422, muestra `mensajeDeError` con su acción.
- [ ] **Paso 4: guardar.**
  - "Guardar" y "Guardar y registrar otra" (`Ctrl+Enter`), con `Idempotency-Key` por intento (patrón `useClaveIdempotencia`);
  - aviso "Venta registrada · Asiento N.º 12/2026 · Ver";
  - el botón se deshabilita mientras la petición está en vuelo;
  - la nota de los sueldos y honorarios (spec §5.4) visible en el formulario de compra o gasto.
- [ ] **Paso 5: listas.** Ingresos (VENTA, COBRO_CLIENTE), Gastos (COMPRA_GASTO, PAGO_PROVEEDOR) y Bancos (APORTE_CAPITAL, PRESTAMO_RECIBIDO, PAGO_CUOTA, TRASLADO_FONDOS) con `GET /contabilidad/operaciones`, filtros, total del período en el pie y scroll con cursor. Detalle con `EstadoDocumento`, datos capturados en lenguaje de negocio, el asiento generado, el historial y Revertir (reutiliza `DialogoReversion` con el estado reiniciado al cambiar el id).
- [ ] **Paso 6: pruebas de componentes.** Cada formulario envía el cuerpo exacto del contrato; el panel muestra el 422 con su acción; "Guardar" está deshabilitado mientras la vista previa no cuadra o hay un error.
- [ ] **Paso 7: e2e `f45-operaciones.spec.ts`.** Usuario nuevo: registra una venta a crédito con IVA, un cobro, una compra con Crédito Fiscal, un pago, un aporte y un traslado. Cada uno aparece en su lista, en el Libro Diario (origen Operación) y en la Balanza. Revierte uno. Sin errores de consola ni respuestas 5xx.
- [ ] **Paso 8: criterio de UX, medido en el e2e:** una venta a crédito con IVA se registra en < 20 s y solo con el teclado (`N` → Venta → campos con `Tab` → `Ctrl+Enter`).

### Tarea U4: Inicio (Frontend, después de B5)

**Archivos:** `frontend/src/apps/contabilidad/inicio/{PaginaInicio,IndicadoresInicio,ListaPendientes,GraficoIngresosGastos,UltimasOperaciones}.tsx` y sus pruebas.

- [ ] **Paso 1:** leer el skill `dataviz` antes del gráfico: barras agrupadas de ingresos y gastos de 6 meses, con colores de los tokens, ejes con `$` y tooltip.
- [ ] **Paso 2:** indicadores con `Monto` y la variación contra el mes anterior (flecha con texto accesible). Pendientes con `Pendiente` y acción: `REGLA_SIN_CUENTA` → Reglas, `DEPRECIACION_PENDIENTE` → Activos y `SALDO_CONTRARIO` → Balanza filtrada. Los rechazos de n8n de los últimos 7 días salen de `GET /integraciones/operaciones?resultado=RECHAZADO&desde=` cuando exista (F5-04); hasta entonces el bloque no se muestra.
- [ ] **Paso 3:** pruebas: estado vacío de una empresa sin movimientos ("Registra tu primera venta" con su acción), carga con esqueleto y un pendiente por tipo.
- [ ] **Paso 4: criterio de UX.** Toda la información de Inicio cabe sin scroll en 1440×900.

### Tarea U5: Activos fijos (Frontend, después de B4)

**Archivos:** `frontend/src/apps/contabilidad/activos/{PaginaActivos,FormularioActivoFijo,BotonDepreciacionMes}.tsx` y sus pruebas.

- [ ] **Paso 1:** tabla de activos con valor en libros y vida útil. `BotonDepreciacionMes` muestra "Registrar depreciación de <mes>", o el pendiente `CON-023` como `Pendiente` con el texto "El contador debe confirmar las vidas útiles" y sin botón.
- [ ] **Paso 2:** el formulario de compra de activo reutiliza `PanelAsientoGenerado` y el patrón de U3.
- [ ] **Paso 3:** pruebas y e2e: compra de un equipo; la depreciación bloqueada con `CON-023` en el entorno de desarrollo (vidas sin confirmar).

---

## Oleada 4

### Tarea F5-03: webhook de n8n (Integración)

**Archivos:**
- `backend/src/main/java/com/bcodesphere/pilot/integracion/**`
- `V19__operaciones_externas.sql`
- `plataforma/api/ConfiguracionSeguridad.java` (matcher acotado a `/api/v1/integraciones/n8n/**`)
- `plataforma/api/FiltroEmpresaActiva.java` o `ExigirAppInstalada.java` (el segmento `integraciones` exige la app `contabilidad` para `PLT-004`)
- `pom.xml` (`com.networknt:json-schema-validator` y `com.bucket4j:bucket4j_jdk17-core`, versiones verificadas en Maven Central)
- sus pruebas y `backend/src/test/resources/casos/operaciones/cierre/*.json`

**Interfaces:** consume `ContabilizarOperacion.contabilizar(new OperacionContable(id, "CIERRE_INGRESOS_DIARIO", fecha, descripcion, null, datos, "N8N"))` y `CobrosNoCuadranException` (B2).

- [ ] **Paso 1:** pruebas en rojo de los criterios del plan de F5:
  - CON_IVA exacto;
  - SIN_IVA → 422 `INT-006` con `diferencia` 146.90;
  - repetición con la misma clave → 201 con `Idempotency-Replayed: true`;
  - misma clave con otro cuerpo → 422 `INT-005`;
  - otra clave con el mismo `idExterno` → 409 `INT-004` con `operacionId` y `asientoId`;
  - forma de pago sin regla → 422 `CON-020` y un intento en la bitácora;
  - después de revertir, el reenvío se acepta;
  - cuerpo > 1 MB → 413 `INT-010`;
  - 61 peticiones en un minuto → 429 con `Retry-After` (ADR-040; límite configurable `pilot.integracion.limite-por-minuto`);
  - sin `Idempotency-Key` → 428 `INT-008`;
  - carrera → 409 `INT-009`;
  - la bitácora (`/integraciones/operaciones`) con OIDC responde 200 al `auditor` (no la captura la cadena de API keys).
- [ ] **Paso 2:** V19 con las tablas de la guía técnica §9.4, RLS `TO pilot_app` y permisos de solo inserción, más `UPDATE (estado, version)` en `operacion_externa`.
- [ ] **Paso 3:** filtro de límite de peticiones (después de la autenticación, antes de leer el cuerpo) y límite de tamaño.
- [ ] **Paso 4: validación.** Esquema JSON (`api-spec/esquemas/operaciones/cierre-ingresos-diario/v1.json`, cargado desde el classpath) → reglas fuera del esquema (`INT-001`: códigos repetidos, al menos un ingreso y un cobro mayores que cero) → `totalCobrado` contra Σ cobros (`INT-006`).
- [ ] **Paso 5: transacción única.** Idempotencia (estados adicionales {409}) → operación vigente (`INT-004`) → `contabilizar` → insertar `operacion_externa`. `CobrosNoCuadranException` → `INT-006`; violación de los índices únicos → `INT-009`.
- [ ] **Paso 6: rechazos.** Se registran en `intento_operacion_externa` en una transacción **aparte** (`REQUIRES_NEW`), porque la principal se revierte, sin datos personales.
- [ ] **Paso 7:** oyente de `AsientoRevertido` (`origenTipo = N8N`) → `operacion_externa.estado = REVERTIDO`.
- [ ] **Paso 8:** `./mvnw -q clean verify` en verde. Revisión de la regla 1.1.3 en cada SQL.

### Tarea F5-04: bitácora en el backend (Integración)

**Archivos:** `integracion/{api,aplicacion,infraestructura}/Bitacora*.java`, exportación de la bitácora (reutiliza **solo** tipos públicos; si hace falta un generador de PDF/XLSX/CSV común, se pide al revisor que lo exponga como API pública de `contabilidad` en una tarea aparte) y sus pruebas.

- [ ] **Paso 1:** pruebas en rojo:
  - lista unificada (operaciones y rechazos) de la más reciente a la más antigua, con cursor y filtros de fecha, resultado y sistema de origen;
  - detalle con el resumen o el Problem Details, sin datos personales;
  - exportación con los mismos filtros;
  - rol `auditor`;
  - aislamiento entre empresas.
- [ ] **Paso 2:** implementar con SQL `UNION ALL` de las dos tablas, cada rama con `empresa_id`.
- [ ] **Paso 3:** `./mvnw -q clean verify` en verde.

### Tarea U6: bitácora de n8n (Frontend, después de F5-04)

- [ ] Lista con `TablaContable`, filtros como etiquetas, `EstadoDocumento` o una etiqueta de rechazo con el código traducido por `mensajeDeError`, detalle con la acción sugerida (p. ej. `CON-020` → "Configurar reglas") y `MenuExportar`. Pruebas y la sección G.
- [ ] Inicio (U4) muestra los rechazos de los últimos 7 días.

### Tarea F5-06: plantilla de n8n y runbook (Integración, sin `backend/`)

- [ ] `integraciones/plantillas-n8n/cierre-ingresos-diario.json`: Webhook → validación mínima → `Idempotency-Key` = SHA-256 del cuerpo → HTTP Request con los reintentos de la guía técnica §12.7 (sí: 409 `INT-009`, 422 `CON-020` tras configurar, 429 respetando `Retry-After`, 5xx con espera exponencial; no: el resto) → Error Trigger.
- [ ] `docs/runbooks/correccion-cierre.md`, según la guía técnica §12.8.
- [ ] Prueba manual con el n8n del compose (`N8N_PUERTO=5679` si el 5678 está ocupado) contra el backend local.

---

## Oleada 5

### Tarea QA-F45: aceptación de F4.5 y de F5 (QA)

- [ ] Casos dorados de cada tipo de operación recorridos por la API, con `fuente` y `[VERIFICAR]`, sin repetir las pruebas de B2 a B5 (patrón de F3-05 y F4-06).
- [ ] Propiedad "saldo = Σ líneas" con secuencias aleatorias que mezclan operaciones guiadas, asientos manuales y reversiones (semillas fijas).
- [ ] Aislamiento A/B de operaciones, activos, tablero y bitácora, por la API y por SQL como `pilot_app`.
- [ ] Mismo cierre en paralelo → un solo asiento (guía técnica §15).
- [ ] Playwright: `e2e:f45` y `e2e:f5` (cierre por n8n → asiento → reportes), y `e2e:f1..f4` en verde.
- [ ] e2e en Chrome del revisor, con GIF por tramo: registrar cada operación con el teclado, Inicio con sus pendientes, bloqueos visibles, reportes y bitácora.

---

## Autorrevisión del plan

- **Cobertura del spec:**
  - §5.2 y §5.3 → B2 y B4;
  - §5.4 → B1 (reglas) y U3 (nota);
  - §5.5 → B1 (plantilla) y B4;
  - §5.6 → B1 y F5-03 (V19);
  - §5.7 → C1, B2, B4 y B5;
  - §6 → C1, B1, B3 y U2;
  - §7 → U1;
  - §8 → U1 a U6;
  - §9.1 → A0 (protocolo) y cada U;
  - §9.2 → B2 a B5, U3 y QA-F45;
  - §10 → orden de las oleadas;
  - §12 → `[VERIFICAR]` en B1 y B4.
- **Consistencia de nombres:** los tipos (`VENTA` … `DEPRECIACION_MENSUAL`), las categorías (`INGRESO`, `COBRO`, `PAGO`, `GASTO`, `CONTRAPARTIDA`, `ACTIVO`, `DEPRECIACION`) y los códigos de las reglas (`GRAVADO`, `EXENTO`, `NO_SUJETO`, `EFECTIVO`, `BANCO`, `TARJETA`, `CREDITO`…) son los mismos en B1 (SQL), B2 (armadores) y C1 (enums del contrato).
- **Foco de revisión:** cada punto tiene su prueba en su tarea (B2 focos 1 y 4, B4 foco 2, B1 foco 3, U3 foco 5).
- **Sin códigos nuevos fuera de `CON-021`, `CON-022` y `CON-023`:** la carrera de la depreciación usa `PLT-008`, y "sin activos que depreciar" usa `PLT-002`.
