# CLAUDE.md — Pilot 1.0 (Núcleo + Contabilidad)

> **Pilot** es un ERP multi-empresa, API-first y modular para PYMES de El Salvador, integrable con cualquier sistema mediante API y n8n. **Pilot 1.0 se limita al núcleo del ERP (usuarios, empresa, estructura de apps) y a una sola app instalable: Contabilidad**, que además recibe operaciones de otras apps (p. ej. un cierre de ingresos diarios) a través de n8n.
>
> **La facturación electrónica (DTE) está en segundo plano.** Su diseño se conserva en `docs/diferido/` y se retomará en una versión posterior (sección 20).

| Campo | Valor |
|---|---|
| Nombre del producto | Pilot |
| Versión objetivo | 1.0 |
| Organización | bcodesphere |
| Repositorio | GitHub — organización y nombre `[DECISIÓN]` |
| Paquete base Java | `com.bcodesphere.pilot` |
| Mercado inicial | El Salvador — PYMES de cualquier giro |
| Moneda | USD (única moneda contable) |
| Zona horaria de negocio | `America/El_Salvador` (UTC−6, sin horario de verano) |
| Idioma de producto | Español (`es-SV`) |
| Estado actual | Fase F1 — Núcleo (ver `docs/plan-de-trabajo.md`) |
| Última actualización de este archivo | 2026-09-24 |

---

## 0. Cómo usar este archivo

- **Claude:** antes de cualquier tarea lee la sección 1 (reglas críticas), la sección 2 (alcance) y la sección 4 (arquitectura). Para asientos y reportes lee la sección 10; para IVA, la sección 11; para la integración con n8n, la sección 12.
- **Si una tarea cae fuera del alcance (sección 2) o aparece en la sección 20, detente y pregunta.** No implementes funcionalidades futuras "por adelantado", en particular nada de DTE.
- **Marcadores usados en este documento:**
  - `[VERIFICAR]` — dato normativo o técnico externo que debe confirmarse contra la fuente oficial (MH, contador, documentación del proveedor) antes de implementarlo. **Nunca implementes un valor fiscal marcado así sin confirmarlo.**
  - `[DECISIÓN]` — decisión aún no tomada. No la asumas: pregunta.
  - `ADR-XXX` — decisión de arquitectura registrada en la sección 18 y en `docs/adr/`.
- Si una tarea contradice este archivo, detente y pregunta antes de continuar.
- Toda decisión nueva se registra en la sección 18 y en `docs/adr/`, y se actualiza este archivo **en el mismo PR**.

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
4. Exigir el header `Idempotency-Key` en todo endpoint que cree asientos, reversiones u operaciones entrantes de n8n.
5. **Validar la partida doble en el frontend y en el backend**, y tener el trigger de base de datos como última defensa (sección 10.1).
6. **Mayorizar en la misma transacción** en que se guarda el asiento: o se guardan asiento y saldos, o no se guarda nada (ADR-018).
7. Crear cambios de esquema solo como migraciones Flyway nuevas: `V<numero>__<descripcion>.sql`.
8. Escribir pruebas junto con el código: unitarias para el dominio y de integración con Testcontainers para repositorios, API y el webhook de n8n.
9. Modificar primero el contrato (`api-spec/`) y después el código (contract-first, ADR-003).
10. Guardar fechas-hora en UTC (`TIMESTAMPTZ`). Las fechas contables (`asiento.fecha`) son `DATE` en hora de El Salvador.
11. Registrar auditoría (quién, qué, cuándo, valor anterior y nuevo) en toda mutación de datos de negocio.
12. Propagar `traceId` y `empresaId` en los logs, y enmascarar datos personales (DUI, NIT, correo, teléfono).
13. Leer la tasa de IVA desde la tabla `tasa_impuesto` con vigencia (desde/hasta); nunca como constante en código.
14. Validar toda operación entrante de n8n contra su esquema JSON versionado **antes** de interpretarla.

### 1.2 NUNCA

1. Poner lógica contable o de cálculo de IVA en n8n, en la app web de origen, en el frontend o en integraciones externas. Vive solo en el núcleo Java (ADR-006). El frontend puede mostrar vistas previas calculadas **por el backend**.
2. Usar `double`, `float` o `number` de JavaScript para sumar o comparar dinero.
3. Modificar o borrar un asiento guardado ni sus líneas. Se corrige únicamente con un asiento de reversión (ADR-019).
4. Permitir que una integración envíe asientos o cuentas contables. n8n envía **operaciones de negocio** (p. ej. un cierre de ingresos); Pilot decide el asiento mediante reglas de contabilización (ADR-020).
5. Guardar un asiento descuadrado, con menos de dos líneas o con líneas que tengan Debe y Haber a la vez.
6. Contabilizar en cuentas que no aceptan movimientos (cuentas padre) o inactivas.
7. Commitear secretos: `.env`, certificados (`.crt`, `.p12`, `.pem`, `.key`), contraseñas, tokens o API keys.
8. Acceder a repositorios o tablas de otro módulo. Los módulos se comunican por su API pública (ADR-001).
9. Editar migraciones Flyway ya aplicadas.
10. Guardar tokens de acceso en `localStorage` en el frontend.
11. Conectar la aplicación a PostgreSQL con un usuario dueño de las tablas, superusuario o con `BYPASSRLS`.
12. Inventar tasas, plazos o códigos fiscales. Si no están confirmados, marcar `[VERIFICAR]` y preguntar.
13. Implementar funcionalidades de la sección 20 (incluida toda la facturación electrónica DTE) sin un ADR que las incorpore al alcance.

---

## 2. Alcance de Pilot 1.0

### 2.1 Componente A — Núcleo del ERP

- Registro e inicio de sesión de usuarios (Keycloak, OIDC con PKCE, MFA para todos): nombre, correo, teléfono de El Salvador (`+503`) y contraseña, **sin DUI**, con la casilla opcional "Acepto recibir recomendaciones por correo" (ADR-027, ADR-028).
- **Empresa personal automática** al primer inicio de sesión, con el usuario como `admin_empresa`. La empresa jurídica (NIT) es de la edición Enterprise (ADR-029).
- Gestión básica de la empresa (nombre; NIT y NRC opcionales) y de sus usuarios: agregar a un usuario **ya registrado** por su correo, cambiar su rol o desactivarlo (ADR-028).
- Un usuario puede pertenecer a varias empresas; la empresa activa se elige en la sesión.
- API keys para integraciones (n8n).
- **Catálogo de apps instalables** (estilo Odoo): tablas `aplicacion` y `empresa_aplicacion`, pantalla "Apps" y lanzador en el frontend. **Contabilidad es la única app instalable**; Ventas, Clientes, Proveedores, Inventario y Marketing se muestran bloqueadas como Enterprise. No hay desinstalación en 1.0 (ADR-021, ADR-030).

### 2.2 Componente B — App Contabilidad

| # | Funcionalidad | Sección |
|---|---|---|
| 1 | Catálogo de cuentas (clases 1 a 5), precargado y editable | 10.2 |
| 2 | **Libro Diario:** registro de asientos con validación de partida doble en frontend y backend | 10.1 |
| 3 | **Mayorización automática en tiempo real** con saldo Deudor/Acreedor por cuenta | 10.3 |
| 4 | **Estados financieros automáticos:** Balance General y Estado de Resultados por primer dígito del código | 10.4 |
| 5 | **Reportes complementarios:** Libro Diario, Mayor/auxiliar, Balanza de Comprobación, resumen de IVA, exportación PDF/XLSX/CSV, bitácora de n8n | 10.5 |
| 6 | **IVA 13 %:** manual (línea "lleva IVA") y automático en operaciones de n8n; configuración del modo de precio por defecto | 11 |
| 7 | **Webhook de n8n** para operaciones de otras apps; primer tipo: **cierre de ingresos diarios** | 12 |

### 2.3 Qué cambió respecto del diseño anterior

| Cambio | Motivo |
|---|---|
| La facturación electrónica (emisión y recepción de DTE) pasa a segundo plano | Decisión de producto: primero dejar funcionando la contabilidad |
| El webhook de n8n ya no recibe DTE: recibe **operaciones de negocio** con un contrato propio de Pilot, empezando por el **cierre de ingresos diarios** | Caso de uso real: una app web envía su cierre diario al ERP |
| Reglas de contabilización por **tipo de operación y código** (concepto de ingreso o forma de pago) en lugar de tipo DTE × dirección | Se ajusta al nuevo contrato (ADR-020) |
| La configuración contable guarda solo el modo de precio y las cuentas de IVA débito y crédito | Retenciones y percepciones venían de los DTE; se registran de forma manual mientras tanto |
| Módulos `catalogo`, `inventario`, `ventas`, `compras`, `dte`, `tesoreria`, `pos`, `rrhh` en la sección 20 | Alcance de 1.0 |
| Outbox + RabbitMQ, Valkey, S3, perfil `worker`, Resilience4j, ShedLock, firmador y simulador del MH **diferidos** | Sin eventos salientes, llamadas externas, archivos persistidos ni tareas en segundo plano |
| Montos contables como `NUMERIC(19,2)` (ADR-022) | El asiento se registra al centavo |
| `decimal.js` y Apache POI agregados al stack | Sumas exactas en el frontend y exportación XLSX |
| Balance General con utilidad del período (ADR-016) | Sin cierre contable, 1 = 2 + 3 literal nunca cuadraría |
| Clase 6 (cuenta liquidadora) fuera del catálogo | Solo se usa en el cierre anual, fuera de alcance |

