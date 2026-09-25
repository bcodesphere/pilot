-- V7 — API keys de integraciones (n8n) (CLAUDE.md §9.2, §14.1). El secreto nunca se guarda en claro.

CREATE TABLE api_key (
    id            UUID PRIMARY KEY,
    empresa_id    UUID NOT NULL REFERENCES empresa(id),
    nombre        VARCHAR(100) NOT NULL,
    prefijo       VARCHAR(16) NOT NULL UNIQUE,
    hash_secreto  VARCHAR(200) NOT NULL,
    alcances      TEXT[] NOT NULL,
    expira_en     TIMESTAMPTZ,
    revocada_en   TIMESTAMPTZ,
    ultimo_uso_en TIMESTAMPTZ,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por    VARCHAR(64) NOT NULL,
    -- 1.0 solo conoce el alcance del webhook de n8n; un alcance nuevo requiere ADR y nueva migración
    CONSTRAINT ck_api_key_alcances CHECK (cardinality(alcances) >= 1 AND alcances <@ ARRAY['integracion:operaciones']::text[]),
    CONSTRAINT ck_api_key_prefijo CHECK (prefijo ~ '^pk_[a-z0-9]{4,13}$')
);

COMMENT ON TABLE api_key IS 'Credenciales de integraciones (n8n). Se autentican por prefijo + secreto; la empresa se deriva de la clave.';
COMMENT ON COLUMN api_key.nombre IS 'Nombre descriptivo, p. ej. "n8n producción".';
COMMENT ON COLUMN api_key.prefijo IS 'Parte visible para identificarla (pk_xxxx); único global porque se busca sin conocer la empresa (ADR-026).';
COMMENT ON COLUMN api_key.hash_secreto IS 'Argon2id del secreto; el secreto se muestra una sola vez al crearla.';
COMMENT ON COLUMN api_key.alcances IS 'Alcances concedidos; en 1.0 solo integracion:operaciones.';
COMMENT ON COLUMN api_key.expira_en IS 'Vencimiento opcional (UTC).';
COMMENT ON COLUMN api_key.revocada_en IS 'Momento de la revocación (UTC); una clave revocada devuelve 401.';
COMMENT ON COLUMN api_key.ultimo_uso_en IS 'Último uso exitoso (UTC).';
COMMENT ON COLUMN api_key.creado_por IS 'Usuario (app.usuario_id) que la creó.';

ALTER TABLE api_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_key FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON api_key TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON api_key IS 'pilot_app solo ve y crea claves de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Crea y lee claves; solo puede revocarlas o registrar su último uso. Nunca UPDATE de hash_secreto, alcances
-- ni prefijo (una credencial no se muta: se revoca y se crea otra) y sin DELETE.
GRANT SELECT, INSERT ON api_key TO pilot_app;
GRANT UPDATE (revocada_en, ultimo_uso_en) ON api_key TO pilot_app;
