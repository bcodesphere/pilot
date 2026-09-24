# CLAUDE.md — Proyecto PILOT (MVP: Núcleo + Contabilidad)

> **Pilot** es un ERP multi-empresa, API-first y modular para PYMES de El Salvador, integrable con cualquier sistema mediante API y n8n. **Este archivo está delimitado al MVP**: el núcleo del ERP (usuarios, empresa, estructura de apps) y **una sola app activa: Contabilidad**. La visión completa del producto se conserva en `docs/vision/CLAUDE-vision-completa-2026-09-23.md` y todo lo que no está en el MVP se lista en la sección 20 (Fuera de alcance).

| Campo | Valor |
|---|---|
| Nombre del producto | Pilot |
| Organización | bcodesphere |
| Repositorio | GitHub — organización y nombre `[DECISIÓN]` |
| Paquete base Java | `com.bcodesphere.pilot` |
| Mercado inicial | El Salvador — PYMES de cualquier giro |
| Moneda | USD (única moneda contable) |
| Zona horaria de negocio | `America/El_Salvador` (UTC−6, sin horario de verano) |
| Idioma de producto | Español (`es-SV`) |
| Alcance vigente | MVP: Núcleo + app Contabilidad |
| Estado actual | Fase F0 — Fundaciones (ver `docs/plan-de-trabajo-mvp.md`) |
| Última actualización de este archivo | 2026-09-23 |

---

## 0. Cómo usar este archivo

- **Claude:** antes de cualquier tarea lee la sección 1 (reglas críticas), la sección 2 (alcance) y la sección 4 (arquitectura). Para asientos y reportes lee la sección 10; para IVA, la sección 11; para la integración con n8n, la sección 12.
- **Si una tarea cae fuera del alcance (sección 2) o aparece en la sección 20, detente y pregunta.** No implementes funcionalidades futuras "por adelantado".
- **Marcadores usados en este documento:**
  - `[VERIFICAR]` — dato normativo o técnico externo que debe confirmarse contra la fuente oficial (MH, contador, documentación del proveedor) antes de implementarlo. **Nunca implementes un valor fiscal marcado así sin confirmarlo.**
  - `[DECISIÓN]` — decisión aún no tomada. No la asumas: pregunta.
  - `ADR-XXX` — decisión de arquitectura registrada en la sección 18.
- Si una tarea contradice este archivo, detente y pregunta antes de continuar.
- Toda decisión nueva se registra en la sección 18 y se actualiza este archivo **en el mismo PR**.

### 0.1 Flujo de trabajo esperado de Claude en este repositorio

1. Entender la tarea e identificar el módulo afectado (sección 4.2) y la fase del plan.
2. Si hay cambio de API, modificar primero el contrato en `api-spec/` (ADR-003).
3. Si hay cambio de datos, crear una nueva migración Flyway (nunca editar las existentes).
4. Escribir o actualizar las pruebas (casos dorados para todo cálculo contable o de IVA).
5. Implementar respetando la arquitectura hexagonal y los límites entre módulos.
6. Comentar todo el código (regla 1.1.1 y sección 8.2).
7. Ejecutar `./mvnw verify` (backend) y/o `pnpm lint && pnpm test` (frontend) antes de dar la tarea por terminada.
8. Actualizar este archivo si cambió alguna decisión, comando o convención.

### 0.2 Definición de terminado (Definition of Done)

- [ ] El código compila y pasa formato (Spotless / Prettier) y lint.
- [ ] Todas las pruebas pasan, incluidas las de arquitectura (Spring Modulith / ArchUnit).
- [ ] Cobertura del dominio ≥ 80 % en el módulo tocado.
- [ ] Todo el código nuevo está comentado (clases, métodos, componentes y bloques lógicos).
- [ ] El contrato OpenAPI está actualizado y validado con Spectral.
- [ ] Las migraciones Flyway son nuevas y revisadas.
- [ ] Los cambios con efecto contable o de IVA tienen pruebas con casos dorados.
- [ ] No hay secretos en el diff (gitleaks en verde).
- [ ] CLAUDE.md y ADR actualizados si aplica.

---

## 1. Reglas críticas (no negociables)

### 1.1 SIEMPRE

1. **Comentar todo el código.** Javadoc/JSDoc/TSDoc en cada clase, método público, componente y hook, y comentarios en línea que expliquen cada bloque lógico (qué hace y por qué). Es un requisito del equipo, no opcional (sección 8.2).
2. Usar `BigDecimal` (Java), `NUMERIC` (PostgreSQL) y `decimal.js` (frontend) para dinero y tasas. Redondeo `RoundingMode.HALF_UP` a 2 decimales en montos contables.
3. Filtrar por `empresa_id` en toda operación de datos y mantener Row-Level Security activo en todas las tablas de negocio (sección 4.5).
4. Exigir el header `Idempotency-Key` en todo endpoint que cree asientos, reversiones o documentos entrantes de n8n.
5. **Validar la partida doble en el frontend y en el backend**, y tener el trigger de base de datos como última defensa (sección 10.1).
6. **Mayorizar en la misma transacción** en que se guarda el asiento: o se guardan asiento y saldos, o no se guarda nada (ADR-018).
7. Crear cambios de esquema solo como migraciones Flyway nuevas: `V<numero>__<descripcion>.sql`.
8. Escribir pruebas junto con el código: unitarias para el dominio y de integración con Testcontainers para repositorios, API y el webhook de n8n.
9. Modificar primero el contrato (`api-spec/`) y después el código (contract-first, ADR-003).
10. Guardar fechas-hora en UTC (`TIMESTAMPTZ`). Las fechas contables (`asiento.fecha`) son `DATE` en hora de El Salvador.
11. Registrar auditoría (quién, qué, cuándo, valor anterior y nuevo) en toda mutación de datos de negocio.
12. Propagar `traceId` y `empresaId` en los logs, y enmascarar datos personales (DUI, NIT, correo, teléfono).
13. Leer la tasa de IVA desde la tabla `tasa_impuesto` con vigencia (desde/hasta); nunca como constante en código.
14. Validar todo documento entrante de n8n contra su esquema JSON versionado **antes** de interpretarlo.

### 1.2 NUNCA

1. Poner lógica contable o de cálculo de IVA en n8n, en el frontend o en integraciones externas. Vive solo en el núcleo Java (ADR-006). El frontend puede mostrar vistas previas calculadas **por el backend**.
2. Usar `double`, `float` o `number` de JavaScript para sumar o comparar dinero.
3. Modificar o borrar un asiento guardado ni sus líneas. Se corrige únicamente con un asiento de reversión (ADR-019).
4. Permitir que una integración envíe asientos o cuentas contables. n8n envía **documentos**; Pilot decide el asiento mediante reglas de contabilización (ADR-020).
5. Guardar un asiento descuadrado, con menos de dos líneas o con líneas que tengan Debe y Haber a la vez.
6. Contabilizar en cuentas que no aceptan movimientos (cuentas padre) o inactivas.
7. Commitear secretos: `.env`, certificados (`.crt`, `.p12`, `.pem`, `.key`), contraseñas, tokens o API keys.
8. Acceder a repositorios o tablas de otro módulo. Los módulos se comunican por su API pública (ADR-001).
9. Editar migraciones Flyway ya aplicadas.
10. Guardar tokens de acceso en `localStorage` en el frontend.
11. Conectar la aplicación a PostgreSQL con un usuario dueño de las tablas, superusuario o con `BYPASSRLS`.
12. Inventar tasas, plazos o códigos fiscales. Si no están confirmados, marcar `[VERIFICAR]` y preguntar.
13. Implementar funcionalidades de la sección 20 sin un ADR que las incorpore al alcance.

---

## 2. Alcance del MVP

### 2.1 Componente A — Núcleo del ERP

- Registro e inicio de sesión de usuarios (Keycloak, OIDC con PKCE).
- Registro y gestión básica de la empresa (datos del contribuyente) y de sus usuarios (invitar, cambiar rol, desactivar).
- Un usuario puede pertenecer a varias empresas; la empresa activa se elige en la sesión.
- API keys para integraciones (n8n).
- **Registro de apps:** estructura preparada para agregar apps en el futuro (tablas `aplicacion` y `empresa_aplicacion`, lanzador en el frontend), con **una sola app activa: Contabilidad** (ADR-021).

### 2.2 Componente B — App Contabilidad

| # | Funcionalidad | Sección |
|---|---|---|
| 1 | Catálogo de cuentas (clases 1 a 5), precargado y editable | 10.2 |
| 2 | **Libro Diario:** registro de asientos con validación de partida doble en frontend y backend | 10.1 |
| 3 | **Mayorización automática en tiempo real** con saldo Deudor/Acreedor por cuenta | 10.3 |
| 4 | **Estados financieros automáticos:** Balance General y Estado de Resultados por primer dígito del código | 10.4 |
| 5 | **Reportes complementarios:** Libro Diario, Mayor/auxiliar, Balanza de Comprobación, resumen de IVA, exportación PDF/XLSX/CSV, bitácora de n8n | 10.5 |
| 6 | **IVA 13 %:** manual (línea "lleva IVA") y automático desde documentos DTE de n8n; configuración del modo de precio por defecto | 11 |
| 7 | **Webhook de n8n** con dos formatos (`DTE_MH` y `SIMPLIFICADO`), idempotencia y manejo de errores | 12 |

### 2.3 Cambios respecto a la versión anterior de CLAUDE.md

| Cambio | Motivo |
|---|---|
| El producto deja de **emitir** DTE; ahora **recibe** documentos DTE ya emitidos por otras apps vía n8n y los contabiliza | Alcance del MVP |
| Módulos `catalogo`, `inventario`, `ventas`, `compras`, `dte`, `tesoreria`, `reportes` (como módulo), `pos` y `rrhh` pasan a la sección 20 | Alcance del MVP |
| Los reportes viven dentro de `contabilidad` (lectura CQRS con `JdbcClient`) | Solo existe una app; separar un módulo `reportes` no aporta en el MVP |
| Outbox + RabbitMQ (ADR-004), AsyncAPI, Valkey (ADR-012), almacenamiento S3 (ADR-011), perfil `worker` (ADR-014), Resilience4j, ShedLock, firmador y simulador del MH quedan **diferidos** | No hay eventos externos, llamadas salientes, archivos persistidos ni tareas en segundo plano en el MVP |
| Montos contables como `NUMERIC(19,2)` en lugar de `NUMERIC(19,4)` (ADR-022) | El asiento se registra al centavo; 4 decimales permitían descuadres de fracciones de centavo invisibles en los reportes |
| `decimal.js` y Apache POI se agregan al stack | Sumas exactas en el frontend (partida doble) y exportación XLSX |
| La integración con n8n es **entrante y síncrona**: n8n envía un documento y recibe el asiento generado en la misma respuesta (ADR-017) | Retroalimentación inmediata a n8n y sin estados asíncronos en el MVP |
| El Balance General incluye la utilidad del período (ADR-016) | Sin cierre contable, la fórmula literal 1 = 2 + 3 nunca cuadraría |
| La clase 6 (cuenta liquidadora) sale del catálogo del MVP | Solo se usa en el cierre anual, que está fuera de alcance |

---

## 3. Glosario

