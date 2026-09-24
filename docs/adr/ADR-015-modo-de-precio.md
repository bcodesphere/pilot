# ADR-015 — Modo de precio `CON_IVA` / `SIN_IVA`

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Algunos negocios registran precios con IVA incluido y otros más IVA. El mismo monto produce asientos distintos según cómo se interprete.

## Decisión
- `CON_IVA`: se respeta el monto ingresado como total; `iva = round(monto × t / (1 + t), 2)`, `base = monto − iva`.
- `SIN_IVA`: se respeta el monto como base; `iva = round(monto × t, 2)`.
- **Registro manual:** modo por asiento, con valor inicial tomado de la configuración.
- **Operaciones de n8n:** siempre el modo por defecto de la configuración de la empresa.
- El modo usado se guarda en el asiento y en la operación para explicar sus centavos.

## Alternativas consideradas
- **Modo por línea:** confuso para el usuario y complica el redondeo.
- **Que la app de origen envíe el IVA:** viola ADR-006.

## Consecuencias
- Si la configuración no coincide con la app de origen, el cierre no cuadra y se rechaza con `INT-006` y la diferencia exacta.
- Reglas pendientes de validación por contador (`docs/contabilidad/formulario-iva.md`).
