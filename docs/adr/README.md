# Decisiones de arquitectura (ADR) — Pilot 1.0

Cada ADR registra contexto, decisión, alternativas y consecuencias. Un ADR aceptado solo se cambia con otro ADR que lo reemplace.

| ID | Decisión | Estado |
|---|---|---|
| [ADR-001](ADR-001-monolito-modular.md) | Monolito modular con Spring Modulith y arquitectura hexagonal | Aceptada |
| [ADR-002](ADR-002-multiempresa-rls.md) | PostgreSQL con esquema compartido, `empresa_id` y RLS forzado | Aceptada |
| [ADR-003](ADR-003-contract-first.md) | Contract-first con OpenAPI | Aceptada |
| ADR-004 | Outbox + RabbitMQ para eventos externos | Diferida (sin eventos salientes en 1.0) |
| ADR-005 | CloudEvents 1.0 para eventos y webhooks salientes | Diferida |
| [ADR-006](ADR-006-logica-fiscal-en-nucleo.md) | Lógica fiscal y contable solo en el núcleo Java | Aceptada |
| ADR-007 | Esquemas, catálogos y endpoints del MH como configuración versionada | Diferida (DTE fuera de alcance) |
| [ADR-008](ADR-008-keycloak.md) | Keycloak como proveedor de identidad | Aceptada |
| [ADR-009](ADR-009-n8n-en-los-bordes.md) | n8n solo en los bordes | Aceptada |
| [ADR-010](ADR-010-uuid-v7.md) | UUID v7 generado en la aplicación | Aceptada |
| ADR-011 | Almacenamiento de objetos detrás de la API S3 | Diferida |
| ADR-012 | Valkey en lugar de Redis | Diferida |
| [ADR-013](ADR-013-montos-como-cadena.md) | Montos como cadena decimal en la API | Aceptada |
| ADR-014 | Perfiles `api` y `worker` en el mismo artefacto | Diferida (solo `api`) |
| [ADR-015](ADR-015-modo-de-precio.md) | Modo de precio `CON_IVA` / `SIN_IVA` | Aceptada |
| [ADR-016](ADR-016-balance-con-utilidad.md) | Balance General con utilidad del período | Aceptada |
| [ADR-017](ADR-017-webhook-n8n-operaciones.md) | Webhook n8n síncrono de operaciones de negocio | Aceptada |
| [ADR-018](ADR-018-mayorizacion-tiempo-real.md) | Mayorización en tiempo real en la misma transacción | Aceptada |
| [ADR-019](ADR-019-asientos-inmutables.md) | Asientos inmutables; corrección por reversión | Aceptada |
| [ADR-020](ADR-020-reglas-contabilizacion.md) | Reglas de contabilización por tipo de operación y código | Aceptada |
| [ADR-021](ADR-021-registro-de-apps.md) | Registro de apps y shell dinámico | Aceptada |
| [ADR-022](ADR-022-numeric-19-2.md) | Montos contables como `NUMERIC(19,2)` | Aceptada |
| [ADR-023](ADR-023-java-21.md) | Java 21 LTS como versión del backend | Aceptada |
| [ADR-024](ADR-024-particiones-auditoria.md) | Particiones anuales de `auditoria` por migración Flyway | Aceptada |
| [ADR-025](ADR-025-auditoria-global.md) | Tabla `auditoria_global` para entidades sin empresa | Aceptada |

Los ADR diferidos están documentados en `docs/diferido/vision-completa-con-dte.md` (sección 22 de ese archivo).

Plantilla para nuevos ADR:

```markdown
# ADR-XXX — <título>

- **Estado:** Propuesta | Aceptada | Diferida | Reemplazada por ADR-YYY
- **Fecha:** AAAA-MM-DD

## Contexto
## Decisión
## Alternativas consideradas
## Consecuencias
```
