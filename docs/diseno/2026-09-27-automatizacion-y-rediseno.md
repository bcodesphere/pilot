# Automatización del ciclo contable y rediseño de la experiencia — Especificación de diseño

| Campo | Valor |
|---|---|
| Documento | Especificación de diseño (spec) de la fase **F4.5 — Automatización y experiencia** |
| Estado | Borrador para revisión del usuario |
| Fecha | 2026-09-27 |
| Autor | Arquitecto / revisor de Pilot 1.0 |
| Decisiones que origina | ADR-041 (motor de operaciones guiadas), ADR-042 (bloqueos de edición), ADR-043 (sistema de diseño y navegación) |
| Relación con el plan | Fase nueva entre F4 y F6; reordena F5 (sección 10) |

---

## 1. Contexto y problema

Pilot 1.0 cumple las reglas contables (partida doble, mayorización en tiempo real, IVA, estados financieros), pero su interfaz **traslada el libro contable en papel a una pantalla**. Para registrar una venta, el usuario escribe Debe y Haber línea por línea y elige cada cuenta, como en un libro diario manuscrito. Eso choca con el usuario objetivo: una persona natural o una PYME de El Salvador **sin formación contable**, con un contador que revisa.

Hallazgos de la prueba de punta a punta de F4 (2026-09-27) y de la revisión del frontend:

- **Captura:** el Libro Diario exige conocer la partida doble y el catálogo. El motor que ya decide cuentas (las reglas de contabilización de n8n, ADR-020) no se ofrece al usuario manual.
- **Edición indebida:** el usuario puede renombrar o cambiar la naturaleza de las cuentas base, cambiar las cuentas de IVA y asignar cualquier cuenta a una regla.
- **Interfaz genérica:**
  - no hay sistema de diseño (`index.css` solo importa Tailwind) ni navegación lateral;
  - el tablero de inicio no existe;
  - los botones son negros por defecto;
  - las tablas no tienen cifras tabulares, totales fijos ni estados de carga o vacío;
  - aparecen textos internos ("CLAUDE.md §10.5", "Cuadra: true");
  - la Balanza omite los totales de saldos.
- **Proceso:** el protocolo de revisión solo audita reglas contables, seguridad y pruebas; no tiene criterios de UI/UX. Los prompts de frontend pedían "pantallas con filtros" sin especificar la experiencia.

## 2. Objetivos

1. **Automatizar el ciclo contable** en lo que 1.0 permite. El usuario describe hechos de negocio y Pilot genera, valida, numera, mayoriza y audita el asiento, y lo refleja al instante en libros y estados.
2. **Conservar toda la información contable:** cada operación produce un asiento visible en el Libro Diario, el Mayor, la Balanza y los estados, igual que hoy.
3. **Quitar la edición de lo que define cómo contabiliza Pilot**, aplicándolo en el backend.
4. **Rediseñar todo el frontend**, salvo el login y el registro, con un sistema de diseño propio basado en la paleta del login y en convenciones de sistemas contables profesionales.
5. **Cambiar el proceso de revisión** para que la calidad de UX se exija y se verifique en cada tarea.

### Criterios de éxito

- Un usuario sin formación contable registra una venta a crédito con IVA **en menos de 20 segundos y solo con el teclado**, y ve el asiento generado antes de guardarlo.
- Las diez operaciones guiadas generan asientos que coinciden al centavo con los casos dorados (validación del contador `[VERIFICAR]`).
- Ninguna cuenta del sistema, cuenta de IVA ni naturaleza se puede cambiar por la API. Probado con pruebas de integración.
- Todas las pantallas pasan la sección G (UI/UX) del protocolo de revisión, en 1440 px y en 1024 px.

## 3. Fuera de alcance

- Asientos recurrentes programados, cierres mensual y anual, cuenta liquidadora y períodos contables: siguen en la guía técnica §20.3.
- Clientes y proveedores individuales (subcuentas por tercero, estados de cuenta): catálogo comercial, §20.2. El nombre del tercero se anota en la descripción.
- Planilla (ISSS, AFP, renta de sueldos) y retenciones automáticas: se registran como gasto simple o con el asiento manual avanzado.
- Tema oscuro de la app: los tokens quedan preparados, pero solo se implementa el tema claro.
- Cambios al login y al registro de Keycloak.
- Facturación electrónica (DTE).