---

## 3. Glosario

| Término | Significado |
|---|---|
| MH | Ministerio de Hacienda de El Salvador |
| NIT / NRC | Número de Identificación Tributaria / Número de Registro de Contribuyente |
| Asiento (partida) | Registro contable con fecha, concepto y al menos dos líneas que cuadran |
| Partida doble | Regla: Σ Debe = Σ Haber en cada asiento |
| Mayorización | Traslado de los movimientos del Libro Diario al saldo de cada cuenta (Libro Mayor) |
| Naturaleza de la cuenta | Deudora (aumenta con el Debe: clases 1 y 4) o acreedora (aumenta con el Haber: clases 2, 3 y 5) |
| Cuenta de detalle | Cuenta sin subcuentas; la única que acepta movimientos |
| Balanza de Comprobación | Lista de todas las cuentas con saldo inicial, movimientos y saldo final |
| IVA débito fiscal | IVA cobrado en ventas (pasivo) |
| IVA crédito fiscal | IVA pagado en compras con Comprobante de Crédito Fiscal (activo) |
| Modo de precio | `CON_IVA` (el monto incluye IVA) o `SIN_IVA` (el IVA se suma); solo cambia cómo se interpreta el monto |
| Operación externa | Hecho de negocio que otra app envía por n8n para que Pilot lo contabilice |
| Cierre de ingresos diarios | Operación externa con los ingresos de un día y cómo se cobraron (efectivo, tarjeta, etc.) |
| Regla de contabilización | Configuración que dice qué cuenta usar para cada concepto de ingreso o forma de pago de una operación |
| DTE | Documento Tributario Electrónico del MH. **Fuera del alcance de 1.0** |
| Tenant / empresa | Contribuyente (NIT) que usa Pilot; unidad de aislamiento de datos |
| App | Conjunto funcional que una empresa activa desde el lanzador (en 1.0 solo Contabilidad) |
| Idempotencia | Garantía de que repetir una petición no duplica su efecto |

---

## 4. Arquitectura

### 4.1 Estilo arquitectónico

- **Monolito modular** con límites verificados por Spring Modulith (ADR-001).
- **Arquitectura hexagonal** (puertos y adaptadores) dentro de cada módulo.
- **CQRS ligero:** escrituras por el modelo de dominio (JPA); reportes por consultas SQL con `JdbcClient`.
- **Un solo perfil de ejecución (`api`)**. El perfil `worker` se activará cuando existan tareas en segundo plano.
- **Transacción única por comando:** guardar asiento + mayorizar + auditar + registrar idempotencia ocurre en una sola transacción de PostgreSQL.

```text
 ┌───────────────────────────┐     ┌──────────────────────┐     ┌──────────────────────┐
 │ Web Pilot (React)          │     │ App web externa      │────►│ n8n                  │
 │ shell + app Contabilidad   │     │ (ventas, caja, etc.) │     │ (orquesta y reintenta)│
 └─────────────┬─────────────┘     └──────────────────────┘     └──────────┬───────────┘
               │ HTTPS + OIDC (PKCE)                                        │ HTTPS + API key
      ┌────────▼─────────┐                                                  │ POST /integraciones/n8n/operaciones
      │ Traefik (TLS)     │◄─────────────────────────────────────────────────┘
      └────────┬─────────┘
       ┌───────▼─────────────────────────────────────────────┐        ┌────────────────┐
       │ Pilot API (Spring Boot, perfil api)                  │◄──────►│ Keycloak (OIDC)│
       │  plataforma · contabilidad · integracion · compartido │        └────────────────┘
       └───────┬─────────────────────────────────────────────┘
               │ pilot_app (sin BYPASSRLS)
       ┌───────▼───────┐
       │ PostgreSQL 17 │  RLS forzado en todas las tablas de negocio
       └───────────────┘
```

### 4.2 Módulos

| Módulo (paquete) | Responsabilidad | Puede depender de |
|---|---|---|
| `compartido` | Tipos base: `Dinero`, `EmpresaId`, `ModoPrecio`, errores de dominio, utilidades | — |
| `plataforma` | Usuarios, empresas, membresías y roles, API keys, registro de apps, auditoría, idempotencia, contexto de empresa | `compartido` |
| `contabilidad` | Catálogo de cuentas, configuración contable, tasas de IVA, reglas de contabilización, asientos, mayorización, estados financieros y reportes | `plataforma`, `compartido` |
| `integracion` | Webhook de n8n: autenticación por API key, validación de esquemas, conversión a `OperacionContable`, bitácora de operaciones | `contabilidad` (API pública), `plataforma`, `compartido` |

Reglas de dependencia:

- `integracion` **no** crea asientos: entrega una `OperacionContable` al caso de uso público `ContabilizarOperacion` de `contabilidad`, que aplica las reglas.
- `contabilidad` nunca depende de `integracion`.
- Las dependencias cíclicas están prohibidas y se verifican con `ApplicationModules.verify()`.
- Una app futura será un módulo nuevo que usará la API pública de `contabilidad`; nunca sus tablas.

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
- **Base de datos:** `aplicacion` (catálogo global, con `edicion` `COMUNITARIA` o `ENTERPRISE`) y `empresa_aplicacion` (apps instaladas por empresa). Un filtro de seguridad rechaza con 403 `PLT-004` las peticiones a rutas de una app no instalada para la empresa.
- **Instalación (ADR-030):** `POST /aplicaciones/{codigo}/instalacion` registra la app y publica el evento síncrono `AplicacionInstalada` en la misma transacción; el módulo de la app lo escucha y hace su precarga. `plataforma` no depende de ningún módulo de app.
- **Frontend:** el shell (`frontend/src/nucleo/`) carga `GET /api/v1/aplicaciones` (catálogo con estado `INSTALADA`, `DISPONIBLE` o `BLOQUEADA_ENTERPRISE`) y registra dinámicamente las rutas de cada app instalada desde `frontend/src/apps/<app>/`. Ninguna app importa código de otra app.

### 4.5 Multi-empresa (multi-tenancy, ADR-002)

- Base de datos compartida, esquema compartido, columna `empresa_id` en toda tabla de negocio, reforzada con **Row-Level Security** forzado.
- La empresa activa llega en el header `X-Empresa-Id` (usuarios) o se deriva de la API key (n8n), y siempre se valida contra las membresías.
- Al iniciar cada transacción se fijan `app.empresa_id` y `app.usuario_id` en la sesión de PostgreSQL.
- Desde F1, las políticas `aislamiento_empresa` se declaran `TO pilot_app`, porque las políticas permisivas se combinan con OR y la de empresa lanza error si falta `app.empresa_id`, lo que rompería las funciones de búsqueda (ADR-026).
- Las búsquedas previas a conocer la empresa (usuario por `sub`, membresías del usuario, API key por prefijo) corren en el modo explícito `ContextoEmpresa.ejecutarSinEmpresa(...)`: no se fija `app.empresa_id`, así que toda tabla con RLS falla; solo se usan tablas globales y funciones `SECURITY DEFINER` mínimas, propiedad del rol `pilot_busqueda` (sin login, sin superusuario ni `BYPASSRLS`), que devuelven solo los campos necesarios. ArchUnit limita qué clases usan ese modo (ADR-026).

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

### 5.1 Backend

| Componente | Elección | Uso |
|---|---|---|
| Lenguaje | Java 21 LTS (ADR-023) | `maven.compiler.release` = 21 |
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
| Validación JSON Schema | `networknt/json-schema-validator` | Esquemas de operaciones de n8n |
| PDF | Thymeleaf + OpenHTMLtoPDF | Reportes y estados financieros |
| XLSX | Apache POI (SXSSF) | Exportación de reportes |
| Límite de peticiones | Bucket4j en memoria | Webhook de n8n por API key |
| Documentación API | OpenAPI 3.1 (contract-first) + `openapi-generator-maven-plugin` | — |
| Pruebas | JUnit Jupiter, AssertJ, Testcontainers, ArchUnit, Spring Modulith Test | — |
| Calidad | Spotless (palantir-java-format), Checkstyle, SpotBugs | CI |

### 5.2 Frontend web

