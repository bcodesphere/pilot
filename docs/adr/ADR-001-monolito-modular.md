# ADR-001 — Monolito modular con Spring Modulith y arquitectura hexagonal

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Pilot debe crecer por apps (hoy solo Contabilidad) con un equipo pequeño y bajo costo de operación.

## Decisión
Un solo artefacto Spring Boot dividido en módulos (`compartido`, `plataforma`, `contabilidad`, `integracion`) cuyos límites verifica Spring Modulith. Cada módulo usa capas hexagonales `api / aplicacion / dominio / infraestructura`. Los módulos se comunican solo por su API pública.

## Alternativas consideradas
- **Microservicios:** mayor costo operativo y complejidad sin una necesidad medida de escala.
- **Monolito sin límites:** rápido al inicio, pero acopla los módulos y dificulta agregar apps.

## Consecuencias
- `ApplicationModules.verify()` forma parte de `./mvnw verify`.
- Un módulo podrá extraerse a servicio solo si hay una necesidad medida.
