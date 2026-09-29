-- V18 — Activos fijos y depreciación (CLAUDE.md §9.3, §10.6; ADR-018, ADR-019, ADR-035, ADR-041).
-- Sigue el patrón de V11/V13/V16: llaves foráneas COMPUESTAS (empresa_id, …) hacia operacion, activo_fijo y
-- asiento, políticas aislamiento_empresa TO pilot_app (ADR-026) y permisos que refuerzan la inmutabilidad
-- (solo SELECT, INSERT y UPDATE de las columnas de estado/versión; sin DELETE, ADR-019).

-- 1. PLANTILLA_VIDA_UTIL: global, de solo lectura para pilot_app, igual que las demás plantillas (ADR-034).
--    Valores [VERIFICAR] con el contador (spec F4.5 §5.5): vida útil, residual y mes de inicio son un borrador;
--    mientras confirmada sea false, DEPRECIACION_MENSUAL responde 422 CON-023 (B2/B4, fuera de esta migración).
CREATE TABLE plantilla_vida_util (
    categoria                    VARCHAR(20) PRIMARY KEY,
    vida_util_meses              SMALLINT NOT NULL CHECK (vida_util_meses > 0),
    valor_residual_porcentaje    NUMERIC(5,4) NOT NULL CHECK (valor_residual_porcentaje >= 0 AND valor_residual_porcentaje < 1),
    inicia_mes_siguiente         BOOLEAN NOT NULL,
    confirmada                   BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ck_plantilla_vida_util_categoria CHECK (categoria IN
        ('MOBILIARIO_EQUIPO', 'EQUIPO_COMPUTO', 'VEHICULO', 'EDIFICIO'))
);

COMMENT ON TABLE plantilla_vida_util IS 'Vida útil, residual y mes de inicio por categoría de activo depreciable: borrador [VERIFICAR] con el contador (ADR-034, ADR-041). Global, solo lectura para pilot_app.';
COMMENT ON COLUMN plantilla_vida_util.categoria IS 'Categoría de activo depreciable; TERRENO no aparece aquí porque no se deprecia (spec F4.5 §5.5).';
COMMENT ON COLUMN plantilla_vida_util.valor_residual_porcentaje IS 'Fracción del costo que no se deprecia (0.0000 a 0.9999); provisional 0 para las cuatro categorías (spec F4.5 §5.5).';
COMMENT ON COLUMN plantilla_vida_util.inicia_mes_siguiente IS 'true: la depreciación inicia el mes siguiente al de la adquisición (propuesta provisional, spec F4.5 §5.5).';
COMMENT ON COLUMN plantilla_vida_util.confirmada IS 'false mientras el contador no valide estos valores; DEPRECIACION_MENSUAL responde 422 CON-023 mientras tanto (ADR-041).';

GRANT SELECT ON plantilla_vida_util TO pilot_app;

-- Meses provisionales de la tabla de la tarea B1; residual 0 y mes de inicio siguiente para las cuatro, sin confirmar
INSERT INTO plantilla_vida_util (categoria, vida_util_meses, valor_residual_porcentaje, inicia_mes_siguiente, confirmada) VALUES
    ('MOBILIARIO_EQUIPO', 60, 0, true, false),
    ('EQUIPO_COMPUTO', 24, 0, true, false),
    ('VEHICULO', 48, 0, true, false),
    ('EDIFICIO', 240, 0, true, false);

-- 2. ACTIVO_FIJO: alta de un activo depreciable o no depreciable (TERRENO), generada por COMPRA_ACTIVO_FIJO.
CREATE TABLE activo_fijo (
    id                 UUID PRIMARY KEY,
    empresa_id         UUID NOT NULL REFERENCES empresa(id),
    descripcion        VARCHAR(300) NOT NULL,
    categoria          VARCHAR(20) NOT NULL,
    fecha_adquisicion  DATE NOT NULL,
    costo              NUMERIC(19,2) NOT NULL CHECK (costo > 0),
    valor_residual     NUMERIC(19,2) NOT NULL DEFAULT 0 CHECK (valor_residual >= 0),
    vida_util_meses    SMALLINT NOT NULL CHECK (vida_util_meses > 0),
    operacion_id       UUID NOT NULL,
    estado             VARCHAR(12) NOT NULL DEFAULT 'EN_USO',
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por         VARCHAR(64) NOT NULL,
    version            BIGINT NOT NULL DEFAULT 0,
    -- Objetivo de la llave foránea compuesta de depreciacion_registrada.activo_id
    CONSTRAINT uq_activo_fijo_empresa_id UNIQUE (empresa_id, id),
    -- La operación que dio de alta el activo (COMPRA_ACTIVO_FIJO), de la misma empresa (llave compuesta)
    CONSTRAINT fk_activo_fijo_operacion FOREIGN KEY (empresa_id, operacion_id) REFERENCES operacion (empresa_id, id),
    CONSTRAINT ck_activo_fijo_estado CHECK (estado IN ('EN_USO', 'REVERTIDO')),
    CONSTRAINT ck_activo_fijo_categoria CHECK (categoria IN
        ('MOBILIARIO_EQUIPO', 'EQUIPO_COMPUTO', 'VEHICULO', 'EDIFICIO', 'TERRENO')),
    -- El residual nunca alcanza ni supera el costo (si no, la cuota mensual sería cero o negativa)
    CONSTRAINT ck_activo_fijo_residual CHECK (valor_residual < costo)
);

