# ADR-029 — Empresa personal automática; empresa jurídica en Enterprise

- **Estado:** Aceptada
- **Fecha:** 2026-09-24

## Contexto
Pilot aísla todos los datos por empresa (ADR-002), así que todo usuario necesita una empresa para usar Contabilidad. Por decisión de producto, el registro de empresas jurídicas (NIT de 14 dígitos) es exclusivo de la edición **Enterprise**, que se obtiene con un upgrade cuya lógica no se implementa en 1.0.

## Decisión
1. `empresa.tipo`: `PERSONAL` o `JURIDICA`. En 1.0 **solo se crean empresas `PERSONAL`**.
2. **Alta automática:** en el primer inicio de sesión, en la misma transacción que el alta del usuario, se crea su empresa `PERSONAL` (nombre = nombre del usuario, NIT y NRC vacíos) y su membresía `admin_empresa`. Sin apps instaladas (ADR-030).
3. Una sola empresa `PERSONAL` por usuario, garantizada por un índice único parcial sobre el usuario propietario.
4. `empresa.nit` pasa a ser **nulo** en `PERSONAL`, y obligatorio de 14 dígitos en `JURIDICA` (restricción `CHECK` por tipo). En 1.0 el administrador puede completar opcionalmente NIT (14 dígitos) y NRC desde la edición de la empresa. `[VERIFICAR]` formato del NRC.
5. Un usuario puede pertenecer a otras empresas si un administrador lo agrega (ADR-028), así que el selector de empresa se mantiene.
6. `POST /empresas` (registro de empresa jurídica) **sale de 1.0** y pasa a la edición Enterprise (guía técnica §20).

## Alternativas consideradas
- **Empresa creada a mano en un segundo paso:** agrega fricción sin aportar nada, porque la empresa personal no pide datos.
- **Usuario sin empresa propia:** en 1.0 nadie podría crear empresas.

## Consecuencias
- La guía técnica §2.1, §9.2 y §13 cambian. La tarea 4 de F1 del plan se reemplaza por el alta automática.
- Las futuras funciones Enterprise (empresa jurídica, upgrade, planes) requieren su propio ADR.