## 4. Decisiones tomadas con el usuario (2026-09-27)

| # | Decisión |
|---|---|
| D1 | La automatización de 1.0 son **operaciones guiadas + tablero**. Recurrentes y cierres quedan fuera. F5 sigue. |
| D2 | Deja de ser editable: **las cuentas del catálogo base, la naturaleza, las cuentas de IVA de la configuración** y la cuenta de las **reglas**, que se restringe a su grupo permitido. |
| D3 | El asiento libre (Debe/Haber) **se conserva como opción avanzada** ("Asiento manual"). |
| D4 | Operaciones de la primera versión: **venta, compra o gasto, cobro a cliente, pago a proveedor, aporte, préstamo recibido, pago de cuota, traslado de fondos, compra de activo fijo y depreciación del mes**. |
| D5 | Motor único en el backend: **enfoque A**. Se descartan las plantillas en el frontend (violan ADR-006) y las plantillas configurables por el usuario (§20.3, contradicen D2). |
| D6 | Diseño de referencia: QuickBooks Online ("+ Nuevo"), Xero (tablero y pendientes), Odoo (listas, barra de estado, historial), Alegra (navegación por Ingresos/Gastos) y SAP B1/Contpaqi (convenciones de tabla). Nada de estética genérica. |

---

## 5. Motor de operaciones guiadas (ADR-041)

### 5.1 Principio

El usuario indica **qué pasó**; Pilot decide **cómo se contabiliza**. Toda la lógica contable vive en el núcleo Java (ADR-006). El frontend solo captura datos de negocio y muestra la vista previa **calculada por el backend**.

### 5.2 Componentes (módulo `contabilidad`)

```text
contabilidad (paquete raíz, API pública)
├── ContabilizarOperacion          interfaz: contabilizar(OperacionContable) → OperacionContabilizada
├── OperacionContable              tipo + fecha + datos tipados por tipo + origen (MANUAL | N8N) + operacionId
└── OperacionContabilizada         asiento (id, número, año, fecha) + resumen normalizado

contabilidad.dominio.operaciones
├── TipoOperacion                  enum de los once tipos (10 guiados + CIERRE_INGRESOS_DIARIO)
├── ArmadorAsiento<T>              estrategia por tipo: datos → líneas (sin Spring)
├── ArmadorVenta, ArmadorCompraGasto, ArmadorCobro, …, ArmadorCierreIngresos
└── ResolutorCuentas               (tipo, categoría, código) → cuenta, con validación CON-020 / CON-022

contabilidad.aplicacion
├── ServicioContabilizarOperacion  orquesta: fecha/tasa → armador → ReglasAsiento.validar → GuardarAsiento
├── RegistrarOperacion             caso de uso de la API manual: idempotencia + tabla operacion
└── PrevisualizarOperacion         mismo armado sin guardar
```

- Una sola ruta de guardado: `GuardarAsiento` numera, inserta, mayoriza y audita en la transacción del llamador (ADR-018).
- `integracion` usa el mismo `ContabilizarOperacion` para `CIERRE_INGRESOS_DIARIO`, con su propia tabla `operacion_externa` y su idempotencia (ADR-039).
- La reversión no cambia: `RevertirAsiento` publica `AsientoRevertido`. Un oyente de `contabilidad` pasa la `operacion` a `REVERTIDA`, y el de `integracion` pasa su `operacion_externa` a `REVERTIDO`.

### 5.3 Catálogo de operaciones y asientos que generan

Convenciones:
- Las cuentas son las del catálogo base V15, resueltas por regla. La regla puede apuntar a una subcuenta propia del mismo grupo (sección 6).
- El modo de precio (`CON_IVA`/`SIN_IVA`) toma por defecto el de la configuración y se puede cambiar por operación (ADR-015).
- El IVA se calcula con `CalculadoraIva` y la tasa vigente a la fecha.
- Las líneas en cero se omiten.
- **Forma de cobro o pago**, cuenta por defecto:

| Código | Cuenta |
|---|---|
| `EFECTIVO` | 11010101 Caja general |
| `BANCO` | 11010103 Bancos |
| `TARJETA` (solo cobro) | 11020102 CxC emisores de tarjetas |
| `CREDITO` en venta | 11020101 Clientes |
| `CREDITO` en compra | 21010101 Proveedores |

