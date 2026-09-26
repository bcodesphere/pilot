# ADR-035 — Catálogo y configuración contable de F2: plantillas, regla sin cuenta, catálogo sin paginar y códigos de error

- **Estado:** Aceptada
- **Fecha:** 2026-09-26

## Contexto
F2 implementa el catálogo de cuentas, la configuración contable y las reglas de contabilización precargadas (plan de trabajo F2; guía técnica §9.3, §10.2, §11.3, §12.5). Al bajar el diseño al contrato y a las migraciones aparecieron cinco huecos:
1. **Precarga:** la guía dice qué se precarga, pero no desde dónde. El catálogo base está en `plantilla_cuenta`; las reglas y la configuración por defecto no tenían tabla.
2. **La regla `COBRO/OTRO`** no tiene cuenta por defecto, pero `regla_contabilizacion.cuenta_id` era `NOT NULL`.
3. **Paginación:** la API pagina por cursor, pero el árbol del catálogo necesita todas las cuentas a la vez.
4. **Códigos de error:** el plan exige rechazar códigos duplicados y padres que no son prefijo, y la guía no tenía códigos para eso.
5. **Movimientos:** `CON-011` y `CON-012` dependen de los movimientos y saldos, y esas tablas llegan en F3.

## Decisión
1. **Plantillas globales**, cargadas por migración y de solo lectura para `pilot_app`:
   - `plantilla_cuenta`: el catálogo base;
   - `plantilla_regla_contabilizacion`: las reglas de §12.5, con el **código** de la cuenta;
   - `plantilla_configuracion_contable`: una fila con el modo de precio por defecto (`CON_IVA`) y los códigos de las cuentas de IVA débito (21020101) y crédito (11040101).

   Al instalar Contabilidad, el oyente de `AplicacionInstalada` copia las tres plantillas a la empresa, resolviendo los códigos a los `id` de sus cuentas, en la misma transacción que la instalación. Si algo falla, la instalación se revierte.
2. **`regla_contabilizacion.cuenta_id` admite nulo**, con `CHECK (NOT activa OR cuenta_id IS NOT NULL)`. `COBRO/OTRO` se precarga **inactiva y sin cuenta**, y el contador la activa al asignarle una. Una operación que use una regla inactiva o ausente se rechaza con `CON-020` (F5).
3. **`GET /contabilidad/cuentas` devuelve el catálogo completo sin paginar.** Es acotado (unos cientos de cuentas) y el árbol y el buscador lo necesitan entero. Admite filtros opcionales, y cada cuenta trae su `version` para `If-Match`.
4. **Códigos nuevos** en la guía técnica §10.2:
   - `CON-014` (409): código duplicado;
   - `CON-015` (422): longitud de código no válida o sin padre existente y activo cuyo código sea su prefijo. El padre se deduce del código, no se envía;
   - `CON-016` (422): una cuenta en uso por la configuración o por una regla activa no se desactiva ni deja de ser de detalle.

   Para la configuración y las reglas se reutiliza `CON-006`: la cuenta debe existir, estar activa y ser de detalle.
5. **Puerto de lectura de movimientos:** en F2 responde "sin movimientos ni saldo"; F3 lo reemplaza por la consulta a `asiento_linea` y `saldo_cuenta_mensual`. La prueba de aceptación de `CON-011` y `CON-012` con movimientos reales queda para F3.
6. **Integridad por empresa:** las referencias entre `cuenta_contable`, `configuracion_contable` y `regla_contabilizacion` usan llaves foráneas compuestas `(empresa_id, cuenta_id)`, para que una fila nunca apunte a la cuenta de otra empresa aunque falle RLS.

## Alternativas consideradas
- **Reglas y configuración por defecto como constantes Java:** mezcla datos con código y exige un despliegue para cambiarlas.
- **No precargar `OTRO`:** la pantalla de reglas tendría que crear filas y el contrato necesitaría un `POST`; se prefiere editar filas existentes.
- **Paginar el catálogo:** el frontend tendría que pedir todas las páginas para armar el árbol.

## Consecuencias
- Hay tres tablas globales nuevas en §9.1 y §9.3 de la guía técnica.
- El contrato de F2 usa etiquetas por recurso (`cuentasContables`, `configuracionContable`, `reglasContabilizacion`) en lugar de la etiqueta única `contabilidad` (§8.1).
- **Cambio de código de una cuenta (confirmado por el usuario el 2026-09-26):** el código nuevo debe tener la misma longitud y el mismo padre que el actual, y la cuenta no puede tener hijas ni movimientos. Si no se cumple, responde `CON-015` (`CON-011` si tiene movimientos). Así el árbol nunca se reordena; mover una cuenta de rama se hace creando otra y desactivando la anterior.
