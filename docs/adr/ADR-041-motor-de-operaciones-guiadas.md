# ADR-041 — Motor único de operaciones guiadas

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
El Libro Diario de F3 exige que el usuario escriba Debe y Haber y elija cada cuenta: es el libro en papel llevado a una pantalla. El usuario de Pilot 1.0 es una persona natural o una PYME sin formación contable (ADR-031, ADR-032). Ya existe un mecanismo que decide las cuentas a partir de un hecho de negocio: las reglas de contabilización del cierre de n8n (ADR-020). El usuario decidió el 2026-09-27 automatizar el ciclo contable con **operaciones guiadas y un tablero**, dentro del alcance de 1.0 (spec `docs/diseno/2026-09-27-automatizacion-y-rediseno.md`, decisiones D1, D3, D4 y D5).

## Decisión
1. **Un solo motor en `contabilidad`:** `ContabilizarOperacion` (API pública del módulo) recibe un tipo de operación y sus datos. Una estrategia por tipo (armador) produce un `AsientoExpandido`, que pasa por `ReglasAsiento` y por `GuardarAsiento`: la misma numeración, mayorización y auditoría que un asiento manual, en la transacción del llamador (ADR-018).
2. **Diez tipos guiados:** `VENTA`, `COMPRA_GASTO`, `COBRO_CLIENTE`, `PAGO_PROVEEDOR`, `APORTE_CAPITAL`, `PRESTAMO_RECIBIDO`, `PAGO_CUOTA`, `TRASLADO_FONDOS`, `COMPRA_ACTIVO_FIJO` y `DEPRECIACION_MENSUAL`. El cierre de n8n (`CIERRE_INGRESOS_DIARIO`) es un tipo más del mismo motor. Los asientos exactos de cada tipo están en la spec §5.3.
3. **Cuentas por reglas:** las reglas de contabilización se amplían a los tipos nuevos, con las categorías `INGRESO`, `COBRO`, `PAGO`, `GASTO`, `CONTRAPARTIDA`, `ACTIVO` y `DEPRECIACION`. Se precargan como borrador `[VERIFICAR]` con contador (patrón de ADR-034) y se copian también a las empresas existentes.
4. **Registro de la operación:** la tabla `operacion` guarda la entrada de negocio, su resumen y el asiento generado (`asiento.origen_tipo = OPERACION`). Las operaciones son inmutables: revertir su asiento las pasa a `REVERTIDA` con el evento `AsientoRevertido` (ADR-036).
5. **Activos fijos:** `COMPRA_ACTIVO_FIJO` da de alta el activo. `DEPRECIACION_MENSUAL` calcula la cuota en línea recta por activo, una sola vez por activo y mes. Las vidas útiles, el valor residual y el mes de inicio son `[VERIFICAR]`: mientras no se confirmen, la depreciación responde 422 `CON-023`.
6. **API:** una ruta por tipo con su vista previa (`POST /contabilidad/operaciones/<tipo>` y `…/vista-previa`), agrupadas en las etiquetas `operacionesIngresos`, `operacionesGastos`, `operacionesBancos` y `operacionesActivos`, más `operaciones` (listado y detalle) y `tablero`.
7. **El asiento libre se conserva** como "Asiento manual (avanzado)" para ajustes, reclasificaciones y retenciones.
8. **Tablero:** `GET /contabilidad/tablero` resume efectivo, cuentas por cobrar y por pagar, resultado del mes, IVA estimado y los pendientes accionables.

## Alternativas consideradas
- **Plantillas en el frontend que rellenan el formulario de Debe/Haber:** el navegador decidiría las cuentas, contra la regla 1.2.1 y ADR-006.
- **Plantillas de asiento configurables por el usuario:** están en la guía técnica §20.3 y contradicen los bloqueos de ADR-042.
- **Asientos recurrentes y cierres de período:** fuera de alcance por decisión del usuario (D1); siguen en §20.3.

## Consecuencias
- La lógica contable sigue solo en el núcleo Java y cada operación deja un asiento auditable y reversible.
- La tarea F5-02 anterior se reemplaza por el motor genérico (B2 del plan de F4.5); el webhook de F5 usa el mismo motor.
- Nuevas migraciones: V16 (operaciones y reglas), V18 (activos fijos). Nuevo código de error `CON-023`.
- Los clientes y proveedores individuales siguen fuera de alcance (§20.2): el tercero se anota en la descripción.