| Tipo | Datos que captura el usuario | Asiento generado |
|---|---|---|
| `VENTA` | fecha, descripción, montos gravado/exento/no sujeto (al menos uno > 0), modo de precio, forma de cobro | **D** forma de cobro (total) · **H** 51010101 base gravada · **H** 21020101 IVA débito · **H** 51010102 exento · **H** 51010103 no sujeto |
| `COMPRA_GASTO` | fecha, descripción, destino (lista cerrada, 5.4), documento (`CREDITO_FISCAL` o `FACTURA`), monto, modo de precio, forma de pago | Crédito Fiscal: **D** destino (base) · **D** 11040101 IVA crédito · **H** forma de pago (total). Factura (IVA no deducible, §11.2): **D** destino (total) · **H** forma de pago |
| `COBRO_CLIENTE` | fecha, origen (`CLIENTES` o `TARJETAS`), monto, destino (efectivo/banco); si es `TARJETAS`: comisión y anticipo de IVA **tal como vienen en la liquidación** (Pilot no calcula esas tasas) | Clientes: **D** destino · **H** 11020101. Tarjetas: **D** banco (neto) · **D** 42010103 comisión · **D** 11040104 anticipo IVA · **H** 11020102 (bruto) |
| `PAGO_PROVEEDOR` | fecha, monto, origen (efectivo/banco) | **D** 21010101 · **H** origen |
| `APORTE_CAPITAL` | fecha, monto, destino (efectivo/banco) | **D** destino · **H** 31010101 |
| `PRESTAMO_RECIBIDO` | fecha, monto, plazo (`CORTO`/`LARGO`), destino | **D** destino · **H** 21040101 (corto) o 22010101 (largo) |
| `PAGO_CUOTA` | fecha, plazo, capital, intereses, comisión (opcional), origen | **D** préstamo (capital) · **D** 43010101 intereses · **D** 43010102 comisión · **H** origen (suma) |
| `TRASLADO_FONDOS` | fecha, monto, cuenta de origen y de destino (solo cuentas de detalle del grupo 1101, distintas) | **D** destino · **H** origen |
| `COMPRA_ACTIVO_FIJO` | fecha, descripción, categoría (5.5), documento, monto, modo de precio, forma de pago | **D** cuenta de activo de la categoría (costo = base con Crédito Fiscal, total con Factura) · **D** 11040101 IVA crédito (solo Crédito Fiscal) · **H** forma de pago. Además, un alta en `activo_fijo` |
| `DEPRECIACION_MENSUAL` | año y mes (no futuro) | Por categoría: **D** 42020105 Depreciación · **H** depreciación acumulada de la categoría; una fila en `depreciacion_registrada` por activo |
| `CIERRE_INGRESOS_DIARIO` | (solo n8n, guía técnica §12) | Sin cambios respecto de §12.5 |

Descripción del asiento: `<nombre del tipo> — <descripción del usuario>` (p. ej. "Venta — Mostrador, cliente Juan"), recortada a 500 caracteres.

### 5.4 Destinos de compra o gasto (lista cerrada, reglas precargadas)

| Código | Cuenta por defecto | Grupo permitido |
|---|---|---|
| `MERCADERIA` | 41020101 Compras de mercadería | 4102 |
| `ALQUILER` | 42020102 Alquileres | 4202 |
| `SERVICIOS_BASICOS` | 42020103 Energía, agua y teléfono | 4202 |
| `PAPELERIA` | 42020104 Papelería y útiles | 4202 |
| `SUELDOS_ADMINISTRACION` | 42020101 | 4202 |
| `SUELDOS_VENTAS` | 42010101 | 4201 |
| `HONORARIOS` | 42020106 | 4202 |
| `PUBLICIDAD` | 42010102 | 4201 |
| `COMISIONES_BANCARIAS` | 43010102 | 4301 |
| `OTRO_GASTO` | sin cuenta, inactiva (como `OTRO` de ADR-035) | 42 |

Los sueldos y los honorarios se registran como gasto simple. Las retenciones (renta, ISSS, AFP) no se automatizan en 1.0 y se registran con el asiento manual avanzado. La nota aparece en el formulario.

### 5.5 Activos fijos y depreciación