| Término | Significado |
|---|---|
| MH | Ministerio de Hacienda de El Salvador |
| DTE | Documento Tributario Electrónico: JSON firmado y sellado por el MH |
| Código de generación | UUID en mayúsculas que identifica de forma única cada DTE |
| Número de control | Identificador correlativo del DTE: `DTE-<tipo>-<establecimiento+punto de venta>-<correlativo>` |
| Sello de recepción | Código que otorga el MH al aceptar un DTE |
| FE (01) | Factura: venta a consumidor final; precio con IVA incluido |
| CCF (03) | Comprobante de Crédito Fiscal: venta entre contribuyentes; IVA desglosado |
| NC (05) / ND (06) | Nota de Crédito / Nota de Débito: ajustes a un CCF |
| FSEE (14) | Factura de Sujeto Excluido: la emite el comprador al adquirir de no inscritos en IVA |
| NIT / NRC | Número de Identificación Tributaria / Número de Registro de Contribuyente |
| Asiento (partida) | Registro contable con fecha, concepto y al menos dos líneas que cuadran |
| Partida doble | Regla: Σ Debe = Σ Haber en cada asiento |
| Mayorización | Traslado de los movimientos del Libro Diario al saldo de cada cuenta (Libro Mayor) |
| Naturaleza de la cuenta | Deudora (aumenta con el Debe: clases 1 y 4) o acreedora (aumenta con el Haber: clases 2, 3 y 5) |
| Cuenta de detalle | Cuenta sin subcuentas; la única que acepta movimientos |
| Balanza de Comprobación | Lista de todas las cuentas con saldo inicial, movimientos y saldo final |
| IVA débito fiscal | IVA cobrado en ventas (pasivo) |
| IVA crédito fiscal | IVA pagado en compras con CCF (activo) |
| Modo de precio | `CON_IVA` (el monto incluye IVA) o `SIN_IVA` (el IVA se suma); solo cambia cómo se interpreta el monto |
| Dirección del documento | `VENTA` si la empresa es el emisor del DTE; `COMPRA` si es el receptor (o si emite una FSEE) |
| Regla de contabilización | Configuración que dice qué cuentas usar para un tipo de DTE, dirección y condición de pago |
| Tenant / empresa | Contribuyente (NIT) que usa Pilot; unidad de aislamiento de datos |
| App | Conjunto funcional que una empresa activa desde el lanzador (en el MVP solo Contabilidad) |
| Idempotencia | Garantía de que repetir una petición no duplica su efecto |

---

## 4. Arquitectura

### 4.1 Estilo arquitectónico (heredado)

- **Monolito modular** con límites verificados por Spring Modulith (ADR-001).
- **Arquitectura hexagonal** (puertos y adaptadores) dentro de cada módulo.
- **CQRS ligero:** escrituras por el modelo de dominio (JPA); reportes por consultas SQL con `JdbcClient`.
- **Un solo perfil de ejecución (`api`)** en el MVP. El perfil `worker` (ADR-014) se activará cuando existan tareas en segundo plano.
- **Transacción única por comando:** guardar asiento + mayorizar + auditar + registrar idempotencia ocurre en una sola transacción de PostgreSQL.

```text
 ┌───────────────────────────┐        ┌──────────────────────────────┐
 │ Web (React) — shell + app │        │ n8n (otras apps, facturadores)│
 │ Contabilidad              │        └───────────────┬──────────────┘
 └─────────────┬─────────────┘                        │ HTTPS + API key
               │ HTTPS + OIDC (PKCE)                  │ POST /integraciones/n8n/documentos
      ┌────────▼─────────┐                            │
      │ Traefik (TLS)     │◄───────────────────────────┘
      └────────┬─────────┘
       ┌───────▼────────────────────────────────────────────┐        ┌────────────────┐
       │ Pilot API (Spring Boot, perfil api)                 │◄──────►│ Keycloak (OIDC)│
       │  plataforma · contabilidad · integracion · compartido│        └────────────────┘
       └───────┬────────────────────────────────────────────┘
               │ pilot_app (sin BYPASSRLS)
       ┌───────▼───────┐
       │ PostgreSQL 17 │  RLS forzado en todas las tablas de negocio
       └───────────────┘
```

### 4.2 Módulos del MVP

| Módulo (paquete) | Responsabilidad | Puede depender de |
|---|---|---|
| `compartido` | Tipos base: `Dinero`, `EmpresaId`, `ModoPrecio`, errores de dominio, utilidades | — |
| `plataforma` | Usuarios, empresas, membresías y roles, API keys, registro de apps, auditoría, idempotencia, contexto de empresa | `compartido` |
| `contabilidad` | Catálogo de cuentas, configuración contable, tasas de IVA, reglas de contabilización, asientos, mayorización, estados financieros y reportes | `plataforma`, `compartido` |
| `integracion` | Webhook de n8n: autenticación por API key, validación de esquemas, adaptación de formatos `DTE_MH`/`SIMPLIFICADO`, bitácora de documentos | `contabilidad` (API pública), `plataforma`, `compartido` |

Reglas de dependencia:

- `integracion` **no** crea asientos: entrega un `DocumentoFiscal` normalizado al caso de uso público `ContabilizarDocumento` de `contabilidad`, que aplica las reglas.
- `contabilidad` nunca depende de `integracion`.
- Las dependencias cíclicas están prohibidas y se verifican con `ApplicationModules.verify()`.
- Una app futura (p. ej. `ventas`) será un módulo nuevo que usará la API pública de `contabilidad` o eventos; nunca sus tablas.

### 4.3 Capas dentro de cada módulo (hexagonal)

```text
com.bcodesphere.pilot.<modulo>
├── api/              # Adaptadores de entrada: controladores REST (interfaces generadas), DTO, mapeadores
├── aplicacion/       # Casos de uso, puertos de entrada/salida, control transaccional
├── dominio/          # Entidades, objetos de valor, reglas de negocio (partida doble, IVA, estados)
└── infraestructura/  # Adaptadores de salida: JPA, consultas JdbcClient, generación PDF/XLSX
```

- `dominio` no depende de Spring; las anotaciones JPA en entidades se toleran por pragmatismo `[DECISIÓN]` si se prefiere modelo separado.
- Los controladores nunca exponen entidades; siempre DTO.
- Solo los tipos del paquete raíz del módulo (API pública) pueden usarse desde otros módulos.

### 4.4 Estructura preparada para más apps (ADR-021)

- **Backend:** cada app es un módulo Spring Modulith con sus cuatro capas. Agregar una app = nuevo paquete + migraciones + contrato + fila en `aplicacion`.
- **Base de datos:** `aplicacion` (catálogo global de apps disponibles) y `empresa_aplicacion` (apps activas por empresa). Un filtro de seguridad rechaza con 403 `PLT-004` las peticiones a rutas de una app inactiva para la empresa.
- **Frontend:** el shell (`frontend/src/nucleo/`) carga `GET /api/v1/aplicaciones` y registra dinámicamente las rutas de cada app activa desde `frontend/src/apps/<app>/`. Ninguna app importa código de otra app.

### 4.5 Multi-empresa (multi-tenancy, heredado — ADR-002)

- Base de datos compartida, esquema compartido, columna `empresa_id` en toda tabla de negocio, reforzada con **Row-Level Security** forzado.
- La empresa activa llega en el header `X-Empresa-Id` (usuarios) o se deriva de la API key (n8n), y siempre se valida contra las membresías.
- Al iniciar cada transacción se fijan `app.empresa_id` y `app.usuario_id` en la sesión de PostgreSQL.
- Las búsquedas previas a conocer la empresa (API key por prefijo, membresías del usuario) usan funciones `SECURITY DEFINER` mínimas, propiedad de `pilot_owner`, que devuelven solo los campos necesarios.

```sql
-- Activa y fuerza Row-Level Security en una tabla de negocio
ALTER TABLE asiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE asiento FORCE ROW LEVEL SECURITY;  -- Aplica incluso al dueño de la tabla

-- Política: solo se ven y escriben filas de la empresa fijada en la sesión
CREATE POLICY aislamiento_empresa ON asiento
    USING (empresa_id = current_setting('app.empresa_id')::uuid)        -- Filtro de lectura
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);  -- Filtro de escritura
```

```java
/**
 * Transaction manager que fija la empresa y el usuario activos en la sesión de PostgreSQL
 * al iniciar cada transacción, para que Row-Level Security filtre los datos.
 */
public class TenantAwareTransactionManager extends JpaTransactionManager {

    /**
     * Abre la transacción y luego establece app.empresa_id y app.usuario_id solo para esa transacción.
     */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        // 1. Abre la transacción con el comportamiento estándar de Spring
        super.doBegin(transaction, definition);

        // 2. Obtiene empresa y usuario del contexto de la petición; lanza error si falta la empresa
        UUID empresaId = TenantContext.empresaRequerida();
        String usuarioId = TenantContext.usuarioOSistema();

        // 3. Recupera el EntityManager ligado a la transacción recién abierta
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(getEntityManagerFactory());

        // 4. set_config(..., true) limita los valores a esta transacción (equivale a SET LOCAL)
        em.createNativeQuery("SELECT set_config('app.empresa_id', :e, true), set_config('app.usuario_id', :u, true)")
                .setParameter("e", empresaId.toString())
                .setParameter("u", usuarioId)
                .getSingleResult();
    }
}
```

---

## 5. Stack tecnológico

> Fijar versiones exactas en `pom.xml`, `package.json` y `compose` al iniciar la fase F0. Revisar parches de seguridad mensualmente.

### 5.1 Backend (se mantiene)

| Componente | Elección | Uso en el MVP |
|---|---|---|
| Lenguaje | Java 25 LTS (mínimo Java 21) | — |
| Framework | Spring Boot 4.1.x (Spring Framework 7) | — |
| Modularidad | Spring Modulith | Límites de módulos |
| Build | Maven (wrapper `./mvnw`) | — |
| Persistencia | Spring Data JPA (Hibernate 7) | Escrituras de dominio |
| Consultas de reportes | `JdbcClient` / SQL nativo | Mayor, balanza, estados, IVA |
| Migraciones | Flyway | `src/main/resources/db/migration` |
| Seguridad | Spring Security (Resource Server OAuth2/JWT) + filtro de API key | Usuarios (Keycloak) y n8n |
| Hash de API keys | Argon2id (Spring Security Crypto) | — |
| Serialización | Jackson 3 (`tools.jackson.*`) | Montos como cadena decimal (ADR-013) |
| Mapeo DTO | MapStruct | Sin lógica de negocio |
| Validación | Jakarta Bean Validation | Forma de los DTO |
| Validación JSON Schema | `networknt/json-schema-validator` | Esquemas `DTE_MH` y `SIMPLIFICADO` |
| PDF | Thymeleaf + OpenHTMLtoPDF | Reportes y estados financieros |
| XLSX | **Apache POI (SXSSF)** — nuevo | Exportación de reportes |
| Límite de peticiones | Bucket4j en memoria | Webhook de n8n por API key |
| Documentación API | OpenAPI 3.1 (contract-first) + `openapi-generator-maven-plugin` | — |
| Pruebas | JUnit Jupiter, AssertJ, Testcontainers, ArchUnit, Spring Modulith Test | — |
| Calidad | Spotless (palantir-java-format), Checkstyle, SpotBugs | CI |

### 5.2 Frontend web (se mantiene)

| Componente | Elección |
|---|---|
| Base | React 19 + TypeScript (modo `strict`) + Vite |
| Datos remotos | TanStack Query |
| Rutas | React Router (rutas por app registradas por el shell) |
| Formularios | React Hook Form + Zod |
| Dinero | **`decimal.js`** — nuevo; toda suma o comparación de montos |
| UI | shadcn/ui + Tailwind CSS |
| Cliente API | Generado desde OpenAPI con Orval (nunca escrito a mano) |
| Autenticación | OIDC con PKCE contra Keycloak (`oidc-client-ts`); tokens en memoria |
| i18n | `es-SV`; formato `$1,234.56` |
| Pruebas | Vitest + Testing Library; Playwright para end-to-end |
| Gestor de paquetes | pnpm |

### 5.3 Infraestructura

