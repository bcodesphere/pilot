# CLAUDE.md — Proyecto PILOT

> **Pilot** es un ERP multi-empresa, API-first y orientado a eventos, con facturación electrónica DTE nativa para El Salvador (Normativa DTE 2.0). Está diseñado para conectarse fácilmente con cualquier sistema y adaptarse a cualquier tipo de negocio sin desarrollo a medida.

| Campo | Valor |
|---|---|
| Nombre del producto | Pilot |
| Organización | bcodesphere |
| Repositorio | GitHub — organización y nombre `[DECISIÓN]` |
| Paquete base Java | `com.bcodesphere.pilot` |
| Mercado inicial | El Salvador — PYMES de cualquier giro |
| Moneda | USD (única moneda contable en v1) |
| Zona horaria de negocio | `America/El_Salvador` (UTC−6, sin horario de verano) |
| Idioma de producto | Español (`es-SV`) |
| Estado actual | Fase 0 — Fundaciones |
| Normativa fiscal objetivo | DTE 2.0 del Ministerio de Hacienda, obligatoria desde 2026-12-01 `[VERIFICAR]` |
| Última actualización de este archivo | 2026-09-23 |

---

## 0. Cómo usar este archivo

- **Claude:** antes de cualquier tarea lee la sección 1 (reglas críticas) y la sección 4 (arquitectura). Para tareas de facturación lee también la sección 11; para contabilidad, la sección 12; para cualquier cálculo de precios, IVA, retenciones o percepciones, la sección 12.7.
- **Marcadores usados en este documento:**
  - `[VERIFICAR]` — dato normativo o técnico externo que debe confirmarse contra la fuente oficial (MH/DGII, documentación del proveedor) antes de implementarlo. **Nunca implementes un valor fiscal marcado así sin confirmarlo.**
  - `[DECISIÓN]` — decisión aún no tomada. No la asumas: pregunta.
  - `ADR-XXX` — decisión de arquitectura registrada en la sección 22.
- Si una tarea contradice este archivo, detente y pregunta antes de continuar.
- Toda decisión nueva se registra en la sección 22 y se actualiza este archivo **en el mismo PR**.
- Nota de tamaño: este archivo se carga completo en cada sesión de Claude Code. Cuando el proyecto crezca, mover las secciones de detalle (11 a 21) a `docs/` y dejar aquí solo un resumen con referencias.

### 0.1 Flujo de trabajo esperado de Claude en este repositorio

1. Entender la tarea e identificar el módulo afectado (sección 4.3).
2. Si hay cambio de API o de eventos, modificar primero el contrato en `api-spec/` (ADR-003).
3. Si hay cambio de datos, crear una nueva migración Flyway (nunca editar las existentes).
4. Escribir o actualizar las pruebas.
5. Implementar respetando la arquitectura hexagonal y los límites entre módulos.
6. Comentar todo el código (regla 1.1.1).
7. Ejecutar `./mvnw verify` (backend) y/o `pnpm lint && pnpm test` (frontend) antes de dar la tarea por terminada.
8. Actualizar este archivo si cambió alguna decisión, comando o convención.

### 0.2 Definición de terminado (Definition of Done)

- [ ] El código compila y pasa formato (Spotless / Prettier) y lint.
- [ ] Todas las pruebas pasan, incluidas las de arquitectura (Spring Modulith / ArchUnit).
- [ ] Cobertura del dominio ≥ 80 % en el módulo tocado.
- [ ] Todo el código nuevo está comentado (clases, métodos y bloques lógicos).
- [ ] El contrato OpenAPI/AsyncAPI está actualizado y validado con Spectral.
- [ ] Las migraciones Flyway son nuevas, idempotentes en su efecto y revisadas.
- [ ] Los cambios con efecto fiscal o contable tienen pruebas con casos dorados.
- [ ] No hay secretos en el diff (gitleaks en verde).
- [ ] CLAUDE.md y ADR actualizados si aplica.

---

## 1. Reglas críticas (no negociables)

### 1.1 SIEMPRE

1. **Comentar todo el código.** Javadoc/JSDoc/TSDoc en cada clase, método público y componente, y comentarios en línea que expliquen cada bloque lógico (qué hace y por qué). Es un requisito del equipo, no opcional.
2. Usar `BigDecimal` (Java) y `NUMERIC` (PostgreSQL) para dinero, cantidades y tasas. Redondeo `RoundingMode.HALF_UP`, salvo que el esquema del MH indique otra regla.
3. Filtrar por `empresa_id` en toda operación de datos y mantener Row-Level Security activo en todas las tablas de negocio (sección 4.6).
4. Exigir el header `Idempotency-Key` en todo endpoint que cree documentos o movimientos con efecto fiscal, contable o de inventario.
5. Registrar los eventos de integración en `outbox_evento` **dentro de la misma transacción** que el cambio de negocio.
6. Crear cambios de esquema solo como migraciones Flyway nuevas: `V<numero>__<descripcion>.sql`.
7. Escribir pruebas junto con el código: unitarias para el dominio y de integración con Testcontainers para repositorios, API y flujos DTE.
8. Modificar primero el contrato (`api-spec/`) y después el código (contract-first, ADR-003).
9. Guardar fechas-hora en UTC (`TIMESTAMPTZ`). Convertir a `America/El_Salvador` solo para fechas de negocio y campos del DTE (`fecEmi`, `horEmi`).
10. Registrar auditoría (quién, qué, cuándo, valor anterior y nuevo) en toda mutación de datos de negocio.
11. Propagar `traceId` y `empresaId` en los logs, y enmascarar datos personales (DUI, NIT, correo, teléfono).
12. Validar todo JSON DTE contra el esquema oficial de su versión **antes** de firmarlo.
13. Leer tasas de impuestos desde la tabla `tasa_impuesto` con vigencia (desde/hasta); nunca como constantes en código.

### 1.2 NUNCA

1. Poner lógica fiscal, contable o de cálculo de impuestos en n8n, en el frontend o en integraciones externas. Vive solo en el núcleo Java (ADR-006).
2. Usar `double` o `float` para dinero.
3. Hardcodear esquemas, catálogos, URLs o endpoints del MH. Son configuración versionada (ADR-007).
4. Modificar o borrar un DTE con sello de recepción. Se corrige únicamente con eventos (invalidación, retorno) o con documentos de ajuste (Nota de Crédito / Nota de Débito).
5. Llamar al MH de forma síncrona dentro de la transacción de una venta.
6. Permitir que una integración escriba asientos contables directamente. Los asientos se generan desde eventos de negocio mediante reglas de contabilización.
7. Exponer el firmador, el certificado, la contraseña de la llave privada o la contraseña de la API del MH fuera de la red interna.
8. Commitear secretos: `.env`, certificados (`.crt`, `.p12`, `.pem`, `.key`), contraseñas o tokens.
9. Acceder a repositorios o tablas de otro módulo. Los módulos se comunican por su API pública o por eventos (ADR-001).
10. Contabilizar en un período cerrado.
11. Editar migraciones Flyway ya aplicadas.
12. Guardar tokens de acceso en `localStorage` en el frontend.
13. Conectar la aplicación a PostgreSQL con un usuario dueño de las tablas, superusuario o con `BYPASSRLS`.
14. Inventar tasas, plazos o códigos fiscales. Si no están confirmados, marcar `[VERIFICAR]` y preguntar.

---

## 2. Visión del producto

### 2.1 Problema

Las PYMES salvadoreñas trabajan con sistemas aislados (POS, hojas de cálculo, contabilidad externa), están obligadas a emitir DTE y las soluciones existentes suelen ser caras, cerradas o difíciles de integrar con lo que ya usan.

### 2.2 Propuesta de valor

1. **Se conecta con todo:** API REST documentada, webhooks firmados, eventos CloudEvents, feed de eventos por consulta, nodo oficial de n8n, SDK generados, importación CSV/XLSX y SFTP.
2. **DTE 2.0 nativo:** emisión, contingencia automática, invalidación, retorno y operaciones especiales.
3. **Contabilidad automática:** cada operación genera su asiento por reglas configurables.
4. **Configurable por industria:** perfiles preconfigurados, sin código a medida por cliente.
5. **Bajo costo:** stack 100 % open source, desplegable en un VPS económico.

### 2.3 Perfiles de industria (plantillas de configuración)

| Perfil | Ejemplos | Módulos clave | DTE principales |
|---|---|---|---|
| Comercio minorista | Tiendas, ferreterías, farmacias | POS, inventario, ventas | FE, CCF, NC |
| Restaurantes | Pupuserías, cafeterías, comedores | POS, caja, consumo de insumos | FE, CCF |
| Servicios profesionales | Consultoras, clínicas, talleres | Ventas, CxC, contabilidad | FE, CCF, FSEE |
| Distribuidoras y mayoristas | Distribución de consumo masivo | Inventario multibodega, crédito, rutas | CCF, NR, CR |
| Manufactura ligera | Panaderías, talleres de producción | Inventario, lista de materiales básica | CCF, FE, NR |
| Exportadores | Productores con venta al exterior | Ventas, inventario | FEXE |
| ONG y fundaciones | Organizaciones receptoras de donaciones | Donaciones, contabilidad | CD |

Cada perfil preconfigura: módulos activos, catálogo de cuentas base, reglas de contabilización, impuestos aplicables, tipos de DTE habilitados y campos personalizados sugeridos.

### 2.4 Principios de diseño

1. **API-first:** toda funcionalidad existe primero como API; la interfaz web es un cliente más.
2. **Orientado a eventos:** todo cambio relevante publica un evento.
3. **Configuración, no personalización:** campos personalizados y reglas configurables; nunca ramas de código por cliente.
4. **Tolerante a fallos externos:** una caída del MH no detiene las ventas.
5. **Inmutabilidad fiscal y contable:** lo emitido o contabilizado no se edita; se revierte o se ajusta.
6. **Costo cero o bajo:** componentes open source con licencias compatibles con SaaS.

### 2.5 Fuera de alcance en v1

- Multi-país y multi-moneda contable (solo USD).
- MRP avanzado y planificación de capacidad.
- E-commerce propio (Pilot se integra con plataformas existentes).
- Nómina (Fase 6) y app móvil (Fase 5).

---

## 3. Glosario

| Término | Significado |
|---|---|
| MH | Ministerio de Hacienda de El Salvador |
| DGII | Dirección General de Impuestos Internos (dependencia del MH) |
| DTE | Documento Tributario Electrónico: JSON firmado y transmitido al MH |
| Sello de recepción | Código que otorga el MH al aceptar un DTE; sin él, el DTE no está validado |
| Código de generación | UUID en mayúsculas que identifica de forma única cada DTE |
| Número de control | Identificador correlativo del DTE: `DTE-<tipo>-<establecimiento+punto de venta>-<correlativo>` |
| Firmador | Servicio oficial del MH que firma el JSON (JWS) con el certificado del emisor |
| Contingencia | Situación que impide transmitir al MH; los DTE se firman y entregan sin sello y se transmiten después |
| Estado transitorio | DTE firmado y entregado que aún no tiene sello de recepción (formalizado en DTE 2.0) |
| Invalidación | Evento que anula un DTE sellado dentro del plazo permitido |
| Evento de retorno | Evento DTE 2.0 que reporta devoluciones o reembolsos sin ajuste al crédito fiscal |
| Evento de operaciones especiales | Evento DTE 2.0 para informar operaciones que no se documentan con un DTE ordinario |
| NIT | Número de Identificación Tributaria |
| NRC | Número de Registro de Contribuyente (IVA) |
| DUI | Documento Único de Identidad |
| FE | Factura Electrónica (consumidor final; precio con IVA incluido) |
| CCF | Comprobante de Crédito Fiscal (entre contribuyentes; IVA desglosado) |
| NC / ND | Nota de Crédito / Nota de Débito (ajustes a CCF) |
| NR | Nota de Remisión (traslado de bienes) |
| CR | Comprobante de Retención (lo emite el agente de retención) |
| FSEE | Factura de Sujeto Excluido (la emite el comprador al adquirir de no inscritos en IVA) |
| FEXE | Factura de Exportación |
| CD | Comprobante de Donación |
| CL / DCL | Comprobante de Liquidación / Documento Contable de Liquidación |
| Gran contribuyente | Categoría asignada por la DGII; puede actuar como agente de retención o percepción |
| IVA débito fiscal | IVA cobrado en ventas (pasivo) |
| IVA crédito fiscal | IVA pagado en compras con CCF (activo) |
| Modo de precio | Indica si los precios de un documento se capturan con IVA incluido (`CON_IVA`) o más IVA (`SIN_IVA`); solo afecta la captura (sección 12.7) |
| Remanente de crédito fiscal | Crédito fiscal no aplicado en un mes que se traslada al siguiente |
| Pago a cuenta | Anticipo mensual del impuesto sobre la renta sobre ingresos brutos |
| F-07 | Declaración mensual de IVA |
| F-14 | Declaración mensual de pago a cuenta y retenciones de renta |
| NIIF para PYMES | Marco contable aplicado por las PYMES en El Salvador |
| Tenant / empresa | Contribuyente (NIT) que usa Pilot; unidad de aislamiento de datos |
| Establecimiento | Sucursal registrada ante el MH (código de 4 caracteres) |
| Punto de venta | Caja o punto de emisión dentro de un establecimiento (código de 4 caracteres) |
| Outbox | Patrón que guarda eventos en la misma transacción para publicarlos después |
| Idempotencia | Garantía de que repetir una petición no duplica su efecto |

---

## 4. Arquitectura

### 4.1 Estilo arquitectónico

- **Monolito modular** con límites de módulo verificados por Spring Modulith (ADR-001). Se podrá extraer un módulo a microservicio solo si hay una necesidad medida de escala.
- **Arquitectura hexagonal** (puertos y adaptadores) dentro de cada módulo.
- **Orientado a eventos:** eventos internos entre módulos (Spring Modulith) y eventos externos vía outbox (ADR-004).
- **CQRS ligero:** escrituras por el modelo de dominio; reportes por vistas y modelos de lectura optimizados.
- **Mismo artefacto, dos perfiles:** `api` (atiende HTTP) y `worker` (procesa DTE, outbox, webhooks y tareas programadas). Se escalan por separado.

### 4.2 Diagrama de componentes