| Categoría | Cuenta de activo | Depreciación acumulada | Se deprecia |
|---|---|---|---|
| `MOBILIARIO_EQUIPO` | 12010101 | 12020101 | Sí |
| `EQUIPO_COMPUTO` | 12010102 | 12020102 | Sí |
| `VEHICULO` | 12010103 | 12020103 | Sí |
| `EDIFICIO` | 12010202 | 12020201 | Sí |
| `TERRENO` | 12010201 | — | No |

- **Método:** línea recta. Cuota mensual = `round((costo − valor residual) / vida útil en meses, 2, HALF_UP)`. La última cuota ajusta la diferencia de redondeo para que la depreciación acumulada sea exactamente `costo − residual`.
- **`[VERIFICAR]` con el contador, antes de habilitar la operación** (tabla global `plantilla_vida_util`, cargada por migración):
  - vida útil por categoría;
  - valor residual (se propone 0);
  - mes de inicio (se propone el mes siguiente al de la adquisición).

  Mientras no se confirmen, `DEPRECIACION_MENSUAL` responde 422 `CON-023` ("vida útil sin confirmar") y el tablero lo muestra como pendiente.
- Una sola depreciación por activo y mes (índice único). Revertir el asiento marca esas filas como revertidas y permite registrar el mes otra vez.
- No hay baja ni venta de activos en esta versión; se hacen con el asiento manual avanzado.

### 5.6 Datos (migraciones nuevas, V16 en adelante)

| Objeto | Contenido |
|---|---|
| `operacion` | `id`, `empresa_id`, `tipo`, `fecha`, `descripcion`, `datos` (JSONB: la entrada ya validada), `resumen` (JSONB: base, IVA, total…), `total`, `asiento_id`, `estado` (`CONTABILIZADA`/`REVERTIDA`), `creado_en`, `creado_por`, `version`. RLS forzado; `pilot_app` solo con `SELECT`, `INSERT` y `UPDATE (estado, version)` |
| `asiento.origen_tipo` | Admite `OPERACION` (el CHECK se cambia en una migración nueva); `origen_id` = `operacion.id` |
| Reglas | Filas nuevas en `plantilla_regla_contabilizacion` por tipo (5.3, 5.4), columna `prefijo_permitido`; copia a las empresas existentes |
| `activo_fijo` | `id`, `empresa_id`, `descripcion`, `categoria`, `fecha_adquisicion`, `costo`, `valor_residual`, `vida_util_meses`, `operacion_id`, `estado` (`EN_USO`/`REVERTIDO`), `version`. RLS forzado |
| `depreciacion_registrada` | `activo_id`, `empresa_id`, `anio`, `mes`, `monto`, `asiento_id`, `estado`. Índice único `(activo_id, anio, mes) WHERE estado = 'VIGENTE'` |
| `plantilla_vida_util` | Global, de solo lectura: categoría, meses y residual; con valores `[VERIFICAR]` y un indicador `confirmada` |
| `cuenta_contable.sistema` | Booleano (sección 6) |

Toda sentencia filtra por `empresa_id` además de RLS (regla 1.1.3). Cada cambio de datos es una migración nueva.

### 5.7 API (contract-first)

Etiquetas por grupo, para que cada interfaz generada la implemente un controlador pequeño (guía técnica §8.1):

| Etiqueta | Operaciones |
|---|---|
| `operacionesIngresos` | `POST /contabilidad/operaciones/ventas`, `…/cobros`, más `…/vista-previa` de cada una |
| `operacionesGastos` | `…/compras-gastos`, `…/pagos-proveedores` (+ vista previa) |
| `operacionesBancos` | `…/aportes`, `…/prestamos`, `…/cuotas-prestamo`, `…/traslados` (+ vista previa) |
| `operacionesActivos` | `…/activos-fijos`, `…/depreciaciones` (+ vista previa), `GET /contabilidad/activos-fijos` |
| `operaciones` | `GET /contabilidad/operaciones` (cursor; filtros de tipo, período, estado y forma de pago) y `GET /contabilidad/operaciones/{id}` |
| `tablero` | `GET /contabilidad/tablero?anio&mes` |

- Todos los `POST` que guardan exigen `Idempotency-Key` y rol `contador`. Las vistas previas responden 200 con las líneas, los totales y `cuadra`, sin guardar (patrón de ADR-036).
- Montos como cadena decimal (ADR-013).
- Los errores usan los códigos existentes más `CON-021`, `CON-022` y `CON-023`, registrados en la guía técnica §10.2 antes de usarlos.
- **Antes de escribir el contrato**, el arquitecto genera en una copia las interfaces con `openapi-generator` para esta forma y compila (lección de F4-02).