| Componente | Elección |
|---|---|
| Base | React 19 + TypeScript (modo `strict`) + Vite |
| Datos remotos | TanStack Query |
| Rutas | React Router (rutas por app registradas por el shell) |
| Formularios | React Hook Form + Zod |
| Dinero | `decimal.js` — toda suma o comparación de montos |
| UI | shadcn/ui + Tailwind CSS |
| Cliente API | Generado desde OpenAPI con Orval (nunca escrito a mano) |
| Autenticación | OIDC con PKCE contra Keycloak (`oidc-client-ts`); tokens en memoria |
| i18n | `es-SV`; formato `$1,234.56` |
| Pruebas | Vitest + Testing Library; Playwright para end-to-end |
| Gestor de paquetes | pnpm |

### 5.3 Infraestructura

| Componente | Elección | Estado |
|---|---|---|
| Base de datos | PostgreSQL 17+ (RLS, JSONB) | Activo |
| Identidad | Keycloak 26.x (autorregistro, MFA) | Activo |
| Orquestación de integraciones | n8n autoalojado | Activo (externo; llama al webhook) |
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
pilot-1.0/
├── CLAUDE.md                          # Este archivo
├── README.md                          # Presentación y arranque rápido
├── .env.example                       # Variables de entorno de ejemplo (sin secretos)
├── .editorconfig · .gitignore
├── api-spec/
│   ├── openapi/pilot-v1.yaml          # Contrato REST, incluido el webhook de n8n (fuente de verdad)
│   ├── esquemas/operaciones/          # Esquemas JSON de operaciones de n8n (cierre-ingresos-diario/v1.json)
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
│           └── resources/casos/       # Casos dorados: asientos, IVA, operaciones n8n, estados
├── frontend/
│   └── src/
│       ├── nucleo/                    # Shell: auth OIDC, selector de empresa, lanzador y registro de apps, layout
│       ├── apps/contabilidad/         # Única app activa
│       │   ├── catalogo/
│       │   ├── libro-diario/
│       │   ├── mayor/
│       │   ├── reportes/              # Balanza, estados financieros, resumen IVA
│       │   ├── operaciones-n8n/       # Bitácora de operaciones recibidas
│       │   └── configuracion/         # Modo de precio, cuentas de IVA, reglas de contabilización
│       ├── compartido/                # Componentes UI, formato de moneda, utilidades decimal.js
│       └── api/                       # Cliente generado por Orval (no editar)
├── integraciones/
│   └── plantillas-n8n/                # Flujos de ejemplo (p. ej. cierre-ingresos-diario.json)
├── infra/
│   ├── docker/compose.dev.yml
│   ├── docker/compose.prod.yml
│   ├── keycloak/                      # Realm "pilot" versionado
│   ├── traefik/
│   └── observabilidad/
├── docs/
│   ├── plan-de-trabajo.md             # Plan por fases con criterios de aceptación
│   ├── adr/                           # ADR-XXX-<titulo>.md
│   ├── contabilidad/                  # Catálogo base, formulario de IVA para el contador y sus validaciones
│   ├── runbooks/                      # Restauración, rotación de API keys, corrección de cierres
│   └── diferido/                      # Diseño de facturación electrónica y visión completa (no implementar)
└── .github/workflows/                 # CI/CD
```

---

## 7. Comandos

```bash
# --- Infraestructura local ---
docker compose --env-file .env -f infra/docker/compose.dev.yml up -d      # Levanta PostgreSQL, Keycloak, n8n y Mailpit
docker compose --env-file .env -f infra/docker/compose.dev.yml down       # Detiene dependencias

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

### 8.1 Idioma y nombres

- **Dominio en español** sin tildes ni `ñ` en identificadores (`asientoContable`, `anio`); sufijos técnicos en inglés estándar (`Controller`, `Service`, `Repository`, `Dto`, `Mapper`).
- Tablas y columnas en `snake_case` singular (`asiento_linea`, `empresa_id`).
- Claves primarias `UUID` versión 7 generadas en la aplicación (ADR-010).
- Endpoints REST en plural y `kebab-case` (`/api/v1/contabilidad/reglas-contabilizacion`).
- Etiquetas (`tags`) de OpenAPI **por recurso**, en camelCase (`usuarioActual`, `empresas`, `apiKeys`): el generador crea una interfaz `<Etiqueta>Api` por etiqueta, sin métodos por defecto, y cada controlador implementa solo la suya. Una etiqueta por módulo obligaría a un solo controlador a implementar todas las operaciones del módulo.
- JSON de la API en `camelCase`; montos como **cadena decimal** (`"123.45"`) (ADR-013); fechas ISO-8601.
- Códigos de negocio en mayúsculas con guion bajo (`CIERRE_INGRESOS_DIARIO`, `VENTAS_GRAVADAS`, `EFECTIVO`).

### 8.2 Comentarios (obligatorio en cada parte del código)

| Dónde | Qué se exige |
|---|---|
| Java | Javadoc en toda clase, record, enum, interfaz y método público; comentario en línea numerado por cada bloque lógico (qué y por qué) |
| TypeScript/React | TSDoc en todo componente, hook, función exportada y esquema Zod; comentarios en bloques de lógica y efectos |
| SQL (Flyway) | Comentario por tabla, columna no obvia, índice, política RLS, trigger y permiso (`COMMENT ON` o `--`) |
| OpenAPI y esquemas JSON | `description` en cada operación, parámetro, propiedad y código de error |
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

### 8.3 Estilo y diseño

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
- Catálogo de códigos `PLT-` (los `CON-` e `INT-` están en las secciones 10 a 12). Un código nuevo se agrega aquí antes de usarlo:

| Código | HTTP | Causa |
|---|---|---|
| `PLT-001` | 400 | Cuerpo JSON ilegible o con tipos incorrectos (incluye un monto enviado como número) |
| `PLT-002` | 422 | Validación de forma (Bean Validation) sobre cuerpo o parámetros; incluye `errores` |
| `PLT-003` | 403 | `X-Empresa-Id` sin membresía activa del usuario |
| `PLT-004` | 403 | La app no está instalada en la empresa (ADR-021, ADR-030) |
| `PLT-005` | 422 | `Idempotency-Key` reutilizada con otro cuerpo (fuera del webhook, que usa `INT-005`) |
| `PLT-006` | 428 | Falta `Idempotency-Key` (fuera del webhook, que usa `INT-008`) |
| `PLT-007` | 404, 405, 415 | Ruta, método o tipo de contenido no soportado |
| `PLT-008` | 409 | Otra petición con la misma `Idempotency-Key` está en proceso; reintentar (fuera del webhook, que usa `INT-009`) |
| `PLT-009` | 401 | Falta la credencial (token o API key) o es inválida, vencida o revocada |
| `PLT-010` | 403 | Rol o alcance insuficiente para la operación |
| `PLT-011` | 403 | La app es de la edición Enterprise y no se puede instalar (ADR-030) |
| `PLT-012` | 422 | El correo no pertenece a un usuario registrado (ADR-028) |
| `PLT-013` | 409 | El usuario ya es miembro de la empresa |
| `PLT-014` | 422 | La operación dejaría a la empresa sin ningún `admin_empresa` activo |
| `PLT-015` | 428 | Falta el header `If-Match` en una edición con concurrencia optimista |
| `PLT-016` | 412 | `If-Match` no coincide con la versión actual del recurso |
| `PLT-017` | 404 | El recurso no existe o no pertenece a la empresa activa |
| `PLT-500` | 500 | Error interno; sin detalle en la respuesta |

### 8.5 Git

- Trunk-based: ramas cortas desde `main` (`feat/`, `fix/`, `chore/`, `docs/`, `refactor/`).
- Conventional Commits en español: `feat(contabilidad): agrega reversión de asientos`.
- Todo cambio entra por PR con CI en verde y al menos una revisión.
- Versionado semántico con etiquetas `vX.Y.Z` y `CHANGELOG.md`.

---

## 9. Modelo de datos

### 9.1 Resumen por módulo

| Módulo | Tablas | RLS |
|---|---|---|
| `plataforma` | `usuario` (global), `empresa`, `empresa_usuario`, `api_key`, `aplicacion` (global), `empresa_aplicacion`, `auditoria`, `auditoria_global` (global), `idempotencia` | Sí, salvo tablas globales |
| `contabilidad` | `tasa_impuesto` (global), `plantilla_cuenta` (global), `cuenta_contable`, `configuracion_contable`, `regla_contabilizacion`, `correlativo_asiento`, `asiento`, `asiento_linea`, `saldo_cuenta_mensual` | Sí, salvo tablas globales |
| `integracion` | `operacion_externa`, `intento_operacion_externa` | Sí |

Columnas comunes en toda tabla de negocio editable: `id UUID`, `empresa_id UUID`, `creado_en`, `creado_por`, `actualizado_en`, `actualizado_por`, `version BIGINT`. Las tablas globales son de solo lectura para `pilot_app` y se cargan por migración.

### 9.2 Núcleo (plataforma)

