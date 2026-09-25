-- V5 — Empresa (tenant) y membresías (CLAUDE.md §9.2, ADR-002, ADR-028, ADR-029).
-- Las políticas aislamiento_empresa se declaran TO pilot_app (ADR-026): las políticas permisivas se combinan
-- con OR y current_setting('app.empresa_id') lanza error si falta, lo que rompería a pilot_busqueda (V8).

-- 1. EMPRESA: unidad de aislamiento. En 1.0 solo PERSONAL, creada al primer inicio de sesión (ADR-029).
CREATE TABLE empresa (
    id               UUID PRIMARY KEY,
    tipo             VARCHAR(8) NOT NULL,
    propietario_id   UUID REFERENCES usuario(id),
    nit              VARCHAR(14) UNIQUE,
    nrc              VARCHAR(10),
    nombre           VARCHAR(250) NOT NULL,
    nombre_comercial VARCHAR(250),
    estado           VARCHAR(15) NOT NULL DEFAULT 'ACTIVA',
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por  VARCHAR(64),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_empresa_tipo CHECK (tipo IN ('PERSONAL', 'JURIDICA')),
    CONSTRAINT ck_empresa_estado CHECK (estado IN ('ACTIVA', 'INACTIVA')),
    CONSTRAINT ck_empresa_nit_formato CHECK (nit IS NULL OR nit ~ '^[0-9]{14}$'),
    -- La empresa jurídica (Enterprise) exige NIT; la personal lo tiene opcional
    CONSTRAINT ck_empresa_juridica_con_nit CHECK (tipo = 'PERSONAL' OR nit IS NOT NULL)
);

COMMENT ON TABLE empresa IS 'Contribuyente que usa Pilot y unidad de aislamiento (tenant). RLS forzado: la empresa ES el tenant, por eso filtra por id.';
COMMENT ON COLUMN empresa.tipo IS 'PERSONAL (1.0, creada automáticamente) o JURIDICA (edición Enterprise, ADR-029).';
COMMENT ON COLUMN empresa.propietario_id IS 'Usuario dueño de la empresa PERSONAL; nulo en las jurídicas.';
COMMENT ON COLUMN empresa.nit IS '14 dígitos; opcional en PERSONAL, obligatorio en JURIDICA.';
COMMENT ON COLUMN empresa.nrc IS 'Registro de IVA; nulo si no es contribuyente. Sin CHECK de formato: [VERIFICAR] formato con el MH/contador (ADR-029).';
COMMENT ON COLUMN empresa.nombre IS 'Razón social o nombre de la empresa.';
COMMENT ON COLUMN empresa.estado IS 'ACTIVA o INACTIVA.';
COMMENT ON COLUMN empresa.actualizado_en IS 'Última modificación (UTC).';
COMMENT ON COLUMN empresa.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';
COMMENT ON COLUMN empresa.version IS 'Versión para concurrencia optimista (If-Match/ETag).';

-- Una sola empresa PERSONAL por usuario
CREATE UNIQUE INDEX uq_empresa_personal ON empresa (propietario_id) WHERE tipo = 'PERSONAL';
COMMENT ON INDEX uq_empresa_personal IS 'Garantiza una sola empresa PERSONAL por propietario (ADR-029).';

ALTER TABLE empresa ENABLE ROW LEVEL SECURITY;
ALTER TABLE empresa FORCE ROW LEVEL SECURITY;

-- Sin missing_ok, a propósito: sin app.empresa_id la consulta FALLA (falla cerrada). Solo aplica a pilot_app (ADR-026).
CREATE POLICY aislamiento_empresa ON empresa TO pilot_app
    USING (id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON empresa IS 'pilot_app solo ve y escribe la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Puede crear su empresa y editar solo datos de perfil. Sin UPDATE de tipo, propietario_id, estado ni id, y sin DELETE.
GRANT SELECT, INSERT ON empresa TO pilot_app;
GRANT UPDATE (nombre, nombre_comercial, nit, nrc, version, actualizado_en, actualizado_por) ON empresa TO pilot_app;

-- 2. MEMBRESÍA: qué usuario pertenece a qué empresa y con qué rol. Sin invitaciones pendientes (ADR-028).
CREATE TABLE empresa_usuario (
    empresa_id      UUID NOT NULL REFERENCES empresa(id),
    usuario_id      UUID NOT NULL REFERENCES usuario(id),
    rol             VARCHAR(30) NOT NULL,
    estado          VARCHAR(15) NOT NULL DEFAULT 'ACTIVA',
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por VARCHAR(64),
    PRIMARY KEY (empresa_id, usuario_id),
    -- El rol técnico "integracion" es de las API keys, no de las personas (CLAUDE.md §14.2)
    CONSTRAINT ck_empresa_usuario_rol CHECK (rol IN ('admin_empresa', 'contador', 'auditor')),
    CONSTRAINT ck_empresa_usuario_estado CHECK (estado IN ('ACTIVA', 'INACTIVA'))
);

COMMENT ON TABLE empresa_usuario IS 'Membresía de un usuario en una empresa con un rol (CLAUDE.md §14.2). Un usuario puede tener varias.';
COMMENT ON COLUMN empresa_usuario.rol IS 'admin_empresa, contador o auditor.';
COMMENT ON COLUMN empresa_usuario.estado IS 'ACTIVA o INACTIVA (la desactivación no borra la fila).';
COMMENT ON COLUMN empresa_usuario.actualizado_en IS 'Última modificación (UTC).';
COMMENT ON COLUMN empresa_usuario.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';

CREATE INDEX idx_empresa_usuario_usuario ON empresa_usuario (usuario_id);
COMMENT ON INDEX idx_empresa_usuario_usuario IS 'Búsqueda de las membresías de un usuario (selector de empresa, GET /me).';

ALTER TABLE empresa_usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE empresa_usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON empresa_usuario TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON empresa_usuario IS 'pilot_app solo ve y escribe membresías de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Agrega miembros y cambia rol/estado; sin UPDATE de las claves (empresa_id, usuario_id) y sin DELETE.
GRANT SELECT, INSERT ON empresa_usuario TO pilot_app;
GRANT UPDATE (rol, estado, actualizado_en, actualizado_por) ON empresa_usuario TO pilot_app;
