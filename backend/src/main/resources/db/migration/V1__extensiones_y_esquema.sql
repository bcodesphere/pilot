-- V1 — Extensiones y permisos base (CLAUDE.md §4.5, §9.3).
-- Flyway corre como pilot_owner. La aplicación usa pilot_app, que solo recibe permisos tabla por tabla
-- en cada migración (sin ALTER DEFAULT PRIVILEGES amplios: cada tabla nueva declara sus propios GRANT).

-- 1. btree_gist: la necesitará la restricción EXCLUDE USING gist de tasa_impuesto (F2, CLAUDE.md §9.3).
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 2. pilot_app puede usar (no crear en) el esquema public. Es idempotente: 01-roles.sh ya lo concede,
--    pero se repite aquí para que las migraciones no dependan de un script fuera de Flyway.
GRANT USAGE ON SCHEMA public TO pilot_app;

-- 3. Nadie más que el dueño crea objetos en public (defensa en profundidad; 01-roles.sh también lo aplica).
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
