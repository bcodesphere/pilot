# Pilot 1.0

ERP multi-empresa y modular para PYMES de El Salvador. La versión 1.0 incluye:

- **Núcleo:** registro e inicio de sesión, espacio de trabajo personal (personas naturales, sin datos empresariales), API keys y registro de apps.
- **App Contabilidad:** catálogo de cuentas, Libro Diario con partida doble, mayorización automática, Balance General, Estado de Resultados, Balanza de Comprobación, resumen de IVA y exportaciones.
- **Integración con n8n:** recepción de operaciones de otras apps; la primera es el **cierre de ingresos diarios**.

La facturación electrónica (DTE) está en segundo plano; su diseño se conserva en `docs/diferido/`.

> [!IMPORTANT]
> **Urgencia — SLA del módulo contable (timeboxing).** Pilot 1.0, con todo el flujo de trabajo ya definido (núcleo, Contabilidad e integración con n8n), tiene un **límite estricto: debe estar finalizado en menos de 24 horas a partir del 2026-09-25**. El plazo no relaja las reglas críticas ni la Definición de Terminado de la guía técnica.

## Propósito y alcance del sistema final

Visión estratégica del producto completo ([ADR-031](docs/adr/ADR-031-open-core-y-cuatro-capas.md)). Pilot 1.0 es el primer paso de esta visión y corresponde al plan Gratuito.

### Mercado objetivo

**El Salvador.** Pequeñas y medianas empresas de cualquier giro, con operación en dólares y en español (`es-SV`).

### Modelo de negocio: ERP Open-Core / Freemium

| Plan | Dirigido a | Qué ofrece |
|---|---|---|
| **Gratuito** | Micro y pequeñas empresas que operan **sin registro fiscal** | Núcleo del ERP y apps comunitarias; en 1.0, Contabilidad completa |
| **Enterprise** | Empresas con registro fiscal ante el Ministerio de Hacienda | Suscripción de **bajo costo** con la **integración oficial de Facturación Electrónica (DTE)** con el Ministerio de Hacienda de El Salvador, empresa jurídica con NIT y apps Enterprise |

### Las cuatro capas del sistema

La solución integral se estructura obligatoriamente sobre cuatro pilares:

| Capa | Pilar | Responsabilidad |
|---|---|---|
| **I** | Licenciamiento | Gestión de los planes Free/Enterprise: qué apps y funciones puede usar cada empresa, suscripción y upgrade |
| **II** | Arquitectura de software | Diseño robusto, escalable y mantenible: monolito modular hexagonal, contract-first, multiempresa con aislamiento por fila, asientos inmutables y auditoría |
| **III** | Acreditación tributaria DTE | Cumplimiento técnico con la normativa del Ministerio de Hacienda: emisión, firma, transmisión, contingencia e invalidación de DTE |
| **IV** | Blindaje legal y comercial | Términos de servicio, protección de datos personales y garantías del servicio |

En 1.0 está activa la capa II; las capas I y IV tienen su base (ediciones de apps, consentimiento de comunicaciones, datos personales enmascarados) y la capa III está diferida. Cada capa se completa en versiones posteriores con su propio ADR.

## Documentación

| Documento | Contenido |
|---|---|
| [`CLAUDE.md`](CLAUDE.md) | Guía técnica completa: alcance, reglas, arquitectura, modelo de datos, API y convenciones |
| [`docs/plan-de-trabajo.md`](docs/plan-de-trabajo.md) | Fases, tareas y criterios de aceptación |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura |
| [`docs/contabilidad/`](docs/contabilidad/) | Catálogo base y formulario de IVA para el contador |
| [`docs/diferido/`](docs/diferido/) | Diseño de facturación electrónica y visión completa (no implementar en 1.0) |

## Arranque rápido

> Disponible al terminar la fase F0 del plan.

```bash
cp .env.example .env                                     # Completa las contraseñas locales
docker compose --env-file .env -f infra/docker/compose.dev.yml up -d      # PostgreSQL, Keycloak, n8n y Mailpit
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,api
cd frontend && pnpm install && pnpm dev
```

## Estado

Fase **F1 — Núcleo**, bajo el timebox de 24 horas. Ver `docs/plan-de-trabajo.md`.
