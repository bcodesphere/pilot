-- V8 — Búsquedas previas a conocer la empresa (ADR-026).
-- Tres funciones SECURITY DEFINER propiedad de pilot_busqueda, un rol sin login, sin superusuario y sin BYPASSRLS
-- que solo puede LEER lo que ellas consultan. Las tablas tienen RLS forzado, que también aplica a su dueño, así que
-- un dueño pilot_owner no vería filas en producción; con este rol las pruebas reproducen ese comportamiento.

-- 1. Rol dueño de las funciones. Se crea solo si no existe (en producción puede crearse antes, ADR-026).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'pilot_busqueda') THEN
        CREATE ROLE pilot_busqueda NOLOGIN NOSUPERUSER NOBYPASSRLS NOINHERIT;
    END IF;
END $$;
COMMENT ON ROLE pilot_busqueda IS 'Dueño de las funciones SECURITY DEFINER de búsqueda sin empresa (ADR-026). Sin login, sin superusuario, sin BYPASSRLS; solo SELECT en empresa, empresa_usuario y api_key.';

-- Para poder asignarle la propiedad de las funciones (ALTER FUNCTION ... OWNER) el dueño de las migraciones debe
-- ser miembro del rol, también cuando no sea superusuario (producción, F6).
GRANT pilot_busqueda TO CURRENT_USER;

-- Necesita usar el esquema para resolver las tablas, y SELECT solo sobre las tres que consultan las funciones.
GRANT USAGE ON SCHEMA public TO pilot_busqueda;
GRANT SELECT ON empresa, empresa_usuario, api_key TO pilot_busqueda;

-- 2. Políticas de lectura para pilot_busqueda. aislamiento_empresa es TO pilot_app (V5 a V7), así que estas son
--    las únicas que le aplican a este rol y no evalúan current_setting('app.empresa_id').
CREATE POLICY busqueda_sin_empresa ON empresa FOR SELECT TO pilot_busqueda USING (true);
CREATE POLICY busqueda_sin_empresa ON empresa_usuario FOR SELECT TO pilot_busqueda USING (true);
CREATE POLICY busqueda_sin_empresa ON api_key FOR SELECT TO pilot_busqueda USING (true);
COMMENT ON POLICY busqueda_sin_empresa ON empresa IS 'Lectura sin filtro para pilot_busqueda; solo la usan las funciones SECURITY DEFINER (ADR-026).';
COMMENT ON POLICY busqueda_sin_empresa ON empresa_usuario IS 'Lectura sin filtro para pilot_busqueda; solo la usan las funciones SECURITY DEFINER (ADR-026).';
COMMENT ON POLICY busqueda_sin_empresa ON api_key IS 'Lectura sin filtro para pilot_busqueda; solo la usan las funciones SECURITY DEFINER (ADR-026).';

-- 3. Membresías activas de un usuario, de empresas activas (selector de empresa, GET /me).
CREATE FUNCTION membresias_de_usuario(p_usuario_id uuid)
RETURNS TABLE (empresa_id uuid, nombre_empresa varchar, tipo_empresa varchar, rol varchar)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    -- Solo campos necesarios; membresía y empresa deben estar activas; orden estable por nombre
    SELECT e.id, e.nombre, e.tipo, m.rol
      FROM empresa_usuario m
      JOIN empresa e ON e.id = m.empresa_id
     WHERE m.usuario_id = p_usuario_id
       AND m.estado = 'ACTIVA'
       AND e.estado = 'ACTIVA'
     ORDER BY e.nombre, e.id
$$;
COMMENT ON FUNCTION membresias_de_usuario(uuid) IS 'Membresías ACTIVAS de un usuario en empresas ACTIVAS: empresa, nombre, tipo y rol. SECURITY DEFINER de pilot_busqueda (ADR-026).';

-- 4. Rol del usuario en una empresa, o NULL si no es miembro activo (validación de X-Empresa-Id, PLT-003).
CREATE FUNCTION membresia_activa(p_usuario_id uuid, p_empresa_id uuid)
RETURNS varchar
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    -- Misma condición que membresias_de_usuario, para que el selector y la validación nunca discrepen
    SELECT m.rol
      FROM empresa_usuario m
      JOIN empresa e ON e.id = m.empresa_id
     WHERE m.usuario_id = p_usuario_id
       AND m.empresa_id = p_empresa_id
       AND m.estado = 'ACTIVA'
       AND e.estado = 'ACTIVA'
$$;
COMMENT ON FUNCTION membresia_activa(uuid, uuid) IS 'Rol del usuario en la empresa si la membresía y la empresa están ACTIVAS; NULL en otro caso. SECURITY DEFINER de pilot_busqueda (ADR-026).';

-- 5. API key por prefijo. Devuelve también las revocadas o vencidas: la aplicación decide el 401.
CREATE FUNCTION api_key_por_prefijo(p_prefijo varchar)
RETURNS TABLE (id uuid, empresa_id uuid, hash_secreto varchar, alcances text[], expira_en timestamptz, revocada_en timestamptz)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
    -- Solo lo necesario para verificar el secreto y derivar la empresa
    SELECT k.id, k.empresa_id, k.hash_secreto, k.alcances, k.expira_en, k.revocada_en
      FROM api_key k
     WHERE k.prefijo = p_prefijo
$$;
COMMENT ON FUNCTION api_key_por_prefijo(varchar) IS 'API key por prefijo: id, empresa, hash, alcances, expiración y revocación (incluye revocadas y vencidas). SECURITY DEFINER de pilot_busqueda (ADR-026).';

-- 6. Permisos de ejecución (antes de cambiar el dueño, para que el ACL viaje con la función).
-- Por defecto PostgreSQL concede EXECUTE a PUBLIC; se revoca y solo pilot_app puede llamarlas.
REVOKE EXECUTE ON FUNCTION membresias_de_usuario(uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION membresia_activa(uuid, uuid) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION api_key_por_prefijo(varchar) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION membresias_de_usuario(uuid) TO pilot_app;
GRANT EXECUTE ON FUNCTION membresia_activa(uuid, uuid) TO pilot_app;
GRANT EXECUTE ON FUNCTION api_key_por_prefijo(varchar) TO pilot_app;

-- 7. Cambio de dueño. El nuevo dueño necesita CREATE en el esquema: se concede solo durante el cambio
--    (V1 lo revocó a PUBLIC a propósito) y se retira enseguida.
GRANT CREATE ON SCHEMA public TO pilot_busqueda;
ALTER FUNCTION membresias_de_usuario(uuid) OWNER TO pilot_busqueda;
ALTER FUNCTION membresia_activa(uuid, uuid) OWNER TO pilot_busqueda;
ALTER FUNCTION api_key_por_prefijo(varchar) OWNER TO pilot_busqueda;
REVOKE CREATE ON SCHEMA public FROM pilot_busqueda;
