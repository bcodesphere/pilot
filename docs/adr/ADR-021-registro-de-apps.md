# ADR-021 — Registro de apps y shell dinámico

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
El ERP debe estar preparado para agregar apps, aunque en 1.0 solo Contabilidad esté activa.

## Decisión
- **Base de datos:** `aplicacion` (catálogo global) y `empresa_aplicacion` (apps activas por empresa).
- **Backend:** cada app es un módulo Spring Modulith; un filtro rechaza con `PLT-004` las rutas de apps inactivas.
- **Frontend:** el shell (`src/nucleo/`) carga `GET /aplicaciones` y registra las rutas de `src/apps/<app>/`; ninguna app importa código de otra.

## Alternativas consideradas
- **Menú fijo en el frontend:** cada app nueva obliga a modificar el shell.
- **Micro-frontends:** complejidad innecesaria para un equipo pequeño.

## Consecuencias
- Agregar una app = módulo backend + carpeta en `src/apps/` + fila en `aplicacion` + ADR.
