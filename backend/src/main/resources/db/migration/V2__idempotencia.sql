-- V2 — Tabla de idempotencia (CLAUDE.md §9.2, §12.6). Evita duplicados cuando un cliente o n8n reintenta.

CREATE TABLE idempotencia (
    empresa_id      UUID NOT NULL,
    clave           VARCHAR(100) NOT NULL,
    hash_solicitud  CHAR(64) NOT NULL,
    estado_http     SMALLINT NOT NULL,
    respuesta       JSONB NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (empresa_id, clave)
);

COMMENT ON TABLE idempotencia IS 'Respuestas guardadas por Idempotency-Key para que un reintento no duplique su efecto; retención 7 días (CLAUDE.md 12.6).';
COMMENT ON COLUMN idempotencia.empresa_id IS 'Empresa dueña de la clave; la clave solo es única dentro de una empresa.';
COMMENT ON COLUMN idempotencia.clave IS 'Valor del header Idempotency-Key.';
COMMENT ON COLUMN idempotencia.hash_solicitud IS 'SHA-256 (hex) del cuerpo: misma clave con otro cuerpo se rechaza (INT-005).';
COMMENT ON COLUMN idempotencia.estado_http IS 'Código HTTP de la respuesta original, para devolverla igual.';
COMMENT ON COLUMN idempotencia.respuesta IS 'Cuerpo de la respuesta original.';
COMMENT ON COLUMN idempotencia.creado_en IS 'Momento de registro (UTC); base de la purga a 7 días.';

-- Índice para la purga por antigüedad (DELETE ... WHERE creado_en < now() - interval '7 days').
CREATE INDEX idx_idempotencia_creado_en ON idempotencia (creado_en);
COMMENT ON INDEX idx_idempotencia_creado_en IS 'Acelera la purga de claves con más de 7 días de antigüedad.';

-- RLS habilitado y forzado: aplica incluso al dueño de la tabla (ADR-002, CLAUDE.md §4.5).
ALTER TABLE idempotencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotencia FORCE ROW LEVEL SECURITY;

-- Política de aislamiento. current_setting SIN missing_ok, a propósito: si la transacción no fijó
-- app.empresa_id, la consulta FALLA en vez de devolver cero filas (falla cerrada, nunca abierta).
CREATE POLICY aislamiento_empresa ON idempotencia
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON idempotencia IS 'Solo se ven y escriben filas de la empresa fijada en app.empresa_id; sin ese parámetro la consulta falla (intencional).';

-- Permisos: leer y registrar claves y purgar las vencidas. Sin UPDATE: una respuesta guardada no se modifica.
GRANT SELECT, INSERT, DELETE ON idempotencia TO pilot_app;
