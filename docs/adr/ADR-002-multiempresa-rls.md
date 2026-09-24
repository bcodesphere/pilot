# ADR-002 — Multi-empresa con esquema compartido y Row-Level Security forzado

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Varias empresas comparten la misma instalación y un usuario puede pertenecer a varias. Una fuga de datos contables entre empresas es un riesgo crítico.

## Decisión
Base de datos y esquema compartidos, columna `empresa_id` en toda tabla de negocio y **RLS con `FORCE`**. Cada transacción fija `app.empresa_id` y `app.usuario_id` con `set_config(..., true)`. La aplicación usa `pilot_app`, sin privilegios de dueño ni `BYPASSRLS`; Flyway usa `pilot_owner`.

## Alternativas consideradas
- **Base de datos por empresa:** aislamiento fuerte, pero costosa de operar y migrar.
- **Esquema por empresa:** migraciones multiplicadas y límites de catálogo en PostgreSQL.
- **Solo filtros en la aplicación:** un olvido en una consulta expone datos.

## Consecuencias
- Las búsquedas previas a conocer la empresa (API key, membresías) usan funciones `SECURITY DEFINER` mínimas.
- Prueba obligatoria de aislamiento por API y por SQL en cada versión.
