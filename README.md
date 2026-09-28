# Pilot 1.0 — Entrega de la demo

**Pilot** es un ERP multiempresa, modular y con API primero, pensado para PYMES de El Salvador. La versión 1.0 incluye el **núcleo** (usuarios, espacio de trabajo, apps) y una sola app instalable: **Contabilidad**, que además recibe operaciones de otras apps a través de n8n. La facturación electrónica (DTE) queda para una versión posterior (`docs/diferido/`).

Contenido de esta carpeta:

| Archivo | Para qué sirve |
|---|---|
| `README.md` | Este documento: instalación, accesos, roles, base de datos, catálogo, historial Git |
| `schema.sql` | Esquema completo de la base de datos (tablas, índices, RLS, triggers, permisos) |
| `data.sql` | Catálogo de cuentas y demás datos globales (IVA, reglas, apps) |
| `generar-sql.sh` | Regenera `schema.sql` y `data.sql` desde la base migrada |
| `historial-git.txt` | Historial Git de todas las ramas |
| `generar-historial-git.sh` | Regenera `historial-git.txt` |
| `guion-demo.md` | Guion de presentación de 10–12 minutos con datos de ejemplo |
| `MANUAL-DE-APLICACION-DE-CUENTAS-COMERCIAL .pdf` | Manual de la Universidad Católica en que se basa el catálogo |

> `schema.sql`, `data.sql` e `historial-git.txt` se generan al final de la entrega; si no aparecen, ejecute los scripts como se indica en las secciones 6 y 8.

---

## 1. Qué cubre la demo

| # | Requisito de la rúbrica | Dónde se ve | Cómo se garantiza |
|---|---|---|---|
| 1 | **Libro Diario** con Fecha, Código/Cuenta, Concepto, Debe y Haber; bloquea el guardado si no cumple la partida doble | Contabilidad → Libro Diario → Nuevo asiento | Se valida **tres veces**: formulario (Zod + `decimal.js`, botón Guardar deshabilitado con la diferencia), backend (`CON-001` a `CON-005`, 422 con la diferencia exacta) y un **trigger diferido de PostgreSQL** que aborta el `COMMIT` si Σ Debe ≠ Σ Haber o hay menos de 2 líneas. Los asientos guardados no se modifican ni se borran: se corrigen con una reversión |
| 2 | **Mayorización automática en tiempo real** con saldo Deudor/Acreedor | Contabilidad → Mayor (y Balanza) | Se mayoriza en la **misma transacción** que guarda el asiento (tabla `saldo_cuenta_mensual`, ADR-018): o se guardan asiento y saldos, o nada. Saldo = Debe − Haber; positivo = Deudor, negativo = Acreedor. Un diagnóstico verifica que los saldos coincidan con las líneas |
| 3 | **Estados financieros automáticos** por el primer dígito del código | Contabilidad → Reportes → Estado de Situación Financiera y Estado de Resultados | Clasificación por `clase` (1 activo, 2 pasivo, 3 patrimonio, 4 costos y gastos, 5 ingresos). Balance: Activo = Pasivo + Patrimonio + utilidad del ejercicio (ADR-016, ADR-037), con alerta y diferencia exacta si no cuadra. Resultados: Ingresos (5) − Costos y gastos (4) = utilidad (el impuesto sobre la renta, grupo 44, se muestra aparte) |
| 4 | **Datos complementarios** | Reportes: Balanza de Comprobación, Resumen de IVA, Diagnóstico; Libro Diario y Mayor; exportación PDF/XLSX/CSV; bitácora de operaciones de n8n | IVA 13 % leído de la tabla `tasa_impuesto` con vigencia (nunca fijo en el código); montos siempre con `BigDecimal`/`NUMERIC(19,2)`; las exportaciones repiten exactamente las cifras de la pantalla |