**Tablero** (`GET /contabilidad/tablero`):
- saldos de cada cuenta de detalle de 1101;
- por cobrar (1102) y por pagar (2101);
- ingresos, costos y gastos, y utilidad del mes y del mes anterior;
- IVA débito menos crédito del mes;
- serie de 6 meses de ingresos y gastos;
- pendientes de contabilidad: reglas activas sin cuenta, depreciación del mes sin registrar (o vida útil sin confirmar), cuentas con saldo contrario a su naturaleza, diagnóstico inconsistente.

Los rechazos de n8n los aporta el frontend desde la bitácora de `integracion`: `contabilidad` no depende de `integracion`.

---

## 6. Bloqueos de edición (ADR-042)

| Elemento | Regla | Cómo se aplica | Error |
|---|---|---|---|
| Cuentas del catálogo base | Solo lectura: código, nombre, naturaleza y estado. El usuario agrega subcuentas propias bajo las cuentas base que no son de detalle, y esas sí las edita | `cuenta_contable.sistema = true` al copiar la plantilla; una migración la marca en las empresas existentes por coincidencia de código con `plantilla_cuenta` | 422 `CON-021` "Cuenta del sistema: no se puede modificar" |
| Naturaleza | Derivada: la del padre, o la de la clase en el nivel 1. Deja de ser un campo de entrada | Se quita de `POST`/`PATCH /contabilidad/cuentas`; en la respuesta es de solo lectura | — |
| Cuentas de IVA | Fijas: 21020101 y 11040101, desde la plantilla | `PUT /contabilidad/configuracion` solo acepta `modoPrecioDefecto` | — |
| Cuenta de una regla | Solo cuentas de detalle, activas, cuyo código empiece por el `prefijo_permitido` de la regla | Validación en `GestionarReglas` y en el `ResolutorCuentas` | 422 `CON-022` "La cuenta no pertenece al grupo permitido para esta regla" |
| Operaciones, asientos, activos | Inmutables; se corrigen revirtiendo | Permisos de BD (ADR-019) y API sin `PATCH` | `CON-008`/`CON-009` |

**En el frontend:**
- Un valor bloqueado se muestra como texto con un ícono de candado y una ayuda al pasar el cursor, no como un campo deshabilitado.
- Los selectores de cuenta solo ofrecen las cuentas válidas para su contexto.
- Los valores por defecto se llenan solos: la fecha de hoy, el modo de la configuración y la forma de pago usada la última vez (preferencia local del navegador, no un dato de negocio).

---

## 7. Sistema de diseño (ADR-043)

El documento operativo es `docs/diseno/sistema-de-diseno.md`, que crea la tarea U1. Aquí quedan sus decisiones.

### 7.1 Principios

1. **Primero el dato.** Densidad de sistema contable, con filas de 36 px, bordes finos de 1 px y sin sombras decorativas.
2. **Lenguaje de negocio en la entrada y lenguaje contable en la evidencia.** Los formularios hablan de "venta" y "forma de cobro"; el panel del asiento, los libros y los reportes usan cuentas, Debe y Haber.
3. **Cada pantalla dice qué hacer después:** estados vacíos con acción, pendientes en el tablero, errores con la solución.
4. **Teclado primero** en la captura.
5. **Nada genérico:**
   - sin degradados;
   - sin tarjetas con bordes muy redondeados en todas partes;
   - sin títulos gigantes centrados;
   - sin íconos decorativos;
   - sin sombras de colores;
   - sin textos de relleno.

### 7.2 Tokens (variables CSS en `index.css`, tema de Tailwind v4)

