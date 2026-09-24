# Pilot 1.0

ERP multi-empresa y modular para PYMES de El Salvador. La versión 1.0 incluye:

- **Núcleo:** registro e inicio de sesión, gestión de empresa y usuarios, API keys y registro de apps.
- **App Contabilidad:** catálogo de cuentas, Libro Diario con partida doble, mayorización automática, Balance General, Estado de Resultados, Balanza de Comprobación, resumen de IVA y exportaciones.
- **Integración con n8n:** recepción de operaciones de otras apps; la primera es el **cierre de ingresos diarios**.

La facturación electrónica (DTE) está en segundo plano; su diseño se conserva en `docs/diferido/`.

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

Fase **F0 — Fundaciones**. Ver `docs/plan-de-trabajo.md`.