Además: aislamiento por empresa con **Row-Level Security**, auditoría de toda mutación, idempotencia (`Idempotency-Key`) y un webhook para n8n que recibe el **cierre de ingresos diarios** (`docs/`, sección 12 de la guía técnica).

## 2. Arquitectura y stack

```text
 ┌──────────────────────────┐   HTTPS + OIDC (PKCE)   ┌────────────────────┐
 │ Frontend React (Vite)     │────────────────────────►│ Keycloak (realm    │
 │ shell + app Contabilidad  │◄────────────────────────│ "pilot", MFA TOTP) │
 └────────────┬─────────────┘        tokens           └─────────▲──────────┘
              │ REST /api/v1 (Bearer JWT, X-Empresa-Id)          │ valida JWT
      ┌───────▼───────────────────────────────────────┐          │
      │ API Spring Boot (monolito modular hexagonal)   │──────────┘
      │ plataforma · contabilidad · integracion        │◄── n8n (API key, webhook)
      └───────┬───────────────────────────────────────┘
              │ usuario pilot_app (sin BYPASSRLS)
      ┌───────▼───────┐
      │ PostgreSQL 17 │  RLS forzado por empresa_id, trigger de partida doble
      └───────────────┘
```

| Capa | Tecnología y versión (tomada de `pom.xml`, `package.json` y `compose.dev.yml`) |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, Spring Data JPA (Hibernate), Flyway, Maven (`./mvnw`) |
| Reportes | OpenHTMLtoPDF 1.1.87 (PDF), Apache POI 5.5.1 (XLSX), CSV |
| Calidad backend | Spotless (palantir-java-format 2.99.0), Checkstyle 14.1.0, SpotBugs, JaCoCo 0.8.15, ArchUnit 1.5.0, Testcontainers |
| Contrato API | OpenAPI 3.1 (`api-spec/openapi/pilot-v1.yaml`), openapi-generator 7.25.0 |
| Frontend | React 19.3.0, TypeScript 5.9.3, Vite 8.3.1, TanStack Query 5.103.2, React Router 7.18.4, React Hook Form 7.89.0, Zod 4.6.5, `decimal.js` 10.6.0, Tailwind CSS 4.3.3, `oidc-client-ts` 3.5.0 |
| Calidad frontend | Vitest 5.0.1, Testing Library, Playwright 1.63.0, ESLint 10.11.0, Prettier 3.9.9, Orval 8.37.0 (cliente API generado) |
| Gestor de paquetes | pnpm 9.15.9, Node ≥ 22.18.0 |
| Infraestructura local | PostgreSQL 17.11 (alpine), Keycloak 26.3.5, n8n 2.14.2, Mailpit v1.31.2, Docker Compose |

## 3. Manual de instalación (Linux)

### 3.1 Requisitos

- **Docker** con el plugin `docker compose` (los puertos 5432, 8180, 8025, 1025 y 5678 deben estar libres).
- **Java 21** (JDK). El proyecto trae el wrapper `./mvnw`; no hace falta instalar Maven.
- **Node.js ≥ 22.18** y **pnpm 9.15.9** (`corepack enable && corepack prepare pnpm@9.15.9 --activate`).
- **Git**.

### 3.2 Variables de entorno

```bash
cp .env.example .env
```

Abra `.env` y complete **con valores propios** (el archivo no se sube a Git):

| Variable | Qué poner |
|---|---|
| `PG_OWNER_PASSWORD` | Contraseña del usuario `pilot_owner` (dueño del esquema; solo lo usa Flyway) |
| `PG_APP_PASSWORD` | Contraseña del usuario `pilot_app` (el que usa la API; sin privilegios de dueño) |
| `KC_ADMIN_PASSWORD` | Contraseña del administrador de la consola de Keycloak |
| `N8N_ENCRYPTION_KEY` | Cadena larga aleatoria para cifrar credenciales de n8n |
| `N8N_PUERTO` | Opcional; cámbiela (p. ej. `5679`) si el 5678 está ocupado |
| `PILOT_OIDC_ISSUER` | Ya viene con `http://localhost:8180/realms/pilot`; déjela así |

