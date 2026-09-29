-- V9 — Tasas de impuesto con vigencia (CLAUDE.md §9.3, regla 1.1.13, ADR-034).
-- La tasa de IVA se lee siempre de esta tabla según la fecha del asiento; nunca es una constante en código.

-- 1. TASA_IMPUESTO: global (sin empresa), solo lectura para la aplicación.
CREATE TABLE tasa_impuesto (
    id             UUID PRIMARY KEY,
    tipo           VARCHAR(20) NOT NULL,
    tasa           NUMERIC(7,4) NOT NULL,
    vigente_desde  DATE NOT NULL,
    vigente_hasta  DATE,
    CONSTRAINT ck_tasa_impuesto_tipo CHECK (tipo IN ('IVA')),
    -- Una tasa es una fracción: 0.1300 = 13 %; fuera de 0..1 sería un error de captura
    CONSTRAINT ck_tasa_impuesto_tasa CHECK (tasa >= 0 AND tasa <= 1),
    CONSTRAINT ck_tasa_impuesto_vigencia CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde),
    -- Sin traslapes de vigencia por tipo (requiere btree_gist, creada en V1); el rango incluye ambos extremos
    CONSTRAINT ex_tasa_impuesto_sin_traslape
        EXCLUDE USING gist (tipo WITH =, daterange(vigente_desde, vigente_hasta, '[]') WITH &&)
);

COMMENT ON TABLE tasa_impuesto IS 'Tasas de impuesto con vigencia (desde/hasta). Global, sin RLS; la cambian las migraciones, nunca la aplicación (regla 1.1.13).';
COMMENT ON COLUMN tasa_impuesto.tipo IS 'Impuesto al que aplica; en 1.0 solo IVA.';
COMMENT ON COLUMN tasa_impuesto.tasa IS 'Fracción decimal: 0.1300 = 13 %.';
COMMENT ON COLUMN tasa_impuesto.vigente_desde IS 'Primer día de vigencia. La fila inicial de IVA usa la fecha TÉCNICA 2000-01-01 (ADR-034); la fecha exacta es [VERIFICAR] con el contador.';
COMMENT ON COLUMN tasa_impuesto.vigente_hasta IS 'Último día de vigencia (inclusive); nulo = vigente sin fecha de fin.';
COMMENT ON CONSTRAINT ex_tasa_impuesto_sin_traslape ON tasa_impuesto IS 'Impide dos vigencias solapadas del mismo tipo de impuesto.';

-- Solo lectura para la aplicación
GRANT SELECT ON tasa_impuesto TO pilot_app;

-- 2. Carga inicial: IVA 13 % con fecha técnica de inicio (ADR-034), UUID v7 fijo. [VERIFICAR] fecha con el contador.
INSERT INTO tasa_impuesto (id, tipo, tasa, vigente_desde, vigente_hasta)
VALUES ('0192f1a4-7c3e-7b21-9d4e-5a6b7c8d9e01', 'IVA', 0.1300, DATE '2000-01-01', NULL);
