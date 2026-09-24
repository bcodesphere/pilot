# ADR-017 — Webhook n8n síncrono de operaciones de negocio

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Otras apps (p. ej. una app web de ventas) deben enviar al ERP hechos como el **cierre de ingresos diarios** a través de n8n. La facturación electrónica (DTE) quedó en segundo plano, así que el contrato no se basa en el JSON del MH.

## Decisión
- Endpoint único `POST /api/v1/integraciones/n8n/operaciones` con un campo `tipoOperacion` y un esquema JSON versionado por tipo. En 1.0 solo existe `CIERRE_INGRESOS_DIARIO` v1.
- Procesamiento **síncrono**: validar, normalizar, contabilizar y mayorizar en una transacción, y responder con el asiento.
- Idempotencia doble: `Idempotency-Key` y clave natural `sistemaOrigen` + `idExterno`.
- Autenticación por API key con alcance `integracion:operaciones`.

## Alternativas consideradas
- **Procesamiento asíncrono con cola:** requiere RabbitMQ, estados intermedios y consulta de resultado; innecesario para el volumen de 1.0.
- **Un endpoint por tipo de operación:** multiplica la autenticación y la idempotencia.
- **Recibir DTE:** diferido junto con la facturación electrónica.

## Consecuencias
- n8n recibe el resultado o el error en la misma respuesta y decide si reintenta.
- Agregar un tipo de operación requiere un esquema nuevo, reglas precargadas y un ADR.
- Cuando vuelva la facturación electrónica, los DTE se agregarán como nuevos tipos de operación.
