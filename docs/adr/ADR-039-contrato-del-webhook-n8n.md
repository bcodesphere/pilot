# ADR-039 — Contrato de F5: webhook de operaciones de n8n, esquema JSON v1 y bitácora

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
F5 publica el webhook `POST /integraciones/n8n/operaciones` (guía técnica §12) y la bitácora de operaciones (§10.5, §13). Al bajarlo al contrato aparecieron estos puntos:
1. El plan de F5 dice que una repetición con la misma `Idempotency-Key` y el mismo cuerpo responde **200**. La guía (§12.6) y el mecanismo de idempotencia de F0 y F3 devuelven la respuesta guardada tal cual (201, o 409 `INT-004`).
2. §12.1 limita el cuerpo a 1 MB, pero el catálogo no tiene código de error para un cuerpo más grande.
3. El cuerpo debe validarse contra su esquema JSON versionado **antes** de interpretarlo (regla 1.1.14), y el tipo de operación y la versión eligen el esquema. Un cuerpo modelado en OpenAPI con todos sus campos haría que Jackson lo interpretara primero.
4. JSON Schema no puede expresar "sin códigos repetidos" en un arreglo de objetos, ni "al menos un ingreso y un cobro mayores que cero".
5. La bitácora mezcla dos tablas: operaciones aceptadas (`operacion_externa`) y rechazos (`intento_operacion_externa`).

## Decisión
1. **Repetición idempotente** (decisión del usuario, 2026-09-27): se devuelve la respuesta guardada **con su estado original** (201 o 409 `INT-004`) y `Idempotency-Replayed: true`. El texto del plan se corrige.
2. **Cuerpo mayor que 1 MB** (decisión del usuario, 2026-09-27): **413 `INT-010`** ("el cuerpo supera 1 MB"); n8n no debe reintentar. Se registra en la bitácora de rechazos sin `idExterno`.
3. **Cuerpo libre en OpenAPI:** el esquema `OperacionN8n` del contrato es un objeto libre (`type: object`), con `tipoOperacion` y `version` documentados y un ejemplo. La validación completa es la del esquema JSON `api-spec/esquemas/operaciones/<tipo>/v<n>.json`, elegido por `tipoOperacion` y `version`. Un tipo o una versión desconocidos responden 422 `INT-002`, y un cuerpo que no cumple el esquema, 400 `INT-001` con `errores`.
4. **Esquema `cierre-ingresos-diario/v1.json`** (JSON Schema 2020-12, `additionalProperties: false`): los campos de §12.3, con los enums de `concepto` y `formaPago`, montos como cadena `^\d{1,17}\.\d{2}$` y `const` en `tipoOperacion` y `version`. Las reglas que JSON Schema no expresa se validan en el núcleo, después del esquema, y también responden **400 `INT-001`** con `errores`: códigos repetidos en `ingresos` o en `cobros`, y "al menos un ingreso y un cobro mayores que cero".
5. **Respuesta 201** (`OperacionContabilizada`): la de §12.7, con el `anio` del asiento agregado para mostrar su número (`N.º numero/anio`). El 409 `INT-004` agrega a Problem Details `operacionId` y `asientoId`, como propiedades opcionales.
6. **Bitácora** (etiqueta `bitacoraOperaciones`, rol `auditor`, con OIDC y `X-Empresa-Id`):
   - `GET /integraciones/operaciones`: paginada por cursor, de la más reciente a la más antigua, con filtros de fecha de recepción (`desde`/`hasta`), `resultado` (`CONTABILIZADO`, `REVERTIDO`, `RECHAZADO`) y `sistemaOrigen`. Cada entrada tiene `clase` (`OPERACION` o `RECHAZO`) y los campos comunes; los que no aplican van nulos.
   - `GET /integraciones/operaciones/{entradaId}`: el detalle, con el resumen normalizado de una operación o el Problem Details de un rechazo, sin datos personales.
   - `GET /integraciones/operaciones/exportacion?formato=`: exportación binaria con los mismos filtros (ADR-038).
7. **Seguridad:** el webhook usa solo el esquema `apiKey` (la empresa sale de la clave, sin `X-Empresa-Id`) con el alcance `integracion:operaciones`. Documenta 429 con `Retry-After`; el límite concreto sigue como `[DECISIÓN]` (60/min propuesto, §19).

## Alternativas consideradas
- **200 en la repetición:** obliga a un comportamiento distinto solo para el webhook y rompe el mecanismo común de idempotencia.
- **400 `INT-001` para un cuerpo de más de 1 MB:** no permite distinguir en la bitácora un cuerpo demasiado grande de uno mal formado.
- **Modelar el cuerpo completo en OpenAPI:** Jackson lo interpretaría antes de la validación del esquema versionado (contra la regla 1.1.14) y duplicaría el esquema.

## Consecuencias
- La guía técnica §12 incorpora `INT-010` y las precisiones de validación; §13, la exportación de la bitácora.
- El plan de F5 dice "repetición → respuesta original (201) con `Idempotency-Replayed: true`".
