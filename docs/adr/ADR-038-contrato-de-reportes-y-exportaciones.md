# ADR-038 — Contrato de F4: reportes contables en JSON y exportaciones como recursos binarios

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
F4 publica los reportes de la guía técnica §10.4 y §10.5 (Libro Mayor, Balanza de Comprobación, Estado de Situación Financiera, Estado de Resultados, resumen de IVA y diagnóstico de mayorización) y su exportación a PDF, XLSX y CSV. La guía (§13) decía que los endpoints de reportes aceptan `?formato=pdf|xlsx|csv` o el header `Accept` y que, por defecto, responden JSON.

Al bajarlo al contrato apareció un problema con el generador (`openapi-generator` para Spring, `interfaceOnly`): una respuesta 200 que declara a la vez `application/json` con un esquema y `application/pdf` binario genera un método con un tipo de retorno fijo, el del JSON (comprobado el 2026-09-27: `ResponseEntity<ResumenCuenta>`). Con esa firma, el controlador no puede devolver un PDF. En cambio, una operación que solo declara tipos binarios genera `ResponseEntity<org.springframework.core.io.Resource>`.

Además quedaban sin definir la forma de los saldos, los filtros de período, el trato de las cuentas padre en el Mayor y los errores de los reportes.

## Decisión
1. **Reportes en JSON y exportaciones en operaciones propias.** Cada reporte tiene su ruta JSON y, aparte, una ruta `…/exportacion?formato=pdf|xlsx|csv` con los **mismos filtros**, que responde el archivo (`Content-Disposition: attachment` con un nombre legible). No se usa el header `Accept`. Rutas de exportación:
   - `GET /contabilidad/asientos/exportacion` (Libro Diario, con los filtros de `GET /contabilidad/asientos`, sin paginar);
   - `GET /contabilidad/mayor/exportacion`, `GET /contabilidad/balanza/exportacion`;
   - `GET /contabilidad/estados/situacion-financiera/exportacion`, `GET /contabilidad/estados/resultados/exportacion`;
   - `GET /contabilidad/reportes/iva/exportacion`.
   La bitácora de n8n se exporta en F5 con el mismo patrón.
2. **Etiquetas:** `reportesContables` (Mayor, Balanza, dos estados, resumen de IVA y diagnóstico) y `exportacionesContables` (las seis exportaciones), para que cada una sea una interfaz que implementa un solo controlador (guía técnica §8.1). `GET /contabilidad/asientos` no cambia.
3. **Sin paginación en los reportes:** se generan completos para el período; el rango lo acota el usuario. Rendimiento objetivo del plan: año de 10,000 asientos en menos de 2 s (p95).
4. **Saldos:** esquema `Saldo` con `monto` (`Monto`, valor absoluto), `lado` (`DEUDOR`, `ACREEDOR` o `CERO`) y `contrarioNaturaleza` (booleano: la alerta de §10.3). Los movimientos (`debe`, `haber`) usan `Monto`, y las diferencias, `MontoConSigno`.
5. **Libro Mayor** (`GET /contabilidad/mayor?cuentaId&desde&hasta`): acepta **cualquier cuenta**. En una cuenta padre, los movimientos son los de todas sus cuentas de detalle (código con su prefijo), y cada línea identifica su cuenta. Devuelve saldo inicial, movimientos con saldo acumulado línea a línea, totales y saldo final.
6. **Balanza de Comprobación** (`GET /contabilidad/balanza?desde&hasta&nivel`): una fila por cuenta con saldo o movimiento hasta el nivel pedido (1 a 5; 5 por defecto), con saldo inicial, Debe, Haber y saldo final. Los totales salen **solo de las cuentas de detalle**, para no contar dos veces: Σ Debe = Σ Haber y Σ saldos deudores = Σ saldos acreedores.
7. **Estados** (ADR-016, ADR-037): filas jerárquicas (código, nombre, nivel, monto en positivo según la naturaleza de la clase) hasta el `nivel` pedido, con `incluirCeros` falso por defecto; rubros totales; en resultados, la utilidad antes de impuesto, el impuesto sobre la renta (grupo 44) y la utilidad del ejercicio; en situación financiera, los resultados de ejercicios anteriores no cerrados, la utilidad del ejercicio y la **comprobación** (`cuadra`, `diferencia`); y el campo `leyenda` de estado de gestión.
8. **Resumen de IVA** (`GET /contabilidad/reportes/iva?anio&mes`): IVA débito fiscal (Haber − Debe de la cuenta configurada) e IVA crédito fiscal (Debe − Haber), cada uno desglosado por el origen del asiento (`MANUAL`, `N8N`, `REVERSION`), y la diferencia estimada (débito − crédito). Es un punto de partida, no la declaración (§10.5).
9. **Diagnóstico** (`GET /contabilidad/diagnostico/mayorizacion`, rol `contador`): `consistente` y la lista de diferencias por cuenta, año y mes entre `saldo_cuenta_mensual` y la suma de las líneas.
10. **Errores** (sin códigos nuevos): 422 `PLT-002` para un rango con `desde` posterior a `hasta`, un `nivel` fuera de 1 a 5, un mes fuera de 1 a 12 o un formato desconocido; 404 `PLT-017` para una cuenta inexistente o de otra empresa; 403 `PLT-004` si Contabilidad no está instalada. Las fechas futuras se permiten en los reportes.
11. **Período:** los reportes filtran por `desde`/`hasta` (el frontend ofrece atajos de mes y año); el resumen de IVA, por `anio`/`mes`.

## Alternativas consideradas
- **Una sola operación con JSON y binarios:** el generador fija el tipo de retorno al del JSON; habría que escribir el controlador a mano, contra ADR-003.
- **Una exportación genérica `?reporte=…`:** mezcla los filtros de todos los reportes en una sola firma y no se puede validar en el esquema.
- **Paginar los reportes:** complica los totales y la exportación sin un beneficio claro con los volúmenes de una PYME.

## Consecuencias
- La guía técnica §13 lista las rutas de exportación y deja de mencionar el header `Accept`.
- El frontend descarga los archivos como `Blob` con el cliente generado por Orval.
- F5 agrega la exportación de la bitácora con el mismo patrón.