| Componente | Elección | Estado en el MVP |
|---|---|---|
| Base de datos | PostgreSQL 17+ (RLS, JSONB) | Activo |
| Identidad | Keycloak 26.x (autorregistro, MFA) | Activo |
| Orquestación de integraciones | n8n autoalojado | Activo (externo; solo llama al webhook) |
| Proxy inverso | Traefik con Let's Encrypt | Activo |
| Contenedores | Docker + Docker Compose | Activo |
| Correo de desarrollo | Mailpit (correos de Keycloak) | Activo |
| Observabilidad | OpenTelemetry, Prometheus, Grafana, Loki | Activo (base) |
| CI/CD | GitHub Actions + GitHub Container Registry | Activo |
| Respaldos | WAL-G | Activo en producción |
| RabbitMQ, Valkey, almacenamiento S3, firmador MH, WireMock MH | — | **Diferidos** (sección 20) |

---

## 6. Estructura del repositorio

```text
pilot/
├── CLAUDE.md                          # Este archivo (alcance MVP)
├── README.md                          # Presentación y arranque rápido
├── .env.example                       # Variables de entorno de ejemplo (sin secretos)
├── api-spec/
│   ├── openapi/pilot-v1.yaml          # Contrato REST, incluido el webhook de n8n (fuente de verdad)
│   ├── esquemas/
│   │   ├── dte-mh/<tipo>/v<n>.json     # Esquemas oficiales del MH de DTE recibidos (01, 03, 05, 06, 14)
│   │   └── simplificado/v1.json       # Esquema del formato SIMPLIFICADO de Pilot
│   └── .spectral.yaml                 # Reglas de lint del contrato
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/bcodesphere/pilot/
│       │   ├── PilotApplication.java
│       │   ├── compartido/
│       │   ├── plataforma/            # api / aplicacion / dominio / infraestructura
│       │   ├── contabilidad/          # api / aplicacion / dominio / infraestructura
│       │   │   └── dominio/           # catalogo/, asiento/, iva/, estados/, reglas/
│       │   └── integracion/           # api / aplicacion / dominio / infraestructura
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── application-dev.yml
│       │   ├── db/migration/          # Migraciones Flyway
│       │   └── plantillas/pdf/        # Plantillas Thymeleaf de reportes
│       └── test/
│           ├── java/...               # Pruebas por módulo
│           └── resources/casos/       # Casos dorados: asientos, IVA, documentos n8n, estados
├── frontend/
│   └── src/
│       ├── nucleo/                    # Shell: auth OIDC, selector de empresa, lanzador y registro de apps, layout
│       ├── apps/contabilidad/         # Única app activa: páginas, componentes, hooks, esquemas Zod
│       │   ├── catalogo/
│       │   ├── libro-diario/
│       │   ├── mayor/
│       │   ├── reportes/              # Balanza, estados financieros, resumen IVA
│       │   ├── documentos-n8n/        # Bitácora de documentos recibidos
│       │   └── configuracion/         # Modo de precio, cuentas de IVA, reglas de contabilización
│       ├── compartido/                # Componentes UI, formato de moneda, utilidades decimal.js
│       └── api/                       # Cliente generado por Orval (no editar)
├── integraciones/
│   └── plantillas-n8n/                # Flujos de ejemplo que llaman al webhook (JSON exportado)
├── infra/
│   ├── docker/compose.dev.yml
│   ├── docker/compose.prod.yml
│   ├── keycloak/                      # Realm "pilot" versionado
│   ├── traefik/
│   └── observabilidad/
├── docs/
│   ├── plan-de-trabajo-mvp.md         # Plan por fases con criterios de aceptación
│   ├── adr/                           # ADR-XXX-<titulo>.md
│   ├── contabilidad/                  # Catálogo base, reglas por defecto y validaciones del contador
│   ├── runbooks/                      # Restauración, rotación de API keys, reproceso n8n
│   └── vision/                        # Visión completa del producto (referencia, fuera del MVP)
└── .github/workflows/                 # CI/CD
```

---

## 7. Comandos

```bash
# --- Infraestructura local ---
docker compose -f infra/docker/compose.dev.yml up -d      # Levanta PostgreSQL, Keycloak, n8n y Mailpit
docker compose -f infra/docker/compose.dev.yml down       # Detiene dependencias

# --- Backend ---
cd backend
./mvnw generate-sources                                   # Genera interfaces desde OpenAPI
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,api # Ejecuta la API en local
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
```

`[DECISIÓN]` Agregar un `Makefile` o `justfile` que envuelva estos comandos.

---

## 8. Convenciones de código

### 8.1 Idioma y nombres (heredado)

- **Dominio en español** sin tildes ni `ñ` en identificadores (`asientoContable`, `anio`); sufijos técnicos en inglés estándar (`Controller`, `Service`, `Repository`, `Dto`, `Mapper`).
- Términos fiscales oficiales se conservan: `codigoGeneracion`, `numeroControl`, `nit`, `nrc`, `tipoDte`.
- Tablas y columnas en `snake_case` singular (`asiento_linea`, `empresa_id`).
- Claves primarias `UUID` versión 7 generadas en la aplicación (ADR-010).
- Endpoints REST en plural y `kebab-case` (`/api/v1/contabilidad/reglas-contabilizacion`).
- JSON de la API en `camelCase`; montos como **cadena decimal** (`"123.45"`) (ADR-013); fechas ISO-8601.

### 8.2 Comentarios (obligatorio en cada parte del código)

| Dónde | Qué se exige |
|---|---|
| Java | Javadoc en toda clase, record, enum, interfaz y método público; comentario en línea numerado por cada bloque lógico (qué y por qué) |
| TypeScript/React | TSDoc en todo componente, hook, función exportada y esquema Zod; comentarios en bloques de lógica y efectos |
| SQL (Flyway) | Comentario por tabla, columna no obvia, índice, política RLS, trigger y permiso (`COMMENT ON` o `--`) |
| OpenAPI | `description` en cada operación, parámetro, esquema y código de error |
| Pruebas | Nombre descriptivo en español + comentario con el caso de negocio y la fuente del valor esperado |
| Configuración (YAML, compose, flujos n8n) | Comentario por bloque explicando su propósito |

- Comentar **el porqué** de las reglas contables y fiscales, citando la sección de este archivo, el ADR o la norma.
- Ejemplo Java:

```java
/**
 * Valida que un asiento cumpla la partida doble antes de guardarlo.
 * Es la validación de dominio; el frontend la replica y la base de datos la verifica con un trigger (sección 10.1).
 *
 * @param lineas líneas ya expandidas (incluidas las de IVA calculado)
 * @throws AsientoInvalidoException con el código CON-00X correspondiente
 */
static void validarPartidaDoble(List<LineaAsiento> lineas) {
    // 1. Un asiento necesita al menos una cuenta que se debite y otra que se acredite
    if (lineas.size() < 2) {
        throw new AsientoInvalidoException("CON-001", "El asiento debe tener al menos dos líneas");
    }

    // 2. Cada línea lleva solo Debe o solo Haber, nunca ambos ni ninguno
    lineas.forEach(LineaAsiento::validarUnSoloLado);

    // 3. Suma ambos lados con BigDecimal para evitar errores de redondeo
    BigDecimal debe = lineas.stream().map(LineaAsiento::debe).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal haber = lineas.stream().map(LineaAsiento::haber).reduce(BigDecimal.ZERO, BigDecimal::add);

    // 4. Un asiento en cero no representa ninguna operación
    if (debe.signum() == 0) {
        throw new AsientoInvalidoException("CON-004", "Los totales del asiento deben ser mayores que cero");
    }

    // 5. compareTo ignora la escala (100.0 == 100.00); si no cuadra se informa la diferencia exacta
    if (debe.compareTo(haber) != 0) {
        throw new AsientoDescuadradoException("CON-005", debe.subtract(haber));
    }
}
```

### 8.3 Estilo y diseño (heredado)

- DTO y objetos de valor como `record` de Java.
- Inyección de dependencias por constructor; campos `final`.
- Métodos de máximo ~40 líneas; clases con una sola responsabilidad.
- Excepciones de dominio específicas (`AsientoDescuadradoException`, `CuentaNoImputableException`) traducidas a Problem Details en la capa `api`.
- Concurrencia optimista con columna `version` en entidades editables (cuentas, configuración, reglas); `If-Match`/`ETag` en la API.
- Nada de lógica en controladores: validación de entrada, llamada al caso de uso y mapeo de salida.
- Sin `Optional` en parámetros ni campos; sí como retorno. Sin `null` en colecciones devueltas.

### 8.4 Errores de la API

- Formato **RFC 9457 Problem Details** (`application/problem+json`).
- Campo `codigo` con prefijo por módulo: `PLT-` (plataforma), `CON-` (contabilidad), `INT-` (integración).
- Los errores de validación incluyen la lista `errores` con `campo` y `mensaje`; los descuadres incluyen `diferencia`.
- Nunca exponer trazas de pila ni mensajes internos.

### 8.5 Git (heredado)

- Trunk-based: ramas cortas desde `main` (`feat/`, `fix/`, `chore/`, `docs/`, `refactor/`).
- Conventional Commits en español: `feat(contabilidad): agrega reversión de asientos`.
- Todo cambio entra por PR con CI en verde y al menos una revisión.
- Versionado semántico con etiquetas `vX.Y.Z` y `CHANGELOG.md`.

---

## 9. Modelo de datos

### 9.1 Resumen por módulo

| Módulo | Tablas | RLS |
|---|---|---|
| `plataforma` | `usuario` (global), `empresa`, `empresa_usuario`, `api_key`, `aplicacion` (global), `empresa_aplicacion`, `auditoria`, `idempotencia` | Sí, salvo tablas globales |
| `contabilidad` | `tasa_impuesto` (global), `plantilla_cuenta` (global), `cuenta_contable`, `configuracion_contable`, `regla_contabilizacion`, `correlativo_asiento`, `asiento`, `asiento_linea`, `saldo_cuenta_mensual` | Sí, salvo tablas globales |
| `integracion` | `documento_externo`, `intento_documento_externo` | Sí |

Columnas comunes en toda tabla de negocio editable: `id UUID`, `empresa_id UUID`, `creado_en`, `creado_por`, `actualizado_en`, `actualizado_por`, `version BIGINT`. Las tablas globales son de solo lectura para `pilot_app` y se cargan por migración.

### 9.2 Núcleo (plataforma)

