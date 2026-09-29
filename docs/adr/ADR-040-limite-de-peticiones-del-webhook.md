# ADR-040 — Límite de peticiones del webhook de n8n: 60 por minuto por API key

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
La guía técnica §12.1 proponía limitar el webhook `POST /integraciones/n8n/operaciones` a 60 peticiones por minuto por API key con Bucket4j, marcado `[DECISIÓN]` (§19). El contrato (ADR-039 §7) ya documenta la respuesta 429 con `Retry-After`, pero no el valor. F5-03 lo necesita para implementar el filtro.

## Decisión
1. **60 peticiones por minuto por API key** (decisión del usuario, 2026-09-27), con Bucket4j en memoria: un cubo por clave con capacidad 60 que se recarga de forma gradual (60 fichas por minuto).
2. Al superarlo se responde **429** en Problem Details, sin código de negocio nuevo, con el header `Retry-After` en segundos (redondeado hacia arriba). El límite se aplica después de autenticar la clave y antes de leer el cuerpo, así una petición rechazada por límite no consume validación ni transacción.
3. El valor es configuración (`pilot.integracion.limite-por-minuto`, 60 por defecto), no una constante en el código, para ajustarlo sin recompilar.
4. El contador vive en la memoria de cada instancia. En 1.0 hay una sola instancia (perfil `api`, guía técnica §4.1). Con varias instancias, el límite efectivo se multiplicaría y habría que moverlo a un almacén compartido; Valkey está diferido (ADR-012).

## Alternativas consideradas
- **120 por minuto:** más holgura para reintentos masivos, pero un flujo n8n en bucle haría el doble de daño antes de frenarse.
- **30 por minuto:** más conservador, pero puede frenar reintentos legítimos cuando varias sucursales comparten una clave.
- **Límite por empresa en lugar de por clave:** mezcla integraciones distintas de la misma empresa; la guía técnica ya lo fija por clave.

## Consecuencias
- La guía técnica §12.1 y §19 dejan de marcarlo como `[DECISIÓN]`.
- n8n reintenta los 429 respetando `Retry-After` (§12.7); la plantilla de F5 lo configura así.
- Un cierre diario por sucursal queda muy por debajo del límite.
