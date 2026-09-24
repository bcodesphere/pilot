# ADR-023 — Java 21 LTS como versión del backend en 1.0

- **Estado:** Aceptada
- **Fecha:** 2026-09-24

## Contexto
CLAUDE.md §5.1 fijaba Java 25 LTS como objetivo, con Java 21 como mínimo. El entorno de desarrollo tiene JDK 21, y el esqueleto del backend (F0-02) compila y pasa todas las verificaciones con `maven.compiler.release` = 21. Ninguna dependencia del stack (Spring Boot 4.1, Spring Modulith 2.1, Hibernate 7, Testcontainers 2) exige Java 25.

## Decisión
El backend de Pilot 1.0 se compila y ejecuta con **Java 21 LTS** (`maven.compiler.release` = 21), en desarrollo, CI (Temurin 21) e imágenes de producción.

## Alternativas consideradas
- **Java 25 LTS:** soporte más largo y funciones del lenguaje más recientes, pero obliga a instalar y mantener otro JDK en todos los entornos sin que ninguna dependencia lo requiera hoy.

## Consecuencias
- No se usan funciones de lenguaje ni APIs posteriores a Java 21 (ni funciones en vista previa).
- Migrar a Java 25 más adelante requiere un ADR nuevo que reemplace este; el cambio se limita a `maven.compiler.release`, la CI y la imagen base.