```text
                ┌──────────────────────────────────────────────────────────────┐
 Clientes       │ Web (React)  ·  Móvil (Expo)  ·  n8n  ·  SDK  ·  Sistemas 3ros │
                └──────────────────────────────┬───────────────────────────────┘
                                               │ HTTPS
                                   ┌───────────▼───────────┐
                                   │ Traefik (TLS, rutas)   │
                                   └───────────┬───────────┘
              ┌────────────────────────────────┼─────────────────────────────┐
              │                                │                             │
      ┌───────▼────────┐             ┌─────────▼─────────┐          ┌────────▼───────┐
      │ Keycloak (OIDC)│             │ Pilot API (perfil │          │ n8n (queue mode)│
      └────────────────┘             │ api)              │          │ + workers       │
                                     └─────────┬─────────┘          └────────┬───────┘
                                               │                             │
        ┌──────────────┬───────────────┬───────┴────────┬────────────────────┘
        │              │               │                │
 ┌──────▼─────┐ ┌──────▼─────┐ ┌───────▼──────┐ ┌───────▼────────┐
 │ PostgreSQL │ │ Valkey     │ │ RabbitMQ     │ │ Almacenamiento │
 │ (RLS)      │ │ (caché,    │ │ (eventos)    │ │ S3 (JSON, PDF) │
 └──────▲─────┘ │ límites)   │ └───────▲──────┘ └───────▲────────┘
        │       └────────────┘         │                │
 ┌──────┴──────────────────────────────┴────────────────┴──┐
 │ Pilot Worker (perfil worker): outbox, DTE, webhooks,     │
 │ tareas programadas                                       │
 └──────────────┬───────────────────────────────────────────┘
                │ red interna
        ┌───────▼────────┐        HTTPS         ┌──────────────────────┐
        │ Firmador MH    │                      │ API de transmisión MH │
        │ (contenedor)   │   Worker ──────────► │ (pruebas/producción)  │
        └────────────────┘                      └──────────────────────┘
```

### 4.3 Módulos

| Módulo (paquete) | Responsabilidad | Puede depender de |
|---|---|---|
| `compartido` | Tipos base: `Dinero`, `Cantidad`, `EmpresaId`, errores, utilidades | — |
| `plataforma` | Empresas, establecimientos, puntos de venta, usuarios, roles, API keys, auditoría, configuración, perfiles de industria, campos personalizados | `compartido` |
| `catalogo` | Productos y servicios, clientes, proveedores, unidades de medida, impuestos y tasas, catálogos oficiales del MH | `plataforma`, `compartido` |
| `inventario` | Bodegas, existencias, kardex, costeo, transferencias, ajustes, lotes | `catalogo`, `plataforma`, `compartido` |
| `ventas` | Cotizaciones, pedidos, documentos de venta, devoluciones | `catalogo`, `plataforma`, `compartido` |
| `compras` | Órdenes de compra, recepción, documentos de compra, DTE recibidos | `catalogo`, `plataforma`, `compartido` |
| `dte` | Construcción, validación, firma, transmisión, contingencia, eventos MH, PDF y QR, conservación | `plataforma`, `catalogo`, `compartido` |
| `contabilidad` | Catálogo de cuentas, reglas de contabilización, asientos, períodos, cierres, libros legales y de IVA, estados financieros | `plataforma`, `compartido` |
| `tesoreria` | Cuentas por cobrar y pagar, cobros, pagos, cajas, cierres de caja, bancos, conciliación | `plataforma`, `catalogo`, `compartido` |
| `reportes` | Modelos de lectura, reportes operativos, exportaciones CSV/XLSX | Solo lectura por eventos y vistas |
| `integracion` | Outbox, publicación de eventos, webhooks, feed de eventos, importaciones, SFTP, mapeo de IDs externos | `plataforma`, `compartido` |
| `pos` (Fase 5) | Punto de venta, sesiones de caja, modo sin conexión | `ventas`, `tesoreria`, `inventario` |
| `rrhh` (Fase 6) | Empleados, planilla, prestaciones | `plataforma`, `contabilidad` |

Reglas de dependencia:

- `ventas` **no** llama a `dte` ni a `contabilidad`. Publica `ventas.documento.emitido` y esos módulos reaccionan.
- `contabilidad` solo reacciona a eventos; nunca es invocada por integraciones para crear asientos.
- Las dependencias cíclicas están prohibidas y se verifican en las pruebas (`ApplicationModules.verify()`).

### 4.4 Capas dentro de cada módulo (hexagonal)

```text
com.bcodesphere.pilot.<modulo>
├── api/              # Adaptadores de entrada: controladores REST, DTO (records), mapeadores
├── aplicacion/       # Casos de uso, puertos de entrada/salida, orquestación transaccional
├── dominio/          # Entidades, objetos de valor, reglas de negocio, eventos de dominio
└── infraestructura/  # Adaptadores de salida: JPA, clientes HTTP (MH), mensajería, almacenamiento
```

- `dominio` no depende de Spring ni de JPA en su lógica (anotaciones JPA toleradas en entidades por pragmatismo `[DECISIÓN]` si se prefiere modelo separado).
- Los controladores nunca exponen entidades; siempre DTO.
- Solo los tipos en el paquete raíz del módulo o marcados como API pública pueden usarse desde otros módulos.

### 4.5 Comunicación entre módulos

| Tipo | Mecanismo | Uso |
|---|---|---|
| Consulta síncrona | Interfaz pública del módulo (servicio de aplicación) | Lecturas: p. ej. `ventas` consulta precio en `catalogo` |
| Evento interno | `@ApplicationModuleListener` (Spring Modulith, persistido en su registro de publicaciones) | Efectos derivados: asiento contable, DTE, movimiento de inventario |
| Evento externo | Tabla `outbox_evento` → publicador → RabbitMQ / webhooks / feed | Integraciones con sistemas terceros y n8n |

### 4.6 Multi-empresa (multi-tenancy)

- Modelo: **base de datos compartida, esquema compartido, columna `empresa_id`** en toda tabla de negocio, reforzado con **Row-Level Security** de PostgreSQL (ADR-002).
- Jerarquía: `empresa` (NIT) → `establecimiento` (código MH) → `punto_venta` (código MH) → cajas/usuarios.
- Un usuario puede pertenecer a varias empresas con roles distintos (`empresa_usuario`).
- La empresa activa llega en el token (claim `empresa_id`) o en el header `X-Empresa-Id`, y siempre se valida contra las membresías del usuario.
- Al iniciar cada transacción se fija `app.empresa_id` en la sesión de PostgreSQL; las políticas RLS filtran por ese valor.
- Los procesos del perfil `worker` fijan el contexto de empresa por cada unidad de trabajo que procesan.

```sql
-- Activa Row-Level Security en una tabla de negocio
ALTER TABLE documento_venta ENABLE ROW LEVEL SECURITY;

-- FORCE hace que la política aplique incluso al dueño de la tabla
ALTER TABLE documento_venta FORCE ROW LEVEL SECURITY;

-- Política: solo se ven y escriben filas de la empresa fijada en la sesión
CREATE POLICY aislamiento_empresa ON documento_venta
    USING (empresa_id = current_setting('app.empresa_id')::uuid)        -- Filtro de lectura
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);  -- Filtro de escritura
```

```java
/**
 * Transaction manager que fija la empresa activa en la sesión de PostgreSQL
 * al iniciar cada transacción, para que Row-Level Security filtre los datos.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    /**
     * Abre la transacción y luego establece app.empresa_id solo para esa transacción.
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 1. Abre la transacción con el comportamiento estándar de Spring
        super.doBegin(transaction, definition);

        // 2. Obtiene la empresa del contexto de la petición; lanza error si no existe
        UUID empresaId = TenantContext.requerido();

        // 3. Recupera el EntityManager ligado a la transacción recién abierta
        EntityManager em = EntityManagerFactoryUtils
                .getTransactionalEntityManager(getEntityManagerFactory());

        // 4. set_config(..., true) limita el valor a esta transacción (equivale a SET LOCAL)
        em.createNativeQuery("SELECT set_config('app.empresa_id', :id, true)")
          .setParameter("id", empresaId.toString())
          .getSingleResult();
    }
}
```

### 4.7 Campos personalizados y configuración por negocio

- Las entidades configurables (`producto`, `cliente`, `proveedor`, `documento_venta`) tienen una columna `atributos JSONB`.
- La tabla `definicion_campo` guarda, por empresa y entidad, un JSON Schema que valida esos atributos.
- Los atributos se exponen en la API bajo `atributos` y se incluyen en los eventos.
- Las reglas de negocio configurables (impuestos, contabilización, numeración, flujos de aprobación) se guardan como datos versionados, nunca como código por cliente.

---

## 5. Stack tecnológico

> Fijar versiones exactas en `pom.xml`, `package.json` y `compose` al iniciar cada fase. Revisar parches de seguridad mensualmente.

### 5.1 Backend

| Componente | Elección | Notas |
|---|---|---|
| Lenguaje | Java 25 LTS | Mínimo aceptable: Java 21 LTS |
| Framework | Spring Boot 4.1.x (Spring Framework 7) | Incluye versionado de API nativo |
| Modularidad | Spring Modulith | Verifica límites y persiste eventos internos |
| Build | Maven (wrapper `./mvnw`) | `[DECISIÓN]` si se prefiere Gradle |
| Persistencia | Spring Data JPA (Hibernate 7) | Escrituras de dominio |
| Consultas de reportes | `JdbcClient` / SQL nativo | Lecturas complejas; evaluar jOOQ si crece |
| Migraciones | Flyway | `src/main/resources/db/migration` |
| Seguridad | Spring Security (Resource Server OAuth2/JWT) | Tokens emitidos por Keycloak |
| Serialización | Jackson 3 | Paquete `tools.jackson.*` en Spring Boot 4 |
| Mapeo DTO | MapStruct | Sin lógica de negocio en mapeadores |
| Validación | Jakarta Bean Validation | En DTO de entrada |
| Validación JSON Schema | `networknt/json-schema-validator` | Esquemas DTE y campos personalizados |
| Resiliencia | Resilience4j (retry, circuit breaker) | `[VERIFICAR]` compatibilidad con Spring Boot 4; alternativa: `@Retryable` nativo de Spring Framework 7 |
| Tareas programadas en clúster | ShedLock | Evita ejecución duplicada entre instancias |
| Límite de peticiones | Bucket4j + Valkey | Por API key y por empresa |
| PDF | Thymeleaf + OpenHTMLtoPDF | Plantillas HTML personalizables por empresa |
| Código QR | ZXing | QR de consulta pública del DTE |
| Documentación API | OpenAPI 3.1 (contract-first) + `openapi-generator-maven-plugin` | Interfaces Spring generadas desde el contrato |
| Contrato de eventos | AsyncAPI 3 | `api-spec/asyncapi/` |
| Pruebas | JUnit Jupiter, AssertJ, Testcontainers, WireMock, ArchUnit, Spring Modulith Test | — |
| Calidad | Spotless (palantir-java-format), Checkstyle, SpotBugs | En CI |

### 5.2 Frontend web

| Componente | Elección |
|---|---|
| Base | React 19 + TypeScript (modo `strict`) + Vite |
| Datos remotos | TanStack Query |
| Rutas | React Router |
| Formularios | React Hook Form + Zod |
| UI | shadcn/ui + Tailwind CSS |
| Cliente API | Generado desde OpenAPI con Orval (nunca escrito a mano) |
| Autenticación | OIDC con PKCE contra Keycloak (`oidc-client-ts`); tokens en memoria |
| i18n | `es-SV` por defecto; formato `$1,234.56` |
| Pruebas | Vitest + Testing Library; Playwright para end-to-end |
| Gestor de paquetes | pnpm |

### 5.3 Móvil (Fase 5)

- Expo (React Native) + TypeScript, SQLite local para modo sin conexión y sincronización por API.

### 5.4 Infraestructura y datos

| Componente | Elección | Motivo |
|---|---|---|
| Base de datos | PostgreSQL 17+ | ACID, JSONB, RLS |
| Caché, límites, colas de n8n | Valkey 8 | Licencia BSD (ADR-012) |
| Mensajería | RabbitMQ 4.x | Enrutamiento flexible de eventos; evaluar Redpanda si el volumen crece |
| Almacenamiento de objetos | API S3 abstraída con AWS SDK v2 | Desarrollo: SeaweedFS o Garage; producción: Cloudflare R2 o Backblaze B2 `[DECISIÓN]` (ADR-011) |
| Identidad | Keycloak 26.x | OIDC, MFA, SSO, gratuito |
| Orquestación de integraciones | n8n autoalojado en modo cola | ADR-009 |
| Proxy inverso | Traefik con Let's Encrypt | TLS automático |
| Contenedores | Docker + Docker Compose; k3s en Fase 7 | Bajo costo al inicio |
| Observabilidad | OpenTelemetry, Prometheus, Grafana, Loki, Tempo | Métricas, logs y trazas |
| CI/CD | GitHub Actions + GitHub Container Registry | — |
| Respaldos | WAL-G hacia almacenamiento S3 | Recuperación a un punto en el tiempo |
| Correo de desarrollo | Mailpit | Captura correos localmente |
| Simulación del MH | WireMock | Pruebas sin depender del MH |
| Firmador DTE | Imagen oficial del MH `[VERIFICAR]` nombre, puerto y API | Solo en red interna |

---

## 6. Estructura del repositorio

```text
pilot/
├── CLAUDE.md                        # Este archivo
├── README.md                        # Presentación y arranque rápido
├── .env.example                     # Variables de entorno de ejemplo (sin secretos)
├── api-spec/
│   ├── openapi/pilot-v1.yaml        # Contrato REST (fuente de verdad)
│   ├── asyncapi/pilot-eventos-v1.yaml  # Contrato de eventos
│   └── .spectral.yaml               # Reglas de lint del contrato
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/bcodesphere/pilot/
│       │   ├── PilotApplication.java
│       │   ├── compartido/
│       │   ├── plataforma/
│       │   ├── catalogo/
│       │   ├── inventario/
│       │   ├── ventas/
│       │   ├── compras/
│       │   ├── dte/
│       │   ├── contabilidad/
│       │   ├── tesoreria/
│       │   ├── reportes/
│       │   └── integracion/
│       ├── main/resources/
│       │   ├── application.yml       # Configuración base
│       │   ├── application-dev.yml   # Desarrollo local
│       │   ├── application-api.yml   # Perfil api
│       │   ├── application-worker.yml # Perfil worker
│       │   ├── db/migration/         # Migraciones Flyway
│       │   ├── dte/esquemas/<tipo>/v<n>.json   # Esquemas JSON oficiales por versión
│       │   ├── dte/catalogos/        # Catálogos MH (CAT-xxx) versionados
│       │   └── plantillas/pdf/       # Plantillas de representación gráfica
│       └── test/
│           ├── java/...              # Pruebas por módulo
│           └── resources/dte/casos/  # Casos dorados JSON por tipo de DTE
├── frontend/                        # React + Vite
├── mobile/                          # Expo (Fase 5)
├── integraciones/
│   ├── n8n-nodes-pilot/             # Nodo comunitario de n8n (TypeScript)
│   └── plantillas-n8n/              # Flujos de ejemplo exportados (JSON)
├── sdks/                            # SDK generados (TypeScript, Python, PHP, Java, C#)
├── infra/
│   ├── docker/compose.dev.yml
│   ├── docker/compose.prod.yml
│   ├── traefik/
│   ├── observabilidad/              # Dashboards de Grafana y reglas de alertas
│   ├── wiremock/mh/                 # Simulación de la API del MH
│   └── k8s/                         # Manifiestos k3s (Fase 7)
├── docs/
│   ├── adr/                         # Decisiones de arquitectura (ADR-XXX.md)
│   ├── dte/                         # Notas de implementación DTE (no subir material con derechos)
│   ├── runbooks/                    # Procedimientos operativos (contingencia, restauración)
│   └── contabilidad/                # Reglas contables validadas por contador
└── .github/workflows/               # CI/CD
```