```sql
-- USUARIO: persona que inicia sesión; su identidad vive en Keycloak (tabla global, sin empresa)
CREATE TABLE usuario (
    id            UUID PRIMARY KEY,
    sub_keycloak  VARCHAR(64) NOT NULL UNIQUE,     -- Claim "sub" del token OIDC
    correo        VARCHAR(254) NOT NULL,           -- Dato personal: se enmascara en logs
    nombre        VARCHAR(200) NOT NULL,
    estado        VARCHAR(15) NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO, BLOQUEADO
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- EMPRESA: contribuyente que usa Pilot; unidad de aislamiento (tenant)
CREATE TABLE empresa (
    id               UUID PRIMARY KEY,
    nit              VARCHAR(14) NOT NULL UNIQUE,  -- Solo dígitos [VERIFICAR] longitud vigente (NIT o DUI homologado)
    nrc              VARCHAR(10),                  -- Registro de IVA; nulo si no es contribuyente
    nombre           VARCHAR(250) NOT NULL,        -- Razón social
    nombre_comercial VARCHAR(250),
    gran_contribuyente BOOLEAN NOT NULL DEFAULT false, -- Informativo; afecta la lectura de retenciones y percepciones
    estado           VARCHAR(15) NOT NULL DEFAULT 'ACTIVA',
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0
);

-- MEMBRESÍA: qué usuario pertenece a qué empresa y con qué rol
CREATE TABLE empresa_usuario (
    empresa_id  UUID NOT NULL REFERENCES empresa(id),
    usuario_id  UUID NOT NULL REFERENCES usuario(id),
    rol         VARCHAR(30) NOT NULL,              -- admin_empresa, contador, auditor (sección 14.2)
    estado      VARCHAR(15) NOT NULL DEFAULT 'ACTIVA',  -- INVITADA, ACTIVA, INACTIVA
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, usuario_id)
);

-- API KEY: credencial de integraciones (n8n); el secreto nunca se guarda en claro
CREATE TABLE api_key (
    id            UUID PRIMARY KEY,
    empresa_id    UUID NOT NULL REFERENCES empresa(id),
    nombre        VARCHAR(100) NOT NULL,           -- Ej.: "n8n producción"
    prefijo       VARCHAR(16) NOT NULL UNIQUE,     -- Parte visible para identificarla (pk_xxxx)
    hash_secreto  VARCHAR(200) NOT NULL,           -- Argon2id del secreto
    alcances      TEXT[] NOT NULL,                 -- Ej.: {integracion:documentos}
    expira_en     TIMESTAMPTZ,
    revocada_en   TIMESTAMPTZ,
    ultimo_uso_en TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- APPS: catálogo global de apps del ERP (en el MVP solo 'contabilidad')
CREATE TABLE aplicacion (
    codigo      VARCHAR(40) PRIMARY KEY,           -- Identificador estable, también prefijo de rutas
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(300),
    disponible  BOOLEAN NOT NULL DEFAULT true      -- Si puede activarse en alguna empresa
);

-- APPS ACTIVAS POR EMPRESA
CREATE TABLE empresa_aplicacion (
    empresa_id         UUID NOT NULL REFERENCES empresa(id),
    aplicacion_codigo  VARCHAR(40) NOT NULL REFERENCES aplicacion(codigo),
    activa             BOOLEAN NOT NULL DEFAULT true,
    activada_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, aplicacion_codigo)
);
```

`auditoria` (insert-only, particionada por mes, retención 10 años) e `idempotencia` (clave, hash SHA-256 del cuerpo, respuesta original, retención 7 días) se mantienen como en la versión anterior (ver `docs/vision/`).

### 9.3 Contabilidad

```sql
-- TASA DE IMPUESTO: tasas con vigencia; global, solo lectura para la aplicación
CREATE TABLE tasa_impuesto (
    id              UUID PRIMARY KEY,
    tipo            VARCHAR(20) NOT NULL,          -- 'IVA'
    tasa            NUMERIC(7,4) NOT NULL,         -- 0.1300 = 13 %
    vigente_desde   DATE NOT NULL,
    vigente_hasta   DATE,                          -- NULL = vigente sin fecha de fin
    EXCLUDE USING gist (tipo WITH =, daterange(vigente_desde, vigente_hasta, '[]') WITH &&)  -- Sin traslapes (requiere btree_gist)
);

-- CUENTA CONTABLE: catálogo por empresa; la clase es el primer dígito del código
CREATE TABLE cuenta_contable (
    id                 UUID PRIMARY KEY,
    empresa_id         UUID NOT NULL,
    codigo             VARCHAR(20) NOT NULL CHECK (codigo ~ '^[1-5][0-9]*$'),  -- Solo clases 1 a 5 en el MVP
    nombre             VARCHAR(200) NOT NULL,
    clase              SMALLINT GENERATED ALWAYS AS (substr(codigo, 1, 1)::smallint) STORED,
    nivel              SMALLINT NOT NULL,          -- 1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5+ detalle
    cuenta_padre_id    UUID REFERENCES cuenta_contable(id),
    naturaleza         VARCHAR(9) NOT NULL,        -- DEUDORA o ACREEDORA (por defecto según la clase)
    acepta_movimientos BOOLEAN NOT NULL,           -- true solo en cuentas de detalle (sin hijas)
    activa             BOOLEAN NOT NULL DEFAULT true,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    UNIQUE (empresa_id, codigo)
);

-- CONFIGURACIÓN CONTABLE: una fila por empresa (sección 11.3)
CREATE TABLE configuracion_contable (
    empresa_id                      UUID PRIMARY KEY,
    modo_precio_defecto             VARCHAR(7) NOT NULL DEFAULT 'CON_IVA',  -- CON_IVA o SIN_IVA
    cuenta_iva_debito_id            UUID NOT NULL,  -- IVA débito fiscal (pasivo)
    cuenta_iva_credito_id           UUID NOT NULL,  -- IVA crédito fiscal (activo)
    cuenta_iva_retenido_favor_id    UUID,           -- Nos retuvieron IVA (activo)
    cuenta_iva_retenido_pagar_id    UUID,           -- Retuvimos IVA como agente (pasivo)
    cuenta_iva_percibido_favor_id   UUID,           -- Nos percibieron IVA (activo)
    cuenta_iva_percibido_pagar_id   UUID,           -- Percibimos IVA como agente (pasivo)
    cuenta_renta_retenida_favor_id  UUID,           -- Nos retuvieron renta (activo)
    cuenta_renta_retenida_pagar_id  UUID,           -- Retuvimos renta, p. ej. en FSEE (pasivo)
    actualizado_en                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                         BIGINT NOT NULL DEFAULT 0
);

-- REGLA DE CONTABILIZACIÓN: cuentas para documentos de n8n por tipo, dirección y condición (ADR-020)
CREATE TABLE regla_contabilizacion (
    id                       UUID PRIMARY KEY,
    empresa_id               UUID NOT NULL,
    tipo_dte                 CHAR(2) NOT NULL,     -- 01, 03, 05, 06, 14
    direccion                VARCHAR(6) NOT NULL,  -- VENTA o COMPRA
    condicion                VARCHAR(7) NOT NULL,  -- CONTADO, CREDITO u OTRO
    cuenta_base_id           UUID NOT NULL,        -- Ventas, compras/gasto, devoluciones…
    cuenta_contrapartida_id  UUID NOT NULL,        -- Caja/bancos, cuentas por cobrar o por pagar
    activa                   BOOLEAN NOT NULL DEFAULT true,
    version                  BIGINT NOT NULL DEFAULT 0,
    UNIQUE (empresa_id, tipo_dte, direccion, condicion)
);

-- CORRELATIVO: numeración de asientos por empresa y año, sin duplicados entre transacciones
CREATE TABLE correlativo_asiento (
    empresa_id  UUID NOT NULL,
    anio        SMALLINT NOT NULL,
    ultimo      BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (empresa_id, anio)
);

-- ASIENTO: cabecera de la partida; inmutable salvo el paso a REVERTIDO (ADR-019)
CREATE TABLE asiento (
    id                    UUID PRIMARY KEY,
    empresa_id            UUID NOT NULL,
    anio                  SMALLINT NOT NULL,         -- Año de la fecha contable (numeración)
    numero                BIGINT NOT NULL,           -- Correlativo por empresa y año
    fecha                 DATE NOT NULL,             -- Fecha contable (hora de El Salvador)
    concepto              VARCHAR(500) NOT NULL,
    estado                VARCHAR(12) NOT NULL,      -- CONTABILIZADO o REVERTIDO
    origen_tipo           VARCHAR(12) NOT NULL,      -- MANUAL, N8N o REVERSION
    origen_id             UUID,                      -- documento_externo.id si viene de n8n
    modo_precio           VARCHAR(7),                -- Modo usado para expandir líneas con IVA (manual)
    asiento_revertido_id  UUID REFERENCES asiento(id), -- En una reversión: el asiento que revierte
    asiento_reversion_id  UUID REFERENCES asiento(id), -- En un asiento revertido: su reversión
    total_debe            NUMERIC(19,2) NOT NULL,
    total_haber           NUMERIC(19,2) NOT NULL,
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por            UUID NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    UNIQUE (empresa_id, anio, numero),
    CHECK (total_debe = total_haber AND total_debe > 0)  -- Partida doble a nivel de cabecera
);

-- Un documento de n8n solo puede tener un asiento vigente (idempotencia contable)
CREATE UNIQUE INDEX uq_asiento_documento_vigente ON asiento (empresa_id, origen_id)
    WHERE origen_tipo = 'N8N' AND estado = 'CONTABILIZADO';

-- LÍNEA DE ASIENTO: cada línea es Debe o Haber, nunca ambos
CREATE TABLE asiento_linea (
    id             UUID PRIMARY KEY,
    empresa_id     UUID NOT NULL,
    asiento_id     UUID NOT NULL REFERENCES asiento(id),
    numero_linea   SMALLINT NOT NULL,
    fecha          DATE NOT NULL,                   -- Copia de asiento.fecha para consultas del Mayor
    cuenta_id      UUID NOT NULL REFERENCES cuenta_contable(id),
    descripcion    VARCHAR(300),
    debe           NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (debe >= 0),
    haber          NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (haber >= 0),
    origen_linea   VARCHAR(14) NOT NULL,            -- USUARIO, IVA_CALCULADO o DOCUMENTO
    linea_base_id  UUID REFERENCES asiento_linea(id), -- En una línea de IVA: la línea que la originó
    CHECK ((debe = 0) <> (haber = 0)),              -- Exactamente uno de los dos es mayor que cero
    UNIQUE (asiento_id, numero_linea)
);

-- Índice del Libro Mayor: movimientos de una cuenta en un rango de fechas
CREATE INDEX idx_linea_mayor ON asiento_linea (empresa_id, cuenta_id, fecha);

-- SALDOS: mayorización en tiempo real, un acumulado por cuenta de detalle y mes (ADR-018)
CREATE TABLE saldo_cuenta_mensual (
    empresa_id     UUID NOT NULL,
    cuenta_id      UUID NOT NULL,
    anio           SMALLINT NOT NULL,
    mes            SMALLINT NOT NULL CHECK (mes BETWEEN 1 AND 12),
    total_debe     NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_haber    NUMERIC(19,2) NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, cuenta_id, anio, mes)
);
```

Defensas en base de datos:

- **Trigger diferido de partida doble** (`validar_partida_doble`, heredado de la versión anterior): al `COMMIT` verifica Σ Debe = Σ Haber y al menos 2 líneas por asiento.
- **Permisos de inmutabilidad:** `pilot_app` tiene solo `SELECT, INSERT` sobre `asiento_linea`; sobre `asiento` solo `SELECT, INSERT, UPDATE (estado, asiento_reversion_id, version)`; ningún `DELETE`.
- `saldo_cuenta_mensual` solo se modifica con el upsert de la sección 10.3.

### 9.4 Integración

