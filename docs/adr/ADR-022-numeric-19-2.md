# ADR-022 — Montos contables como `NUMERIC(19,2)`

- **Estado:** Aceptada (reemplaza el uso de `NUMERIC(19,4)` del diseño anterior)
- **Fecha:** 2026-09-23

## Contexto
Los asientos, saldos y reportes se expresan al centavo. Con 4 decimales podían guardarse fracciones de centavo que descuadran los reportes de forma invisible.

## Decisión
Montos contables (`debe`, `haber`, saldos, montos de operaciones) en `NUMERIC(19,2)`. Las tasas usan `NUMERIC(7,4)`. El dominio redondea con `HALF_UP` a 2 decimales antes de persistir.

## Alternativas consideradas
- **`NUMERIC(19,4)`:** precisión que el MVP no necesita y que genera descuadres de fracciones de centavo.

## Consecuencias
- Si en el futuro se necesitan costos unitarios con más decimales (inventario), se usarán columnas propias con mayor escala, sin cambiar los montos contables.