---

## 7. Comandos

```bash
# --- Infraestructura local ---
docker compose -f infra/docker/compose.dev.yml up -d      # Levanta dependencias de desarrollo
docker compose -f infra/docker/compose.dev.yml down       # Detiene dependencias

# --- Backend ---
cd backend
./mvnw generate-sources                                   # Genera interfaces desde OpenAPI
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,api       # Ejecuta el perfil api
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,worker    # Ejecuta el perfil worker
./mvnw test                                               # Pruebas unitarias
./mvnw verify                                             # Unitarias + integración + arquitectura + calidad
./mvnw spotless:apply                                     # Aplica formato de código
./mvnw flyway:info                                        # Estado de migraciones

# --- Frontend ---
cd frontend
pnpm install                                              # Instala dependencias
pnpm api:generate                                         # Regenera cliente API desde OpenAPI (Orval)
pnpm dev                                                  # Servidor de desarrollo
pnpm lint && pnpm test                                    # Lint y pruebas
pnpm e2e                                                  # Pruebas end-to-end (Playwright)

# --- Contratos ---
npx @stoplight/spectral-cli lint api-spec/openapi/pilot-v1.yaml   # Valida el contrato REST

# --- Nodo de n8n ---
cd integraciones/n8n-nodes-pilot
pnpm build && pnpm lint                                   # Compila y valida el nodo
```

`[DECISIÓN]` Agregar un `Makefile` o `justfile` que envuelva estos comandos.

---

## 8. Convenciones de código

### 8.1 Idioma y nombres

- **Dominio en español** sin tildes ni `ñ` en identificadores (`documentoVenta`, `anio`); sufijos técnicos en inglés estándar (`Controller`, `Service`, `Repository`, `Dto`, `Mapper`).
- Términos fiscales oficiales se conservan tal cual: `codigoGeneracion`, `numeroControl`, `selloRecibido`, `nrc`, `nit`.
- Tablas y columnas en `snake_case` singular (`documento_venta`, `empresa_id`).
- Claves primarias `UUID` versión 7 generadas en la aplicación (ordenables en el tiempo) (ADR-010).
- Eventos: `<modulo>.<entidad>.<accion-en-participio>` en minúsculas (`dte.documento.sellado`).
- Endpoints REST en plural y `kebab-case` (`/api/v1/puntos-venta`).
- JSON de la API en `camelCase`; montos como **cadena decimal** (`"123.45"`) (ADR-013); fechas ISO-8601.

### 8.2 Comentarios (obligatorio)

```java
/**
 * Calcula los totales de un documento de venta a partir de sus líneas.
 * Aplica las tasas vigentes a la fecha del documento.
 *
 * @param lineas líneas del documento con cantidad, precio y tratamiento fiscal
 * @param fecha  fecha de emisión, usada para buscar la tasa vigente
 * @return totales gravados, exentos, no sujetos, IVA y total a pagar
 */
public TotalesDocumento calcular(List<LineaDocumento> lineas, LocalDate fecha) {
    // 1. Obtiene la tasa de IVA vigente a la fecha (nunca constante en código)
    BigDecimal tasaIva = tasas.vigente(TipoTasa.IVA, fecha);

    // 2. Suma las líneas gravadas por separado de las exentas y no sujetas
    BigDecimal gravado = sumarPor(lineas, TratamientoFiscal.GRAVADO);
    BigDecimal exento = sumarPor(lineas, TratamientoFiscal.EXENTO);
    BigDecimal noSujeto = sumarPor(lineas, TratamientoFiscal.NO_SUJETO);

    // 3. Calcula el IVA solo sobre lo gravado y redondea a 2 decimales
    BigDecimal iva = gravado.multiply(tasaIva).setScale(2, RoundingMode.HALF_UP);

    // 4. Devuelve el resumen inmutable con todos los totales
    return new TotalesDocumento(gravado, exento, noSujeto, iva,
            gravado.add(exento).add(noSujeto).add(iva));
}
```

- Comentar **el porqué** de las reglas fiscales y referenciar la norma o el ADR cuando aplique.
- En SQL, comentar cada tabla, columna no obvia, índice y política.

### 8.3 Estilo y diseño

- DTO y objetos de valor como `record` de Java.
- Inyección de dependencias por constructor; campos `final`.
- Métodos de máximo ~40 líneas; clases con una sola responsabilidad.
- Excepciones de dominio específicas (`PeriodoCerradoException`, `StockInsuficienteException`) traducidas a Problem Details en la capa `api`.
- Concurrencia optimista con columna `version` en entidades editables; `If-Match`/`ETag` en la API.
- Nada de lógica en controladores: solo validación de entrada, llamada al caso de uso y mapeo de salida.
- Sin `Optional` en parámetros ni en campos; sí como retorno.
- Sin `null` en colecciones devueltas: usar colecciones vacías.

### 8.4 Errores de la API

- Formato **RFC 9457 Problem Details** (`application/problem+json`).
- Campo `codigo` con prefijo por módulo: `PLT-`, `CAT-`, `INV-`, `VEN-`, `COM-`, `DTE-`, `CON-`, `TES-`, `INT-`.
- Los errores de validación incluyen la lista `errores` con `campo` y `mensaje`.
- Nunca exponer trazas de pila ni mensajes internos.

### 8.5 Git

- Trunk-based: ramas cortas desde `main` (`feat/`, `fix/`, `chore/`, `docs/`, `refactor/`).
- Conventional Commits en español: `feat(dte): agrega evento de retorno`.
- Todo cambio entra por PR con CI en verde y al menos una revisión.
- Versionado semántico con etiquetas `vX.Y.Z` y `CHANGELOG.md`.

---

## 9. Modelo de datos (núcleo)

### 9.1 Tablas principales por módulo

| Módulo | Tablas |
|---|---|
| `plataforma` | `empresa`, `establecimiento`, `punto_venta`, `usuario`, `empresa_usuario`, `rol`, `permiso`, `rol_permiso`, `api_key`, `auditoria`, `configuracion_empresa`, `definicion_campo`, `perfil_industria` |
| `catalogo` | `producto`, `categoria_producto`, `unidad_medida`, `cliente`, `proveedor`, `tipo_impuesto`, `tasa_impuesto`, `catalogo_mh`, `catalogo_mh_valor` |
| `inventario` | `bodega`, `existencia`, `movimiento_inventario`, `lote`, `transferencia`, `ajuste_inventario` |
| `ventas` | `cotizacion`, `pedido`, `documento_venta`, `documento_venta_linea`, `devolucion` |
| `compras` | `orden_compra`, `recepcion`, `documento_compra`, `documento_compra_linea`, `dte_recibido` |
| `dte` | `dte`, `dte_evento`, `correlativo_dte`, `contingencia`, `lote_transmision`, `certificado_emisor`, `esquema_dte` |
| `contabilidad` | `cuenta_contable`, `periodo_contable`, `asiento`, `asiento_linea`, `regla_contabilizacion`, `centro_costo` |
| `tesoreria` | `cuenta_por_cobrar`, `cuenta_por_pagar`, `cobro`, `pago`, `caja`, `sesion_caja`, `cuenta_bancaria`, `movimiento_bancario`, `conciliacion` |
| `integracion` | `outbox_evento`, `idempotencia`, `webhook_suscripcion`, `webhook_entrega`, `importacion`, `id_externo` |

Columnas comunes en toda tabla de negocio: `id UUID`, `empresa_id UUID`, `creado_en`, `creado_por`, `actualizado_en`, `actualizado_por`, `version BIGINT`.

### 9.2 Integración: outbox, idempotencia y mapeo de IDs externos

```sql
-- OUTBOX: el evento se guarda en la MISMA transacción que el cambio de negocio,
-- así nunca se pierde aunque falle el broker o la red
CREATE TABLE outbox_evento (
    id              UUID PRIMARY KEY,               -- ID del evento (también es el id de CloudEvents)
    empresa_id      UUID NOT NULL,                  -- Empresa dueña del evento
    tipo            VARCHAR(120) NOT NULL,          -- Ej.: 'dte.documento.sellado'
    version_esquema SMALLINT NOT NULL DEFAULT 1,    -- Versión del payload del evento
    sujeto          VARCHAR(200),                   -- Entidad afectada (ej. código de generación)
    payload         JSONB NOT NULL,                 -- Datos del evento
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    publicado_en    TIMESTAMPTZ,                    -- NULL = pendiente de publicar
    intentos        INT NOT NULL DEFAULT 0          -- Intentos de publicación fallidos
);

-- Índice parcial: el publicador solo busca eventos pendientes, en orden de creación
CREATE INDEX idx_outbox_pendientes ON outbox_evento (creado_en) WHERE publicado_en IS NULL;

-- IDEMPOTENCIA: evita documentos duplicados cuando un cliente reintenta
CREATE TABLE idempotencia (
    empresa_id      UUID NOT NULL,                  -- La clave es única por empresa
    clave           VARCHAR(100) NOT NULL,          -- Valor del header Idempotency-Key
    hash_solicitud  CHAR(64) NOT NULL,              -- SHA-256 del cuerpo: misma clave con otro cuerpo = error 422
    estado_http     SMALLINT NOT NULL,              -- Código HTTP de la respuesta original
    respuesta       JSONB NOT NULL,                 -- Respuesta original para devolverla igual
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, clave)                 -- Si se repite la clave, choca aquí
);
-- Retención: purgar registros con más de 7 días (tarea programada)

-- MAPEO DE IDS EXTERNOS: relaciona IDs de otros sistemas con IDs de Pilot
CREATE TABLE id_externo (
    empresa_id      UUID NOT NULL,
    sistema_origen  VARCHAR(50) NOT NULL,           -- Ej.: 'shopify', 'woocommerce', 'pos-legado'
    tipo_entidad    VARCHAR(50) NOT NULL,           -- Ej.: 'cliente', 'producto'
    id_origen       VARCHAR(200) NOT NULL,          -- ID tal como existe en el otro sistema
    id_pilot        UUID NOT NULL,                  -- ID interno de Pilot
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, sistema_origen, tipo_entidad, id_origen)
);
```

### 9.3 DTE y correlativos

```sql
-- DTE: un registro por documento tributario electrónico emitido
CREATE TABLE dte (
    id                  UUID PRIMARY KEY,
    empresa_id          UUID NOT NULL,
    origen_tipo         VARCHAR(30) NOT NULL,        -- 'documento_venta', 'documento_compra', etc.
    origen_id           UUID NOT NULL,               -- Documento de negocio que lo originó
    tipo_dte            CHAR(2) NOT NULL,            -- Código del catálogo CAT-002 (ej. '01')
    version_esquema     SMALLINT NOT NULL,           -- Versión del esquema JSON usada
    ambiente            CHAR(2) NOT NULL,            -- '00' pruebas, '01' producción [VERIFICAR]
    codigo_generacion   UUID NOT NULL UNIQUE,        -- UUID en mayúsculas en el JSON
    numero_control      VARCHAR(31) NOT NULL,        -- DTE-01-XXXXXXXX-000000000000001
    estado              VARCHAR(20) NOT NULL,        -- Ver máquina de estados (sección 11.5)
    tipo_transmision    SMALLINT NOT NULL,           -- 1 normal, 2 contingencia [VERIFICAR]
    modelo_facturacion  SMALLINT NOT NULL,           -- 1 previo, 2 diferido [VERIFICAR]
    fecha_emision       DATE NOT NULL,               -- fecEmi en hora de El Salvador
    hora_emision        TIME NOT NULL,               -- horEmi en hora de El Salvador
    sello_recepcion     VARCHAR(100),                -- Sello otorgado por el MH
    fecha_procesamiento TIMESTAMPTZ,                 -- Fecha en que el MH procesó el DTE
    contingencia_id     UUID,                        -- Evento de contingencia asociado, si aplica
    clave_json_firmado  TEXT,                        -- Ruta del JSON firmado en almacenamiento S3
    clave_pdf           TEXT,                        -- Ruta de la representación gráfica
    hash_sha256         CHAR(64),                    -- Hash del JSON firmado (integridad por 10 años)
    intentos            INT NOT NULL DEFAULT 0,      -- Intentos de transmisión
    ultimo_error        JSONB,                       -- Último error técnico
    observaciones_mh    JSONB,                       -- Observaciones o motivos de rechazo del MH
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0,   -- Bloqueo optimista
    UNIQUE (empresa_id, numero_control)              -- El número de control no se repite por emisor
);

-- Índice para que el worker encuentre DTE pendientes de transmitir
CREATE INDEX idx_dte_pendientes ON dte (estado, creado_en)
    WHERE estado IN ('PENDIENTE', 'FIRMADO', 'TRANSITORIO');

-- CORRELATIVOS: numeración por empresa, establecimiento, punto de venta y tipo de DTE
CREATE TABLE correlativo_dte (
    empresa_id          UUID NOT NULL,
    establecimiento_id  UUID NOT NULL,
    punto_venta_id      UUID NOT NULL,
    tipo_dte            CHAR(2) NOT NULL,
    anio                SMALLINT NOT NULL,           -- [VERIFICAR] si el correlativo se reinicia por año
    ultimo              BIGINT NOT NULL DEFAULT 0,   -- Último número asignado
    PRIMARY KEY (empresa_id, establecimiento_id, punto_venta_id, tipo_dte, anio)
);

-- Obtención atómica del siguiente correlativo: el UPDATE bloquea la fila
-- y evita números duplicados entre transacciones concurrentes
UPDATE correlativo_dte
   SET ultimo = ultimo + 1
 WHERE empresa_id = :empresa AND establecimiento_id = :est
   AND punto_venta_id = :pv AND tipo_dte = :tipo AND anio = :anio
RETURNING ultimo;
```

### 9.4 Contabilidad con partida doble garantizada

```sql
-- ASIENTO: cabecera de la partida contable
CREATE TABLE asiento (
    id              UUID PRIMARY KEY,
    empresa_id      UUID NOT NULL,
    periodo_id      UUID NOT NULL,                   -- Período contable (debe estar abierto)
    numero          BIGINT NOT NULL,                 -- Número correlativo por empresa y período
    fecha           DATE NOT NULL,                   -- Fecha contable
    concepto        VARCHAR(500) NOT NULL,           -- Descripción de la partida
    estado          VARCHAR(15) NOT NULL,            -- BORRADOR, CONTABILIZADO, REVERTIDO
    origen_tipo     VARCHAR(40),                     -- Evento de negocio que lo generó
    origen_id       UUID,                            -- ID del documento de origen
    asiento_reverso_id UUID,                         -- Si fue revertido, apunta al asiento de reversión
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (empresa_id, periodo_id, numero)
);

-- LÍNEAS: cada línea es débito o crédito, nunca ambos
CREATE TABLE asiento_linea (
    id              UUID PRIMARY KEY,
    empresa_id      UUID NOT NULL,
    asiento_id      UUID NOT NULL REFERENCES asiento(id),
    cuenta_id       UUID NOT NULL,                   -- Cuenta de detalle (acepta movimientos)
    centro_costo_id UUID,                            -- Opcional
    debe            NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (debe >= 0),
    haber           NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (haber >= 0),
    descripcion     VARCHAR(300),
    CHECK ((debe = 0) <> (haber = 0))                -- Exactamente uno de los dos es mayor que cero
);

-- Función que valida que el asiento cuadre (suma débitos = suma créditos)
CREATE FUNCTION validar_partida_doble() RETURNS trigger AS $$
DECLARE
    diferencia NUMERIC(19,4);
BEGIN
    -- Calcula la diferencia entre débitos y créditos del asiento afectado
    SELECT COALESCE(SUM(debe), 0) - COALESCE(SUM(haber), 0)
      INTO diferencia
      FROM asiento_linea
     WHERE asiento_id = COALESCE(NEW.asiento_id, OLD.asiento_id);

    -- Si no cuadra, aborta toda la transacción
    IF diferencia <> 0 THEN
        RAISE EXCEPTION 'Asiento % descuadrado por %', COALESCE(NEW.asiento_id, OLD.asiento_id), diferencia;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger diferido: se evalúa al COMMIT, cuando ya se insertaron todas las líneas
CREATE CONSTRAINT TRIGGER trg_partida_doble
    AFTER INSERT OR UPDATE OR DELETE ON asiento_linea
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validar_partida_doble();
```

