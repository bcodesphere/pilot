-- V4 — Usuario (tabla global) y auditoría global (CLAUDE.md §9.2, ADR-025, ADR-028).
-- Ambas tablas son globales: no tienen empresa_id, así que NO llevan RLS y se pueden usar en el modo
-- "sin empresa" del gestor de transacciones (ADR-026).

-- 1. USUARIO: persona que inicia sesión; su identidad vive en Keycloak.
CREATE TABLE usuario (
    id            UUID PRIMARY KEY,
    sub_keycloak  VARCHAR(64) NOT NULL UNIQUE,
    correo        VARCHAR(254) NOT NULL UNIQUE,
    nombre        VARCHAR(200) NOT NULL,
    telefono      VARCHAR(12) NOT NULL,
    recomendaciones_aceptadas_en TIMESTAMPTZ,
    recomendaciones_retiradas_en TIMESTAMPTZ,
    estado        VARCHAR(15) NOT NULL DEFAULT 'ACTIVO',
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ,
    -- Keycloak guarda el correo en minúsculas; la búsqueda por correo (ADR-028) es exacta, así que se exige minúscula aquí
    CONSTRAINT ck_usuario_correo_minuscula CHECK (correo = lower(correo)),
    -- Solo teléfonos de El Salvador (+503 y 8 dígitos)
    CONSTRAINT ck_usuario_telefono CHECK (telefono ~ '^\+503[0-9]{8}$'),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVO', 'BLOQUEADO'))
);

COMMENT ON TABLE usuario IS 'Persona que inicia sesión (tabla global, sin RLS). La identidad y la contraseña viven en Keycloak; aquí solo el perfil (ADR-028).';
COMMENT ON COLUMN usuario.id IS 'UUID v7 generado por la aplicación (ADR-010).';
COMMENT ON COLUMN usuario.sub_keycloak IS 'Claim "sub" del token OIDC; enlaza la sesión con el usuario. Inmutable para la aplicación.';
COMMENT ON COLUMN usuario.correo IS 'Correo verificado en Keycloak, siempre en minúsculas. Dato personal: se enmascara en logs.';
COMMENT ON COLUMN usuario.nombre IS 'Nombre completo del usuario. Dato personal.';
COMMENT ON COLUMN usuario.telefono IS 'Teléfono de El Salvador con formato +503XXXXXXXX. Dato personal (ADR-028).';
COMMENT ON COLUMN usuario.recomendaciones_aceptadas_en IS '"Acepto recibir recomendaciones por correo": momento de aceptación; nulo = nunca aceptó (ADR-028).';
COMMENT ON COLUMN usuario.recomendaciones_retiradas_en IS 'Último retiro del consentimiento; vigente si aceptadas_en > retiradas_en o retiradas_en es nulo.';
COMMENT ON COLUMN usuario.estado IS 'ACTIVO o BLOQUEADO.';
COMMENT ON COLUMN usuario.creado_en IS 'Momento del alta (UTC), en el primer inicio de sesión.';
COMMENT ON COLUMN usuario.actualizado_en IS 'Última modificación del perfil (UTC); nulo si nunca se modificó.';

-- pilot_app crea usuarios y edita solo su perfil. Sin UPDATE de sub_keycloak, id, estado ni creado_en
-- (la identidad no se reasigna; el bloqueo es administrativo) y sin DELETE (los usuarios no se borran).
GRANT SELECT, INSERT ON usuario TO pilot_app;
GRANT UPDATE (correo, nombre, telefono, recomendaciones_aceptadas_en, recomendaciones_retiradas_en, actualizado_en)
    ON usuario TO pilot_app;

-- 2. AUDITORÍA GLOBAL: misma forma que auditoria (V3) sin empresa_id ni particiones (ADR-025).
CREATE TABLE auditoria_global (
    id             UUID PRIMARY KEY,
    entidad        VARCHAR(60) NOT NULL,
    entidad_id     VARCHAR(100) NOT NULL,
    accion         VARCHAR(30) NOT NULL,
    usuario_id     VARCHAR(64) NOT NULL,
    valor_anterior JSONB,
    valor_nuevo    JSONB,
    trace_id       VARCHAR(64),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE auditoria_global IS 'Bitácora de mutaciones de entidades sin empresa (p. ej. usuario). Solo inserción; sin RLS (ADR-025).';
COMMENT ON COLUMN auditoria_global.entidad IS 'Nombre lógico de la entidad modificada (p. ej. usuario).';
COMMENT ON COLUMN auditoria_global.entidad_id IS 'Identificador de la entidad; texto para admitir claves no UUID.';
COMMENT ON COLUMN auditoria_global.accion IS 'Acción realizada (p. ej. CREAR, ACTUALIZAR).';
COMMENT ON COLUMN auditoria_global.usuario_id IS 'Usuario (UUID) que ejecutó la acción, o el marcador del sistema.';
COMMENT ON COLUMN auditoria_global.valor_anterior IS 'Estado previo; nulo en creaciones. Sin datos personales sin enmascarar.';
COMMENT ON COLUMN auditoria_global.valor_nuevo IS 'Estado posterior; nulo en eliminaciones.';
COMMENT ON COLUMN auditoria_global.trace_id IS 'traceId de la petición, para correlacionar con los logs.';
COMMENT ON COLUMN auditoria_global.creado_en IS 'Momento del evento (UTC).';

CREATE INDEX idx_auditoria_global_entidad ON auditoria_global (entidad, entidad_id, creado_en);
COMMENT ON INDEX idx_auditoria_global_entidad IS 'Historial de una entidad global en orden cronológico.';

-- Insert-only y sin lectura: la aplicación registra pero no consulta la bitácora global (la lectura será administrativa).
GRANT INSERT ON auditoria_global TO pilot_app;

-- 3. La decisión pendiente de V3 ya está tomada: las entidades sin empresa van a auditoria_global (ADR-025).
COMMENT ON COLUMN auditoria.empresa_id IS 'Empresa a la que pertenece el registro; base de la política RLS. Las entidades sin empresa (usuario) se registran en auditoria_global (ADR-025).';