```sql
-- USUARIO: persona que inicia sesión; su identidad vive en Keycloak (tabla global, sin empresa)
CREATE TABLE usuario (
    id            UUID PRIMARY KEY,
    sub_keycloak  VARCHAR(64) NOT NULL UNIQUE,     -- Claim "sub" del token OIDC
    correo        VARCHAR(254) NOT NULL UNIQUE,    -- Dato personal: se enmascara en logs; verificado en Keycloak
    nombre        VARCHAR(200) NOT NULL,
    telefono      VARCHAR(12) NOT NULL CHECK (telefono ~ '^\+503[0-9]{8}$'),  -- Solo El Salvador; dato personal (ADR-028)
    recomendaciones_aceptadas_en TIMESTAMPTZ,      -- "Acepto recibir recomendaciones por correo"; nulo = nunca aceptó (ADR-028)
    recomendaciones_retiradas_en TIMESTAMPTZ,      -- Último retiro; vigente si aceptadas_en > retiradas_en o retiradas_en es nulo
    estado        VARCHAR(15) NOT NULL DEFAULT 'ACTIVO',  -- ACTIVO, BLOQUEADO
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- EMPRESA: unidad de aislamiento (tenant). En 1.0 solo PERSONAL, creada al primer inicio de sesión (ADR-029)
CREATE TABLE empresa (
    id               UUID PRIMARY KEY,
    tipo             VARCHAR(8) NOT NULL,          -- PERSONAL (1.0) o JURIDICA (Enterprise)
    propietario_id   UUID REFERENCES usuario(id),  -- Usuario dueño de la empresa PERSONAL
    nit              VARCHAR(14) UNIQUE,           -- 14 dígitos; opcional en PERSONAL, obligatorio en JURIDICA
    nrc              VARCHAR(10),                  -- Registro de IVA; nulo si no es contribuyente [VERIFICAR] formato
    nombre           VARCHAR(250) NOT NULL,        -- Razón social
    nombre_comercial VARCHAR(250),
    estado           VARCHAR(15) NOT NULL DEFAULT 'ACTIVA',
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    version          BIGINT NOT NULL DEFAULT 0,
    CHECK (nit IS NULL OR nit ~ '^[0-9]{14}$'),
    CHECK (tipo = 'PERSONAL' OR nit IS NOT NULL)        -- La empresa jurídica exige NIT
);

-- Una sola empresa PERSONAL por usuario
CREATE UNIQUE INDEX uq_empresa_personal ON empresa (propietario_id) WHERE tipo = 'PERSONAL';

-- MEMBRESÍA: qué usuario pertenece a qué empresa y con qué rol
CREATE TABLE empresa_usuario (
    empresa_id  UUID NOT NULL REFERENCES empresa(id),
    usuario_id  UUID NOT NULL REFERENCES usuario(id),
    rol         VARCHAR(30) NOT NULL,              -- admin_empresa, contador, auditor (sección 14.2)
    estado      VARCHAR(15) NOT NULL DEFAULT 'ACTIVA',  -- ACTIVA, INACTIVA (sin invitaciones pendientes, ADR-028)
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
    alcances      TEXT[] NOT NULL,                 -- Ej.: {integracion:operaciones}
    expira_en     TIMESTAMPTZ,
    revocada_en   TIMESTAMPTZ,
    ultimo_uso_en TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- APPS: catálogo global de apps del ERP (ADR-030). En 1.0 solo 'contabilidad' es instalable;
-- ventas, clientes, proveedores, inventario y marketing son ENTERPRISE (visibles y bloqueadas)
CREATE TABLE aplicacion (
    codigo      VARCHAR(40) PRIMARY KEY,           -- Identificador estable, también prefijo de rutas
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(300),
    edicion     VARCHAR(11) NOT NULL,              -- COMUNITARIA o ENTERPRISE
    orden       SMALLINT NOT NULL,                 -- Orden de presentación en el catálogo
    disponible  BOOLEAN NOT NULL DEFAULT true      -- Si se muestra en el catálogo
);

-- APPS INSTALADAS POR EMPRESA (sin desinstalación en 1.0: pilot_app solo SELECT e INSERT)
CREATE TABLE empresa_aplicacion (
    empresa_id         UUID NOT NULL REFERENCES empresa(id),
    aplicacion_codigo  VARCHAR(40) NOT NULL REFERENCES aplicacion(codigo),
    instalada_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    instalada_por      VARCHAR(64) NOT NULL,          -- Usuario que la instaló (app.usuario_id)
    PRIMARY KEY (empresa_id, aplicacion_codigo)
);

-- IDEMPOTENCIA: evita duplicados cuando un cliente o n8n reintenta (retención 7 días)
CREATE TABLE idempotencia (
    empresa_id      UUID NOT NULL,
    clave           VARCHAR(100) NOT NULL,         -- Valor del header Idempotency-Key
    hash_solicitud  CHAR(64) NOT NULL,             -- SHA-256 del cuerpo: misma clave con otro cuerpo = error
    estado_http     SMALLINT NOT NULL,             -- Código HTTP de la respuesta original
    respuesta       JSONB NOT NULL,                -- Respuesta original para devolverla igual
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, clave)
);
```

`auditoria`: insert-only, particionada por mes, retención 10 años (entidad, id, acción, usuario, valor anterior y nuevo en JSONB, `traceId`). Las particiones del año siguiente se crean con una migración anual y `auditoria_default` debe estar siempre vacía (ADR-024).

`auditoria_global`: mismas columnas sin `empresa_id`, para entidades sin empresa como `usuario`; sin RLS por empresa, y `pilot_app` solo con `INSERT` (ADR-025).

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
    codigo             VARCHAR(20) NOT NULL CHECK (codigo ~ '^[1-5][0-9]*$'),  -- Solo clases 1 a 5
    nombre             VARCHAR(200) NOT NULL,
    clase              SMALLINT GENERATED ALWAYS AS (substr(codigo, 1, 1)::smallint) STORED,
    nivel              SMALLINT NOT NULL,          -- 1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5 detalle
    cuenta_padre_id    UUID REFERENCES cuenta_contable(id),
    naturaleza         VARCHAR(9) NOT NULL,        -- DEUDORA o ACREEDORA (por defecto según la clase)
    acepta_movimientos BOOLEAN NOT NULL,           -- true solo en cuentas sin hijas
    activa             BOOLEAN NOT NULL DEFAULT true,
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,
    UNIQUE (empresa_id, codigo)
);

-- CONFIGURACIÓN CONTABLE: una fila por empresa (sección 11.3)
CREATE TABLE configuracion_contable (
    empresa_id             UUID PRIMARY KEY,
    modo_precio_defecto    VARCHAR(7) NOT NULL DEFAULT 'CON_IVA',  -- CON_IVA o SIN_IVA
    cuenta_iva_debito_id   UUID NOT NULL,          -- IVA débito fiscal (pasivo)
    cuenta_iva_credito_id  UUID NOT NULL,          -- IVA crédito fiscal (activo)
    actualizado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                BIGINT NOT NULL DEFAULT 0
);