```sql
-- DOCUMENTO EXTERNO: documento recibido de n8n y contabilizado
CREATE TABLE documento_externo (
    id                   UUID PRIMARY KEY,
    empresa_id           UUID NOT NULL,
    formato              VARCHAR(12) NOT NULL,     -- DTE_MH o SIMPLIFICADO
    sistema_origen       VARCHAR(50) NOT NULL,     -- Ej.: 'facturador-x', 'pos-legado'
    clave_documento      VARCHAR(250) NOT NULL,    -- codigoGeneracion en mayúsculas, o sistemaOrigen:idExterno
    tipo_dte             CHAR(2) NOT NULL,
    direccion            VARCHAR(6) NOT NULL,      -- VENTA o COMPRA
    condicion            VARCHAR(7) NOT NULL,      -- CONTADO, CREDITO u OTRO
    fecha_emision        DATE NOT NULL,
    numero_control       VARCHAR(31),
    nit_contraparte      VARCHAR(20),              -- Dato personal si es DUI: se enmascara en logs
    nombre_contraparte   VARCHAR(250),
    base_gravada         NUMERIC(19,2) NOT NULL,   -- Montos ya normalizados (sección 12.5)
    exento               NUMERIC(19,2) NOT NULL,
    no_sujeto            NUMERIC(19,2) NOT NULL,
    no_gravado           NUMERIC(19,2) NOT NULL,
    iva                  NUMERIC(19,2) NOT NULL,
    iva_retenido         NUMERIC(19,2) NOT NULL,
    iva_percibido        NUMERIC(19,2) NOT NULL,
    renta_retenida       NUMERIC(19,2) NOT NULL,
    total_pagar          NUMERIC(19,2) NOT NULL,
    modo_precio_aplicado VARCHAR(7),               -- Solo SIMPLIFICADO: modo con que se interpretó
    payload              JSONB NOT NULL,           -- Documento original tal como llegó
    estado               VARCHAR(13) NOT NULL,     -- CONTABILIZADO o REVERTIDO
    asiento_id           UUID NOT NULL,            -- Asiento vigente
    recibido_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,
    UNIQUE (empresa_id, clave_documento)           -- El mismo documento nunca entra dos veces
);

-- BITÁCORA DE RECHAZOS: intentos fallidos, para diagnóstico desde la app (retención 90 días)
CREATE TABLE intento_documento_externo (
    id               UUID PRIMARY KEY,
    empresa_id       UUID NOT NULL,
    clave_documento  VARCHAR(250),                 -- Puede ser nula si el cuerpo no se pudo leer
    idempotency_key  VARCHAR(100),
    codigo_error     VARCHAR(10) NOT NULL,         -- INT-00X o CON-0XX
    detalle          JSONB NOT NULL,               -- Problem Details devuelto (sin datos personales)
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 10. Reglas de negocio contables

### 10.1 Libro Diario — registro de asientos

**Formulario:** Fecha, Concepto (cabecera) y líneas con Código/Cuenta, Descripción opcional, Debe, Haber y casilla "lleva IVA" (sección 11.2).

| Código | Validación | Frontend | Backend | Base de datos |
|---|---|---|---|---|
| CON-001 | Mínimo 2 líneas | ✔ | ✔ | Trigger |
| CON-002 | Cada línea tiene solo Debe o solo Haber (no ambos, no ninguno) | ✔ | ✔ | `CHECK` |
| CON-003 | Montos no negativos, con máximo 2 decimales | ✔ | ✔ | `CHECK` + `NUMERIC(19,2)` |
| CON-004 | Totales mayores que cero | ✔ | ✔ | `CHECK` |
| CON-005 | Σ Debe = Σ Haber (se informa la diferencia) | ✔ bloquea Guardar | ✔ | Trigger + `CHECK` |
| CON-006 | Cuenta existente, activa, de la empresa y de detalle | ✔ (buscador filtrado) | ✔ | FK + RLS |
| CON-007 | Fecha obligatoria y no futura `[DECISIÓN]` confirmar si se permiten fechas futuras | ✔ | ✔ | — |
| CON-013 | "Lleva IVA" no se permite sobre cuentas de IVA | ✔ | ✔ | — |
| — | Concepto obligatorio (máx. 500), máximo 200 líneas | ✔ | ✔ | Longitud |

- **Validación sobre las líneas expandidas:** cuando alguna línea "lleva IVA", el frontend pide `POST /contabilidad/asientos/vista-previa` (con retardo de 300 ms) y valida la partida doble sobre las líneas que devuelve el backend, porque en modo `SIN_IVA` la expansión cambia los totales. Sin líneas con IVA, valida localmente con `decimal.js`.
- **Guardado** (`RegistrarAsientoManual`, una transacción): expandir IVA → validar → asignar número (`UPDATE correlativo_asiento … RETURNING`) → insertar cabecera y líneas → mayorizar (10.3) → auditar → guardar respuesta de idempotencia.
- **Reversión** (`RevertirAsiento`): crea un asiento `origen_tipo = REVERSION` con Debe y Haber intercambiados, fecha elegida por el usuario (por defecto hoy) y concepto "Reversión del asiento N.º …"; marca el original `REVERTIDO`. Un asiento revertido o una reversión no pueden revertirse (`CON-008`, `CON-009`). Si el original venía de n8n, el documento pasa a `REVERTIDO` y puede reprocesarse (12.8).

```ts
import Decimal from 'decimal.js';
import { z } from 'zod';

/** Monto como cadena decimal con máximo 2 decimales (ADR-013); nunca number de JavaScript. */
const montoSchema = z.string().regex(/^\d{1,17}(\.\d{1,2})?$/, 'Monto inválido');

/**
 * Esquema Zod del formulario del Libro Diario.
 * Replica en el frontend las validaciones CON-001 a CON-005; el backend las vuelve a aplicar.
 */
export const asientoSchema = z
  .object({
    fecha: z.string().date(),
    concepto: z.string().min(1).max(500),
    lineas: z.array(
      z.object({
        cuentaId: z.string().uuid(),
        descripcion: z.string().max(300).optional(),
        debe: montoSchema,
        haber: montoSchema,
        llevaIva: z.boolean(),
      }),
    ),
  })
  .superRefine((asiento, ctx) => {
    // 1. CON-001: al menos dos líneas
    if (asiento.lineas.length < 2) {
      ctx.addIssue({ code: 'custom', path: ['lineas'], message: 'El asiento debe tener al menos dos líneas' });
    }

    // 2. CON-002: cada línea lleva solo Debe o solo Haber
    asiento.lineas.forEach((l, i) => {
      if (new Decimal(l.debe).isZero() === new Decimal(l.haber).isZero()) {
        ctx.addIssue({ code: 'custom', path: ['lineas', i, 'debe'], message: 'Use solo Debe o solo Haber' });
      }
    });

    // 3. CON-004 y CON-005: suma exacta con decimal.js y comparación de ambos lados
    const debe = asiento.lineas.reduce((s, l) => s.plus(l.debe), new Decimal(0));
    const haber = asiento.lineas.reduce((s, l) => s.plus(l.haber), new Decimal(0));
    if (debe.isZero()) {
      ctx.addIssue({ code: 'custom', path: ['lineas'], message: 'Los totales deben ser mayores que cero' });
    } else if (!debe.equals(haber)) {
      ctx.addIssue({ code: 'custom', path: ['lineas'], message: `Diferencia: ${debe.minus(haber).toFixed(2)}` });
    }
  });
```

### 10.2 Catálogo de cuentas

- Al registrar la empresa se copia `plantilla_cuenta` (catálogo base NIIF para PYMES, `docs/contabilidad/catalogo-base.md`, `[VERIFICAR]` con contador).
- **Clases:** 1 Activo, 2 Pasivo, 3 Capital Contable, 4 Costos y Gastos, 5 Ingresos. Otros primeros dígitos se rechazan (`CON-010`).
- **Niveles:** clase (1 dígito), grupo (2), cuenta (4), subcuenta (6) y detalle (8 o más). El código de la cuenta padre debe ser prefijo del código hija.
- **Naturaleza por defecto:** deudora en clases 1 y 4; acreedora en 2, 3 y 5. Se puede cambiar para cuentas complementarias (p. ej. depreciación acumulada en la clase 1, acreedora).
- Solo las cuentas sin hijas aceptan movimientos. Crear una hija en una cuenta con movimientos se rechaza (`CON-011`).
- El código no puede cambiarse si la cuenta tiene movimientos (`CON-011`); una cuenta con saldo distinto de cero no puede desactivarse (`CON-012`).
- Cuentas mínimas precargadas: caja, bancos, clientes, proveedores, IVA débito fiscal, IVA crédito fiscal, IVA retenido y percibido (a favor y por pagar), renta retenida (a favor y por pagar), capital social, ventas, devoluciones sobre ventas, compras, devoluciones sobre compras, gastos generales.

### 10.3 Mayorización automática en tiempo real (ADR-018)

- Dentro de la misma transacción del asiento, por cada línea (ordenadas por `cuenta_id` para evitar interbloqueos):

```sql
-- Acumula el movimiento de la línea en el saldo mensual de su cuenta.
-- ON CONFLICT hace el incremento atómico aunque otra transacción mayorice la misma cuenta.
INSERT INTO saldo_cuenta_mensual (empresa_id, cuenta_id, anio, mes, total_debe, total_haber)
VALUES (:empresa, :cuenta, :anio, :mes, :debe, :haber)
ON CONFLICT (empresa_id, cuenta_id, anio, mes)
DO UPDATE SET total_debe     = saldo_cuenta_mensual.total_debe  + EXCLUDED.total_debe,
              total_haber    = saldo_cuenta_mensual.total_haber + EXCLUDED.total_haber,
              actualizado_en = now();