| Token | Valor | Uso |
|---|---|---|
| `--color-primario` | `#1d4ed8` | Acción principal, enlaces, foco (igual que el login) |
| `--color-primario-oscuro` | `#1e3a8a` | Hover, elemento activo de la barra lateral |
| `--color-primario-suave` | `#dbeafe` | Selección de fila, fondo de la etiqueta activa |
| `--color-lienzo` | `#f1f5f9` | Fondo de la app (igual que el login) |
| `--color-superficie` | `#ffffff` | Tablas, formularios, paneles |
| `--color-borde` | `#e2e8f0` | Bordes de 1 px |
| `--color-texto` | `#0f172a` | Texto principal |
| `--color-texto-suave` | `#475569` | Etiquetas, texto secundario (AA sobre blanco) |
| `--color-lateral` | `#1b1d21` | Barra lateral: el tono de la tarjeta oscura del login (se confirma contra una captura en U1) |
| `--color-lateral-texto` | `#cbd5e1` | Texto de la barra lateral |
| `--color-exito` | `#15803d` | Contabilizado, cuadra |
| `--color-alerta` | `#b45309` | Pendientes, saldo contrario a su naturaleza |
| `--color-error` | `#b91c1c` | Descuadre, rechazo |
| `--color-neutro-revertido` | `#64748b` | Revertido (con tachado sutil del monto) |

- Deudor y Acreedor se muestran con una etiqueta **D/A** neutra: no se les asigna "bueno" ni "malo" por color.
- Radios: 4 px en controles y 6 px en paneles.
- Espaciado: escala de 4 px.

### 7.3 Tipografía

- Red Hat Display (títulos) y Red Hat Text (cuerpo): las familias de PatternFly que usa el login. Red Hat Mono para códigos de cuenta y números de asiento.
- Se sirven localmente con paquetes `@fontsource`, sin CDN (versiones exactas verificadas en npm en U1).
- Todas las cifras con `font-variant-numeric: tabular-nums`. Los montos se alinean a la derecha y siempre con formato `es-SV` (`$1,234.56`).

### 7.4 Navegación y estructura

- **Barra lateral fija**, que se contrae a íconos por debajo de 1280 px:
  - Inicio
  - **Ingresos:** Ventas, Cobros
  - **Gastos:** Compras y gastos, Pagos
  - **Bancos y efectivo**
  - **Activos fijos**
  - **Contabilidad:** Libro Diario, Mayor, Catálogo
  - **Reportes**
  - **Integraciones:** Bitácora n8n, API keys
  - **Configuración:** Espacio de trabajo, Contabilidad (modo de precio y reglas), Apps, Perfil
- **Barra superior:**
  - migas de pan;
  - buscador global `Ctrl+K` (pantallas, cuentas por código o nombre, asientos por número);
  - botón primario **"+ Registrar"**;
  - menú del usuario.
- El lanzador de apps actual pasa a **Configuración → Apps**. Al entrar sin Contabilidad instalada, el Inicio muestra un estado vacío con "Instalar Contabilidad".

### 7.5 Componentes

- shadcn/ui (ya en uso): se agregan `sidebar`, `command`, `dropdown-menu`, `table`, `tabs`, `tooltip`, `sonner`, `skeleton`, `popover`, `calendar`, `toggle-group`, `breadcrumb`, `collapsible` y `chart` (Recharts).
- Cada dependencia nueva lleva su versión exacta, verificada en npm, y se justifica en el reporte de la tarea.
- **Componentes propios de dominio:** `Monto`, `CodigoCuenta`, `EtiquetaSaldo` (D/A), `EstadoDocumento` (barra de estado), `PanelAsientoGenerado`, `BarraFiltrosReporte`, `MenuExportar`, `TablaContable` (encabezado y totales fijos, filas expandibles), `EstadoVacio` y `Pendiente`.

### 7.6 Textos y errores

- Español de El Salvador, trato de "tú", verbos concretos ("Registrar venta", no "Enviar").
- Cada código de error de la API tiene un **mensaje para el usuario y una acción**, en un solo catálogo del frontend. Ejemplos:

| Código | Mensaje | Acción |
|---|---|---|
| `CON-020` | "Falta configurar la cuenta para «Otro gasto»." | "Configurar" |
| `CON-005` | "El asiento no cuadra por $X." | — |

- Los booleanos se muestran como "Sí"/"No". Ningún texto visible cita documentos internos.

---

## 8. Fichas de pantalla

Cada ficha la detalla `sistema-de-diseno.md` con sus estados (carga, vacío, error) y atajos. Aquí van el propósito y el contenido.

