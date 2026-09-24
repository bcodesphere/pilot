-- V3 — Auditoría insert-only, particionada por mes (CLAUDE.md §9.2, regla 1.1.11; retención 10 años).

CREATE TABLE auditoria (
    id             UUID NOT NULL,
    empresa_id     UUID NOT NULL,
    entidad        VARCHAR(60) NOT NULL,
    entidad_id     VARCHAR(100) NOT NULL,
    accion         VARCHAR(30) NOT NULL,
    usuario_id     VARCHAR(64) NOT NULL,
    valor_anterior JSONB,
    valor_nuevo    JSONB,
    trace_id       VARCHAR(64),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- La clave primaria de una tabla particionada debe incluir la columna de partición
    PRIMARY KEY (id, creado_en)
) PARTITION BY RANGE (creado_en);

COMMENT ON TABLE auditoria IS 'Bitácora de mutaciones de datos de negocio: quién, qué, cuándo, valor anterior y nuevo. Solo inserción; partición mensual; retención 10 años.';
COMMENT ON COLUMN auditoria.empresa_id IS 'Empresa a la que pertenece el registro; base de la política RLS. [DECISIÓN] pendiente para entidades globales (usuario).';
COMMENT ON COLUMN auditoria.entidad IS 'Nombre lógico de la entidad modificada (p. ej. asiento, cuenta_contable).';
COMMENT ON COLUMN auditoria.entidad_id IS 'Identificador de la entidad; texto para admitir claves no UUID.';
COMMENT ON COLUMN auditoria.accion IS 'Acción realizada (p. ej. CREAR, ACTUALIZAR, REVERTIR).';
COMMENT ON COLUMN auditoria.usuario_id IS 'Usuario (UUID) que ejecutó la acción, o el marcador del sistema (app.usuario_id).';
COMMENT ON COLUMN auditoria.valor_anterior IS 'Estado previo de la entidad; nulo en creaciones.';
COMMENT ON COLUMN auditoria.valor_nuevo IS 'Estado posterior de la entidad; nulo en eliminaciones.';
COMMENT ON COLUMN auditoria.trace_id IS 'traceId de la petición, para correlacionar con los logs.';
COMMENT ON COLUMN auditoria.creado_en IS 'Momento del evento (UTC); columna de partición mensual.';

-- Índice de consulta del historial de una entidad. En tabla particionada se propaga a cada partición.
CREATE INDEX idx_auditoria_entidad ON auditoria (empresa_id, entidad, entidad_id, creado_en);
COMMENT ON INDEX idx_auditoria_entidad IS 'Historial de una entidad de una empresa en orden cronológico.';

-- Particiones mensuales de 2026-09 a 2027-12 (16 meses), creadas con un bloque para evitar errores de copia.
DO $$
DECLARE
    v_mes DATE := DATE '2026-09-01';
BEGIN
    WHILE v_mes < DATE '2028-01-01' LOOP
        -- Rango semiabierto [inicio, inicio + 1 mes) sobre el instante UTC de medianoche
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF auditoria FOR VALUES FROM (%L) TO (%L)',
            'auditoria_' || to_char(v_mes, 'YYYY_MM'),
            v_mes::timestamp AT TIME ZONE 'UTC',
            (v_mes + INTERVAL '1 month')::date::timestamp AT TIME ZONE 'UTC');
        EXECUTE format('COMMENT ON TABLE %I IS %L', 'auditoria_' || to_char(v_mes, 'YYYY_MM'),
            'Partición mensual de auditoria (UTC). Sin permisos para pilot_app: se accede por la tabla padre.');
        v_mes := (v_mes + INTERVAL '1 month')::date;
    END LOOP;
END $$;

-- Partición de respaldo: recibe filas fuera de los rangos creados, para que un INSERT nunca falle por falta de partición.
CREATE TABLE auditoria_default PARTITION OF auditoria DEFAULT;
COMMENT ON TABLE auditoria_default IS 'Partición de respaldo para fechas sin partición mensual; debe vigilarse y vaciarse creando la partición que falte.';

-- RLS forzado en la tabla padre (las particiones no reciben permisos, así que solo se llega a ellas por el padre).
ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON auditoria
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON auditoria IS 'Solo se ven y escriben filas de la empresa fijada en app.empresa_id; sin ese parámetro la consulta falla (intencional).';

-- Permisos SOLO sobre la tabla padre y solo lectura e inserción (insert-only): sin UPDATE ni DELETE.
GRANT SELECT, INSERT ON auditoria TO pilot_app;