```

- **Saldo de una cuenta a una fecha** = Σ meses completos anteriores (`saldo_cuenta_mensual`) + líneas del mes parcial (`asiento_linea`).
- **Cuentas padre:** su saldo se calcula al consultar sumando las cuentas de detalle cuyo código empieza con el suyo; no se almacena.
- **Presentación del saldo:** `debe − haber`; si es positivo es **Deudor**, si es negativo es **Acreedor** (se muestra el valor absoluto). Se alerta cuando el saldo es contrario a la naturaleza de la cuenta.
- La reversión mayoriza igual que cualquier asiento, por eso los saldos vuelven a su valor anterior.
- Invariante verificado por pruebas y por el endpoint de diagnóstico: para toda cuenta y mes, `saldo_cuenta_mensual` = Σ de sus líneas.

### 10.4 Estados financieros automáticos

Clasificación por el **primer dígito del código** (`cuenta_contable.clase`). Montos en positivo según la naturaleza de la clase.

**Estado de Resultados (rango `desde`–`hasta`):**

| Rubro | Cálculo |
|---|---|
| Ingresos (5) | Σ (haber − debe) de las cuentas de la clase 5 en el rango |
| Costos y gastos (4) | Σ (debe − haber) de las cuentas de la clase 4 en el rango |
| **Utilidad (pérdida)** | Ingresos − Costos y gastos |

**Balance General (fecha de corte), ADR-016:**

| Rubro | Cálculo |
|---|---|
| Activo (1) | Σ (debe − haber) clase 1 hasta la fecha de corte |
| Pasivo (2) | Σ (haber − debe) clase 2 hasta la fecha de corte |
| Capital Contable (3) | Σ (haber − debe) clase 3 hasta la fecha de corte |
| + Resultados de ejercicios anteriores no cerrados | Utilidad (5 − 4) desde el primer movimiento hasta el 31/12 del año anterior al corte |
| + Utilidad del ejercicio | Utilidad (5 − 4) desde el 1/1 del año del corte hasta la fecha de corte |
| **Comprobación** | Activo = Pasivo + Capital + Resultados anteriores + Utilidad del ejercicio |

- El ejercicio es el año calendario `[VERIFICAR]`.
- Si la comprobación no cuadra se muestra una **alerta con la diferencia exacta**. Con la partida doble garantizada solo puede ocurrir por un error de datos (p. ej. saldos corruptos); la alerta incluye un enlace al diagnóstico de mayorización.
- Ambos estados muestran la jerarquía de cuentas con subtotales por nivel y se generan al consultar (sin tablas intermedias).

### 10.5 Reportes complementarios

| Reporte | Justificación |
|---|---|
| **Libro Diario** (listado de asientos por rango) | Es el libro legal primario; permite revisar y auditar lo registrado |
| **Libro Mayor / auxiliar por cuenta** con saldo acumulado línea a línea | Explica cómo se formó el saldo de una cuenta; imprescindible para conciliar |
| **Balanza de Comprobación** (saldo inicial, movimientos, saldo final) | Prueba global de la partida doble y base para revisar los estados antes de presentarlos |
| **Filtros por período** (rango de fechas o mes/año) en todos los reportes | Los estados y libros se revisan por mes y por ejercicio |
| **Resumen de IVA mensual** (débito, crédito, retenciones, percepciones, diferencia estimada y desglose por tipo de DTE) | Punto de partida para preparar el F-07; no reemplaza la declaración ni sus anexos |
| **Bitácora de documentos n8n** (aceptados, revertidos y rechazados con su código de error) | Permite al contador detectar documentos que no entraron y corregir la configuración |
| **Exportación PDF, XLSX y CSV** de todos los reportes | El contador externo trabaja en Excel y los libros se imprimen para legalización |

- Todos los reportes se calculan en el momento, así que reflejan cada asiento en cuanto se guarda.
- Se muestran por rango de fechas; los montos y totales de la exportación son idénticos a los de la pantalla.

---

## 11. Manejo del IVA (13 %)

> Reglas contables pendientes de validación por contador (`docs/contabilidad/formulario-iva.md` y su borrador de respuestas).

### 11.1 Cálculo único (dominio `contabilidad.dominio.iva`)

Toda separación de base e IVA usa `CalculadoraIva`, con la tasa de `tasa_impuesto` vigente a la fecha del asiento o del documento:

| Modo | Se respeta | Fórmula |
|---|---|---|
| `CON_IVA` | El monto ingresado (total) | `iva = round(monto × t / (1 + t), 2)`; `base = monto − iva` |
| `SIN_IVA` | El monto ingresado (base) | `base = monto`; `iva = round(monto × t, 2)` |

Casos dorados obligatorios (t = 13 %): `CON_IVA` 113.00 → 100.00 + 13.00 · `SIN_IVA` 100.00 → 100.00 + 13.00 · `CON_IVA` 5.00 → 4.42 + 0.58 · `SIN_IVA` 4.42 → 4.42 + 0.57.

### 11.2 Origen manual (formulario del Libro Diario)

- La cabecera tiene el **modo de precio**; por defecto el de la configuración, editable por asiento.
- Cada línea tiene la casilla **"lleva IVA"**. Al marcarla, el backend expande la línea en dos, **del mismo lado**:
  - la línea base, en la misma cuenta, por la base;
  - una línea `IVA_CALCULADO` por el IVA: **Haber → IVA débito fiscal** (venta); **Debe → IVA crédito fiscal** (compra).
- Ejemplo `CON_IVA`: Caja 113.00 (Debe) / Ventas 113.00 "lleva IVA" (Haber) → se guardan Caja 113.00 / Ventas 100.00 + IVA débito fiscal 13.00.
- Ejemplo `SIN_IVA`: Compras 100.00 "lleva IVA" (Debe) / Caja 113.00 (Haber) → se guardan Compras 100.00 + IVA crédito fiscal 13.00 / Caja 113.00.
- El usuario puede corregir el asiento solo con una reversión; nunca editando la línea de IVA.

### 11.3 Configuración de la app Contabilidad

| Parámetro | Valores | Aplica a |
|---|---|---|
| Modo de precio por defecto | `CON_IVA` (precios con IVA incluido) / `SIN_IVA` (precios + IVA) | Valor inicial del formulario manual y **documentos `SIMPLIFICADO` de n8n** |
| Cuentas de IVA y retenciones | Cuentas de detalle del catálogo | Líneas de IVA manuales y asientos de n8n |
| Reglas de contabilización | Tipo DTE × dirección × condición → cuenta base y contrapartida | Asientos de n8n |

- Los documentos `DTE_MH` **no usan** el modo de precio: el IVA ya viene separado en el JSON oficial (ADR-017).
- Todo cambio de configuración queda auditado y aplica solo a los asientos futuros.

### 11.4 Origen n8n — tratamiento por tipo de DTE

| Tipo | Venta (la empresa es emisora) | Compra (la empresa es receptora) |
|---|---|---|
| 01 FE | IVA separado → IVA débito fiscal | **IVA no deducible**: se suma a la cuenta base; sin línea de crédito fiscal |
| 03 CCF | IVA → IVA débito fiscal | IVA → IVA crédito fiscal |
| 05 NC | Asiento de venta con lados invertidos (disminuye ventas e IVA débito) | Asiento de compra con lados invertidos (disminuye compras e IVA crédito) |
| 06 ND | Igual que CCF de venta | Igual que CCF de compra |
| 14 FSEE | No aplica (se rechaza, `INT-003`) | Sin IVA; retención de renta si existe. Es compra aunque la empresa sea la emisora |

Otros tipos (04, 07, 08, 09, 11, 15) se rechazan con `INT-002` en el MVP.

---

## 12. Integración n8n — contrato del webhook

### 12.1 Resumen

| Elemento | Valor |
|---|---|
| Endpoint | `POST /api/v1/integraciones/n8n/documentos` |
| Autenticación | `Authorization: Bearer <api-key>` con alcance `integracion:documentos`; la empresa se deriva de la API key |
| Idempotencia | Header `Idempotency-Key` **obligatorio** + clave natural del documento |
| Formatos | `DTE_MH` (JSON oficial del MH) y `SIMPLIFICADO` (formato propio de Pilot) |
| Procesamiento | **Síncrono**: valida, normaliza, contabiliza y responde con el asiento (ADR-017) |
| Tamaño máximo | 1 MB por petición |
| Límite | 60 peticiones por minuto por API key (Bucket4j) `[DECISIÓN]` confirmar |

### 12.2 Headers

| Header | Obligatorio | Uso |
|---|---|---|
| `Authorization: Bearer pk_xxxx.secreto` | Sí | API key de la empresa |
| `Idempotency-Key` | Sí | Recomendado determinista: `n8n-<sistemaOrigen>-<codigoGeneracion o idExterno>` |
| `Content-Type: application/json` | Sí | — |
| `X-Request-Id` | No | Correlación; se genera si no viene y se devuelve |

### 12.3 Formato `DTE_MH`

```jsonc
// Los comentarios no forman parte del cuerpo real
{
  "formato": "DTE_MH",
  "sistemaOrigen": "facturador-x",          // Sistema que emitió o recibió el DTE
  "selloRecepcion": "2026ABCD...",           // Opcional; se guarda como referencia
  "dte": {                                   // JSON del DTE tal como lo recibió el MH (sin firma JWS)
    "identificacion": { "version": 3, "tipoDte": "03", "codigoGeneracion": "3F25…", "numeroControl": "DTE-03-…", "fecEmi": "2026-10-15" },
    "emisor":   { "nit": "06140101001011", "nombre": "…" },
    "receptor": { "nit": "06140202002022", "nrc": "…", "nombre": "…" },
    "cuerpoDocumento": [ /* líneas */ ],
    "resumen":  { "totalGravada": 1000.00, "tributos": [{ "codigo": "20", "valor": 130.00 }], "totalPagar": 1130.00, "condicionOperacion": 2 }
  }
}
```

- El `dte` se valida contra `api-spec/esquemas/dte-mh/<tipo>/v<version>.json`. Una versión de esquema no cargada se rechaza con `INT-001`.

### 12.4 Formato `SIMPLIFICADO`

```jsonc
{
  "formato": "SIMPLIFICADO",
  "sistemaOrigen": "pos-legado",
  "documento": {
    "idExterno": "VTA-000123",                // Obligatorio si no hay codigoGeneracion
    "codigoGeneracion": null,                 // Opcional; si viene, es la clave natural
    "tipoDte": "01",                          // 01, 03, 05, 06 o 14
    "numeroControl": null,                    // Opcional
    "fechaEmision": "2026-10-15",
    "nitEmisor": "06140101001011",
    "nitReceptor": null,                      // Obligatorio para 03, 05 y 06
    "nombreContraparte": "Consumidor final",
    "condicionOperacion": "CONTADO",          // CONTADO, CREDITO u OTRO
    "montoGravado": "113.00",                 // Se interpreta con el modo de precio de la configuración
    "montoExento": "0.00",
    "montoNoSujeto": "0.00",                  // En 14 FSEE, el monto de la compra va aquí
    "ivaRetenido": "0.00",
    "ivaPercibido": "0.00",
    "rentaRetenida": "0.00",
    "totalPagar": "113.00",                   // Opcional; si viene, debe coincidir con el cálculo de Pilot
    "descripcion": "Venta de mostrador"
  }
}
```

- Validado contra `api-spec/esquemas/simplificado/v1.json`. Montos como cadena decimal con 2 decimales.
- `montoGravado` se separa con el **modo de precio por defecto** de la empresa (11.1). En 14 FSEE `montoGravado` debe ser `"0.00"`.

### 12.5 Normalización a `DocumentoFiscal`

Ambos formatos se convierten al mismo objeto antes de contabilizar. Mapeo desde `DTE_MH` `[VERIFICAR]` nombres y semántica exactos contra los esquemas oficiales vigentes:

| Campo normalizado | FE (01) | CCF (03), NC (05), ND (06) | FSEE (14) |
|---|---|---|---|
| `baseGravada` | `totalGravada − descuGravada − totalIva` | `totalGravada − descuGravada` | 0 |
| `exento` | `totalExenta − descuExenta` | igual | 0 |
| `noSujeto` | `totalNoSuj − descuNoSuj` | igual | `totalCompra − descu` |
| `noGravado` | `totalNoGravado` | igual | 0 |
| `iva` | `totalIva` | Σ `tributos[codigo = "20"].valor` | 0 |
| `ivaRetenido` | `ivaRete1` | `ivaRete1` | 0 |
| `ivaPercibido` | 0 | `ivaPerci1` | 0 |
| `rentaRetenida` | `reteRenta` | `reteRenta` | `reteRenta` |
| `totalPagar` | `totalPagar` | `totalPagar` (o `montoTotalOperacion` si no existe) | `totalPagar` |
| `condicion` | `condicionOperacion`: 1 → CONTADO, 2 → CREDITO, 3 → OTRO | igual | igual |

- **Dirección:** `emisor.nit` = NIT de la empresa → `VENTA`; NIT del receptor = NIT de la empresa → `COMPRA`; tipo 14 con la empresa como emisora → `COMPRA`; en otro caso → `INT-003`.
- **Tributos distintos del IVA** (código ≠ `"20"`) → `INT-007` (no soportados en el MVP).
- **Cuadre interno obligatorio** (si falla → `INT-006`, no se contabiliza):
  `baseGravada + exento + noSujeto + noGravado + iva + ivaPercibido − ivaRetenido − rentaRetenida = totalPagar`

### 12.6 Generación del asiento (`ContabilizarDocumento`)

Cuentas: `base` y `contrapartida` de la regla (tipo, dirección, condición); el resto de `configuracion_contable`. Las líneas en cero se omiten; una línea con monto > 0 sin cuenta configurada → `CON-020`.

| Lado | Venta (01, 03, 06) | Compra (03, 06) | Compra FE (01) | Compra FSEE (14) |
|---|---|---|---|---|
| Debe | Contrapartida = `totalPagar` | Base = `baseGravada + exento + noSujeto + noGravado` | Base = todo lo anterior **+ `iva`** | Base = `noSujeto` |
| Debe | IVA retenido a favor = `ivaRetenido` | IVA crédito fiscal = `iva` | IVA percibido a favor = `ivaPercibido` | — |
| Debe | Renta retenida a favor = `rentaRetenida` | IVA percibido a favor = `ivaPercibido` | — | — |
| Haber | Base = `baseGravada + exento + noSujeto + noGravado` | Contrapartida = `totalPagar` | Contrapartida = `totalPagar` | Contrapartida = `totalPagar` |
| Haber | IVA débito fiscal = `iva` | IVA retenido por pagar = `ivaRetenido` | IVA retenido por pagar = `ivaRetenido` | Renta retenida por pagar = `rentaRetenida` |
| Haber | IVA percibido por pagar = `ivaPercibido` | Renta retenida por pagar = `rentaRetenida` | Renta retenida por pagar = `rentaRetenida` | — |

- **05 NC:** se arma como la venta o compra correspondiente y se **intercambian Debe y Haber**; su regla apunta a devoluciones sobre ventas o sobre compras.
- Fecha del asiento = fecha de emisión del documento; concepto = `DTE <tipo> <numeroControl o idExterno> — <nombreContraparte>`.
- El asiento pasa por las mismas validaciones y la misma mayorización que uno manual (10.1, 10.3). El cuadre interno (12.5) garantiza la partida doble.

Ejemplo: CCF de venta al crédito, base 1,000.00, IVA 130.00, retención 10.00, total a pagar 1,120.00 → Debe CxC 1,120.00 + IVA retenido a favor 10.00 / Haber Ventas 1,000.00 + IVA débito fiscal 130.00.

### 12.7 Idempotencia y concurrencia

1. **Nivel HTTP:** se busca `(empresa_id, Idempotency-Key)` en `idempotencia`.
   - Misma clave y mismo hash de cuerpo → se devuelve la respuesta guardada con `Idempotency-Replayed: true`.
   - Misma clave y otro cuerpo → 422 `INT-005`.
2. **Nivel documento:** si `clave_documento` ya existe con estado `CONTABILIZADO` → 409 `INT-004` con `documentoId` y `asientoId`; **nunca** se crea un segundo asiento.
3. **Nivel contable:** índice único `uq_asiento_documento_vigente` (9.3) como última defensa.
4. **Concurrencia:** dos peticiones simultáneas con la misma clave compiten por la inserción en `idempotencia` y `documento_externo` dentro de la transacción; la perdedora recibe 409 `INT-009` y puede reintentar.
5. Se guardan en `idempotencia` las respuestas 201 y 409 `INT-004`; los errores 4xx de validación y los 5xx no se guardan, para permitir reintentar tras corregir.

### 12.8 Respuestas y manejo de errores

**201 Created:**

```json
{
  "documentoId": "0192f1a4-7c3e-7b21-9d4e-5a6b7c8d9e0f",
  "estado": "CONTABILIZADO",
  "direccion": "VENTA",
  "asiento": { "id": "0192f1a4-…", "numero": 1523, "fecha": "2026-10-15" },
  "resumen": { "baseGravada": "1000.00", "iva": "130.00", "totalPagar": "1120.00" }
}
```

| HTTP | Código | Causa | ¿n8n debe reintentar? |
|---|---|---|---|
| 400 | `INT-001` | JSON inválido o no cumple su esquema (incluye `errores`) | No; corregir el flujo |
| 401 / 403 | — | API key inválida, vencida, revocada o sin alcance | No |
| 403 | `PLT-004` | La app Contabilidad no está activa en la empresa | No |
| 409 | `INT-004` | Documento ya contabilizado (devuelve `documentoId` y `asientoId`) | No; tratar como éxito |
| 409 | `INT-009` | Petición con la misma clave en proceso | Sí, tras unos segundos |
| 422 | `INT-002` | Tipo de DTE no soportado | No |
| 422 | `INT-003` | El NIT de la empresa no es emisor ni receptor | No |
| 422 | `INT-005` | `Idempotency-Key` reutilizada con otro cuerpo | No |
| 422 | `INT-006` | Totales internos del documento no cuadran | No |
| 422 | `INT-007` | Tributo o concepto no soportado en el MVP | No |
| 422 | `CON-007` | Fecha de emisión futura | No |
| 422 | `CON-020` | Falta regla de contabilización o cuenta configurada | Sí, después de configurar |
| 428 | `INT-008` | Falta `Idempotency-Key` | No; corregir el flujo |
| 429 | — | Límite de peticiones superado (`Retry-After`) | Sí |
| 5xx | — | Error interno | Sí, con espera exponencial (seguro gracias a la idempotencia) |

- Todo rechazo se registra en `intento_documento_externo` y se ve en la bitácora de la app.
- **Reproceso:** `POST /api/v1/integraciones/documentos/{id}/reproceso` solo para documentos `REVERTIDO`; genera un asiento nuevo con las reglas vigentes.
- La plantilla `integraciones/plantillas-n8n/enviar-documento.json` implementa los reintentos de esta tabla y un Error Trigger que notifica al responsable.

---

## 13. API — endpoints del MVP

Todos bajo `/api/v1`, contrato en `api-spec/openapi/pilot-v1.yaml`. Paginación por cursor (`?limite=50&cursor=…`), montos como cadena decimal.

| Método y ruta | Descripción | Alcance / rol mínimo |
|---|---|---|
| `GET /me` | Usuario actual y sus membresías | Autenticado |
| `POST /empresas` | Registra una empresa; el usuario queda como `admin_empresa`; precarga catálogo, configuración y reglas | Autenticado |
| `GET /empresas/{id}` · `PATCH /empresas/{id}` | Consulta y edición de datos de la empresa | `admin_empresa` |
| `GET /empresas/{id}/usuarios` · `POST …/invitaciones` · `PATCH …/usuarios/{usuarioId}` | Listar, invitar, cambiar rol o desactivar | `admin_empresa` |
| `GET /aplicaciones` | Apps activas de la empresa (lanzador) | Autenticado |
| `GET /api-keys` · `POST /api-keys` · `DELETE /api-keys/{id}` | Gestión de API keys (el secreto se muestra una vez) | `admin_empresa` |
| `GET /contabilidad/cuentas` · `POST` · `PATCH /{id}` | Catálogo de cuentas (árbol, búsqueda) | leer: `auditor`; escribir: `contador` |
| `GET /contabilidad/configuracion` · `PUT` | Modo de precio y cuentas de IVA | leer: `auditor`; escribir: `contador` |
| `GET /contabilidad/reglas-contabilizacion` · `PUT /{id}` | Reglas por tipo DTE, dirección y condición | leer: `auditor`; escribir: `contador` |
| `POST /contabilidad/asientos` | Registra un asiento manual (`Idempotency-Key`) | `contador` |
| `POST /contabilidad/asientos/vista-previa` | Expande IVA y valida sin guardar | `contador` |
| `GET /contabilidad/asientos` · `GET /{id}` | Libro Diario con filtros (fecha, número, origen, cuenta) | `auditor` |
| `POST /contabilidad/asientos/{id}/reversion` | Revierte un asiento (`Idempotency-Key`) | `contador` |
| `GET /contabilidad/mayor?cuentaId&desde&hasta` | Libro Mayor / auxiliar con saldo acumulado | `auditor` |
| `GET /contabilidad/balanza?desde&hasta` | Balanza de Comprobación | `auditor` |
| `GET /contabilidad/estados/balance-general?fechaCorte` | Balance General con comprobación y alerta | `auditor` |
| `GET /contabilidad/estados/resultados?desde&hasta` | Estado de Resultados | `auditor` |
| `GET /contabilidad/reportes/iva?anio&mes` | Resumen de IVA mensual | `auditor` |
| `GET /contabilidad/diagnostico/mayorizacion` | Verifica saldos contra líneas | `contador` |
| `POST /integraciones/n8n/documentos` | Webhook de n8n (sección 12) | API key `integracion:documentos` |
| `GET /integraciones/documentos` · `GET /{id}` | Bitácora de documentos y rechazos | `auditor` |
| `POST /integraciones/documentos/{id}/reproceso` | Reprocesa un documento revertido | `contador` |

- **Exportación:** los endpoints de reportes aceptan `?formato=pdf|xlsx|csv` o el header `Accept` correspondiente; por defecto JSON.
- Errores: Problem Details (8.4). Todas las respuestas llevan `X-Request-Id`.

---

## 14. Seguridad

### 14.1 Lineamientos (heredados, acotados)

- Objetivo: OWASP ASVS nivel 2.
- TLS en todo el tráfico externo; PostgreSQL y Keycloak admin solo en red privada.
- MFA obligatorio para `admin_empresa` y `contador` (Keycloak).
- API keys con prefijo visible, secreto mostrado una sola vez, hash Argon2id, alcances y expiración.
- Auditoría de solo inserción; asientos inmutables por permisos de base de datos.
- Límite de peticiones en el webhook por API key.
- Secretos: SOPS + age o secretos de Docker.
- Escaneo continuo: gitleaks, CodeQL o Semgrep, Dependabot, Trivy.

### 14.2 Roles del MVP

| Rol | Permisos |
|---|---|
| `admin_empresa` | Todo dentro de su empresa: datos, usuarios, API keys, apps y todo lo de `contador` |
| `contador` | Catálogo, configuración, reglas, asientos, reversiones, reportes, reproceso de documentos |
| `auditor` | Solo lectura de la contabilidad, la bitácora de n8n y la auditoría |
| `integracion` | Rol técnico de API keys; solo `POST /integraciones/n8n/documentos` |

---

## 15. Estrategia de pruebas

| Nivel | Herramienta | Alcance |
|---|---|---|
| Unitarias de dominio | JUnit + AssertJ | Partida doble, `CalculadoraIva`, expansión de líneas, normalización de documentos, armado de asientos, estados financieros; cobertura ≥ 80 % |
| Arquitectura | Spring Modulith + ArchUnit | Límites de módulos y capas; `integracion` no accede a tablas de `contabilidad` |
| Integración | Testcontainers (PostgreSQL) | Repositorios, RLS, trigger de partida doble, permisos de inmutabilidad, idempotencia, mayorización |
| API | MockMvc + validación contra OpenAPI | Contrato, códigos de error, seguridad por rol y alcance |
| Frontend | Vitest + Testing Library | Esquema Zod del Libro Diario, botón Guardar bloqueado, totales en vivo |
| Extremo a extremo | Playwright | Registro → empresa → asiento → reportes; n8n → asiento → reportes |
| Carga | k6 | 20 asientos/s y 20 documentos n8n/s sostenidos por instancia |

**Casos dorados obligatorios** (`backend/src/test/resources/casos/`):

1. Asientos válidos e inválidos para cada código `CON-001` a `CON-013`.
2. IVA: los cuatro casos de 11.1 y los dos ejemplos de 11.2.
3. Documentos n8n: cada combinación tipo × dirección de 11.4 en ambos formatos, con retención, percepción y renta, y el asiento esperado.
4. Estados financieros: un mes completo con balanza, Balance General (incluida la utilidad) y Estado de Resultados validados por contador.

**Pruebas obligatorias adicionales:**

- **Aislamiento:** un usuario o API key de la empresa A nunca lee ni escribe datos de la empresa B, por API ni por SQL con `pilot_app`.
- **Concurrencia:** 50 asientos simultáneos → numeración sin duplicados y saldos exactos; misma `Idempotency-Key` en paralelo → un solo asiento.
- **Propiedad de mayorización:** tras cualquier secuencia aleatoria de asientos y reversiones, `saldo_cuenta_mensual` = Σ líneas.

---

## 16. Infraestructura y despliegue

### 16.1 Ambientes

| Ambiente | Propósito |
|---|---|
| `local` | Desarrollo con Docker Compose |
| `staging` | Pruebas integradas y flujos n8n de prueba |
| `produccion` | Clientes reales |

### 16.2 Docker Compose de desarrollo

```yaml
# infra/docker/compose.dev.yml — dependencias del MVP para desarrollo local
# Fijar versiones exactas de imagen al crear el archivo; nunca usar "latest" en producción
services:

  postgres:                                   # Base de datos principal de Pilot
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: pilot
      POSTGRES_USER: pilot_owner              # Dueño del esquema (solo migraciones Flyway)
      POSTGRES_PASSWORD: ${PG_OWNER_PASSWORD} # Tomado de .env (no se commitea)
    ports: ["5432:5432"]
    volumes: [pg_data:/var/lib/postgresql/data]

  keycloak:                                   # Proveedor de identidad (registro, login, MFA)
    image: quay.io/keycloak/keycloak:26.3     # [VERIFICAR] versión vigente al iniciar
    command: start-dev --import-realm         # Importa el realm "pilot" versionado
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KC_ADMIN_PASSWORD}
    volumes: [../keycloak:/opt/keycloak/data/import]
    ports: ["8180:8080"]

  n8n:                                        # Orquestador que envía documentos al webhook
    image: docker.n8n.io/n8nio/n8n            # Fijar versión vigente
    environment:
      N8N_ENCRYPTION_KEY: ${N8N_ENCRYPTION_KEY}
      GENERIC_TIMEZONE: America/El_Salvador
    ports: ["5678:5678"]
    volumes: [n8n_data:/home/node/.n8n]

  mailpit:                                    # Captura los correos de verificación de Keycloak
    image: axllent/mailpit
    ports: ["1025:1025", "8025:8025"]         # SMTP y bandeja web