| Pantalla | Contenido |
|---|---|
| **Inicio** | Indicadores: efectivo y bancos (por cuenta), por cobrar, por pagar, utilidad del mes con su variación contra el mes anterior, IVA estimado. **Pendientes** accionables. Gráfico de ingresos contra gastos de 6 meses. Últimas 10 operaciones |
| **+ Registrar** | Menú agrupado (Ingresos, Gastos, Bancos, Activos) y, separado, "Asiento manual (avanzado)". Atajo `N` |
| **Formulario de operación** | A la izquierda, campos mínimos con selectores segmentados. A la derecha, el panel fijo "Asiento que se generará", con la vista previa del backend (espera de 300 ms tras cada cambio), totales y la etiqueta Cuadra. Botones "Guardar" y "Guardar y registrar otra" (`Ctrl+Enter`). Tras guardar, un aviso con el número del asiento y "Ver" |
| **Listas de Ingresos, Gastos y Bancos** | Operaciones en lenguaje de negocio. Filtros: período, tipo, forma de pago y estado. Total del período fijo al pie. Clic → detalle |
| **Detalle de operación o asiento** | Barra de estado (Contabilizado → Revertido); datos capturados; asiento generado; **Historial** (auditoría: quién y cuándo); acción Revertir con el diálogo de fecha |
| **Bancos y efectivo** | Una tarjeta por cuenta de 1101 con su saldo; debajo, los movimientos (el Mayor filtrado); acción "Traslado" |
| **Activos fijos** | Tabla: descripción, categoría, costo, depreciación acumulada, valor en libros y vida útil. Estado del mes: "Registrar depreciación de <mes>" o el pendiente de confirmación de las vidas útiles |
| **Libro Diario** | Lista con filas expandibles a sus líneas; filtros por período, origen (Operación, Manual, n8n, Reversión) y cuenta; botón secundario "Asiento manual" |
| **Asiento manual (avanzado)** | Formulario actual mejorado: cuentas recientes primero, `Enter` agrega línea, totales fijos, "Cuadrar con esta línea", vista previa de IVA sin cambios |
| **Mayor** | Selector de cuenta en árbol con búsqueda, atajos de período, saldo acumulado con D/A, exportación |
| **Catálogo** | Árbol expandible, búsqueda, candado en las cuentas del sistema, "Agregar subcuenta" en las filas que lo permiten; filtros "Con movimiento" y "Activas" |
| **Reportes** | Barra común de filtros y "Exportar ▾". Encabezado de informe (espacio, período, fecha de generación). **Balanza** con los totales de Debe/Haber y de saldos deudores/acreedores y la etiqueta Cuadra. **ESF en dos columnas** (Activo \| Pasivo y Patrimonio) en pantallas anchas. **ER** con el impuesto aparte. **IVA** por origen. **Diagnóstico** con el conteo de combinaciones. Leyenda de estado de gestión en los estados |
| **Integraciones** | Bitácora de n8n (F5) y API keys |
| **Configuración** | Espacio de trabajo (nombre); Contabilidad: modo de precio y reglas agrupadas por tipo de operación con selector restringido; Apps; Perfil |

- **Diseño adaptable:** escritorio primero (1440 px y 1280 px); tableta (1024 px) completa; teléfono (390 px) con Inicio y reportes de solo lectura.
- **Accesibilidad:** contraste AA, foco visible de 2 px con el primario, etiquetas asociadas y navegación completa por teclado.

---

## 9. Calidad y proceso

### 9.1 Sección G del protocolo de revisión (UI/UX)

- [ ] Solo se usan tokens del sistema de diseño: ningún color, radio ni tamaño de letra fuera de ellos.
- [ ] Cada vista tiene estados de carga (esqueleto), vacío (con acción) y error (mensaje y acción).
- [ ] Montos con `Monto` (cifras tabulares, a la derecha, `es-SV`); códigos con `CodigoCuenta`.
- [ ] Ningún texto visible cita documentos internos ni muestra booleanos o códigos crudos.
- [ ] Recorrido completo con el teclado; contraste AA verificado.
- [ ] Capturas en 1440 px y 1024 px en el e2e en Chrome del revisor, antes de aprobar.
- [ ] Cumple los criterios de UX del prompt (p. ej. el tiempo y los pasos para registrar una operación).

Un incumplimiento de la sección G es un hallazgo **Mayor**.

### 9.2 Pruebas

