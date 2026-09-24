# ADR-020 — Reglas de contabilización por tipo de operación y código

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Cada negocio usa cuentas distintas para sus ventas y formas de cobro. Las integraciones no deben enviar cuentas contables.

## Decisión
Tabla `regla_contabilizacion` por empresa: `tipo_operacion` × `categoria` (`INGRESO` o `COBRO`) × `codigo` (p. ej. `VENTAS_GRAVADAS`, `EFECTIVO`) → cuenta de detalle. Se precarga al registrar la empresa y el contador la edita. La cuenta de IVA débito sale de `configuracion_contable`. Un código sin regla activa rechaza la operación con `CON-020`.

## Alternativas consideradas
- **Cuentas fijas:** no se adaptan a cada negocio.
- **Que n8n envíe las cuentas:** viola la regla 1.2.4 y ADR-009.
- **Asiento en borrador para revisión:** contradice ADR-019 y la inmediatez del cierre.

## Consecuencias
- Cambiar una regla afecta solo operaciones futuras y queda auditado.