La misma validación existe también en el dominio Java; el trigger es la última línea de defensa.

### 9.5 Retención de datos

| Dato | Retención | Dónde |
|---|---|---|
| JSON firmado, sello y PDF de DTE | Mínimo 10 años | Almacenamiento S3 con versionado y bloqueo de objetos |
| Registros contables y documentos de soporte | Mínimo 10 años `[VERIFICAR]` plazo exacto del Código Tributario | PostgreSQL + respaldos |
| Auditoría | 10 años | Tabla particionada por mes |
| Idempotencia | 7 días | PostgreSQL |
| Outbox publicado | 30 días | PostgreSQL (purga programada) |
| Entregas de webhooks | 90 días | PostgreSQL |
| Logs de aplicación | 30 días calientes, 1 año archivados | Loki + S3 |

---

## 10. Módulos funcionales

### 10.1 Plataforma

- Registro de empresa con datos del contribuyente: NIT, NRC, nombre, nombre comercial, actividad económica (catálogo MH), dirección (departamento/municipio según catálogo MH), correo, teléfono, categoría de contribuyente (grande, mediano, otros).
- Establecimientos y puntos de venta con sus códigos asignados ante el MH.
- Usuarios y membresías por empresa; roles y permisos (sección 16.2).
- API keys por integración: prefijo visible, secreto mostrado una sola vez y guardado como hash (Argon2id), alcances (scopes) y fecha de expiración.
- Perfiles de industria que preconfiguran la empresa al registrarla.
- Auditoría consultable por entidad y usuario.

### 10.2 Catálogo

- Productos y servicios: código interno, código de barras, descripción, unidad de medida (catálogo MH), tipo de ítem (bien, servicio, ambos, otro) `[VERIFICAR]` códigos, tratamiento fiscal (gravado, exento, no sujeto), tributos especiales aplicables, precio con o sin IVA, costo, controla inventario (sí/no), `atributos`.
- Clientes: tipo de documento de identificación (NIT, DUI, pasaporte, carné de residente, otro — catálogo MH), NRC si es contribuyente, actividad económica, dirección, condiciones de crédito, si es gran contribuyente (define retenciones).
- Proveedores: mismos datos fiscales; si es sujeto excluido.
- Catálogos oficiales del MH (CAT-001 a CAT-032 aproximadamente `[VERIFICAR]`) cargados como datos versionados, con fecha de vigencia. Ejemplos: tipos de documento, unidades de medida, actividades económicas, departamentos y municipios, formas de pago, países.
- Departamentos y municipios: El Salvador reorganizó su división municipal en 2024; usar exclusivamente el catálogo vigente del MH `[VERIFICAR]` manejo de distritos.
- Impuestos: `tipo_impuesto` y `tasa_impuesto` con vigencia. IVA general 13 %.

### 10.3 Inventario

- Bodegas por establecimiento; existencias por producto, bodega y lote opcional.
- Kardex: cada movimiento (entrada, salida, transferencia, ajuste) queda registrado con costo unitario y saldo.
- Método de costeo por defecto: **costo promedio ponderado** `[VERIFICAR]` métodos permitidos por el Código Tributario; configurable por empresa.
- Transferencias entre establecimientos generan Nota de Remisión (DTE 04) cuando corresponda.
- Alertas de stock mínimo publican `inventario.existencia.bajo-minimo`.
- Ajustes de inventario requieren motivo y generan asiento contable.
- Stock negativo: prohibido por defecto; configurable por empresa.

### 10.4 Ventas

- Cotización → pedido → documento de venta (el flujo es opcional; se puede facturar directo).
- Tipo de documento según el receptor:
  - Consumidor final → FE (precio con IVA incluido).
  - Contribuyente con NRC → CCF (IVA desglosado).
  - Cliente en el exterior → FEXE (Fase 6).
- Condición de la operación: contado, crédito u otra (catálogo MH).
- Formas de pago múltiples por documento (catálogo MH).
- Retención de IVA del 1 % cuando el comprador es gran contribuyente y el vendedor no lo es `[VERIFICAR]` reglas exactas.
- Percepción de IVA del 1 % cuando el vendedor es agente de percepción `[VERIFICAR]`.
- Devoluciones:
  - Sobre CCF → Nota de Crédito (DTE 05).
  - Sobre FE, FEXE o FSEE → Evento de Retorno (DTE 2.0).
  - Error en el documento → invalidación dentro del plazo (sección 11.8).
- Al emitir: se descuenta inventario, se crea la cuenta por cobrar si es a crédito y se publica `ventas.documento.emitido`.

### 10.5 Compras

- Orden de compra → recepción → documento de compra.
- Registro de CCF recibidos para crédito fiscal.
- **DTE recibidos:** ingreso del JSON del proveedor (por API, carga manual o correo procesado con n8n), validación de estructura, verificación opcional contra la consulta del MH y creación de un borrador de compra.
- Compras a sujetos excluidos → Pilot emite FSEE (DTE 14) con retención de renta si aplica `[VERIFICAR]` porcentaje y casos.
- Si la empresa es agente de retención → emite Comprobante de Retención (DTE 07).

### 10.6 Contabilidad

Ver sección 12.

### 10.7 Tesorería

- Cuentas por cobrar y por pagar con vencimientos y antigüedad de saldos.
- Cobros y pagos parciales, anticipos y aplicación a documentos.
- Cajas y sesiones de caja: apertura, arqueo por forma de pago, cierre con diferencias registradas.
- Cuentas bancarias, importación de estados de cuenta (CSV/XLSX/OFX) y conciliación bancaria asistida.
- Cada cobro, pago y cierre genera su asiento.

### 10.8 Reportes

- Operativos: ventas por período, producto, cliente, vendedor y establecimiento; existencias valorizadas; antigüedad de saldos; DTE por estado.
- Fiscales: libros de IVA, detalle de retenciones y percepciones, anexos para F-07 `[VERIFICAR]` formato vigente.
- Contables: libro diario, libro mayor, balance de comprobación, balance general, estado de resultados.
- Todos exportables a CSV y XLSX; los contables también a PDF.

### 10.9 Integración

Ver secciones 13, 14 y 15.

### 10.10 POS (Fase 5)

- Aplicación web progresiva optimizada para pantalla táctil, lector de código de barras e impresora térmica.
- Modo sin conexión: cola local de ventas y sincronización al reconectar.
- Emisión DTE sin internet requiere firmar localmente: evaluar un agente local "Pilot Edge" con firmador en la sucursal `[DECISIÓN]`.

### 10.11 Recursos humanos y nómina (Fase 6)

- Empleados, contratos, planilla, horas extra, vacaciones, aguinaldo, indemnización.
- Descuentos de ley: ISSS, AFP y retención de renta según tablas vigentes `[VERIFICAR]` tasas y techos.
- Generación de asientos de planilla y archivos para las instituciones.

---

## 11. Módulo DTE (ruta crítica)

> Todo dato de esta sección marcado `[VERIFICAR]` debe confirmarse contra la Normativa de Cumplimiento DTE vigente, el Manual Funcional del Sistema de Transmisión, el Manual Técnico y los esquemas JSON publicados por el MH antes de implementarlo.

### 11.1 Fuentes oficiales que deben estar en `docs/dte/`

- Normativa de Cumplimiento de los DTE versión 2.0.
- Manual Funcional del Sistema de Transmisión (versión vigente).
- Manual Técnico / guía de integración de la API.
- Esquemas JSON por tipo de documento y por evento.
- Catálogos oficiales (CAT-xxx).
- Documentación del firmador oficial.

Registrar en `docs/dte/versiones.md` qué versión de cada documento se implementó y la fecha de descarga.

### 11.2 Tipos de DTE

| Código `[VERIFICAR]` | Documento | Quién lo emite / uso | Fase |
|---|---|---|---|
| 01 | Factura (FE) | Venta a consumidor final | 1 |
| 03 | Comprobante de Crédito Fiscal (CCF) | Venta a contribuyente de IVA | 1 |
| 04 | Nota de Remisión (NR) | Traslado de bienes | 2 |
| 05 | Nota de Crédito (NC) | Disminuye un CCF | 1 |
| 06 | Nota de Débito (ND) | Aumenta un CCF | 1 |
| 07 | Comprobante de Retención (CR) | Agente de retención de IVA | 2 |
| 08 | Comprobante de Liquidación (CL) | Liquidación de ventas por cuenta de terceros | 6 |
| 09 | Documento Contable de Liquidación (DCL) | Liquidaciones contables | 6 |
| 11 | Factura de Exportación (FEXE) | Exportaciones | 6 |
| 14 | Factura de Sujeto Excluido (FSEE) | La emite el comprador a no inscritos | 2 |
| 15 | Comprobante de Donación (CD) | Donatarios | 6 |

Eventos: invalidación, contingencia y, desde DTE 2.0, retorno y operaciones especiales.

### 11.3 Identificadores

- **Código de generación:** UUID (versión 4) en **mayúsculas**, generado por Pilot, único por DTE.
- **Número de control:** `DTE-<tipo 2>-<código establecimiento 4><código punto de venta 4>-<correlativo 15 dígitos>`, por ejemplo `DTE-01-M001P001-000000000000001` (31 caracteres) `[VERIFICAR]` patrón exacto en el esquema.
- Correlativo por empresa, establecimiento, punto de venta y tipo de DTE, obtenido de forma atómica (sección 9.3).
- `[VERIFICAR]` si un DTE rechazado puede reenviarse con el mismo código de generación y número de control o requiere nuevos.

### 11.4 Ambientes y endpoints `[VERIFICAR]` todos contra el manual vigente

| Concepto | Pruebas | Producción |
|---|---|---|
| Código de ambiente | `00` | `01` |
| URL base API | `https://apitest.dtes.mh.gob.sv` | `https://api.dtes.mh.gob.sv` |
| Autenticación | `POST /seguridad/auth` (usuario = NIT, contraseña de API) | igual |
| Recepción de DTE | `POST /fesv/recepciondte` | igual |
| Recepción por lote | `POST /fesv/recepcionlote/` | igual |
| Consulta de DTE | `POST /fesv/recepcion/consultadte/` | igual |
| Consulta de lote | `GET /fesv/recepcion/consultadtelote/{codigoLote}` | igual |
| Evento de contingencia | `POST /fesv/contingencia` | igual |
| Evento de invalidación | `POST /fesv/anulardte` | igual |
| Retorno y operaciones especiales | Definidos en DTE 2.0 | igual |

- El token de autenticación tiene vigencia limitada `[VERIFICAR]` duración por ambiente. Se guarda cifrado en Valkey y se renueva antes de expirar.
- Todas las URL y rutas viven en `application-*.yml`, nunca en código.

### 11.5 Máquina de estados

```text
                ┌────────────┐
                │ PENDIENTE  │  (documento de negocio emitido; JSON construido y validado)
                └─────┬──────┘
                      │ firma
                ┌─────▼──────┐
          ┌─────┤  FIRMADO   ├───────────────┐
          │     └─────┬──────┘               │ MH no disponible → contingencia
          │           │ transmisión          │ (se entrega al cliente sin sello)
          │     ┌─────▼────────┐       ┌─────▼────────┐
          │     │ TRANSMITIENDO│       │ TRANSITORIO  │
          │     └──┬────────┬──┘       └──┬────────┬──┘
          │ sello  │        │ rechazo     │ sello  │ rechazo
          │     ┌──▼────┐ ┌─▼────────┐    │        │
          │     │SELLADO│◄┼──────────┼────┘        │
          │     └┬─────┬┘ │RECHAZADO │◄────────────┘
          │      │     │  └────┬─────┘
          │      │     │       └──► corrección → nuevo PENDIENTE
          │      │     └──► CON_RETORNO (uno o varios retornos)
          │      └──► INVALIDADO (terminal)
```

```java
/**
 * Estados del ciclo de vida de un DTE dentro de Pilot.
 * Cada estado declara los estados a los que puede pasar, para impedir transiciones inválidas.
 */
public enum EstadoDte {
    PENDIENTE, FIRMADO, TRANSMITIENDO, TRANSITORIO, SELLADO, RECHAZADO, INVALIDADO, CON_RETORNO;

    // Mapa de transiciones permitidas: estado origen -> estados destino válidos
    private static final Map<EstadoDte, Set<EstadoDte>> TRANSICIONES = Map.of(
            PENDIENTE,     Set.of(FIRMADO),
            FIRMADO,       Set.of(TRANSMITIENDO, TRANSITORIO),
            TRANSMITIENDO, Set.of(SELLADO, RECHAZADO, TRANSITORIO), // TRANSITORIO si el MH cae a mitad del envío
            TRANSITORIO,   Set.of(SELLADO, RECHAZADO),
            SELLADO,       Set.of(INVALIDADO, CON_RETORNO),
            CON_RETORNO,   Set.of(CON_RETORNO),                     // Admite retornos parciales adicionales
            RECHAZADO,     Set.of(),                                // Se corrige creando un DTE nuevo [VERIFICAR]
            INVALIDADO,    Set.of());                               // Estado terminal

    /**
     * Indica si se puede pasar de este estado al estado destino.
     *
     * @param destino estado al que se quiere transicionar
     * @return true si la transición está permitida
     */
    public boolean puedeTransicionarA(EstadoDte destino) {
        // Busca los destinos válidos del estado actual y verifica si incluye el destino
        return TRANSICIONES.getOrDefault(this, Set.of()).contains(destino);
    }
}
```

### 11.6 Flujo de emisión (perfil `worker`)