COMMENT ON TABLE activo_fijo IS 'Activo fijo dado de alta por la operación COMPRA_ACTIVO_FIJO (ADR-041). Inmutable salvo el paso a REVERTIDO cuando se revierte esa operación (ADR-019). RLS forzado.';
COMMENT ON COLUMN activo_fijo.categoria IS 'Una de las cinco categorías de la spec F4.5 §5.5; TERRENO no se deprecia (sin fila en plantilla_vida_util).';
COMMENT ON COLUMN activo_fijo.costo IS 'Costo de adquisición; base del cálculo de la cuota mensual (costo − residual) / vida útil en meses.';
COMMENT ON COLUMN activo_fijo.valor_residual IS 'Valor que no se deprecia; 0 mientras plantilla_vida_util no esté confirmada (spec F4.5 §5.5).';
COMMENT ON COLUMN activo_fijo.vida_util_meses IS 'Copiada de plantilla_vida_util al momento de dar de alta el activo (no cambia si la plantilla cambia después).';
COMMENT ON COLUMN activo_fijo.operacion_id IS 'Operación COMPRA_ACTIVO_FIJO que dio de alta el activo (llave compuesta con empresa_id).';
COMMENT ON COLUMN activo_fijo.estado IS 'EN_USO o REVERTIDO; pasa a REVERTIDO cuando se revierte el asiento de su operación de alta.';
COMMENT ON CONSTRAINT uq_activo_fijo_empresa_id ON activo_fijo IS 'Objetivo de la llave foránea compuesta de depreciacion_registrada.activo_id (ADR-035).';
COMMENT ON CONSTRAINT ck_activo_fijo_residual ON activo_fijo IS 'El valor residual nunca alcanza el costo: la cuota mensual siempre es mayor que cero.';

ALTER TABLE activo_fijo ENABLE ROW LEVEL SECURITY;
ALTER TABLE activo_fijo FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON activo_fijo TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON activo_fijo IS 'pilot_app solo ve y escribe activos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

GRANT SELECT, INSERT ON activo_fijo TO pilot_app;
GRANT UPDATE (estado, version) ON activo_fijo TO pilot_app;

-- 3. DEPRECIACION_REGISTRADA: una cuota mensual por activo, generada por DEPRECIACION_MENSUAL.
CREATE TABLE depreciacion_registrada (
    id          UUID PRIMARY KEY,
    empresa_id  UUID NOT NULL REFERENCES empresa(id),
    activo_id   UUID NOT NULL,
    anio        SMALLINT NOT NULL,
    mes         SMALLINT NOT NULL CHECK (mes BETWEEN 1 AND 12),
    monto       NUMERIC(19,2) NOT NULL CHECK (monto > 0),
    asiento_id  UUID NOT NULL,
    estado      VARCHAR(12) NOT NULL DEFAULT 'VIGENTE',
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por  VARCHAR(64) NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    -- El activo y el asiento son de la misma empresa (llaves compuestas, como en V13/V16)
    CONSTRAINT fk_depreciacion_activo FOREIGN KEY (empresa_id, activo_id) REFERENCES activo_fijo (empresa_id, id),
    CONSTRAINT fk_depreciacion_asiento FOREIGN KEY (empresa_id, asiento_id) REFERENCES asiento (empresa_id, id),
    CONSTRAINT ck_depreciacion_estado CHECK (estado IN ('VIGENTE', 'REVERTIDA'))
);

COMMENT ON TABLE depreciacion_registrada IS 'Cuota de depreciación de un activo en un mes, generada por la operación DEPRECIACION_MENSUAL (ADR-041). Inmutable salvo el paso a REVERTIDA. RLS forzado.';
COMMENT ON COLUMN depreciacion_registrada.anio IS 'Año del mes depreciado (con mes, forma el período: uq_depreciacion_vigente evita repetirlo por activo).';
COMMENT ON COLUMN depreciacion_registrada.monto IS 'Cuota del mes; la línea recta calcula (costo − residual) / vida útil, con la última cuota ajustando el redondeo (spec F4.5 §5.5).';
COMMENT ON COLUMN depreciacion_registrada.asiento_id IS 'Asiento de la operación DEPRECIACION_MENSUAL que generó esta cuota (llave compuesta con empresa_id).';
COMMENT ON COLUMN depreciacion_registrada.estado IS 'VIGENTE o REVERTIDA; pasa a REVERTIDA cuando se revierte el asiento, y libera el período para registrarlo de nuevo (spec F4.5 §5.5).';

-- Una sola cuota vigente por activo y mes (evita la doble depreciación del "Foco de revisión" 2 del plan de F4.5,
-- incluso con dos peticiones simultáneas: la segunda choca con este índice y responde 409, sin código nuevo)
CREATE UNIQUE INDEX uq_depreciacion_vigente ON depreciacion_registrada (empresa_id, activo_id, anio, mes)
    WHERE estado = 'VIGENTE';
COMMENT ON INDEX uq_depreciacion_vigente IS 'Última defensa contra dos depreciaciones vigentes del mismo activo y mes, aunque dos peticiones compitan (spec F4.5, foco de revisión 2).';

ALTER TABLE depreciacion_registrada ENABLE ROW LEVEL SECURITY;
ALTER TABLE depreciacion_registrada FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON depreciacion_registrada TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON depreciacion_registrada IS 'pilot_app solo ve y escribe depreciaciones de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

GRANT SELECT, INSERT ON depreciacion_registrada TO pilot_app;
GRANT UPDATE (estado, version) ON depreciacion_registrada TO pilot_app;