- **Dominio:** un armador por tipo, con casos dorados en `backend/src/test/resources/casos/operaciones/<tipo>/`, con fuente y `[VERIFICAR]` de contador. Incluyen redondeo del IVA, líneas en cero omitidas, la última cuota de depreciación y los bloqueos.
- **Integración:** cada endpoint (201, vista previa, idempotencia, `CON-020`/`021`/`022`/`023`), aislamiento entre empresas, reversión que actualiza `operacion` y `depreciacion_registrada`, y una depreciación que no se duplica en paralelo.
- **Frontend:** Vitest por componente de dominio y por formulario; Playwright para registrar cada operación, verla en el Libro Diario y en los reportes, y revertirla.
- **Mutaciones del revisor** en una copia con `backend/`, `api-spec/` e `infra/`.

---

## 10. Plan de ejecución

El backend va en serie (nunca dos tareas de `backend/` a la vez); el frontend va en paralelo.

| Oleada | Backend (en serie) | Frontend (en paralelo) |
|---|---|---|
| 0 — Arquitecto | Este spec; ADR-041, 042 y 043; guía técnica (§2.2, §9.3, §10.2, §11.3, §13, §18, §20); plan (fase F4.5); AGENTS.md (criterios de UX); protocolo (sección G); prueba del generador | — |
| 1 | F4-07 (conteo del diagnóstico, PDF Sí/No) → F4-06 (aceptación de F4; **cierra F4**) | U1: sistema de diseño y estructura (tokens, fuentes, barra lateral y superior, `Ctrl+K`, avisos, componentes de dominio, `sistema-de-diseno.md`) |
| 2 | C1: contrato de operaciones, tablero, bloqueos y activos → B1: migraciones → B2: motor genérico y los ocho tipos sin activos (reemplaza el F5-02 anterior) | U2: rediseño de lo existente (Catálogo, Libro Diario, Mayor, reportes con el hallazgo F4-08, Configuración, Integraciones/API keys) |
| 3 | B3: bloqueos (`CON-021`, `CON-022`, naturaleza derivada, IVA fijo) → B4: activos fijos y depreciación (`CON-023` hasta que se confirmen las vidas útiles) → B5: tablero | U3: formularios de operación y listas; U4: Inicio; U5: Activos fijos |
| 4 | F5-03: webhook (cierre como tipo del motor, V-nueva de `operacion_externa`, cadena de seguridad acotada a `/integraciones/n8n/**`, 60/min según ADR-040) → F5-04: bitácora en el backend | U6: bitácora; F5-06: plantilla de n8n y runbook |
| 5 | QA: aceptación de F4.5 y de F5 | e2e en Chrome del revisor de todo el recorrido |

Después sigue F6 (endurecimiento y piloto), sin cambios.

## 11. Riesgos

| Riesgo | Mitigación |
|---|---|
| El plazo de 24 h ya venció y esto suma más o menos el doble del esfuerzo de F4 | Oleadas con entregables utilizables; el frontend en paralelo con el backend |
| Reglas de contabilización de las operaciones sin validar por el contador | Se cargan como borrador (patrón de ADR-034) y se marcan `[VERIFICAR]`; los casos dorados se validan en F6 |
| Vidas útiles y residuales sin confirmar | La depreciación queda bloqueada (`CON-023`) hasta que se confirmen, sin inventar valores |
| La forma del contrato genera interfaces inmanejables | Prueba del generador en una copia antes de C1 |
| Las empresas existentes quedan con catálogo o reglas antiguas | Migraciones de datos que completan `sistema`, reglas y prefijos también en las empresas existentes |
| Un rediseño grande rompe los e2e de F1 a F3 | U1 y U2 actualizan los selectores de Playwright en la misma tarea; los e2e f1–f4 son criterio de aceptación |

## 12. Pendientes para el usuario o el contador

- `[VERIFICAR]` con el contador:
  - las cuentas por defecto de cada regla nueva (5.3, 5.4);
  - el tratamiento de la liquidación de tarjetas (comisión y anticipo de IVA, en los montos que ingresa el usuario desde la liquidación; Pilot no aplica tasas);
  - las vidas útiles, el valor residual y el mes de inicio de la depreciación.
- `[VERIFICAR]` técnico: las versiones exactas de las dependencias nuevas (`@fontsource`, componentes shadcn, Recharts) al empezar U1.
- Pendiente anterior, sin cambios: la edición NIIF para PYMES y la resolución del CVPCPA (ADR-037).