1. `ventas` confirma el documento y publica `ventas.documento.emitido` (misma transacción, sin llamar al MH).
2. El listener de `dte` crea el registro `dte` en `PENDIENTE`, asigna código de generación y número de control.
3. Construye el JSON según el esquema de la versión vigente del tipo de DTE.
4. Valida el JSON contra el esquema oficial; si falla, marca error de construcción y alerta (es un bug, no un rechazo del MH).
5. Firma con el firmador interno → `FIRMADO`. Guarda el JSON firmado en S3 y su hash SHA-256.
6. Transmite al MH → `TRANSMITIENDO`.
   - Respuesta procesada: guarda sello y fecha de procesamiento → `SELLADO` → publica `dte.documento.sellado`.
   - Rechazo: guarda observaciones → `RECHAZADO` → publica `dte.documento.rechazado` y alerta al usuario. Corregir y reenviar dentro del plazo permitido `[VERIFICAR]` (referencias externas indican 24 horas).
   - Error técnico o tiempo agotado: reintento con espera exponencial. Si el circuit breaker se abre → contingencia (11.7).
7. Genera el PDF con QR y lo guarda en S3.
8. n8n (o el módulo de notificaciones) envía JSON + PDF al receptor por correo y, opcionalmente, WhatsApp.

```java
/**
 * Adaptador de salida hacia la API de transmisión del Ministerio de Hacienda.
 * Implementa el puerto TransmisorDte del dominio (arquitectura hexagonal).
 */
@Component
class MhTransmisorAdapter implements TransmisorDte {

    private final RestClient restClient;          // Cliente HTTP con la URL base del ambiente configurado
    private final MhTokenProvider tokenProvider;  // Obtiene, cifra, cachea y renueva el token del MH
    private final MhProperties props;             // Configuración externa: ambiente, rutas, timeouts

    /**
     * Construye el adaptador con la configuración del ambiente activo.
     */
    MhTransmisorAdapter(RestClient.Builder builder, MhTokenProvider tokenProvider, MhProperties props) {
        // La URL base sale de configuración (pruebas o producción), nunca del código
        this.restClient = builder.baseUrl(props.urlBase()).build();
        this.tokenProvider = tokenProvider;
        this.props = props;
    }

    /**
     * Transmite un DTE firmado y devuelve la respuesta del MH (procesado o rechazado).
     * Solo se reintentan errores técnicos; un rechazo del MH no se reintenta.
     */
    @Override
    @Retry(name = "mh")
    @CircuitBreaker(name = "mh")
    public RespuestaMh transmitir(DteFirmado dte) {
        // 1. Arma el cuerpo que exige el endpoint de recepción [VERIFICAR] nombres de campos
        var solicitud = new SolicitudRecepcionMh(
                props.ambiente(),          // "00" pruebas / "01" producción
                dte.idEnvio(),             // Identificador del envío
                dte.versionEsquema(),      // Versión del esquema del tipo de DTE
                dte.tipoDte(),             // Código CAT-002, p. ej. "01"
                dte.documentoFirmado(),    // JWS devuelto por el firmador
                dte.codigoGeneracion());   // UUID en mayúsculas

        // 2. Envía con el token vigente; los rechazos del MH llegan como 4xx con cuerpo JSON,
        //    por eso no se lanza excepción y se interpreta la respuesta [VERIFICAR] códigos HTTP
        return restClient.post()
                .uri(props.rutaRecepcion())
                .header(HttpHeaders.AUTHORIZATION, tokenProvider.tokenVigente())
                .body(solicitud)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    // No lanzar: el cuerpo contiene el estado RECHAZADO y sus observaciones
                })
                .body(RespuestaMh.class);
    }
}
```

### 11.7 Contingencia

- **Activación automática** cuando el circuit breaker del MH se abre o se superan N fallos consecutivos; **activación manual** por el administrador (falla de internet o energía del emisor).
- Tipos de contingencia según catálogo MH `[VERIFICAR]`: no disponibilidad del sistema del MH, no disponibilidad del sistema del emisor, falla de internet del emisor, falla de energía del emisor, otro.
- Durante la contingencia los DTE se firman, se entregan al receptor y quedan en `TRANSITORIO`.
- Al superarse la contingencia:
  1. Se transmite el **evento de contingencia** con el detalle de los DTE afectados.
  2. Una vez sellado el evento, los DTE se transmiten (preferiblemente por lote) dentro de **72 horas**.
- Alertas: al iniciar la contingencia, cada 12 horas mientras dure y cuando falten 12 horas para vencer el plazo de 72 horas.
- Contabilidad: los DTE en estado transitorio se registran contablemente con su código de generación y fecha de generación (requisito DTE 2.0).
- Runbook obligatorio: `docs/runbooks/contingencia-dte.md`.

### 11.8 Invalidación

| Tipo de DTE | Plazo máximo desde el sello de recepción |
|---|---|
| FE, FEXE, FSEE | Tres meses (90 días, según actualización del manual funcional de febrero de 2026) |
| Resto de tipos (CCF, NC, ND, NR, CR, etc.) `[VERIFICAR]` lista exacta | Hasta las 23:59:59 del día siguiente |

- Tipos de invalidación según catálogo MH `[VERIFICAR]`: error en la información (requiere documento que lo reemplace), rescisión de la operación, otro.
- Pilot valida el plazo antes de permitir la acción y exige el documento de reemplazo cuando corresponde.
- El evento se firma y transmite como cualquier DTE; al sellarse, el DTE original pasa a `INVALIDADO` y se revierten sus efectos (inventario, cuenta por cobrar, asiento).
- El receptor debe ser notificado.

### 11.9 Normativa DTE 2.0 — cambios a implementar desde el inicio

> Fuente: publicaciones de proveedores basadas en la normativa. `[VERIFICAR]` todo contra la normativa oficial y los esquemas 2.0.

| Cambio | Implementación en Pilot |
|---|---|
| **Evento de Retorno**: devoluciones o reembolsos en FE, FEXE y FSEE sin ajuste al crédito fiscal; no es descuento ni invalida el original; se entrega al cliente con representación gráfica; plazo general de tres meses desde el sello del documento relacionado | Flujo de devolución en `ventas`; nuevo estado `CON_RETORNO`; asiento de devolución y reingreso a inventario |
| **Evento de Operaciones Especiales**: informar Facturas de Venta Simplificada y comprobantes de control interno; dentro de los primeros diez días hábiles del mes siguiente | Tarea programada mensual con resumen y alerta de plazo |
| **Transmisión Diferida Normal**: con autorización por resolución, transmitir por lote hasta un día después de la entrega | Modo configurable por empresa con número de resolución; transmisión por lote |
| **Estado Transitorio**: documento firmado y entregado sin sello | Estado `TRANSITORIO` y registro contable obligatorio |
| **Nuevas definiciones**: punto de venta, establecimiento, gestor de facturación electrónica | Modelo de datos alineado; evaluar registro como gestor (sección 23) |
| **Cambios de estructura**: campos nuevos, modificados y eliminados | Esquemas 2.0 versionados; pruebas con casos dorados por tipo |
| **Generación con fecha posterior**: el plazo pasó de uno a cinco días sin exceder el período tributario | Validación de fecha de generación configurable `[VERIFICAR]` |

### 11.10 Firmador y credenciales

- Contenedor del firmador oficial solo en la red interna de Docker, sin puerto publicado.
- El certificado del emisor se monta como volumen de solo lectura desde un secreto cifrado.
- Contraseña de la llave privada y contraseña de la API del MH cifradas en reposo (secreto de despliegue o columna cifrada con llave fuera de la base de datos).
- `certificado_emisor` guarda metadatos: NIT, fecha de vencimiento, huella digital; **nunca** la llave privada en texto plano.
- Alertas de vencimiento del certificado a 30, 15, 7 y 1 día.
- Rotación: procedimiento en `docs/runbooks/rotacion-certificado.md`.

### 11.11 Representación gráfica (PDF)

- Incluye: datos del emisor y receptor, tipo de documento, código de generación, número de control, sello de recepción, fecha y hora de emisión, detalle, totales en números y en letras, y **código QR** hacia la consulta pública del MH `[VERIFICAR]` URL y parámetros (ambiente, código de generación, fecha de emisión).
- Si el DTE está en contingencia, el PDF indica que el documento está pendiente de sello.
- Plantillas personalizables por empresa (logo, colores, notas), sin alterar los campos obligatorios.
- Formatos: carta y ticket de 80 mm.

### 11.12 Conservación

- JSON firmado, respuesta del MH y PDF en almacenamiento S3 con versionado y bloqueo de objetos, durante al menos **10 años**.
- Hash SHA-256 en la base de datos para verificar integridad.
- Ruta de objeto: `dte/<empresa_id>/<anio>/<mes>/<codigo_generacion>.{json,pdf}`.

### 11.13 Proceso de habilitación como emisor

1. Solicitud de autorización ante el MH.
2. Obtención de certificado de pruebas y credenciales de API de pruebas.
3. Emisión de los documentos de prueba exigidos en el ambiente de pruebas `[VERIFICAR]` cantidades por tipo; referencias externas indican un plazo de 60 días para completarlas.
4. Autorización, certificado de producción y credenciales de producción.
5. Inicio de emisión en producción en el plazo indicado en la notificación.

Pilot debe incluir un **asistente de certificación** que genere y transmita automáticamente los documentos de prueba requeridos y muestre el avance.

### 11.14 Pruebas del módulo DTE

- Casos dorados: un JSON de entrada y un JSON esperado por cada tipo de DTE y evento, validados contra el esquema oficial.
- WireMock simula respuestas del MH: procesado, rechazado, tiempo agotado, error 5xx, token vencido.
- Pruebas de contingencia de extremo a extremo: caída del MH → transitorio → evento → lote → sellado.
- Pruebas de concurrencia de correlativos (sin duplicados ni huecos no justificados).
- Pruebas periódicas contra el ambiente real de pruebas del MH en CI nocturno.

---

## 12. Contabilidad

> Las reglas fiscales y contables de esta sección deben validarse con un contador público autorizado antes de cada fase. Registrar la validación en `docs/contabilidad/`.

### 12.1 Marco y catálogo de cuentas

- Marco contable: NIIF para PYMES.
- Catálogo de cuentas por empresa, generado desde la plantilla del perfil de industria y editable.
- Estructura sugerida (configurable):

| Clase | Contenido |
|---|---|
| 1 | Activo |
| 2 | Pasivo |
| 3 | Patrimonio |
| 4 | Costos y gastos (resultado deudor) |
| 5 | Ingresos (resultado acreedor) |
| 6 | Cuenta liquidadora de resultados |

- Niveles: clase (1 dígito), grupo (2), cuenta (4), subcuenta (6) y cuentas de detalle; solo las cuentas de detalle aceptan movimientos.
- Cuentas fiscales clave: IVA crédito fiscal, IVA débito fiscal, IVA retenido a favor, IVA percibido a favor, IVA por pagar, pago a cuenta, retenciones de renta por pagar, remanente de crédito fiscal.

### 12.2 Reglas de contabilización (ejemplos base)

| Evento de negocio | Débito | Crédito |
|---|---|---|
| Venta FE al contado | Caja o bancos (total) | Ventas (neto) + IVA débito fiscal |
| Venta CCF al crédito | Cuentas por cobrar (total) | Ventas (neto) + IVA débito fiscal |
| Costo de la venta | Costo de ventas | Inventario |
| Compra CCF al crédito | Inventario o gasto + IVA crédito fiscal | Cuentas por pagar |
| Nota de crédito emitida | Devoluciones sobre ventas + IVA débito fiscal | Cuentas por cobrar |
| Retorno (DTE 2.0) de una FE | Devoluciones sobre ventas + IVA débito fiscal | Caja, bancos o cuentas por cobrar |
| Cobro con retención de IVA 1 % recibida | Bancos (neto) + IVA retenido a favor | Cuentas por cobrar (total) |
| Compra con percepción de IVA 1 % | IVA percibido a favor (además del registro de la compra) | Cuentas por pagar |
| Compra a sujeto excluido con retención de renta | Gasto | Cuentas por pagar (neto) + Retención de renta por pagar |
| Pago a cuenta mensual | Pago a cuenta | Bancos |
| Ajuste de inventario (faltante) | Pérdida en inventario | Inventario |
| Liquidación mensual de IVA | IVA débito fiscal | IVA crédito fiscal + IVA retenido/percibido a favor + IVA por pagar (o remanente) |

- Las reglas se guardan en `regla_contabilizacion` por empresa y tipo de evento, con cuentas mapeadas y fórmulas sobre los campos del evento.
- Cada asiento guarda `origen_tipo` y `origen_id` para rastrear el documento de negocio.

### 12.3 Reglas de integridad

1. Todo asiento cuadra (débitos = créditos), validado en dominio y en base de datos (sección 9.4).
2. Un asiento contabilizado es inmutable; se corrige con un asiento de reversión y uno nuevo.
3. No se contabiliza en períodos cerrados; se requiere reabrir con permiso especial y auditoría.
4. Solo cuentas de detalle activas aceptan movimientos.
5. Los asientos automáticos son idempotentes: un mismo evento de negocio no genera dos asientos (clave única por `origen_tipo` + `origen_id` + tipo de regla).

### 12.4 Impuestos y obligaciones (configurables, `[VERIFICAR]` con contador y normativa vigente)

| Concepto | Valor de referencia |
|---|---|
| IVA general | 13 % |
| Retención de IVA por gran contribuyente | 1 % |
| Percepción de IVA | 1 % |
| Pago a cuenta del impuesto sobre la renta | 1.75 % de ingresos brutos |
| Retención de renta a servicios de personas naturales | 10 % |
| Impuesto sobre la renta de personas jurídicas | 30 % (25 % si las rentas gravadas no superan el umbral legal) |
| Reserva legal (sociedades) | 7 % de utilidades hasta el límite legal |

- Liquidación de IVA del mes: débito fiscal − crédito fiscal − remanente del mes anterior = impuesto determinado; impuesto determinado − retenciones, percepciones y anticipos a favor + IVA retenido/percibido a terceros (si la empresa es agente) = IVA a pagar o nuevo remanente. Detalle en la sección 12.7.9.
- Tributos especiales (combustibles, turismo, telecomunicaciones, etc.) se configuran desde el catálogo de tributos del MH.

### 12.5 Períodos y cierres

- **Cierre mensual:** verificar DTE sin sello (transitorios o rechazados), conciliar bancos, cuadrar inventario contra contabilidad, liquidar IVA, generar libros de IVA, cerrar período.
- **Cierre anual:** asiento de cierre de resultados con la cuenta liquidadora, cálculo de impuesto sobre la renta y reserva legal, asiento de apertura del nuevo ejercicio.
- El cierre bloquea nuevas transacciones con fecha del período.

### 12.6 Libros y reportes legales

- Libro diario, libro mayor, balance de comprobación.
- Libro de ventas a consumidor final, libro de ventas a contribuyentes, libro de compras.
- Anexos para la declaración F-07 y datos para F-14 `[VERIFICAR]` formatos vigentes de carga.
- Balance general y estado de resultados.

### 12.7 Tratamiento del IVA en documentos (ADR-015)

> Reglas propuestas, **pendientes de validación por contador**. Borrador de criterio y preguntas abiertas en `docs/contabilidad/formulario-iva-respuestas.md`; formulario en blanco en `docs/contabilidad/formulario-iva.md`. Todo lo marcado `[VERIFICAR]` se confirma antes de implementarlo (regla 1.2.14).

