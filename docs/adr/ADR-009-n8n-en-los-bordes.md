# ADR-009 — n8n solo en los bordes

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
n8n permite conectar apps externas sin código a medida, pero sus flujos no tienen pruebas ni control de versiones equivalentes al núcleo.

## Decisión
n8n **orquesta**: recibe datos de otras apps, calcula la `Idempotency-Key`, llama a Pilot y maneja reintentos y alertas. **Pilot decide**: valida, calcula IVA y genera asientos. Los flujos se versionan en `integraciones/plantillas-n8n/`.

## Alternativas consideradas
- **Lógica contable en n8n:** reglas duplicadas, sin pruebas y fuera de la auditoría.

## Consecuencias
- Ningún flujo calcula impuestos, totales ni asientos.
- Cada flujo productivo tiene un Error Trigger que notifica al responsable.
