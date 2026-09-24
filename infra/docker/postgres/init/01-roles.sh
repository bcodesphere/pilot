#!/bin/bash
# 01-roles.sh — crea el rol de la aplicación (pilot_app) al inicializar PostgreSQL (CLAUDE.md §4.5, regla 1.2.11).
# Corre una sola vez, con el volumen vacío, como POSTGRES_USER (pilot_owner). Es idempotente.
# Los esquemas y tablas los crea Flyway (F0-04); aquí solo roles y permisos base.
# Requiere PG_APP_PASSWORD en el entorno; la contraseña no se imprime.
set -euo pipefail

# 1. Falla temprano si falta la contraseña
: "${PG_APP_PASSWORD:?PG_APP_PASSWORD no definida}"

# 2. Se apaga el eco de comandos para no filtrar la contraseña en logs
set +x

# 3. Crea o actualiza el rol; la contraseña viaja como variable psql (:'pw'), con el quoting correcto.
#    Atributos: sin superusuario, sin BYPASSRLS (RLS siempre aplica), sin crear bases ni roles, sin herencia.
#    La contraseña NO se pasa como argumento: psql la lee del entorno con \getenv dentro del script SQL
#    (stdin), así que no aparece en la lista de procesos. ADVERTENCIA: el ALTER ROLE ... PASSWORD
#    quedaría en el log del servidor si se activa log_statement=all; no lo active al inicializar.
psql -v ON_ERROR_STOP=1 -q --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv pw PG_APP_PASSWORD
\getenv db POSTGRES_DB
-- Crea el rol solo si no existe (idempotencia)
SELECT 'CREATE ROLE pilot_app' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'pilot_app') \gexec

-- Fija atributos y contraseña (también al re-ejecutar)
SELECT format('ALTER ROLE pilot_app WITH LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', :'pw') \gexec

-- Puede conectarse a la base de Pilot
SELECT format('GRANT CONNECT ON DATABASE %I TO pilot_app', :'db') \gexec

-- Puede usar (no crear en) el esquema public; nadie más crea objetos allí
GRANT USAGE ON SCHEMA public TO pilot_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SQL