#### 12.7.1 Tres decisiones independientes

| Decisión | Quién la determina | Cuándo | Qué afecta |
|---|---|---|---|
| **Modo de precio** (`CON_IVA` / `SIN_IVA`) | El usuario o el valor por defecto | Al crear el borrador; editable hasta confirmar | Solo la captura y qué valor se respeta al redondear |
| **Tipo de documento** (FE, CCF, NC, ND…) | La calidad del receptor (NRC vigente → CCF; si no → FE) | Al elegir el cliente | Presentación del DTE, libro de IVA, retención y percepción |
| **Tratamiento fiscal de la línea** (gravado, exento, no sujeto) | El producto en `catalogo` | Al agregar la línea | Si la línea lleva IVA |

Las tres no se mezclan: capturar con IVA y emitir un CCF, o capturar sin IVA y emitir una FE, son combinaciones válidas.

#### 12.7.2 Modo de precio

- **Catálogo:** cada producto (o lista de precios) guarda su precio y la marca `precio_incluye_iva`.
- **Documento:** un solo `modo_precio` por documento, en la cabecera (compras y ventas). No se admite modo por línea en v1.
- **Valor por defecto**, en este orden: valor explícito de la solicitud → configuración del punto de venta → `CON_IVA` para FE y `SIN_IVA` para CCF, NC y ND `[DECISIÓN]` confirmar el orden.
- **Registro mixto:** al agregar una línea, el precio del catálogo se convierte al modo del documento. Ej.: documento `SIN_IVA`; producto A a $11.30 con IVA → línea $10.00; producto B a $50.00 sin IVA → línea $50.00.
- **Ciclo de vida:** en `BORRADOR` el modo puede cambiarse y todo se recalcula; cambiar el cliente puede cambiar FE ↔ CCF pero **no** cambia el modo. Al confirmar el modo queda fijo e inmutable.
- Las líneas exentas y no sujetas ignoran el modo: su precio nunca contiene IVA.

#### 12.7.3 Normalización al confirmar

Se ejecuta **solo en el núcleo Java** (ADR-006); el frontend puede mostrar una vista previa pero el backend siempre recalcula y nunca acepta totales enviados por el cliente.

1. Leer la tasa vigente de `tasa_impuesto` a la fecha de emisión (regla 1.1.13).
2. Separar las líneas gravadas, exentas y no sujetas.
3. Calcular según el modo:
   - **`CON_IVA` (se respeta el total):** `ivaDoc = round(totalGravadoConIva × tasa / (1 + tasa), 2)`; `netoDoc = totalGravadoConIva − ivaDoc`. El IVA se reparte entre las líneas en proporción a su valor y el centavo sobrante se asigna a la última línea.
   - **`SIN_IVA` (se respeta el neto):** `ivaDoc = round(netoGravado × tasa, 2)`.
4. Precio unitario neto con hasta 8 decimales `[VERIFICAR]` precisión del esquema MH; valores de línea y totales a 2 decimales, `RoundingMode.HALF_UP`.
5. Guardar, por línea: precio ingresado, modo, precio unitario neto, venta gravada/exenta/no sujeta e IVA; por documento: totales, IVA, retención, percepción y total a pagar. Queda auditado (regla 1.1.10).

Casos dorados obligatorios (tasa 13 %):

| Modo | Se ingresa | Neto | IVA | Total |
|---|---|---|---|---|
| `CON_IVA` | 113.00 | 100.00 | 13.00 | **113.00** |
| `SIN_IVA` | 100.00 | **100.00** | 13.00 | 113.00 |
| `CON_IVA` | 5.00 | 4.42 | 0.58 | **5.00** |
| `SIN_IVA` | 4.42 | **4.42** | 0.57 | 4.99 |
| `CON_IVA` | 3 líneas × 1.00 | 2.65 | 0.35 (0.12 + 0.12 + 0.11) | **3.00** |

`[VERIFICAR]` tolerancia de redondeo que acepta el MH entre el IVA por línea y el total del documento.

#### 12.7.4 Presentación por tipo de DTE

| Tipo | Precio que ve el cliente | IVA en el DTE `[VERIFICAR]` nombres de campos en el esquema 2.0 | Libro de IVA |
|---|---|---|---|
| FE (01) | Con IVA incluido; el IVA no se desglosa al cliente | `ivaItem` por línea y `totalIva` en el resumen | Ventas a consumidores finales (con IVA incluido; resumen diario permitido) |
| CCF (03) | Neto; IVA desglosado | `resumen.tributos` código `"20"` sobre la base gravada después de descuentos | Ventas a contribuyentes (neto y débito por separado) |
| NC (05) / ND (06) | Neto; IVA desglosado | Igual que CCF + `documentoRelacionado` al CCF | Ventas a contribuyentes; NC con signo negativo, en el período de emisión |
| FEXE (11) | Sin IVA (tasa 0 %) | Sin IVA | Columna de exportaciones |

- NC y ND solo ajustan un CCF del mismo receptor. Devoluciones sobre FE, FEXE y FSEE → Evento de Retorno (sección 11.9). Errores → invalidación con documento de reemplazo (sección 11.8).
- Una NC usa el **mismo precio unitario neto** del CCF original (con todos sus decimales) y nunca supera el saldo pendiente del CCF.
- Los descuentos que constan en el documento reducen la base **antes** de calcular el IVA.

#### 12.7.5 Retención y percepción del 1 %

| | Retención | Percepción |
|---|---|---|
| Aplica cuando | Receptor gran contribuyente **compra** a emisor que no lo es | Emisor gran contribuyente **vende** a receptor que no lo es |
| Base | Neto gravado, sin IVA | Neto gravado, sin IVA |
| Monto mínimo | $100.00 `[VERIFICAR]` | $100.00 `[VERIFICAR]` |
| Efecto | Resta del total a pagar | Suma al total a pagar |
| Documento adicional | El agente emite Comprobante de Retención (07) | — |

- Son mutuamente excluyentes y **no modifican el débito fiscal**, solo el monto a cobrar o pagar.
- La condición de gran contribuyente se lee de los datos maestros de `empresa` y `cliente`/`proveedor`, con vigencia; nunca del frontend.
- El total se reconcilia con la fórmula del esquema: `totalPagar = montoTotalOperacion + ivaPerci1 − ivaRete1 − reteRenta` `[VERIFICAR]`.
- Anticipo a cuenta de IVA en cobros con tarjeta (2 % `[VERIFICAR]` tasa y base): se registra como activo y se deduce en el F-07.

#### 12.7.6 Compras: el documento recibido decide la deducibilidad

| Documento del proveedor | Tratamiento del IVA | Asiento base |
|---|---|---|
| CCF | Crédito fiscal deducible dentro del plazo legal `[VERIFICAR]` | Inventario o gasto + IVA crédito fiscal / Cuentas por pagar |
| FE (consumidor final) | **No deducible**; forma parte del costo | Inventario o gasto (total) / Cuentas por pagar |
| FSEE (emitida por la empresa) | Sin IVA | Gasto / Cuentas por pagar + retención de renta si aplica |
| Importación (declaración de mercancías) | IVA pagado en aduana es crédito fiscal | Inventario + IVA crédito fiscal / Bancos o agente aduanal |

En compras el usuario elige el **tipo de documento recibido**; el modo de precio solo afecta la captura, igual que en ventas. Si la empresa tiene operaciones gravadas y exentas, el crédito fiscal común se deduce en proporción (Ley de IVA art. 66 `[VERIFICAR]`).

#### 12.7.7 Contabilidad: nunca depende del modo

- Los eventos (`ventas.documento.emitido`, `compras.documento.registrado`, `dte.retorno.registrado`…) llevan montos ya normalizados: neto gravado, exento, no sujeto, IVA, retención, percepción y total. **Nunca llevan el modo de precio.**
- Por eso el asiento es idéntico para una venta capturada con o sin IVA. Ej.: venta de $113.00 → Caja 113.00 / Ventas 100.00 + IVA débito fiscal 13.00.
- Cuentas de IVA mínimas en el catálogo (sección 12.1): IVA crédito fiscal, remanente de crédito fiscal, IVA retenido a favor, IVA percibido a favor, IVA anticipo a cuenta (tarjetas), IVA débito fiscal, IVA por pagar, IVA retenido por pagar, IVA percibido por pagar.
- Ventas a consumidor final al contado: se permite asiento resumen diario por establecimiento `[DECISIÓN]` confirmar con contador; ventas al crédito se registran por documento.

#### 12.7.8 Validaciones antes de firmar el DTE

1. La tasa proviene de `tasa_impuesto` vigente a `fecEmi`.
2. Cada línea tiene un solo tratamiento fiscal; exentas y no sujetas no llevan IVA.
3. Los totales se recalculan desde las líneas y cuadran con la regla de redondeo del modo.
4. FE: la suma de `ivaItem` es igual a `totalIva`. CCF/NC/ND: `tributos` contiene el código `"20"` con el IVA de la base gravada.
5. CCF, NC y ND exigen NIT, NRC, actividad económica y dirección del receptor; sin NRC vigente se emite FE.
6. NC y ND referencian un CCF sellado del mismo receptor; la NC no supera el saldo pendiente.
7. Retención y percepción se aplican solo si se cumplen sus condiciones y nunca juntas.
8. FE: identificación del receptor a partir del monto que exija la norma `[VERIFICAR]` ($200.00 CT / $25,000.00 esquema DTE).
9. El JSON completo se valida contra el esquema oficial de su versión (regla 1.1.12).

#### 12.7.9 Libros de IVA y liquidación (F-07)

- El IVA que se declara es **la suma del IVA de los documentos emitidos**, nunca un recálculo sobre el total del mes; así no se generan diferencias de redondeo.
- El IVA se causa al emitir el documento: los DTE en estado `TRANSITORIO` se registran en el mes de emisión (sección 11.7).
- Orden de la liquidación: débito fiscal − crédito fiscal − remanente anterior = impuesto determinado; − retenciones, percepciones y anticipos a favor; + IVA retenido y percibido a terceros como agente = IVA a pagar. Si el crédito supera al débito, la diferencia pasa como remanente.
- Presentación y pago dentro de los 10 primeros días hábiles del mes siguiente `[VERIFICAR]`.
- Conciliaciones previas al F-07: DTE emitidos contra sellados; libros contra cuentas de IVA; compras registradas contra DTE recibidos en la consulta del MH; retenciones, percepciones y anticipos contra sus comprobantes; remanente contra el F-07 anterior.

---

## 13. API pública

### 13.1 Principios

- REST sobre HTTPS, JSON en `camelCase`, contrato OpenAPI 3.1 como fuente de verdad (ADR-003).
- Versionado en la ruta: `/api/v1/...`. Cambios incompatibles solo en una nueva versión mayor.
- Política de obsolescencia: aviso con headers `Deprecation` y `Sunset`; mínimo 12 meses de soporte tras el anuncio.
- Montos como cadena decimal (`"1500.00"`), fechas ISO-8601, IDs UUID.
- Paginación por cursor: `?limite=50&cursor=<token>` → `{ "datos": [...], "siguienteCursor": "..." }`.
- Filtros por parámetros explícitos (`?estado=SELLADO&desde=2026-10-01`) y orden con `?orden=-fechaEmision`.
- Operaciones masivas: `POST /api/v1/<recurso>/lote` (máximo 500 elementos, resultado por elemento).

### 13.2 Headers

| Header | Dirección | Uso |
|---|---|---|
| `Authorization: Bearer <token>` | Entrada | Token OAuth2 o API key |
| `X-Empresa-Id` | Entrada | Empresa activa si la credencial tiene acceso a varias |
| `Idempotency-Key` | Entrada | Obligatorio en creaciones con efecto fiscal, contable o de inventario |
| `If-Match` | Entrada | Concurrencia optimista en actualizaciones |
| `X-Request-Id` | Entrada/salida | Correlación; se genera si no viene |
| `ETag` | Salida | Versión del recurso |
| `Location` | Salida | URL del recurso creado |
| `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` | Salida | Límite de peticiones |

### 13.3 Autenticación y alcances

| Mecanismo | Para quién |
|---|---|
| OIDC con PKCE (Keycloak) | Usuarios de la web y la app móvil |
| OAuth2 Client Credentials | Integraciones servidor a servidor |
| API key | Integraciones simples y n8n |

Alcances (scopes): `catalogo:leer`, `catalogo:escribir`, `inventario:leer`, `inventario:escribir`, `ventas:leer`, `ventas:escribir`, `compras:leer`, `compras:escribir`, `dte:leer`, `dte:escribir`, `contabilidad:leer`, `contabilidad:escribir`, `tesoreria:leer`, `tesoreria:escribir`, `integracion:administrar`, `admin`.

### 13.4 Recursos principales (v1)

| Recurso | Operaciones clave |
|---|---|
| `/empresas`, `/establecimientos`, `/puntos-venta` | Consulta y configuración |
| `/clientes`, `/proveedores`, `/productos` | CRUD, búsqueda, `PUT /<recurso>/externo/{sistema}/{idOrigen}` (upsert por ID externo) |
| `/bodegas`, `/inventario/existencias`, `/inventario/movimientos` | Consulta de stock, ajustes, transferencias |
| `/ventas/cotizaciones`, `/ventas/pedidos` | Flujo comercial |
| `/ventas/documentos` | Emitir FE/CCF, consultar, devoluciones |
| `/compras/ordenes`, `/compras/documentos` | Flujo de compras |
| `/dte` | Consultar estado; `GET /dte/{codigoGeneracion}/json`, `/pdf` |
| `/dte/{codigoGeneracion}/invalidacion` | Solicitar invalidación |
| `/dte/{codigoGeneracion}/retornos` | Registrar retorno (DTE 2.0) |
| `/dte-recibidos` | Registrar DTE de proveedores |
| `/contabilidad/cuentas`, `/contabilidad/asientos`, `/contabilidad/periodos` | Consulta; asientos manuales solo con permiso contable |
| `/reportes/...` | Libros de IVA, estados financieros, exportaciones |
| `/tesoreria/cobros`, `/tesoreria/pagos`, `/tesoreria/cuentas-por-cobrar` | Cobranza y pagos |
| `/webhooks/suscripciones`, `/webhooks/entregas` | Gestión y reenvío de webhooks |
| `/eventos` | Feed de eventos por consulta con cursor |
| `/importaciones` | Carga CSV/XLSX con reporte de validación |
| `/api-keys` | Gestión de credenciales de integración |

### 13.5 Portal de desarrolladores

- Documentación generada desde OpenAPI (Scalar o Redoc) publicada en `developers.<dominio>` `[DECISIÓN]`.
- Empresa sandbox conectada al ambiente de pruebas del MH.
- Colección de Postman/Bruno generada del contrato.
- SDK generados con `openapi-generator`: TypeScript, Python, PHP, Java y C#.
- Probador de webhooks y registro de entregas visible para el integrador.

---

## 14. Eventos y webhooks

### 14.1 Formato

- Sobre **CloudEvents 1.0** en modo estructurado JSON (ADR-005). Contrato en AsyncAPI.
- Entrega **al menos una vez**: los consumidores deben descartar duplicados usando `id`.
- El orden solo se garantiza por `subject` (misma entidad).

