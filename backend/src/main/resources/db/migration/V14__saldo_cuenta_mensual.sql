-- V14 — Mayorización en tiempo real: saldo acumulado por cuenta de detalle y mes (CLAUDE.md §9.3, §10.3; ADR-018, ADR-036).
-- La referencia a la cuenta es una llave foránea COMPUESTA (empresa_id, cuenta_id): nunca apunta a otra empresa (ADR-035).

-- SALDO_CUENTA_MENSUAL: un acumulado por cuenta de detalle y mes.
CREATE TABLE saldo_cuenta_mensual (
    empresa_id      UUID NOT NULL,
    cuenta_id       UUID NOT NULL,
    anio            SMALLINT NOT NULL,
    mes             SMALLINT NOT NULL,
    total_debe      NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_haber     NUMERIC(19,2) NOT NULL DEFAULT 0,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_saldo_cuenta_mensual PRIMARY KEY (empresa_id, cuenta_id, anio, mes),
    CONSTRAINT fk_saldo_cuenta_mensual_cuenta FOREIGN KEY (empresa_id, cuenta_id)
        REFERENCES cuenta_contable (empresa_id, id),
    CONSTRAINT ck_saldo_cuenta_mensual_mes CHECK (mes BETWEEN 1 AND 12),
    -- Los acumulados solo crecen: un asiento nunca resta (la reversión suma en el lado contrario)
    CONSTRAINT ck_saldo_cuenta_mensual_debe CHECK (total_debe >= 0),
    CONSTRAINT ck_saldo_cuenta_mensual_haber CHECK (total_haber >= 0)
);

COMMENT ON TABLE saldo_cuenta_mensual IS 'Movimientos acumulados por cuenta de detalle y mes. Solo se modifica con el upsert de CLAUDE.md §10.3, en la misma transacción del asiento (ADR-018). RLS forzado.';
COMMENT ON COLUMN saldo_cuenta_mensual.cuenta_id IS 'Cuenta de detalle de la misma empresa (llave compuesta); las cuentas padre se calculan al consultar.';
COMMENT ON COLUMN saldo_cuenta_mensual.anio IS 'Año de la fecha contable de las líneas acumuladas.';
COMMENT ON COLUMN saldo_cuenta_mensual.mes IS 'Mes (1 a 12) de la fecha contable de las líneas acumuladas.';
COMMENT ON COLUMN saldo_cuenta_mensual.total_debe IS 'Σ Debe de las líneas de la cuenta en el mes; invariante: igual a la suma de asiento_linea.';
COMMENT ON COLUMN saldo_cuenta_mensual.total_haber IS 'Σ Haber de las líneas de la cuenta en el mes; invariante: igual a la suma de asiento_linea.';
COMMENT ON COLUMN saldo_cuenta_mensual.actualizado_en IS 'Última acumulación (UTC).';
COMMENT ON CONSTRAINT ck_saldo_cuenta_mensual_mes ON saldo_cuenta_mensual IS 'Mes válido de 1 a 12.';

ALTER TABLE saldo_cuenta_mensual ENABLE ROW LEVEL SECURITY;
ALTER TABLE saldo_cuenta_mensual FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON saldo_cuenta_mensual TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON saldo_cuenta_mensual IS 'pilot_app solo ve y escribe saldos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- El upsert necesita SELECT, INSERT y UPDATE de los tres acumuladores; sin DELETE (los saldos no se borran)
GRANT SELECT, INSERT ON saldo_cuenta_mensual TO pilot_app;
GRANT UPDATE (total_debe, total_haber, actualizado_en) ON saldo_cuenta_mensual TO pilot_app;