Para el frontend: `cp frontend/.env.example frontend/.env.local` (ya trae los valores correctos de desarrollo: `VITE_OIDC_AUTHORITY=http://localhost:8180/realms/pilot`, `VITE_OIDC_CLIENT_ID=pilot-web`, `VITE_API_BASE_URL=http://localhost:8080/api/v1`).

### 3.3 Infraestructura (PostgreSQL, Keycloak, Mailpit)

Desde la raíz del repositorio:

```bash
docker compose --env-file .env -f infra/docker/compose.dev.yml up -d postgres keycloak mailpit
docker compose --env-file .env -f infra/docker/compose.dev.yml ps      # esperar a que estén "healthy"
```

La primera vez, PostgreSQL crea el usuario `pilot_app` (script `infra/docker/postgres/init/01-roles.sh`) y Keycloak importa el realm `pilot` (`infra/keycloak/`). Compose exige todas las variables del `.env` aunque no arranque n8n. Para levantar también n8n, quite `postgres keycloak mailpit` del comando.

### 3.4 Backend

```bash
set -a; source .env; set +a          # exporta PG_OWNER_PASSWORD, PG_APP_PASSWORD y PILOT_OIDC_ISSUER
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,api
```

Al arrancar, Flyway aplica las migraciones V1–V19 con `pilot_owner` y la API queda en <http://localhost:8080/api/v1>, conectada como `pilot_app`.

### 3.5 Frontend

```bash
cd frontend
pnpm install
pnpm dev                              # http://localhost:5173
```

Las variables `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID` y `VITE_API_BASE_URL` se leen de `frontend/.env.local`; si falta alguna, la app muestra un error de configuración.

### 3.6 Verificación

```bash
cd backend  && ./mvnw verify          # unitarias + integración (Testcontainers, requiere Docker) + arquitectura + calidad
cd frontend && pnpm lint && pnpm test # ESLint, Prettier y Vitest
cd frontend && pnpm e2e               # opcional: Playwright, con todo el sistema arriba
```

## 4. Accesos

| Servicio | URL | Nota |
|---|---|---|
| Aplicación (frontend) | <http://localhost:5173> | Entrada para usuarios |
| API REST | <http://localhost:8080/api/v1> | Contrato en `api-spec/openapi/pilot-v1.yaml` |
| Keycloak (login y registro) | <http://localhost:8180> | Consola de administración en <http://localhost:8180/admin>, usuario `admin` y la contraseña de `KC_ADMIN_PASSWORD` |
| Mailpit (correos de desarrollo) | <http://localhost:8025> | Aquí llegan los correos de verificación |
| n8n (opcional) | <http://localhost:5678> (o `N8N_PUERTO`) | Reenvía cierres de ingresos al webhook |

**Cómo obtener acceso.** El realm no trae usuarios de prueba: cada persona se registra.