```jsonc
// Ejemplo comentado; los comentarios no forman parte del payload real
{
  "specversion": "1.0",                               // Versión de CloudEvents
  "id": "0192f1a4-7c3e-7b21-9d4e-5a6b7c8d9e0f",       // ID único del evento (UUIDv7)
  "source": "https://api.pilot.example/empresas/<empresaId>", // Origen del evento
  "type": "dte.documento.sellado",                    // Tipo de evento
  "subject": "3F2504E0-4F89-41D3-9A0C-0305E82C3301",  // Código de generación del DTE
  "time": "2026-10-15T16:32:10Z",                     // Momento del evento en UTC
  "datacontenttype": "application/json",
  "dataschema": "https://api.pilot.example/esquemas/eventos/dte.documento.sellado/v1",
  "empresaid": "<empresaId>",                         // Extensión: empresa dueña del evento
  "data": {                                           // Datos específicos del evento
    "codigoGeneracion": "3F2504E0-4F89-41D3-9A0C-0305E82C3301",
    "numeroControl": "DTE-01-M001P001-000000000000123",
    "tipoDte": "01",
    "selloRecepcion": "2026ABCD...",
    "total": "113.00",
    "documentoVentaId": "0192f1a4-..."
  }
}
```

### 14.2 Catálogo de eventos (v1)

| Evento | Cuándo |
|---|---|
| `catalogo.producto.creado` / `.actualizado` | Alta o cambio de producto |
| `catalogo.cliente.creado` / `.actualizado` | Alta o cambio de cliente |
| `inventario.movimiento.registrado` | Cualquier movimiento de kardex |
| `inventario.existencia.bajo-minimo` | Stock por debajo del mínimo |
| `ventas.documento.emitido` | Documento de venta confirmado |
| `ventas.documento.anulado` | Documento revertido por invalidación |
| `ventas.devolucion.registrada` | Devolución (NC o retorno) |
| `compras.documento.registrado` | Compra registrada |
| `dte.documento.sellado` | El MH otorgó sello |
| `dte.documento.rechazado` | El MH rechazó el DTE |
| `dte.documento.invalidado` | Invalidación sellada |
| `dte.retorno.registrado` | Retorno sellado |
| `dte.contingencia.iniciada` / `.finalizada` | Cambios de contingencia |
| `dte.recibido.registrado` | DTE de proveedor ingresado |
| `dte.certificado.por-vencer` | Certificado cerca de expirar |
| `contabilidad.asiento.contabilizado` | Asiento contabilizado |
| `contabilidad.periodo.cerrado` | Cierre de período |
| `tesoreria.cobro.registrado` / `tesoreria.pago.registrado` | Movimientos de tesorería |

### 14.3 Webhooks

- Suscripción por empresa con URL HTTPS, lista de tipos de evento y secreto propio.
- Firma: header `Pilot-Signature: t=<epoch>,v1=<hmac-sha256-hex>` sobre `"<t>.<cuerpo>"`.
- Reintentos con espera exponencial durante 24 horas (1 min, 5 min, 30 min, 2 h, 6 h, 12 h, 24 h); luego la entrega pasa a fallida y se puede reenviar manualmente.
- Desactivación automática de la suscripción tras 3 días de fallos continuos, con aviso por correo.
- Tiempo máximo de respuesta del receptor: 10 segundos; cualquier 2xx es éxito.
- **Feed alternativo:** `GET /api/v1/eventos?desde=<cursor>` para sistemas que no pueden recibir webhooks.

```java
/**
 * Genera la firma HMAC-SHA256 de un webhook de Pilot.
 * Formato del header resultante: Pilot-Signature: t=<epoch>,v1=<hex>
 */
public final class FirmaWebhook {

    // Clase utilitaria: no se instancia
    private FirmaWebhook() {
    }

    /**
     * Firma el cuerpo del webhook con el secreto de la suscripción.
     *
     * @param secreto    secreto compartido de la suscripción
     * @param timestamp  segundos desde epoch en que se envía el webhook
     * @param cuerpoJson cuerpo exacto que se enviará (sin reformatear)
     * @return valor completo del header Pilot-Signature
     */
    public static String firmar(String secreto, long timestamp, String cuerpoJson) {
        try {
            // 1. Combina timestamp y cuerpo para evitar ataques de repetición
            String mensaje = timestamp + "." + cuerpoJson;

            // 2. Inicializa HMAC-SHA256 con el secreto de la suscripción
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

            // 3. Calcula la firma y la convierte a hexadecimal
            String firma = HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));

            // 4. Devuelve el header con el formato acordado
            return "t=" + timestamp + ",v1=" + firma;
        } catch (GeneralSecurityException e) {
            // Solo ocurre si el JDK no soporta HmacSHA256: error de configuración
            throw new IllegalStateException("No se pudo firmar el webhook", e);
        }
    }
}
```

```typescript
import { createHmac, timingSafeEqual } from 'crypto';

/**
 * Verifica la firma de un webhook de Pilot (usado en el nodo trigger de n8n y en los SDK).
 * @param secreto        Secreto de la suscripción
 * @param header         Valor del header Pilot-Signature (t=...,v1=...)
 * @param cuerpo         Cuerpo crudo tal como llegó, sin volver a serializar
 * @param toleranciaSeg  Antigüedad máxima aceptada del webhook, en segundos
 * @returns true si la firma es válida y reciente
 */
export function verificarFirma(secreto: string, header: string, cuerpo: string, toleranciaSeg = 300): boolean {
  // 1. Separa el header en pares clave=valor (t y v1)
  const partes = Object.fromEntries(header.split(',').map((p) => p.split('=', 2) as [string, string]));
  const t = Number(partes.t);
  const firmaRecibida = partes.v1 ?? '';

  // 2. Rechaza timestamps inválidos o fuera de la ventana de tolerancia
  if (!Number.isFinite(t) || Math.abs(Date.now() / 1000 - t) > toleranciaSeg) return false;

  // 3. Recalcula la firma esperada con el mismo formato que usa el servidor
  const esperada = createHmac('sha256', secreto).update(`${t}.${cuerpo}`).digest('hex');

  // 4. Compara en tiempo constante para evitar ataques de temporización
  const a = Buffer.from(esperada, 'hex');
  const b = Buffer.from(firmaRecibida, 'hex');
  return a.length === b.length && timingSafeEqual(a, b);
}
```

### 14.4 Mapa de conectividad (cómo se conecta cualquier sistema)

| Tipo de sistema | Mecanismo recomendado |
|---|---|
| Sistema moderno con API | REST + webhooks + SDK |
| Plataformas sin código (n8n, Make, Zapier) | Nodo n8n oficial; webhooks y HTTP genérico para las demás |
| E-commerce (Shopify, WooCommerce) | Plantillas de flujos n8n + upsert por ID externo |
| Sistemas que no reciben webhooks | Feed `GET /eventos` por consulta |
| Sistemas legados con base de datos | Flujo n8n que lee una vista de solo lectura + endpoints de lote |
| Sistemas legados sin API ni base de datos | Importación CSV/XLSX o carpeta SFTP |
| Contador externo | Exportaciones de libros de IVA y partidas en XLSX/CSV |
| Proveedores que envían DTE por correo | Flujo n8n con disparador IMAP → `POST /dte-recibidos` |

---

## 15. Integración con n8n

### 15.1 Despliegue

- n8n autoalojado en **modo cola**: una instancia principal, workers y Valkey como cola; base de datos PostgreSQL propia (base separada de la de Pilot).
- Detrás de Traefik con autenticación; solo las rutas `/webhook/*` son públicas.
- `N8N_ENCRYPTION_KEY` fija, respaldada y guardada como secreto.
- Zona horaria `GENERIC_TIMEZONE=America/El_Salvador`.
- Respaldos diarios de la base de n8n y exportación de flujos a Git (`integraciones/plantillas-n8n/`).

### 15.2 Reglas

1. n8n **orquesta**; Pilot **decide**. Ningún flujo calcula impuestos, totales ni asientos.
2. Los flujos llaman a la API de Pilot con credenciales de alcance mínimo.
3. Todo flujo que crea documentos envía `Idempotency-Key` derivada del ID del sistema origen.
4. Los flujos que reciben webhooks verifican la firma (el nodo trigger de Pilot lo hace automáticamente).
5. Cada flujo productivo tiene manejo de errores (Error Trigger) que notifica al responsable.

### 15.3 Flujos plantilla

| Flujo | Disparador | Acción |
|---|---|---|
| Entrega de DTE al cliente | `dte.documento.sellado` | Descarga JSON y PDF, envía por correo y WhatsApp |
| Recepción de DTE de proveedores | Correo IMAP con adjunto JSON | `POST /dte-recibidos` |
| Alerta de stock bajo | `inventario.existencia.bajo-minimo` | Notifica por correo, Slack o WhatsApp |
| Sincronización e-commerce | Pedido nuevo en la tienda | Upsert de cliente y emisión de venta |
| Recordatorio de cobro | Programado diario | Consulta cuentas vencidas y envía recordatorios |
| Reporte diario de ventas | Programado | Genera resumen y lo envía a gerencia |
| Alerta de contingencia | `dte.contingencia.iniciada` | Notifica al administrador |
| Alerta de certificado | `dte.certificado.por-vencer` | Notifica con días restantes |

### 15.4 Nodo comunitario `n8n-nodes-pilot`

- Paquete npm en `integraciones/n8n-nodes-pilot/` (TypeScript).
- Credencial: URL base + API key (y OAuth2 client credentials como opción).
- Nodo de acciones con recursos: cliente, producto, documento de venta, DTE, inventario, cobro.
- Nodo trigger: registra y elimina la suscripción de webhook automáticamente y verifica la firma.
- Publicación en npm y verificación como nodo comunitario `[DECISIÓN]`.

### 15.5 Licencia

- n8n usa la *Sustainable Use License*: el uso interno está permitido, pero ofrecer n8n alojado a clientes como parte del producto puede requerir licencia comercial `[VERIFICAR]` antes de incluirlo en la oferta de Pilot.

---

## 16. Seguridad

### 16.1 Lineamientos

- Objetivo: OWASP ASVS nivel 2.
- TLS en todo el tráfico externo; servicios internos solo en redes privadas de Docker.
- MFA obligatorio para los roles administrador y contador (Keycloak).
- Secretos: SOPS + age en el repositorio de infraestructura o secretos de Docker; evaluar Vault en Fase 7.
- Cifrado en reposo: disco cifrado en el servidor y columnas sensibles cifradas con llave fuera de la base de datos.
- Auditoría de solo inserción (sin UPDATE ni DELETE para el usuario de la aplicación).
- Límite de peticiones por API key, por usuario y por IP.
- Protección SSRF en URL de webhooks: bloquear IP privadas, locales y de metadatos de la nube.
- Cumplimiento de la Ley de Protección de Datos Personales de El Salvador `[VERIFICAR]` obligaciones concretas (consentimiento, derechos del titular, notificación de brechas).
- Escaneo continuo: gitleaks (secretos), CodeQL o Semgrep (código), Dependabot (dependencias), Trivy (imágenes).

### 16.2 Roles base

| Rol | Permisos principales |
|---|---|
| `admin_empresa` | Todo dentro de su empresa, incluida configuración y usuarios |
| `contador` | Contabilidad completa, reportes fiscales, cierres, lectura del resto |
| `supervisor` | Ventas, compras, inventario, anulaciones e invalidaciones |
| `cajero` | POS, cobros, apertura y cierre de su caja |
| `vendedor` | Cotizaciones, pedidos, emisión de ventas |
| `bodeguero` | Inventario, recepciones, transferencias |
| `auditor` | Solo lectura de todo, incluida la auditoría |
| `integracion` | Rol técnico para API keys, limitado por alcances |

---

## 17. Observabilidad

### 17.1 Estándares

- Logs JSON con `traceId`, `spanId`, `empresaId`, `usuarioId`, `modulo`; datos personales enmascarados.
- Trazas distribuidas con OpenTelemetry (API → worker → firmador → MH).
- Métricas con Micrometer expuestas a Prometheus.

### 17.2 Métricas de negocio

- DTE emitidos, sellados, rechazados y en transitorio, por tipo y empresa.
- Tiempo desde la emisión hasta el sello (p50, p95, p99).
- Retraso del outbox (antigüedad del evento pendiente más viejo).
- Tasa de éxito de webhooks por suscripción.
- Días restantes del certificado por empresa.

### 17.3 Alertas

| Alerta | Condición | Severidad |
|---|---|---|
| MH no disponible | Circuit breaker abierto más de 5 minutos | Crítica |
| Plazo de contingencia | DTE transitorio con más de 60 horas | Crítica |
| DTE rechazados | Más de 3 rechazos en 1 hora para una empresa | Alta |
| Outbox atrasado | Evento pendiente con más de 5 minutos | Alta |
| Certificado por vencer | Menos de 15 días | Alta |
| Webhooks fallando | Tasa de éxito menor al 90 % en 1 hora | Media |
| Base de datos | Disco mayor al 80 % o réplica atrasada | Alta |
| Respaldo fallido | Sin respaldo exitoso en 24 horas | Crítica |

### 17.4 Objetivos de nivel de servicio iniciales

| Indicador | Objetivo |
|---|---|
| Disponibilidad de la API | 99.5 % mensual |
| Latencia de lectura (p95) | < 300 ms |
| Latencia de escritura (p95) | < 800 ms |
| Emisión a sello con el MH operativo (p95) | < 10 s |
| RPO / RTO | 15 minutos / 4 horas |

---

## 18. Estrategia de pruebas

| Nivel | Herramienta | Alcance |
|---|---|---|
| Unitarias de dominio | JUnit + AssertJ | Cálculo de impuestos, reglas contables, máquina de estados; cobertura ≥ 80 % |
| Arquitectura | Spring Modulith + ArchUnit | Límites de módulos, capas, dependencias prohibidas |
| Integración | Testcontainers (PostgreSQL, RabbitMQ, Valkey) | Repositorios, RLS, outbox, idempotencia |
| API | MockMvc / cliente de pruebas + validación contra OpenAPI | Contrato, errores, seguridad |
| DTE | Casos dorados + WireMock + ambiente de pruebas del MH (nocturno) | Construcción, firma, transmisión, contingencia |
| Contables | Casos dorados validados por contador | Cada regla de contabilización |
| Frontend | Vitest + Testing Library | Componentes y lógica de UI |
| Extremo a extremo | Playwright | Flujos críticos: venta → DTE → PDF; cierre de caja |
| Carga | k6 | Meta inicial: 50 documentos por segundo sostenidos por instancia |
| Seguridad | OWASP ZAP (base), pruebas de aislamiento entre empresas | Antes de cada versión mayor |

Prueba obligatoria de aislamiento: un usuario de la empresa A nunca puede leer ni escribir datos de la empresa B, ni por API ni por SQL directo con el usuario de la aplicación.

---

## 19. Infraestructura y despliegue

### 19.1 Ambientes

