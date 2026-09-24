# ADR-019 — Asientos inmutables; corrección por reversión

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
La trazabilidad contable exige que lo registrado no se altere; los errores se corrigen dejando rastro.

## Decisión
Un asiento guardado queda `CONTABILIZADO` y mayorizado. Se corrige con `POST /contabilidad/asientos/{id}/reversion`, que crea un asiento con Debe y Haber invertidos y marca el original `REVERTIDO`. La base de datos lo refuerza: `pilot_app` solo tiene `SELECT, INSERT` en `asiento_linea` y solo puede actualizar `estado`, `asiento_reversion_id` y `version` en `asiento`.

## Alternativas consideradas
- **Borrador y luego contabilizar:** agrega un estado y un flujo que 1.0 no necesita.
- **Edición libre:** pierde la trazabilidad.

## Consecuencias
- Un asiento revertido o una reversión no se pueden revertir.
- Revertir el asiento de una operación de n8n permite reenviar la operación corregida con el mismo `idExterno`.
