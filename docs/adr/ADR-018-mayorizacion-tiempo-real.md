# ADR-018 — Mayorización en tiempo real en la misma transacción

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Al guardar un asiento, el saldo de cada cuenta debe actualizarse de inmediato y sin cálculos manuales. Recalcular todo desde las líneas en cada consulta no escala.

## Decisión
Tabla `saldo_cuenta_mensual` (cuenta de detalle × año × mes) actualizada con `INSERT … ON CONFLICT DO UPDATE` en la misma transacción que el asiento, procesando las líneas ordenadas por `cuenta_id` para evitar interbloqueos. Los saldos de cuentas padre y los saldos a una fecha intermedia se calculan al consultar.

## Alternativas consideradas
- **Calcular siempre desde las líneas:** simple, pero lento con volumen.
- **Saldo por cuenta sin período:** no permite reportes por fecha.
- **Actualización asíncrona:** saldos desfasados y más infraestructura.

## Consecuencias
- Si el asiento falla, ningún saldo cambia.
- Invariante verificado por pruebas y por `GET /contabilidad/diagnostico/mayorizacion`: saldo = Σ líneas.