| Ambiente | Propósito | MH |
|---|---|---|
| `local` | Desarrollo | WireMock |
| `staging` | Pruebas integradas y sandbox de integradores | Ambiente de pruebas (`00`) |
| `produccion` | Clientes reales | Producción (`01`) |

### 19.2 Docker Compose de desarrollo (referencia)

```yaml
# infra/docker/compose.dev.yml — dependencias para desarrollo local
# Fijar versiones exactas de imagen al crear el archivo; nunca usar "latest" en producción
services:

  postgres:                                   # Base de datos principal de Pilot
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: pilot                      # Base de la aplicación
      POSTGRES_USER: pilot_owner              # Dueño del esquema (solo migraciones)
      POSTGRES_PASSWORD: ${PG_OWNER_PASSWORD} # Tomado de .env (no se commitea)
    ports: ["5432:5432"]
    volumes: [pg_data:/var/lib/postgresql/data]

  valkey:                                     # Caché, límites de peticiones y cola de n8n
    image: valkey/valkey:8-alpine
    ports: ["6379:6379"]

  rabbitmq:                                   # Bus de eventos externos
    image: rabbitmq:4-management-alpine
    ports: ["5672:5672", "15672:15672"]       # AMQP y consola de administración

  keycloak:                                   # Proveedor de identidad (OIDC)
    image: quay.io/keycloak/keycloak:26.3     # [VERIFICAR] versión vigente al iniciar
    command: start-dev --import-realm         # Modo desarrollo e importación del realm
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KC_ADMIN_PASSWORD}
    volumes: [./keycloak:/opt/keycloak/data/import]  # Realm "pilot" versionado
    ports: ["8180:8080"]

  s3:                                         # Almacenamiento compatible con S3 para desarrollo
    image: chrislusf/seaweedfs                # [DECISIÓN] SeaweedFS o Garage; fijar versión
    command: server -s3 -dir=/data
    ports: ["8333:8333"]
    volumes: [s3_data:/data]

  firmador:                                   # Firmador oficial del MH (solo red interna)
    image: svfe/svfe-api-firmador             # [VERIFICAR] nombre de imagen, puerto y rutas
    volumes: [./secretos/certificados:/uploads:ro]   # Certificados de PRUEBAS, nunca en Git
    # Sin "ports": no se publica al host

  mh-simulado:                                # Simulación de la API del MH
    image: wiremock/wiremock:3.9.1            # Fijar versión vigente
    volumes: [../wiremock/mh:/home/wiremock]
    ports: ["8089:8080"]

  n8n:                                        # Orquestador de integraciones
    image: docker.n8n.io/n8nio/n8n            # Fijar versión vigente
    environment:
      N8N_ENCRYPTION_KEY: ${N8N_ENCRYPTION_KEY}
      GENERIC_TIMEZONE: America/El_Salvador
      WEBHOOK_URL: http://localhost:5678/
    ports: ["5678:5678"]
    volumes: [n8n_data:/home/node/.n8n]

  mailpit:                                    # Captura de correos en desarrollo
    image: axllent/mailpit
    ports: ["1025:1025", "8025:8025"]         # SMTP y bandeja web

volumes:
  pg_data:
  s3_data:
  n8n_data:
```

La aplicación se conecta a PostgreSQL con un usuario distinto (`pilot_app`) sin privilegios de dueño ni `BYPASSRLS`; Flyway usa `pilot_owner`.

### 19.3 Producción inicial (bajo costo)

- Un VPS (4 vCPU, 8–16 GB RAM) con Docker Compose, o la capa gratuita ARM de Oracle Cloud `[VERIFICAR]` condiciones vigentes.
- Traefik con Let's Encrypt, firewall solo con puertos 80/443 y SSH por llave.
- PostgreSQL con WAL-G: respaldo completo diario, WAL continuo, recuperación a un punto en el tiempo de 14 días, prueba de restauración mensual.
- Almacenamiento de DTE en proveedor S3 externo con versionado (no en el mismo servidor).
- Monitoreo externo de disponibilidad (servicio gratuito de uptime).
- Migración a k3s con varios nodos en Fase 7 o cuando la carga lo justifique.

### 19.4 CI/CD (GitHub Actions)

1. Formato y lint (Spotless, Checkstyle, ESLint, Spectral).
2. Pruebas unitarias y de arquitectura.
3. Pruebas de integración con Testcontainers.
4. Escaneos de seguridad (gitleaks, CodeQL/Semgrep, Trivy).
5. Construcción de imágenes y publicación en GitHub Container Registry.
6. Despliegue automático a `staging` al fusionar en `main`.
7. Despliegue a `produccion` con etiqueta `vX.Y.Z` y aprobación manual.
8. Migraciones Flyway ejecutadas como paso previo al despliegue, compatibles hacia atrás (patrón expandir/contraer).

---

## 20. Roadmap

> Estimaciones para un equipo de 1–2 desarrolladores a tiempo completo `[DECISIÓN]` confirmar tamaño del equipo. **Ruta crítica:** si Pilot debe emitir en producción antes del 2026-12-01, las Fases 0 y 1 (≈10 semanas desde el 2026-09-23) no tienen holgura; considerar salir solo con FE, CCF, NC y ND.

### Fase 0 — Fundaciones (3 semanas)

- [ ] Monorepo, CI completo, Docker Compose de desarrollo.
- [ ] Proyecto Spring Boot con Spring Modulith y prueba de límites de módulos.
- [ ] Keycloak con realm `pilot`, login web con PKCE.
- [ ] Multi-empresa con RLS y prueba de aislamiento.
- [ ] Auditoría, outbox, idempotencia, Problem Details.
- [ ] Contrato OpenAPI base y cliente generado en el frontend.
- [ ] Observabilidad base (logs JSON, métricas, trazas).
- **Terminado cuando:** una API de ejemplo pasa pruebas de aislamiento, idempotencia y publica eventos por outbox.

### Fase 1 — Catálogos y facturación DTE núcleo (7 semanas)

- [ ] Empresa, establecimientos, puntos de venta, usuarios y roles.
- [ ] Catálogos MH versionados, productos, clientes, impuestos con vigencia.
- [ ] Documentos de venta: FE y CCF; NC y ND.
- [ ] Módulo DTE con esquemas 2.0: construcción, validación, firmador, transmisión, estados.
- [ ] Contingencia automática y manual; transmisión por lote.
- [ ] Invalidación con validación de plazos; evento de retorno.
- [ ] PDF con QR y envío por correo.
- [ ] Asistente de certificación en el ambiente de pruebas del MH.
- **Terminado cuando:** se completan los documentos de prueba exigidos por el MH y una empresa piloto emite en producción.

### Fase 2 — Inventario y compras (5 semanas)

- [ ] Bodegas, kardex, costo promedio, ajustes y transferencias.
- [ ] Nota de Remisión (04).
- [ ] Compras, recepción y DTE recibidos.
- [ ] FSEE (14) y Comprobante de Retención (07).
- [ ] Evento de operaciones especiales.
- **Terminado cuando:** el inventario valorizado cuadra con los movimientos de compras y ventas.

### Fase 3 — Contabilidad (7 semanas)

- [ ] Catálogo de cuentas por perfil de industria.
- [ ] Reglas de contabilización y asientos automáticos idempotentes.
- [ ] Asientos manuales, reversiones, períodos y cierres.
- [ ] Libros de IVA, anexos F-07, liquidación de IVA.
- [ ] Libro diario, mayor, balance de comprobación, balance general y estado de resultados.
- **Terminado cuando:** un contador valida un mes completo de una empresa piloto.

### Fase 4 — Integraciones (4 semanas)

- [ ] Webhooks públicos con firma, reintentos y reenvío.
- [ ] Feed de eventos por consulta, API keys con alcances, mapeo de IDs externos.
- [ ] Importación CSV/XLSX y carpeta SFTP.
- [ ] Nodo `n8n-nodes-pilot` y plantillas de flujos.
- [ ] SDK generados y portal de desarrolladores con sandbox.
- **Terminado cuando:** una tienda en línea y un sistema legado se integran sin código a medida.

### Fase 5 — Tesorería, POS y móvil (6 semanas)

- [ ] Cuentas por cobrar y pagar, cobros, pagos, antigüedad de saldos.
- [ ] Bancos y conciliación.
- [ ] POS web con cajas, arqueos y modo sin conexión.
- [ ] App móvil para vendedores (Expo).
- **Terminado cuando:** una tienda opera un día completo solo con el POS de Pilot, incluido un corte de internet.

### Fase 6 — Documentos avanzados y nómina (8 semanas)

- [ ] FEXE (11), CD (15), CL (08), DCL (09).
- [ ] Recursos humanos y planilla con descuentos de ley.
- [ ] Producción básica (lista de materiales) para manufactura ligera.

### Fase 7 — Escala y SaaS (continuo)

- [ ] Registro autoservicio, planes y facturación de la suscripción.
- [ ] k3s con alta disponibilidad, réplicas de lectura.
- [ ] BI con Metabase sobre réplica de solo lectura.
- [ ] Funciones de IA: categorización de gastos, predicción de inventario, asistente de conciliación.
- [ ] Evaluar extracción de `dte` como servicio independiente si el volumen lo exige.

### Hitos

| Fecha | Hito |
|---|---|
| 2026-09-23 | Inicio de Fase 0 |
| 2026-12-01 | Normativa DTE 2.0 obligatoria `[VERIFICAR]` |
| Fin de Fase 1 | Primera empresa piloto emitiendo en producción |
| Fin de Fase 3 | MVP comercial: facturación + inventario + contabilidad |
| Fin de Fase 4 | Lanzamiento de la propuesta "se conecta con todo" |

---

## 21. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Cambios normativos del MH | Alto | Esquemas y catálogos versionados, revisión mensual del portal del MH, casos dorados por versión |
| Caída del MH | Alto | Contingencia automática, alertas de plazo de 72 horas |
| Plazo de DTE 2.0 | Alto | Priorizar Fases 0 y 1; alcance mínimo FE/CCF/NC/ND |
| Equipo pequeño | Alto | Este archivo, ADR, runbooks, automatización de pruebas |
| Crecimiento descontrolado del alcance | Alto | Roadmap por fases y lista explícita de fuera de alcance |
| Errores contables | Alto | Reglas validadas por contador, casos dorados, partida doble en BD |
| Fuga de datos entre empresas | Crítico | RLS forzado, pruebas de aislamiento obligatorias |
| Robo del certificado o credenciales del MH | Crítico | Red interna, cifrado, rotación, alertas |
| Licencias de terceros (n8n, almacenamiento, caché) | Medio | ADR-009, ADR-011, ADR-012 y revisión legal |
| Costos de infraestructura | Medio | Stack open source, VPS, monitoreo de costos |
| Baja adopción | Medio | Perfiles de industria, importadores, onboarding guiado |

---

## 22. Decisiones de arquitectura (ADR)

| ID | Decisión | Estado |
|---|---|---|
| ADR-001 | Monolito modular con Spring Modulith y arquitectura hexagonal | Aceptada |
| ADR-002 | PostgreSQL con esquema compartido, `empresa_id` y Row-Level Security forzado | Aceptada |
| ADR-003 | Contract-first: OpenAPI como fuente de verdad; código y SDK generados | Aceptada |
| ADR-004 | Patrón outbox + RabbitMQ para eventos externos | Aceptada |
| ADR-005 | CloudEvents 1.0 como formato de eventos y webhooks | Aceptada |
| ADR-006 | Lógica fiscal y contable exclusivamente en el núcleo Java | Aceptada |
| ADR-007 | Esquemas, catálogos y endpoints del MH como configuración versionada | Aceptada |
| ADR-008 | Keycloak como proveedor de identidad | Aceptada |
| ADR-009 | n8n solo en los bordes (orquestación), nunca en la lógica central | Aceptada |
| ADR-010 | UUID versión 7 como clave primaria generada en la aplicación | Aceptada |
| ADR-011 | Almacenamiento de objetos detrás de la API S3; proveedor intercambiable | Aceptada (proveedor `[DECISIÓN]`) |
| ADR-012 | Valkey en lugar de Redis por licencia BSD | Aceptada |
| ADR-013 | Montos como cadena decimal en la API pública | Aceptada |
| ADR-014 | Mismo artefacto con perfiles `api` y `worker` | Aceptada |
| ADR-015 | Un solo modo de precio (`CON_IVA` / `SIN_IVA`) por documento, normalizado a neto, IVA y total al confirmar; la contabilidad nunca depende del modo (sección 12.7) | Aceptada (reglas fiscales pendientes de validación por contador) |

Cada ADR tiene su archivo en `docs/adr/ADR-XXX-<titulo>.md` con contexto, decisión, alternativas y consecuencias.

---

## 23. Decisiones pendientes y preguntas abiertas

- [ ] Organización y nombre del repositorio en GitHub.
- [ ] Tamaño del equipo (afecta todas las estimaciones).
- [ ] Modelo de precios y planes de suscripción.
- [ ] Hosting de producción (Oracle Cloud, Hetzner u otro).
- [ ] Proveedor S3 de producción (Cloudflare R2, Backblaze B2 u otro).
- [ ] Proveedor de correo transaccional y de WhatsApp (API oficial).
- [ ] Dominio del producto y del portal de desarrolladores.
- [ ] Registro de Pilot como gestor de facturación electrónica bajo la normativa 2.0 `[VERIFICAR]` requisitos.
- [ ] Agente local "Pilot Edge" para POS sin conexión.
- [ ] Licencia comercial de n8n si se ofrece embebido a clientes.
- [ ] Maven o Gradle (por defecto Maven).
- [ ] Modelo de dominio separado de JPA o entidades JPA como dominio.
- [ ] Métodos de costeo permitidos además del promedio ponderado.
- [ ] Validación por contador del tratamiento del IVA (sección 12.7) usando `docs/contabilidad/formulario-iva.md`.
- [ ] Orden del valor por defecto del modo de precio y asiento resumen diario de ventas a consumidor final (sección 12.7).

---

## 24. Fuentes y referencias

- Ministerio de Hacienda: https://www.mh.gob.sv
- Portal de facturación electrónica del MH (normativa, manuales, esquemas, catálogos): `[VERIFICAR]` URL vigente, p. ej. https://factura.gob.sv
- Spring Boot: https://spring.io/projects/spring-boot
- Spring Modulith: https://spring.io/projects/spring-modulith
- PostgreSQL Row-Level Security: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- CloudEvents: https://cloudevents.io
- AsyncAPI: https://www.asyncapi.com
- RFC 9457 Problem Details: https://www.rfc-editor.org/rfc/rfc9457
- Keycloak: https://www.keycloak.org
- n8n (documentación y licencia): https://docs.n8n.io
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/

---

## 25. Mantenimiento de este archivo

- Responsable: líder técnico del proyecto.
- Revisión obligatoria al cerrar cada fase del roadmap y cuando el MH publique cambios normativos.
- Todo `[VERIFICAR]` resuelto se reemplaza por el dato confirmado y su fuente (documento y versión).
- Todo `[DECISIÓN]` resuelto se convierte en ADR.
- Actualizar la fecha de "Última actualización" en el encabezado.
