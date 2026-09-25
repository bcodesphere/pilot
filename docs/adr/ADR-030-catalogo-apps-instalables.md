# ADR-030 — Catálogo de apps instalables y apps Enterprise bloqueadas

- **Estado:** Aceptada (amplía ADR-021)
- **Fecha:** 2026-09-24

## Contexto
ADR-021 preparó el registro de apps con Contabilidad como única app activa. Por decisión de producto, las apps se **instalan** por empresa, al estilo de Odoo: el usuario ve todas las apps disponibles e instala las que le convengan. Algunas apps solo estarán en la edición Enterprise, cuya lógica de upgrade no se implementa en 1.0. Además, `plataforma` no debe depender de ningún módulo de app.

## Decisión
1. `aplicacion` agrega `edicion` (`COMUNITARIA` o `ENTERPRISE`) y `orden` de presentación. Se cargan por migración:
   - `contabilidad` — `COMUNITARIA`, instalable;
   - `ventas`, `clientes`, `proveedores`, `inventario`, `marketing` — `ENTERPRISE`, visibles pero **bloqueadas** (no instalables).
2. `GET /aplicaciones` devuelve el **catálogo completo** con el estado de cada app para la empresa activa: `INSTALADA`, `DISPONIBLE` o `BLOQUEADA_ENTERPRISE`.
3. `POST /aplicaciones/{codigo}/instalacion` (rol `admin_empresa`) instala una app `COMUNITARIA`. Instalar una app ya instalada no hace nada nuevo. Instalar una app Enterprise responde 403 con un código `PLT-` nuevo.
4. **Sin desinstalación en 1.0:** una app instalada no se puede quitar, porque se perderían libros contables.
5. **Precarga al instalar:** `plataforma` publica el evento síncrono `AplicacionInstalada(empresaId, codigo)` con `ApplicationEventPublisher`, **dentro de la misma transacción**. El módulo de la app lo escucha con `@EventListener` y hace su precarga. Contabilidad copia el catálogo, la configuración y las reglas en F2. Si la precarga falla, la instalación se revierte. `plataforma` no depende de ningún módulo de app. No se usa outbox ni el registro de publicaciones de Spring Modulith.
6. **Frontend:** el lanzador muestra las apps instaladas y una pantalla "Apps" muestra el catálogo, con el botón Instalar o la marca "Enterprise". Las rutas de una app no instalada siguen rechazándose con `PLT-004` (ADR-021).

## Alternativas consideradas
- **Contabilidad preinstalada:** contradice el modelo de apps instalables.
- **Desactivar en lugar de desinstalar:** se pospone; en 1.0 no hay necesidad.
- **Llamada directa de `plataforma` al módulo de la app:** crea la dependencia prohibida.

## Consecuencias
- En F1 el evento se publica pero nadie lo escucha todavía. F2 agrega el oyente de Contabilidad.
- Las apps Enterprise son solo filas del catálogo: no tienen módulo backend ni carpeta en el frontend hasta que un ADR las incorpore (guía técnica §20).
