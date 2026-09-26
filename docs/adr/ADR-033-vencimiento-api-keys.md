# ADR-033 — Vencimiento de una API key elegido por fecha: final del día en hora de El Salvador

- **Estado:** Aceptada
- **Fecha:** 2026-09-26

## Contexto
La pantalla de API keys (F1-08) deja elegir un vencimiento opcional con un selector de **fecha**, sin hora. El contrato recibe `expiraEn` como instante (`TIMESTAMPTZ`, UTC), así que el frontend debe decidir a qué instante corresponde "vence el día X". Las fechas de negocio se interpretan en `America/El_Salvador`, UTC−6 y sin horario de verano (guía técnica, encabezado y regla 1.1.10).

## Decisión
1. Una API key que "vence el día X" sigue vigente **durante todo ese día** y vence a las **23:59:59 del día X en hora de El Salvador**. Se envía como `X T23:59:59-06:00`, convertido a UTC (`X+1 T05:59:59Z`).
2. La conversión vive en `finDeDiaElSalvadorEnUtc` (`frontend/src/compartido/formato/fecha.ts`). El backend guarda y compara el instante recibido; no reinterpreta fechas.
3. Solo se aceptan fechas futuras (desde mañana en hora de El Salvador).

## Alternativas consideradas
- **Inicio del día (00:00:00):** el último día útil sería el anterior al elegido, lo que confunde a quien lee "vence el 30".
- **Fecha y hora:** más precisión, pero complica el formulario sin un caso de uso en 1.0.

## Consecuencias
- En la tabla de API keys el vencimiento se muestra en hora de El Salvador: la clave aparece "Vigente" hasta el final del día elegido.
- Si algún día se admite fecha y hora, el contrato no cambia: solo el formulario.