-- REGLA DE CONTABILIZACIÓN: cuenta para cada código de una operación de n8n (ADR-020)
CREATE TABLE regla_contabilizacion (
    id              UUID PRIMARY KEY,
    empresa_id      UUID NOT NULL,
    tipo_operacion  VARCHAR(40) NOT NULL,          -- Ej.: CIERRE_INGRESOS_DIARIO
    categoria       VARCHAR(10) NOT NULL,          -- INGRESO (concepto) o COBRO (forma de pago)
    codigo          VARCHAR(40) NOT NULL,          -- Ej.: VENTAS_GRAVADAS, EFECTIVO, TARJETA
    cuenta_id       UUID NOT NULL,                 -- Cuenta de detalle a usar
    activa          BOOLEAN NOT NULL DEFAULT true,
    version         BIGINT NOT NULL DEFAULT 0,
    UNIQUE (empresa_id, tipo_operacion, categoria, codigo)
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
    origen_id             UUID,                      -- operacion_externa.id si viene de n8n
    modo_precio           VARCHAR(7),                -- Modo usado para separar el IVA
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

-- Una operación de n8n solo puede tener un asiento vigente (idempotencia contable)
CREATE UNIQUE INDEX uq_asiento_operacion_vigente ON asiento (empresa_id, origen_id)
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
    origen_linea   VARCHAR(14) NOT NULL,            -- USUARIO, IVA_CALCULADO u OPERACION
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

- **Trigger diferido de partida doble:** al `COMMIT` verifica Σ Debe = Σ Haber y al menos 2 líneas por asiento.

```sql
-- Valida que el asiento afectado cuadre y tenga al menos dos líneas; aborta la transacción si no
CREATE FUNCTION validar_partida_doble() RETURNS trigger AS $$
DECLARE
    v_asiento    UUID := COALESCE(NEW.asiento_id, OLD.asiento_id);
    v_diferencia NUMERIC(19,2);
    v_lineas     INT;
BEGIN
    -- Calcula la diferencia entre débitos y créditos y cuenta las líneas del asiento
    SELECT COALESCE(SUM(debe), 0) - COALESCE(SUM(haber), 0), COUNT(*)
      INTO v_diferencia, v_lineas
      FROM asiento_linea
     WHERE asiento_id = v_asiento;

    -- Rechaza asientos descuadrados o con menos de dos líneas
    IF v_diferencia <> 0 OR v_lineas < 2 THEN
        RAISE EXCEPTION 'Asiento % inválido: diferencia %, líneas %', v_asiento, v_diferencia, v_lineas;
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

- **Permisos de inmutabilidad:** `pilot_app` tiene solo `SELECT, INSERT` sobre `asiento_linea`; sobre `asiento` solo `SELECT, INSERT, UPDATE (estado, asiento_reversion_id, version)`; ningún `DELETE`.
- `saldo_cuenta_mensual` solo se modifica con el upsert de la sección 10.3.

### 9.4 Integración

```sql
-- OPERACIÓN EXTERNA: operación recibida de n8n y contabilizada (p. ej. un cierre de ingresos diarios)
CREATE TABLE operacion_externa (
    id                    UUID PRIMARY KEY,
    empresa_id            UUID NOT NULL,
    tipo_operacion        VARCHAR(40) NOT NULL,     -- CIERRE_INGRESOS_DIARIO
    version_esquema       SMALLINT NOT NULL,        -- Versión del contrato con que llegó
    sistema_origen        VARCHAR(50) NOT NULL,     -- Ej.: 'app-ventas-web'
    id_externo            VARCHAR(200) NOT NULL,    -- ID de la operación en la app de origen
    fecha                 DATE NOT NULL,            -- Fecha del cierre (fecha contable)
    sucursal              VARCHAR(100),             -- Texto informativo enviado por la app de origen
    modo_precio_aplicado  VARCHAR(7) NOT NULL,      -- Modo con que se separó el IVA
    base_gravada          NUMERIC(19,2) NOT NULL,   -- Montos ya normalizados (sección 12.5)
    iva                   NUMERIC(19,2) NOT NULL,
    exento                NUMERIC(19,2) NOT NULL,
    no_sujeto             NUMERIC(19,2) NOT NULL,
    total                 NUMERIC(19,2) NOT NULL,
    payload               JSONB NOT NULL,           -- Cuerpo original tal como llegó
    estado                VARCHAR(13) NOT NULL,     -- CONTABILIZADO o REVERTIDO
    asiento_id            UUID NOT NULL,
    recibido_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT NOT NULL DEFAULT 0
);

-- Solo una operación vigente por ID externo; las revertidas quedan como historial
CREATE UNIQUE INDEX uq_operacion_vigente ON operacion_externa (empresa_id, sistema_origen, id_externo)
    WHERE estado = 'CONTABILIZADO';

-- BITÁCORA DE RECHAZOS: intentos fallidos, para diagnóstico desde la app (retención 90 días)
CREATE TABLE intento_operacion_externa (
    id               UUID PRIMARY KEY,
    empresa_id       UUID NOT NULL,
    sistema_origen   VARCHAR(50),
    id_externo       VARCHAR(200),                 -- Puede ser nulo si el cuerpo no se pudo leer
    idempotency_key  VARCHAR(100),
    codigo_error     VARCHAR(10) NOT NULL,         -- INT-00X o CON-0XX
    detalle          JSONB NOT NULL,               -- Problem Details devuelto (sin datos personales)
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 10. Reglas de negocio contables

### 10.1 Libro Diario — registro de asientos

**Formulario:** Fecha, Concepto (cabecera), modo de precio (si alguna línea lleva IVA) y líneas con Código/Cuenta, Descripción opcional, Debe, Haber y casilla "lleva IVA" (sección 11.2).

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
- **Reversión** (`RevertirAsiento`): crea un asiento `origen_tipo = REVERSION` con Debe y Haber intercambiados, fecha elegida por el usuario (por defecto hoy) y concepto "Reversión del asiento N.º …"; marca el original `REVERTIDO`. Un asiento revertido o una reversión no pueden revertirse (`CON-008`, `CON-009`). Si el original venía de n8n, la operación pasa a `REVERTIDO` y la app de origen puede reenviarla corregida (12.8).

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

- Al instalar la app Contabilidad en una empresa (evento `AplicacionInstalada`, ADR-030) se copia `plantilla_cuenta`, cargada desde el catálogo base de `docs/contabilidad/catalogo-base.md` (`[VERIFICAR]` con contador).
- **Clases:** 1 Activo, 2 Pasivo, 3 Capital Contable, 4 Costos y Gastos, 5 Ingresos. Otros primeros dígitos se rechazan (`CON-010`).
- **Niveles por longitud del código:** clase (1 dígito), grupo (2), cuenta (4), subcuenta (6), detalle (8). El código de la cuenta padre debe ser prefijo del código hija.
- **Naturaleza por defecto:** deudora en clases 1 y 4; acreedora en 2, 3 y 5. Se puede cambiar para cuentas complementarias (p. ej. depreciación acumulada en la clase 1, acreedora).
- Solo las cuentas sin hijas aceptan movimientos. Crear una hija en una cuenta con movimientos se rechaza (`CON-011`).
- El código no puede cambiarse si la cuenta tiene movimientos (`CON-011`); una cuenta con saldo distinto de cero no puede desactivarse (`CON-012`).

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
- Si la comprobación no cuadra se muestra una **alerta con la diferencia exacta**. Con la partida doble garantizada solo puede ocurrir por un error de datos; la alerta enlaza al diagnóstico de mayorización.
- Ambos estados muestran la jerarquía de cuentas con subtotales por nivel y se generan al consultar.

### 10.5 Reportes complementarios

| Reporte | Justificación |
|---|---|
| **Libro Diario** (listado de asientos por rango) | Es el libro legal primario; permite revisar y auditar lo registrado |
| **Libro Mayor / auxiliar por cuenta** con saldo acumulado línea a línea | Explica cómo se formó el saldo de una cuenta; imprescindible para conciliar |
| **Balanza de Comprobación** (saldo inicial, movimientos, saldo final) | Prueba global de la partida doble y base para revisar los estados antes de presentarlos |
| **Filtros por período** (rango de fechas o mes/año) en todos los reportes | Los estados y libros se revisan por mes y por ejercicio |
| **Resumen de IVA mensual** (movimientos de IVA débito y crédito fiscal y diferencia estimada, con desglose manual / n8n) | Punto de partida para preparar la declaración de IVA; no la reemplaza |
| **Bitácora de operaciones n8n** (aceptadas, revertidas y rechazadas con su código de error) | Permite al contador detectar cierres que no entraron y corregir la configuración |
| **Exportación PDF, XLSX y CSV** de todos los reportes | El contador externo trabaja en Excel y los libros se imprimen para legalización |

- Todos los reportes se calculan en el momento, así que reflejan cada asiento en cuanto se guarda.
- Los montos y totales de la exportación son idénticos a los de la pantalla.

---

## 11. Manejo del IVA (13 %)

> Reglas contables pendientes de validación por contador (`docs/contabilidad/formulario-iva.md` y su borrador de respuestas).

### 11.1 Cálculo único (dominio `contabilidad.dominio.iva`)

Toda separación de base e IVA usa `CalculadoraIva`, con la tasa de `tasa_impuesto` vigente a la fecha del asiento o de la operación:

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
- Una compra con factura de consumidor final (IVA no deducible) se registra **sin** marcar "lleva IVA": el total va al costo o gasto.
- Retenciones y percepciones de IVA se registran como líneas manuales en sus cuentas del catálogo.

### 11.3 Configuración de la app Contabilidad

| Parámetro | Valores | Aplica a |
|---|---|---|
| Modo de precio por defecto | `CON_IVA` (precios con IVA incluido) / `SIN_IVA` (precios + IVA) | Valor inicial del formulario manual y **todas las operaciones que llegan por n8n** |
| Cuenta de IVA débito fiscal | Cuenta de detalle del catálogo | Líneas de IVA de ventas (manuales y de n8n) |
| Cuenta de IVA crédito fiscal | Cuenta de detalle del catálogo | Líneas de IVA de compras (manuales) |
| Reglas de contabilización | Tipo de operación × categoría × código → cuenta | Asientos de n8n |

- Todo cambio de configuración queda auditado y aplica solo a los asientos futuros.

---

## 12. Integración n8n — contrato del webhook de operaciones

### 12.1 Resumen

| Elemento | Valor |
|---|---|
| Caso de uso | Una app web (ventas, caja, etc.) envía a n8n su **cierre de ingresos diarios**; n8n lo reenvía a Pilot, que genera y mayoriza el asiento |
| Endpoint | `POST /api/v1/integraciones/n8n/operaciones` |
| Autenticación | `Authorization: Bearer <api-key>` con alcance `integracion:operaciones`; la empresa se deriva de la API key |
| Idempotencia | Header `Idempotency-Key` **obligatorio** + clave natural (`sistemaOrigen` + `idExterno`) |
| Tipos de operación en 1.0 | `CIERRE_INGRESOS_DIARIO` (otros tipos requieren ADR) |
| Procesamiento | **Síncrono**: valida, normaliza, contabiliza y responde con el asiento (ADR-017) |
| Tamaño máximo | 1 MB por petición |
| Límite | 60 peticiones por minuto por API key (Bucket4j) `[DECISIÓN]` confirmar |

### 12.2 Headers

| Header | Obligatorio | Uso |
|---|---|---|
| `Authorization: Bearer pk_xxxx.secreto` | Sí | API key de la empresa |
| `Idempotency-Key` | Sí | Recomendado: SHA-256 del cuerpo (un reintento idéntico reutiliza la clave; un cierre corregido genera otra) |
| `Content-Type: application/json` | Sí | — |
| `X-Request-Id` | No | Correlación; se genera si no viene y se devuelve |

### 12.3 Cuerpo: `CIERRE_INGRESOS_DIARIO` v1

```jsonc
// Los comentarios no forman parte del cuerpo real
{
  "tipoOperacion": "CIERRE_INGRESOS_DIARIO",
  "version": 1,                               // Versión del esquema de la operación
  "sistemaOrigen": "app-ventas-web",          // Identificador estable de la app que genera el cierre
  "idExterno": "CIERRE-2026-10-15-SUC01",     // ID único del cierre en la app de origen
  "fecha": "2026-10-15",                      // Día que se cierra (fecha contable del asiento)
  "sucursal": "Sucursal Centro",              // Opcional; solo informativo (va al concepto)
  "descripcion": "Cierre de caja del día",    // Opcional
  "ingresos": [                               // Qué se vendió, por tratamiento fiscal
    { "concepto": "VENTAS_GRAVADAS", "monto": "1130.00" },  // Se interpreta con el modo de precio de la empresa
    { "concepto": "VENTAS_EXENTAS",  "monto": "50.00" }
  ],
  "cobros": [                                 // Cómo se cobró
    { "formaPago": "EFECTIVO", "monto": "780.00" },
    { "formaPago": "TARJETA",  "monto": "400.00" }
  ],
  "totalCobrado": "1180.00"                   // Opcional; si viene, debe ser igual a la suma de cobros
}
```

- Validado contra `api-spec/esquemas/operaciones/cierre-ingresos-diario/v1.json`.
- `concepto` ∈ `VENTAS_GRAVADAS`, `VENTAS_EXENTAS`, `VENTAS_NO_SUJETAS`. `formaPago` ∈ `EFECTIVO`, `TARJETA`, `TRANSFERENCIA`, `CHEQUE`, `CREDITO`, `OTRO`.
- Sin códigos repetidos dentro de `ingresos` ni de `cobros`; montos como cadena decimal con 2 decimales, no negativos; al menos un ingreso y un cobro mayores que cero.
- La app de origen **no envía IVA**: Pilot lo calcula (regla 1.2.1).

### 12.4 Validaciones

| Código | Validación |
|---|---|
| `INT-001` | Cuerpo que no cumple el esquema (incluye lista `errores`) |
| `INT-002` | `tipoOperacion` o `version` no soportados |
| `CON-007` | Fecha futura |
| `INT-006` | Σ `cobros` ≠ total de la operación normalizada (12.5), o `totalCobrado` ≠ Σ `cobros`; se informa la `diferencia` |
| `CON-020` | Un `concepto` o `formaPago` usado no tiene regla activa ni cuenta, o falta la cuenta de IVA débito |

### 12.5 Normalización y generación del asiento (`ContabilizarOperacion`)

1. Se toma el **modo de precio por defecto** de la empresa y la tasa vigente a `fecha`.
2. `VENTAS_GRAVADAS` se separa con `CalculadoraIva` (11.1) en `baseGravada` e `iva`.
3. `total = baseGravada + iva + exento + noSujeto`; debe ser igual a Σ `cobros` (`INT-006`).
4. Se arma el asiento:

| Lado | Línea | Cuenta |
|---|---|---|
| Debe | Una por cada forma de pago, por su monto | Regla `COBRO` / `formaPago` |
| Haber | Ventas gravadas por `baseGravada` | Regla `INGRESO` / `VENTAS_GRAVADAS` |
| Haber | IVA débito fiscal por `iva` | `configuracion_contable.cuenta_iva_debito_id` |
| Haber | Ventas exentas y no sujetas por su monto | Regla `INGRESO` / concepto |

5. Fecha del asiento = `fecha`; concepto = `Cierre de ingresos <fecha> — <sucursal> (<sistemaOrigen>:<idExterno>)`. Las líneas en cero se omiten.
6. El asiento pasa por las mismas validaciones, numeración y mayorización que uno manual (10.1, 10.3), en la misma transacción que la operación y la idempotencia.

**Ejemplo con modo `CON_IVA`** (cuerpo de 12.3): base 1,000.00, IVA 130.00, exentas 50.00, total 1,180.00 = cobros ✔.

| Cuenta | Debe | Haber |
|---|---|---|
| Caja general | 780.00 | |
| Cuentas por cobrar — tarjetas | 400.00 | |
| Ventas gravadas | | 1,000.00 |
| IVA débito fiscal | | 130.00 |
| Ventas exentas | | 50.00 |
| **Totales** | **1,180.00** | **1,180.00** |

**El mismo cuerpo con modo `SIN_IVA`:** base 1,130.00, IVA 146.90, total 1,326.90 ≠ cobros 1,180.00 → 422 `INT-006` con `diferencia: "146.90"`. La configuración debe coincidir con cómo la app de origen registra sus precios.

**Reglas precargadas para `CIERRE_INGRESOS_DIARIO`** (editables; cuentas del catálogo base):

| Categoría | Código | Cuenta por defecto |
|---|---|---|
| INGRESO | `VENTAS_GRAVADAS` | 51010101 Ventas gravadas |
| INGRESO | `VENTAS_EXENTAS` | 51010102 Ventas exentas |
| INGRESO | `VENTAS_NO_SUJETAS` | 51010103 Ventas no sujetas |
| COBRO | `EFECTIVO` | 11010101 Caja general |
| COBRO | `TARJETA` | 11020102 Cuentas por cobrar — emisores de tarjetas |
| COBRO | `TRANSFERENCIA` | 11010103 Bancos |
| COBRO | `CHEQUE` | 11010103 Bancos |
| COBRO | `CREDITO` | 11020101 Clientes |
| COBRO | `OTRO` | Sin cuenta: debe configurarse antes de usarse |

### 12.6 Idempotencia y concurrencia

1. **Nivel HTTP:** se busca `(empresa_id, Idempotency-Key)` en `idempotencia`.
   - Misma clave y mismo hash de cuerpo → se devuelve la respuesta guardada con `Idempotency-Replayed: true`.
   - Misma clave y otro cuerpo → 422 `INT-005`.
2. **Nivel operación:** si ya existe una operación `CONTABILIZADO` con el mismo `sistemaOrigen` + `idExterno` → 409 `INT-004` con `operacionId` y `asientoId`; **nunca** se crea un segundo asiento.
3. **Nivel contable:** índices únicos `uq_operacion_vigente` y `uq_asiento_operacion_vigente` como última defensa.
4. **Concurrencia:** dos peticiones simultáneas del mismo cierre compiten por los índices únicos dentro de la transacción; la perdedora recibe 409 `INT-009` y puede reintentar (recibirá la respuesta guardada o `INT-004`).
5. Se guardan en `idempotencia` las respuestas 201 y 409 `INT-004`; los 4xx de validación y los 5xx no se guardan, para permitir reintentar tras corregir.

### 12.7 Respuestas y manejo de errores

**201 Created:**

```json
{
  "operacionId": "0192f1a4-7c3e-7b21-9d4e-5a6b7c8d9e0f",
  "estado": "CONTABILIZADO",
  "asiento": { "id": "0192f1a4-…", "numero": 1523, "fecha": "2026-10-15" },
  "resumen": { "modoPrecio": "CON_IVA", "baseGravada": "1000.00", "iva": "130.00", "exento": "50.00", "noSujeto": "0.00", "total": "1180.00" }
}
```

| HTTP | Código | Causa | ¿n8n debe reintentar? |
|---|---|---|---|
| 400 | `INT-001` | JSON inválido o no cumple el esquema (incluye `errores`) | No; corregir el flujo o la app |
| 401 / 403 | — | API key inválida, vencida, revocada o sin alcance | No |
| 403 | `PLT-004` | La app Contabilidad no está activa en la empresa | No |
| 409 | `INT-004` | El cierre ya está contabilizado (devuelve `operacionId` y `asientoId`) | No; tratar como éxito |
| 409 | `INT-009` | Petición del mismo cierre en proceso | Sí, tras unos segundos |
| 422 | `INT-002` | Tipo u versión de operación no soportados | No |
| 422 | `INT-005` | `Idempotency-Key` reutilizada con otro cuerpo | No |
| 422 | `INT-006` | Cobros que no cuadran con los ingresos (incluye `diferencia`) | No; corregir el cierre |
| 422 | `CON-007` | Fecha futura | No |
| 422 | `CON-020` | Falta regla o cuenta configurada | Sí, después de configurar en Pilot |
| 428 | `INT-008` | Falta `Idempotency-Key` | No; corregir el flujo |
| 429 | — | Límite de peticiones superado (`Retry-After`) | Sí |
| 5xx | — | Error interno | Sí, con espera exponencial (seguro gracias a la idempotencia) |

- Todo rechazo se registra en `intento_operacion_externa` y se ve en la bitácora de la app.
- La plantilla `integraciones/plantillas-n8n/cierre-ingresos-diario.json` implementa: Webhook (desde la app web) → validación mínima → cálculo de `Idempotency-Key` → HTTP Request a Pilot con los reintentos de esta tabla → Error Trigger que notifica al responsable.

### 12.8 Corrección de un cierre ya contabilizado

1. El contador revierte el asiento del cierre en Pilot (`POST /contabilidad/asientos/{id}/reversion`); la operación pasa a `REVERTIDO`.
2. La app de origen reenvía el cierre corregido con el **mismo `idExterno`** (el cuerpo cambió, así que la `Idempotency-Key` también).
3. Pilot lo acepta como nueva operación vigente; la revertida queda como historial. Runbook: `docs/runbooks/correccion-cierre.md` (se crea en F5).

---

## 13. API — endpoints de 1.0

Todos bajo `/api/v1`, contrato en `api-spec/openapi/pilot-v1.yaml`. Paginación por cursor (`?limite=50&cursor=…`), montos como cadena decimal.

| Método y ruta | Descripción | Alcance / rol mínimo |
|---|---|---|
| `GET /me` | Usuario actual y sus membresías | Autenticado |
| `PATCH /me` | Retira o vuelve a dar el consentimiento de publicidad | Autenticado |
| `GET /empresas/{id}` · `PATCH /empresas/{id}` | Consulta y edición de la empresa (nombre, nombre comercial; NIT y NRC opcionales) | `admin_empresa` |
| `GET /empresas/{id}/usuarios` · `POST …/usuarios` · `PATCH …/usuarios/{usuarioId}` | Listar, agregar un usuario registrado por correo, cambiar rol o desactivar | `admin_empresa` |
| `GET /aplicaciones` | Catálogo de apps con su estado para la empresa activa (ADR-030) | Autenticado |
| `POST /aplicaciones/{codigo}/instalacion` | Instala una app comunitaria y ejecuta su precarga | `admin_empresa` |
| `GET /api-keys` · `POST /api-keys` · `DELETE /api-keys/{id}` | Gestión de API keys (el secreto se muestra una vez) | `admin_empresa` |
| `GET /contabilidad/cuentas` · `POST` · `PATCH /{id}` | Catálogo de cuentas (árbol, búsqueda) | leer: `auditor`; escribir: `contador` |
| `GET /contabilidad/configuracion` · `PUT` | Modo de precio y cuentas de IVA | leer: `auditor`; escribir: `contador` |
| `GET /contabilidad/reglas-contabilizacion` · `PUT /{id}` | Reglas por tipo de operación, categoría y código | leer: `auditor`; escribir: `contador` |
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
| `POST /integraciones/n8n/operaciones` | Webhook de n8n (sección 12) | API key `integracion:operaciones` |
| `GET /integraciones/operaciones` · `GET /{id}` | Bitácora de operaciones y rechazos | `auditor` |

- **Exportación:** los endpoints de reportes aceptan `?formato=pdf|xlsx|csv` o el header `Accept` correspondiente; por defecto JSON.
- Errores: Problem Details (8.4). Todas las respuestas llevan `X-Request-Id`.

---

## 14. Seguridad

### 14.1 Lineamientos

- Objetivo: OWASP ASVS nivel 2.
- TLS en todo el tráfico externo; PostgreSQL y la consola de Keycloak solo en red privada.
- MFA (TOTP) obligatorio para todos los usuarios (Keycloak, ADR-027).
- API keys con prefijo visible, secreto mostrado una sola vez, hash Argon2id, alcances y expiración.
- Auditoría de solo inserción; asientos inmutables por permisos de base de datos.
- Límite de peticiones en el webhook por API key.
- Secretos: SOPS + age o secretos de Docker.
- Escaneo continuo: gitleaks, CodeQL o Semgrep, Dependabot, Trivy.

### 14.2 Roles

| Rol | Permisos |
|---|---|
| `admin_empresa` | Todo dentro de su empresa: datos, usuarios, API keys, apps y todo lo de `contador` |
| `contador` | Catálogo, configuración, reglas, asientos, reversiones, reportes |
| `auditor` | Solo lectura de la contabilidad, la bitácora de n8n y la auditoría |
| `integracion` | Rol técnico de API keys; solo `POST /integraciones/n8n/operaciones` |

---

## 15. Estrategia de pruebas

| Nivel | Herramienta | Alcance |
|---|---|---|
| Unitarias de dominio | JUnit + AssertJ | Partida doble, `CalculadoraIva`, expansión de líneas, normalización del cierre, armado de asientos, estados financieros; cobertura ≥ 80 % |
| Arquitectura | Spring Modulith + ArchUnit | Límites de módulos y capas; `integracion` no accede a tablas de `contabilidad` |
| Integración | Testcontainers (PostgreSQL) | Repositorios, RLS, trigger de partida doble, permisos de inmutabilidad, idempotencia, mayorización |
| API | MockMvc + validación contra OpenAPI | Contrato, códigos de error, seguridad por rol y alcance |
| Frontend | Vitest + Testing Library | Esquema Zod del Libro Diario, botón Guardar bloqueado, totales en vivo |
| Extremo a extremo | Playwright | Registro → empresa → asiento → reportes; cierre por n8n → asiento → reportes |
| Carga | k6 | 20 asientos/s y 20 operaciones n8n/s sostenidos por instancia |

**Casos dorados obligatorios** (`backend/src/test/resources/casos/`):

1. Asientos válidos e inválidos para cada código `CON-001` a `CON-013`.
2. IVA: los cuatro casos de 11.1 y los dos ejemplos de 11.2.
3. Cierres de ingresos: el ejemplo de 12.5 en `CON_IVA` (acepta) y `SIN_IVA` (rechaza con `INT-006`), un cierre solo con exentas, uno con varias formas de pago y uno con un código sin regla (`CON-020`).
4. Estados financieros: un mes completo con balanza, Balance General (incluida la utilidad) y Estado de Resultados validados por contador.

**Pruebas obligatorias adicionales:**

- **Aislamiento:** un usuario o API key de la empresa A nunca lee ni escribe datos de la empresa B, por API ni por SQL con `pilot_app`.
- **Concurrencia:** 50 asientos simultáneos → numeración sin duplicados y saldos exactos; el mismo cierre enviado en paralelo → un solo asiento.
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
# infra/docker/compose.dev.yml — dependencias de Pilot 1.0 para desarrollo local
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

  n8n:                                        # Orquestador que reenvía los cierres al webhook
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

Pilot usa el n8n de este compose (versión fijada), no uno externo. Si en la máquina ya corre otro n8n en el 5678, se fija `N8N_PUERTO=5679` en `.env`.

### 16.3 Producción y CI/CD

- Un VPS con Docker Compose, Traefik con Let's Encrypt, firewall solo 80/443 y SSH por llave.
- PostgreSQL con WAL-G: respaldo diario, WAL continuo, recuperación a un punto en el tiempo de 14 días, prueba de restauración mensual.
- CI: formato y lint → pruebas unitarias y de arquitectura → integración con Testcontainers → escaneos de seguridad → imagen en GHCR → despliegue automático a `staging`; a `produccion` con etiqueta `vX.Y.Z` y aprobación manual. Migraciones compatibles hacia atrás.

### 16.4 Observabilidad

- Logs JSON con `traceId`, `empresaId`, `usuarioId`, `modulo`; datos personales enmascarados.
- Métricas: asientos creados por origen, operaciones n8n aceptadas y rechazadas por código, latencia p95 del webhook y del registro de asientos.
- Alertas: tasa de rechazo de n8n > 10 % en 1 hora por empresa; diferencia en el diagnóstico de mayorización ≠ 0 (crítica); respaldo fallido en 24 horas (crítica); `auditoria_default` con filas (ADR-024).

---

## 17. Plan de trabajo

Detalle completo, tareas y criterios de aceptación en **`docs/plan-de-trabajo.md`**.

| Fase | Contenido | Duración | Depende de |
|---|---|---|---|
| F0 | Fundaciones: estructura, CI, compose, Modulith, RLS, idempotencia, auditoría, Problem Details, OpenAPI + Orval | 2 sem. | — |
| F1 | Núcleo: Keycloak, usuarios, empresa personal, membresías, API keys, catálogo de apps y lanzador | 2 sem. | F0 |
| F2 | Catálogo de cuentas, `tasa_impuesto`, configuración contable y reglas precargadas | 1.5 sem. | F1 |
| F3 | Libro Diario, partida doble en frontend y backend, IVA manual, mayorización, reversión | 3 sem. | F2 |
| F4 | Libro Diario y Mayor, Balanza, estados financieros, resumen IVA, exportaciones | 2.5 sem. | F3 |
| F5 | Webhook n8n: cierre de ingresos diarios, idempotencia, bitácora, plantilla de flujo | 2 sem. | F3 |
| F6 | Aislamiento, carga, seguridad, e2e, validación de un mes piloto con contador | 1.5 sem. | F4, F5 |

F4 y F5 pueden ejecutarse en paralelo. Estimaciones para 1–2 desarrolladores `[DECISIÓN]` confirmar tamaño del equipo.

---

## 18. Decisiones de arquitectura (ADR)

Índice y archivos en `docs/adr/`.

| ID | Decisión | Estado en 1.0 |
|---|---|---|
| ADR-001 | Monolito modular con Spring Modulith y arquitectura hexagonal | Aceptada |
| ADR-002 | PostgreSQL con esquema compartido, `empresa_id` y Row-Level Security forzado | Aceptada |
| ADR-003 | Contract-first: OpenAPI como fuente de verdad; código generado | Aceptada |
| ADR-004 | Patrón outbox + RabbitMQ para eventos externos | Diferida |
| ADR-005 | CloudEvents 1.0 para eventos y webhooks salientes | Diferida |
| ADR-006 | Lógica fiscal y contable exclusivamente en el núcleo Java | Aceptada |
| ADR-007 | Esquemas, catálogos y endpoints del MH como configuración versionada | Diferida (DTE fuera de alcance) |
| ADR-008 | Keycloak como proveedor de identidad | Aceptada |
| ADR-009 | n8n solo en los bordes (orquestación), nunca en la lógica central | Aceptada |
| ADR-010 | UUID versión 7 como clave primaria generada en la aplicación | Aceptada |
| ADR-011 | Almacenamiento de objetos detrás de la API S3 | Diferida |
| ADR-012 | Valkey en lugar de Redis por licencia BSD | Diferida |
| ADR-013 | Montos como cadena decimal en la API pública | Aceptada |
| ADR-014 | Mismo artefacto con perfiles `api` y `worker` | Diferida (solo `api`) |
| ADR-015 | Modo de precio `CON_IVA`/`SIN_IVA`: por asiento en el registro manual y por empresa para operaciones de n8n | Aceptada |
| ADR-016 | Balance General = Pasivo + Capital + resultados no cerrados + utilidad del ejercicio | Aceptada |
| ADR-017 | Webhook de n8n entrante y síncrono para operaciones de negocio (primer tipo: cierre de ingresos diarios) | Aceptada |
| ADR-018 | Mayorización en tiempo real con `saldo_cuenta_mensual` en la misma transacción del asiento | Aceptada |
| ADR-019 | Asientos inmutables; corrección solo por reversión, reforzada con permisos de base de datos | Aceptada |
| ADR-020 | Reglas de contabilización por tipo de operación × categoría × código, editables y precargadas | Aceptada |
| ADR-021 | Registro de apps y shell de frontend con apps cargadas dinámicamente | Aceptada (ampliada por ADR-030) |
| ADR-022 | Montos contables como `NUMERIC(19,2)` | Aceptada |
| ADR-023 | Java 21 LTS como versión del backend | Aceptada |
| ADR-024 | Particiones anuales de `auditoria` por migración Flyway y alerta sobre `auditoria_default` | Aceptada |
| ADR-025 | Tabla `auditoria_global` para entidades sin empresa | Aceptada |
| ADR-026 | Consultas previas a conocer la empresa: modo sin empresa + funciones `SECURITY DEFINER` | Aceptada |
| ADR-027 | MFA obligatorio para todos los usuarios | Aceptada |
| ADR-028 | Registro de usuarios en Keycloak, sin DUI, con consentimiento de publicidad; sin invitaciones pendientes | Aceptada |
| ADR-029 | Empresa personal automática; empresa jurídica en Enterprise | Aceptada |
| ADR-030 | Catálogo de apps instalables y apps Enterprise bloqueadas (amplía ADR-021) | Aceptada |

---

## 19. Decisiones pendientes y preguntas abiertas

- [ ] Organización y nombre del repositorio en GitHub.
- [ ] Tamaño del equipo (afecta las estimaciones del plan).
- [ ] Hosting de producción y dominio.
- [ ] ¿Se permiten asientos con fecha futura? (hoy: no, `CON-007`).
- [ ] Límite de peticiones del webhook (propuesto: 60/min por API key).
- [ ] Catálogo de cuentas base y reglas por defecto: validación con contador.
- [ ] Validación por contador del tratamiento del IVA (`docs/contabilidad/formulario-iva.md`).
- [ ] ¿El cierre diario debe registrar faltantes y sobrantes de caja? (hoy: no; los cobros deben cuadrar exactamente).
- [ ] Modelo de dominio separado de JPA o entidades JPA como dominio.

---

## 20. Fuera de alcance (futuro)

Documentado para no perder la visión; **no se implementa en 1.0**. Cualquier incorporación requiere un ADR.

### 20.1 Facturación electrónica (DTE) — en segundo plano

- Emisión de DTE 2.0: construcción, firma, transmisión al MH, contingencia, invalidación, evento de retorno, PDF y QR, asistente de certificación.
- Recepción de DTE desde n8n (formatos `DTE_MH` y simplificado) con reglas por tipo DTE × dirección, retenciones, percepciones y renta.
- Diseño conservado en `docs/diferido/mvp-con-webhook-dte.md` y `docs/diferido/vision-completa-con-dte.md`.

### 20.2 Apps y módulos futuros

| App / módulo | Contenido previsto |
|---|---|
| Ventas | Cotizaciones, pedidos, documentos de venta, devoluciones |
| Compras | Órdenes de compra, recepción, documentos de compra |
| Catálogo comercial | Productos, clientes, proveedores, unidades de medida |
| Inventario | Bodegas, kardex, costeo, transferencias, lotes |
| Tesorería | Cuentas por cobrar y pagar, cobros, pagos, cajas, bancos, conciliación |
| POS y app móvil | Punto de venta con modo sin conexión; app Expo |
| RRHH y nómina | Empleados, planilla, descuentos de ley |
| Perfiles de industria | Plantillas de configuración por giro |

### 20.2.1 Edición Enterprise

- Registro de empresas jurídicas con NIT de 14 dígitos y DUI, mediante un upgrade desde la empresa personal (ADR-029).
- Instalación de las apps marcadas `ENTERPRISE` en el catálogo (ADR-030), planes y lógica de licenciamiento.
- Envío de publicidad a los usuarios que dieron su consentimiento (ADR-028).

### 20.3 Funcionalidades contables futuras

- Períodos contables y cierres mensual y anual (cuenta liquidadora, clase 6), reapertura con permiso.
- Centros de costo y campos personalizados.
- Liquidación formal de IVA, anexos del F-07, datos del F-14 y libros legales de IVA.
- Proporcionalidad del crédito fiscal, tributos especiales, multi-moneda.
- Asientos recurrentes, plantillas de asiento, importación masiva CSV/XLSX.
- Estados financieros comparativos, flujo de efectivo y cambios en el patrimonio.
- Más tipos de operación por n8n: gastos, compras, cobros de cartera, faltantes y sobrantes de caja, anticipo de IVA por tarjetas.

### 20.4 Integración y plataforma futuras

- Eventos salientes (outbox, RabbitMQ, CloudEvents, AsyncAPI), webhooks salientes firmados y feed `GET /eventos`.
- Nodo comunitario `n8n-nodes-pilot`, SDK generados y portal de desarrolladores.
- Mapeo de IDs externos y upsert por ID externo; importación CSV/XLSX genérica y SFTP.
- Almacenamiento S3, Valkey, perfil `worker`, ShedLock, Resilience4j, k3s.
- Registro autoservicio con planes y facturación de la suscripción; BI con Metabase; funciones de IA.

---

## 21. Fuentes y referencias

- Ministerio de Hacienda: https://www.mh.gob.sv
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
- Este archivo se carga completo en cada sesión de Claude Code. Cuando el proyecto crezca más allá de 1.0, mover el detalle de las secciones 9 a 12 a `docs/` y dejar aquí un resumen con referencias.
- Actualizar la fecha de "Última actualización" en el encabezado.
