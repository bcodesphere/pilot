# ADR-013 — Montos como cadena decimal en la API

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Los números JSON se interpretan como coma flotante en JavaScript y en muchas herramientas (incluido n8n), lo que introduce errores de redondeo.

## Decisión
Todos los montos de la API y de las operaciones de n8n viajan como cadena decimal con 2 decimales (`"1130.00"`). El backend los lee como `BigDecimal`; el frontend, con `decimal.js`.

## Alternativas consideradas
- **Números JSON:** errores de precisión.
- **Enteros en centavos:** exactos, pero propensos a errores de interpretación por integradores.

## Consecuencias
- Los esquemas JSON validan el patrón `^\d{1,17}(\.\d{1,2})?$`.