volumes:
  pg_data:
  n8n_data:
```

La aplicación se conecta con `pilot_app` (sin privilegios de dueño ni `BYPASSRLS`); Flyway usa `pilot_owner`.

### 16.3 Producción y CI/CD (heredado)

- Un VPS con Docker Compose, Traefik con Let's Encrypt, firewall solo 80/443 y SSH por llave.
- PostgreSQL con WAL-G: respaldo diario, WAL continuo, recuperación a un punto en el tiempo de 14 días, prueba de restauración mensual.
- CI: formato y lint → pruebas unitarias y de arquitectura → integración con Testcontainers → escaneos de seguridad → imagen en GHCR → despliegue automático a `staging`; a `produccion` con etiqueta `vX.Y.Z` y aprobación manual. Migraciones compatibles hacia atrás.

### 16.4 Observabilidad

- Logs JSON con `traceId`, `empresaId`, `usuarioId`, `modulo`; datos personales enmascarados.
- Métricas: asientos creados por origen, documentos n8n aceptados y rechazados por código, latencia p95 del webhook y del registro de asientos.
- Alertas: tasa de rechazo de n8n > 10 % en 1 hora por empresa; diferencia en el diagnóstico de mayorización ≠ 0 (crítica); respaldo fallido en 24 horas (crítica).

---

## 17. Plan de trabajo

Detalle completo, tareas y criterios de aceptación en **`docs/plan-de-trabajo-mvp.md`**.

| Fase | Contenido | Duración | Depende de |
|---|---|---|---|
| F0 | Fundaciones: repo, CI, compose, Modulith, RLS, idempotencia, auditoría, Problem Details, OpenAPI + Orval | 2 sem. | — |
| F1 | Núcleo: Keycloak, usuarios, empresa, membresías, API keys, registro de apps y lanzador | 2 sem. | F0 |
| F2 | Catálogo de cuentas, `tasa_impuesto`, configuración contable y reglas precargadas | 1.5 sem. | F1 |
| F3 | Libro Diario, partida doble en frontend y backend, IVA manual, mayorización, reversión | 3 sem. | F2 |
| F4 | Libro Diario y Mayor, Balanza, estados financieros, resumen IVA, exportaciones | 2.5 sem. | F3 |
| F5 | Webhook n8n con ambos formatos, normalización, reglas, idempotencia, bitácora | 2.5 sem. | F3 |
| F6 | Aislamiento, carga, seguridad, e2e, validación de un mes piloto con contador | 1.5 sem. | F4, F5 |

F4 y F5 pueden ejecutarse en paralelo. Estimaciones para 1–2 desarrolladores `[DECISIÓN]` confirmar tamaño del equipo.

---

## 18. Decisiones de arquitectura (ADR)

| ID | Decisión | Estado en el MVP |
|---|---|---|
| ADR-001 | Monolito modular con Spring Modulith y arquitectura hexagonal | Aceptada |
| ADR-002 | PostgreSQL con esquema compartido, `empresa_id` y Row-Level Security forzado | Aceptada |
| ADR-003 | Contract-first: OpenAPI como fuente de verdad; código generado | Aceptada |
| ADR-004 | Patrón outbox + RabbitMQ para eventos externos | **Diferida**: el MVP no publica eventos externos |
| ADR-005 | CloudEvents 1.0 como formato de eventos y webhooks salientes | **Diferida** |
| ADR-006 | Lógica fiscal y contable exclusivamente en el núcleo Java | Aceptada |
| ADR-007 | Esquemas y catálogos del MH como configuración versionada | Aceptada (esquemas de DTE **recibidos**) |
| ADR-008 | Keycloak como proveedor de identidad | Aceptada |
| ADR-009 | n8n solo en los bordes (orquestación), nunca en la lógica central | Aceptada |
| ADR-010 | UUID versión 7 como clave primaria generada en la aplicación | Aceptada |
| ADR-011 | Almacenamiento de objetos detrás de la API S3 | **Diferida**: las exportaciones se generan al vuelo |
| ADR-012 | Valkey en lugar de Redis por licencia BSD | **Diferida**: sin caché distribuida en el MVP |
| ADR-013 | Montos como cadena decimal en la API pública | Aceptada |
| ADR-014 | Mismo artefacto con perfiles `api` y `worker` | **Diferida**: solo perfil `api` |
| ADR-015 | Modo de precio `CON_IVA`/`SIN_IVA`: por asiento en el registro manual y por empresa para documentos `SIMPLIFICADO`; la contabilidad recibe siempre base e IVA separados | Aceptada (redefinida para el MVP) |
| ADR-016 | Balance General = Pasivo + Capital + resultados no cerrados + utilidad del ejercicio | Aceptada (nueva) |
| ADR-017 | Webhook de n8n entrante y síncrono con dos formatos (`DTE_MH`, `SIMPLIFICADO`) normalizados a `DocumentoFiscal` | Aceptada (nueva) |
| ADR-018 | Mayorización en tiempo real con `saldo_cuenta_mensual` actualizado en la misma transacción del asiento | Aceptada (nueva) |
| ADR-019 | Asientos inmutables; corrección solo por reversión, reforzada con permisos de base de datos | Aceptada (nueva) |
| ADR-020 | Reglas de contabilización por tipo DTE × dirección × condición, editables y precargadas | Aceptada (nueva) |
| ADR-021 | Registro de apps (`aplicacion`, `empresa_aplicacion`) y shell de frontend con apps cargadas dinámicamente | Aceptada (nueva) |
| ADR-022 | Montos contables como `NUMERIC(19,2)` | Aceptada (nueva; reemplaza `NUMERIC(19,4)`) |

Cada ADR tiene su archivo en `docs/adr/ADR-XXX-<titulo>.md` con contexto, decisión, alternativas y consecuencias (pendiente de crear en F0).

---

## 19. Decisiones pendientes y preguntas abiertas

- [ ] Organización y nombre del repositorio en GitHub.
- [ ] Tamaño del equipo (afecta las estimaciones del plan).
- [ ] Hosting de producción y dominio.
- [ ] ¿Se permiten asientos con fecha futura? (hoy: no, `CON-007`).
- [ ] Límite de peticiones del webhook (propuesto: 60/min por API key).
- [ ] Catálogo de cuentas base y reglas de contabilización por defecto: validación con contador.
- [ ] Validación por contador del tratamiento del IVA (`docs/contabilidad/formulario-iva.md`).
- [ ] Mapeo exacto de campos del JSON DTE oficial (12.5) contra los esquemas vigentes.
- [ ] Modelo de dominio separado de JPA o entidades JPA como dominio.

---

## 20. Fuera de alcance (futuro)

Documentado para no perder la visión; **no se implementa en el MVP**. Detalle en `docs/vision/CLAUDE-vision-completa-2026-09-23.md`. Cualquier incorporación requiere un ADR.

### 20.1 Apps y módulos futuros

| App / módulo | Contenido previsto |
|---|---|
| Facturación DTE (`dte`) | Emisión de DTE 2.0: construcción, firma, transmisión, contingencia, invalidación, retorno, PDF y QR, asistente de certificación |
| Ventas | Cotizaciones, pedidos, documentos de venta, devoluciones |
| Compras | Órdenes de compra, recepción, registro de DTE recibidos con borrador de compra |
| Catálogo comercial | Productos, clientes, proveedores, unidades de medida, catálogos del MH |
| Inventario | Bodegas, kardex, costeo, transferencias, lotes |
| Tesorería | Cuentas por cobrar y pagar, cobros, pagos, cajas, bancos, conciliación |
| POS y app móvil | Punto de venta con modo sin conexión; app Expo |
| RRHH y nómina | Empleados, planilla, descuentos de ley |
| Perfiles de industria | Plantillas de configuración por giro |

### 20.2 Funcionalidades contables futuras

- Períodos contables y cierres mensual y anual (cuenta liquidadora, clase 6), reapertura con permiso.
- Centros de costo y campos personalizados (`atributos JSONB`).
- Liquidación formal de IVA, generación de anexos del F-07 y datos del F-14.
- Libros legales de IVA (ventas a consumidor final, a contribuyentes, compras).
- Proporcionalidad del crédito fiscal, tributos especiales distintos del IVA, multi-moneda.
- Asientos recurrentes, plantillas de asiento, importación masiva CSV/XLSX de asientos.
- Estados financieros comparativos, flujo de efectivo y cambios en el patrimonio.
- Tipos de DTE 04, 07, 08, 09, 11 y 15 desde n8n.

### 20.3 Integración y plataforma futuras

- Eventos salientes (outbox, RabbitMQ, CloudEvents, AsyncAPI), webhooks salientes firmados y feed `GET /eventos`.
- Nodo comunitario `n8n-nodes-pilot`, SDK generados y portal de desarrolladores.
- Mapeo de IDs externos (`id_externo`) y upsert por ID externo.
- Importación CSV/XLSX genérica y SFTP.
- Verificación de DTE recibidos contra la consulta pública del MH.
- Almacenamiento S3, Valkey, perfil `worker`, ShedLock, Resilience4j, k3s.
- Registro autoservicio con planes y facturación de la suscripción; BI con Metabase; funciones de IA.

---

## 21. Fuentes y referencias

- Ministerio de Hacienda: https://www.mh.gob.sv
- Portal de facturación electrónica del MH (esquemas y catálogos): `[VERIFICAR]` URL vigente
- Spring Boot: https://spring.io/projects/spring-boot · Spring Modulith: https://spring.io/projects/spring-modulith
- PostgreSQL Row-Level Security: https://www.postgresql.org/docs/current/ddl-rowsecurity.html
- RFC 9457 Problem Details: https://www.rfc-editor.org/rfc/rfc9457
- Keycloak: https://www.keycloak.org · n8n: https://docs.n8n.io
- OWASP ASVS: https://owasp.org/www-project-application-security-verification-standard/

---

## 22. Mantenimiento de este archivo

- Responsable: líder técnico del proyecto.
- Revisión obligatoria al cerrar cada fase del plan y cuando cambie el alcance.
- Todo `[VERIFICAR]` resuelto se reemplaza por el dato confirmado y su fuente (documento y versión).
- Todo `[DECISIÓN]` resuelto se convierte en ADR.
- Este archivo se carga completo en cada sesión de Claude Code. Cuando el proyecto crezca más allá del MVP, mover el detalle de las secciones 9 a 12 a `docs/` y dejar aquí un resumen con referencias.
- Actualizar la fecha de "Última actualización" en el encabezado.
