# ADR-036 — Libro Diario de F3: códigos de error, vista previa, montos de entrada, reversión y defensas de base de datos

- **Estado:** Aceptada
- **Fecha:** 2026-09-26

## Contexto
F3 implementa el Libro Diario: registro manual de asientos con IVA, mayorización en tiempo real y reversión (plan de trabajo F3; guía técnica §9.3, §10.1, §10.3, §11). Al bajar el diseño al contrato y a las migraciones aparecieron estos huecos:
1. La tabla de validaciones de §10.1 no dice el estado HTTP de `CON-001` a `CON-007` ni de `CON-013`, y `CON-008` y `CON-009` (reversión) no están en ninguna tabla.
2. El plan exige que un monto negativo responda `CON-003`, pero el esquema `Monto` del contrato ya rechaza los negativos con `PLT-002` (validación de forma), y lo mismo pasaría con `minItems: 2` y `CON-001`.
3. La guía no dice qué responde `POST /contabilidad/asientos/vista-previa` cuando las líneas no cuadran, aunque el frontend necesita la diferencia para mostrarla.
4. No hay regla para la fecha de una reversión ni para una fecha sin tasa de IVA vigente.
5. Si se revierte un asiento que vino de n8n, la operación externa debe pasar a `REVERTIDO` (§10.1, §12.8), pero `contabilidad` no puede depender de `integracion` (§4.2).

## Decisión
1. **Estados HTTP de los códigos del Libro Diario** (guía técnica §10.1):
   - `CON-001` a `CON-005`, `CON-007` y `CON-013`: 422;
   - `CON-008` (el asiento ya está revertido) y `CON-009` (una reversión no se revierte): 409, porque dependen del estado del recurso;
   - **`CON-017`** (422, nuevo): no hay tasa de IVA vigente a la fecha del asiento;
   - **`CON-018`** (422, nuevo): la fecha de una reversión es anterior a la del asiento original.
2. **Las reglas contables no se expresan en el esquema.** `debe` y `haber` usan un esquema de entrada propio (`MontoEntrada`: cadena decimal con signo y cualquier cantidad de decimales), y el arreglo de líneas no lleva `minItems`. Así la regla de negocio responde `CON-001` y `CON-003`, igual que en el frontend y en el trigger. Se mantiene `maxItems: 200` (`PLT-002`). Las respuestas siguen usando `Monto` (2 decimales exactos, ADR-013).
3. **Vista previa:** responde 200 con las líneas expandidas (incluidas las de IVA calculado), los totales, la `diferencia` y un indicador `cuadra`, **aunque el asiento no cuadre o tenga menos de dos líneas**: el frontend valida `CON-001`, `CON-004` y `CON-005` sobre esas líneas (§10.1). Responde 422 solo si la expansión es imposible: `CON-002`, `CON-003`, `CON-006`, `CON-007`, `CON-013` y `CON-017`. No guarda nada y no exige `Idempotency-Key`.
4. **Modo de precio:** es opcional en el cuerpo; si falta, se usa el de la configuración contable. `asiento.modo_precio` se guarda solo si alguna línea lleva IVA; si no, queda nulo.
5. **Reversión:**
   - el cuerpo es opcional, con una sola propiedad `fecha`, que por defecto es hoy en hora de El Salvador;
   - la fecha no puede ser futura (`CON-007`, decisión del usuario del 2026-09-26: no se permiten asientos con fecha futura) ni anterior a la del original (`CON-018`, decisión del usuario del 2026-09-26);
   - el concepto es fijo: `Reversión del asiento N.º <numero>/<anio>`;
   - la reversión se numera en el año de su propia fecha.
6. **Evento `AsientoRevertido`:** `RevertirAsiento` publica un evento **síncrono** en la API pública de `contabilidad`, dentro de su transacción, con el id del asiento original, su `origen_tipo` y su `origen_id`. En F5, `integracion` lo escucha y marca la operación `REVERTIDO`; hasta entonces nadie lo escucha. Sigue el patrón de `AplicacionInstalada` (ADR-030).
7. **Defensas adicionales en la base de datos** (además de las de §9.3); `asiento.creado_por` es `VARCHAR(64)` con el valor de `app.usuario_id`, como las tablas de V11, porque un asiento de n8n no lo crea un usuario:
   - `CHECK (anio = EXTRACT(YEAR FROM fecha))` en `asiento`;
   - `CHECK` de coherencia de la reversión: `origen_tipo = 'REVERSION'` si y solo si `asiento_revertido_id` no es nulo;
   - llaves foráneas compuestas `(empresa_id, …)` de `asiento_linea` hacia `asiento` y `cuenta_contable`, y de `saldo_cuenta_mensual` hacia `cuenta_contable` (como en ADR-035);
   - el trigger diferido de partida doble también corre al insertar la cabecera, para que un asiento **sin líneas** no pase el `COMMIT`, y comprueba además que `total_debe` y `total_haber` de la cabecera sean iguales a la suma de sus líneas y que la `fecha` de cada línea sea la del asiento.
8. **Numeración:** `correlativo_asiento` se incrementa con `INSERT … ON CONFLICT DO UPDATE … RETURNING`, dentro de la transacción del asiento, para que el primer asiento del año no necesite una fila previa. El bloqueo de la fila serializa la numeración sin huecos entre transacciones que terminan bien.
9. **Libro Diario (`GET /contabilidad/asientos`):** paginación por cursor ordenada por `(anio, numero)` ascendente, con filtros por rango de fechas, número, origen, estado y cuenta.

## Alternativas consideradas
- **Validar en el esquema OpenAPI (patrón no negativo, `minItems: 2`):** daría `PLT-002` en lugar de `CON-003` y `CON-001`, contra el criterio del plan y el mensaje que ya muestra el frontend.
- **Vista previa con 422 cuando no cuadra:** el frontend no podría mostrar las líneas expandidas ni la diferencia en modo `SIN_IVA`.
- **Llamar desde `contabilidad` a `integracion` al revertir:** crea una dependencia prohibida por §4.2.
- **`UPDATE correlativo_asiento … RETURNING` sin upsert:** exige sembrar una fila por empresa y año.

## Consecuencias
- La guía técnica §10.1 agrega una columna HTTP y los códigos `CON-008`, `CON-009`, `CON-017` y `CON-018`, y §19 marca resuelta la pregunta de la fecha futura.
- El contrato agrega el esquema `MontoEntrada`, que solo se usa en la entrada de líneas de asiento.
- La prueba de aceptación de F3 cubre `CON-011` y `CON-012` con movimientos reales (pendiente de F2, ADR-035).