1. Abrir <http://localhost:5173> → **Registrarse**: nombre, apellido, correo, teléfono de El Salvador (8 dígitos, se guarda como `+503…`), contraseña (mínimo 12 caracteres) y, opcionalmente, "Acepto recibir recomendaciones por correo". No se pide DUI.
2. **Configurar el TOTP (MFA)**: es obligatorio para todos. Escanear el QR con una app autenticadora y escribir el código de 6 dígitos.
3. **Verificar el correo**: abrir Mailpit (<http://localhost:8025>) y pulsar el enlace del correo recibido.
4. Al primer inicio de sesión, Pilot crea automáticamente la **empresa personal** del usuario, con el usuario como `admin_empresa`.
5. Ir a **Configuración → Apps** e **instalar Contabilidad**: se precargan el catálogo de cuentas, las reglas de contabilización y la configuración de IVA. Después aparece en el lanzador.

## 5. Tabla de roles

Jerarquía: `admin_empresa` ⊃ `contador` ⊃ `auditor`. `integracion` es un rol técnico independiente (solo API keys). En la versión abierta 1.0, todo usuario es `admin_empresa` de su espacio personal; los demás roles existen en el modelo y en la API, y su gestión de miembros es de la edición Enterprise.

| Rol | Quién lo usa | Puede | No puede |
|---|---|---|---|
| `admin_empresa` | Dueño del espacio de trabajo | Todo lo de `contador`; cambiar el nombre del espacio; crear y revocar **API keys**; instalar apps | — |
| `contador` | Quien lleva la contabilidad | Todo lo de `auditor`; **registrar y revertir asientos** (Libro Diario), vista previa, crear y editar cuentas del catálogo, editar configuración y reglas de contabilización, ejecutar el diagnóstico de mayorización | Gestionar API keys ni apps |
| `auditor` | Revisión y consulta | **Solo lectura**: Libro Diario, Mayor, Balanza, Estado de Situación Financiera, Estado de Resultados, Resumen de IVA, catálogo, configuración, reglas, bitácora de n8n y exportaciones PDF/XLSX/CSV | Crear, editar o revertir nada |
| `integracion` | API key de n8n (alcance `integracion:operaciones`) | Solo `POST /api/v1/integraciones/n8n/operaciones` | Cualquier otra ruta; no satisface roles de usuario |

Toda petición valida además la membresía a la empresa (`X-Empresa-Id`), y la base de datos filtra por empresa con RLS.

## 6. Base de datos

- **Motor:** PostgreSQL 17, esquema compartido con columna `empresa_id` y **Row-Level Security forzado** en todas las tablas de negocio.
- **Roles:** `pilot_owner` (dueño del esquema; solo Flyway), `pilot_app` (usuario de la aplicación: sin superusuario ni `BYPASSRLS`; sobre `asiento_linea` solo `SELECT, INSERT`, ningún `DELETE`) y `pilot_busqueda` (sin login; funciones de búsqueda previas a conocer la empresa).
- **Migraciones Flyway V1–V19** en `backend/src/main/resources/db/migration/`: extensiones, idempotencia, auditoría, usuarios, empresas y membresías, apps, API keys, IVA, plantillas contables, catálogo y configuración, libro diario con trigger de partida doble, saldos mensuales, catálogo NIIF, operaciones guiadas, cuentas del sistema, activos fijos y el catálogo de la Universidad Católica (V19).
- **`schema.sql`:** esquema completo (sin el historial de Flyway) con tablas, índices, políticas RLS, trigger de partida doble, permisos (`GRANT`) y comentarios. No crea los roles: en una base vacía, créelos antes con `infra/docker/postgres/init/01-roles.sh` (o ejecute Flyway, que es el camino recomendado).
- **`data.sql`:** `INSERT` de las tablas globales: `plantilla_cuenta` (catálogo de cuentas), `plantilla_regla_contabilizacion`, `plantilla_configuracion_contable`, `tasa_impuesto` (IVA 13 %), `aplicacion` y `plantilla_vida_util`. El catálogo de cada empresa se copia de `plantilla_cuenta` al instalar Contabilidad.
- **Regenerar** (con compose arriba y la base ya migrada):

```bash
bash entregables/generar-sql.sh       # escribe entregables/schema.sql y entregables/data.sql
```

## 7. Catálogo de cuentas

El catálogo base se apoya en el **manual de aplicación de cuentas comercial de la Universidad Católica** (archivo PDF en esta carpeta) y su decisión de arquitectura es el **ADR-044** (`docs/adr/`, índice en [`docs/adr/README.md`](../docs/adr/README.md)).

- **Clases usadas:** 1 Activo, 2 Pasivo, 3 Patrimonio, 4 Costos y gastos, 5 Ingresos. El primer dígito del código clasifica la cuenta en los estados financieros.
- **Se omitió:** los niveles de 10 y 11 dígitos del manual (Pilot llega a 8 dígitos, nivel de detalle) y las **clases 6 y 7** (cuentas liquidadoras y de orden, que solo se usan en el cierre anual, fuera del alcance de 1.0).
- **Cuentas propias de Pilot** agregadas para que el sistema funcione: IVA débito fiscal (`21020101`) e IVA crédito fiscal (`11040101`), que son fijas y no se editan; las cuentas de ventas por tratamiento fiscal (gravadas, exentas y no sujetas), las de cobro por forma de pago que usan las reglas de n8n, y las de activos fijos y depreciación. Los códigos definitivos están en `data.sql` (`plantilla_cuenta`).
- Las cuentas copiadas del catálogo base son **cuentas del sistema** (no se editan); cada empresa puede agregar subcuentas propias debajo de ellas.
- La validación del catálogo por un contador sigue pendiente (ver limitaciones).

## 8. Historial Git

- **Ramas por fase:** `feat/f0-fundaciones`, `docs/f1-decisiones`, `feat/f2-catalogo-configuracion`, `feat/f3-libro-diario`, `feat/f4-f5-reportes-n8n` y `main` (integración).
- **Convención:** *Conventional Commits* en español, por ejemplo `feat(contabilidad): agrega la exportación de reportes a PDF, XLSX y CSV`. Tipos usados: `feat`, `fix`, `docs`, `chore`, `refactor`, `test`.
- **Cómo leer `historial-git.txt`:** cada bloque es un commit con hash, ramas y etiquetas (`decorate`), autor, fecha ISO y los archivos tocados con su número de líneas (`--stat`); el `*` y las líneas del grafo muestran cómo se unieron las ramas. Al final hay un resumen de commits por autor.
- **Regenerar:**

```bash
bash entregables/generar-historial-git.sh   # escribe entregables/historial-git.txt
```

## 9. Pruebas, calidad y limitaciones

**Volumen de pruebas** (conteo aproximado por búsqueda en el repositorio, no un resultado de ejecución):

| Nivel | Cantidad |
|---|---|
| Backend: clases de prueba unitaria (`*Test`) | 37 archivos |
| Backend: clases de integración (`*IT`, Testcontainers con PostgreSQL) | 62 archivos |
| Backend: métodos de prueba (`@Test` y parametrizados) | ~535 |
| Frontend: archivos Vitest | 43 |
| Frontend: casos Vitest | ~254 |
| Extremo a extremo (Playwright) | 5 archivos de especificación (F1, F2, F3, F4 y flujo completo) |

Cubren, entre otros: casos dorados de partida doble e IVA (113.00 → 100.00 + 13.00, etc.), aislamiento entre empresas, permisos de inmutabilidad, mayorización, reportes, exportaciones y arquitectura (Spring Modulith y ArchUnit). El estado detallado por fase está en [`docs/plan-de-trabajo.md`](../docs/plan-de-trabajo.md).

**Limitaciones conocidas de la demo:**

- El catálogo de cuentas, las reglas por defecto y el tratamiento del IVA están cargados como **borrador**, pendientes de validación por un contador (`docs/contabilidad/`).
- Los estados financieros son **estados de gestión** (no un juego completo conforme a NIIF para PYMES); no hay cierres contables, flujo de efectivo ni comparativos.
- La depreciación de activos fijos está bloqueada hasta confirmar las vidas útiles con el contador.
- Solo hay una app instalable (Contabilidad); Ventas, Clientes, Proveedores, Inventario y Marketing se muestran bloqueadas como Enterprise, y la facturación electrónica (DTE) no está incluida.
- Configuración de **desarrollo**: SMTP a Mailpit, `pilot_owner` con privilegios amplios en el contenedor y un solo perfil de ejecución; el despliegue de producción (Traefik, respaldos, observabilidad) queda para una fase posterior.
- La versión abierta no tiene miembros adicionales por empresa: agregar usuarios y cambiar roles es de la edición Enterprise.
