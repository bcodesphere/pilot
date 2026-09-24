# ADR-003 — Contract-first con OpenAPI

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Pilot es API-first: la web y n8n son clientes de la misma API. El contrato debe ser estable y verificable.

## Decisión
`api-spec/openapi/pilot-v1.yaml` es la fuente de verdad. Las interfaces Spring se generan con `openapi-generator-maven-plugin` y el cliente del frontend con Orval. Los cuerpos de operaciones de n8n tienen además esquemas JSON versionados en `api-spec/esquemas/operaciones/`. El contrato se valida con Spectral en CI.

## Alternativas consideradas
- **Code-first (anotaciones):** el contrato cambia sin revisión explícita.

## Consecuencias
- Todo cambio de API empieza modificando el contrato.
- El cliente del frontend nunca se escribe a mano.
