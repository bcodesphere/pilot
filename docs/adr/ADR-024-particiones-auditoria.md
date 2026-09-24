# ADR-024 — Particiones anuales de `auditoria` por migración Flyway

- **Estado:** Aceptada
- **Fecha:** 2026-09-24

## Contexto
`auditoria` está particionada por mes (`creado_en`, UTC) y V3 crea las particiones de 2026-09 a 2027-12 más una partición `DEFAULT`. Pilot 1.0 no tiene tareas en segundo plano (el perfil `worker` está diferido, CLAUDE.md §20.4), así que nadie crea particiones nuevas de forma automática. Sin acción, desde 2028-01 las filas caen en `auditoria_default`. Crear después una partición cuyo rango se solape con filas ya guardadas en `DEFAULT` falla, y obliga a moverlas a mano.

## Decisión
1. Cada año se agrega una **migración Flyway** que crea las 12 particiones mensuales del año siguiente. Debe estar aplicada en producción antes del 1 de diciembre del año en curso, y se revisa al cerrar cada fase del plan.
2. **Alerta** cuando `auditoria_default` tenga al menos una fila (CLAUDE.md §16.4). Esa partición debe estar siempre vacía.
3. `auditoria_default` se conserva como red de seguridad para que un INSERT nunca falle por falta de partición.

## Alternativas consideradas
- **Función `crear_particion_auditoria(mes)` llamada al arrancar la aplicación:** acopla el arranque a DDL y requiere que la aplicación tenga permisos de dueño, lo que va contra la regla 1.2.11.
- **Tarea programada en el perfil `worker`:** es la solución natural, pero el perfil está diferido. Se revisará este ADR cuando exista.
- **Crear particiones para muchos años por adelantado:** cientos de tablas vacías, y el problema solo se pospone.

## Consecuencias
- Hay una tarea anual recurrente (runbook `docs/runbooks/particiones-auditoria.md`, a crear en F6).
- La alerta sobre `auditoria_default` detecta el olvido antes de que se acumulen filas difíciles de mover.
