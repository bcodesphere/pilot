# ADR-042 — Bloqueos de edición: catálogo base, naturaleza, cuentas de IVA y reglas por grupo

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
En F2 el usuario puede cambiar el código, el nombre, la naturaleza y el estado de las cuentas del catálogo base, las cuentas de IVA de la configuración y asignar cualquier cuenta de detalle a una regla de contabilización. Con operaciones automáticas (ADR-041), esas libertades permiten romper la contabilización sin darse cuenta: una regla de cobro apuntando a una cuenta de ingresos, o una cuenta de IVA cambiada. El usuario decidió el 2026-09-27 qué deja de ser editable (spec, decisión D2).

## Decisión
1. **Cuentas del sistema:** `cuenta_contable.sistema` es verdadero en las cuentas copiadas del catálogo base, también en las empresas existentes (V17). Su código, nombre, naturaleza y estado no se editan: 422 **`CON-021`**. El usuario agrega subcuentas propias bajo las cuentas base que no son de detalle, y esas sí las edita.
2. **Naturaleza derivada:** la de la cuenta padre, o la de la clase en el nivel 1. Deja de ser un campo de entrada en `POST` y `PATCH /contabilidad/cuentas`; en la respuesta es de solo lectura. Así la depreciación acumulada (bajo 1202) sale acreedora sin intervención.
3. **Cuentas de IVA fijas:** IVA débito 21020101 e IVA crédito 11040101, desde la plantilla. `PUT /contabilidad/configuracion` solo acepta el modo de precio.
4. **Reglas por grupo:** cada regla tiene un `prefijo_permitido`. Su cuenta debe ser de detalle, estar activa y tener un código que empiece por ese prefijo: 422 **`CON-022`** si no. El traslado de fondos solo acepta cuentas de detalle del grupo 1101.
5. **Inmutables, como antes:** operaciones, asientos y activos; se corrigen revirtiendo (ADR-019).
6. **Interfaz:** un valor bloqueado se muestra como texto con un candado y el motivo, no como un campo deshabilitado; los selectores solo ofrecen las cuentas válidas.

## Alternativas consideradas
- **Bloquear solo en el frontend:** una llamada directa a la API podría saltarlo; los bloqueos se aplican en el backend.
- **Permitir desactivar cuentas base sin uso:** el catálogo base se mantiene completo; la interfaz oculta las cuentas sin movimiento con un filtro.

## Consecuencias
- Nuevos códigos `CON-021` y `CON-022` en la guía técnica §10.2.
- Cambian los esquemas `CuentaNueva`, `CuentaCambios`, `Cuenta` (`sistema`), `ConfiguracionContableCambios` y `ReglaContabilizacion` (`prefijoPermitido`).
- La migración V16/V17 levanta temporalmente el `FORCE ROW LEVEL SECURITY` de `regla_contabilizacion` y `cuenta_contable` dentro de su transacción para completar las empresas existentes, y lo restablece.
