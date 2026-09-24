# ADR-010 — UUID versión 7 generado en la aplicación

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Las claves deben ser únicas entre empresas, no revelar volumen y generarse antes de insertar (para responder con el ID y enlazar registros en la misma transacción).

## Decisión
Claves primarias `UUID` versión 7 generadas en la aplicación. Su prefijo temporal mantiene los índices ordenados.

## Alternativas consideradas
- **Secuencias numéricas:** revelan volumen y complican la generación previa.
- **UUID v4:** aleatorios; fragmentan los índices B-tree.

## Consecuencias
- El número visible del asiento es un correlativo aparte (`correlativo_asiento`), no la clave primaria.
