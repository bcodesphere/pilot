-- V13 — Libro Diario: correlativo, asientos y líneas (CLAUDE.md §9.3, §10.1; ADR-018, ADR-019, ADR-022, ADR-036).
-- Las políticas aislamiento_empresa se declaran TO pilot_app (ADR-026) y sin missing_ok: sin app.empresa_id falla.
-- Las referencias hacia asiento y cuenta_contable son llaves foráneas COMPUESTAS (empresa_id, …): una fila nunca apunta
-- a datos de otra empresa aunque RLS fallara (ADR-035, decisión 6; ADR-036, decisión 7). La inmutabilidad de los
-- asientos se refuerza con permisos por columna y sin DELETE (ADR-019).

-- 1. CORRELATIVO_ASIENTO: numeración por empresa y año, sin duplicados entre transacciones (ADR-036, decisión 8).
CREATE TABLE correlativo_asiento (
    empresa_id  UUID NOT NULL REFERENCES empresa(id),
    anio        SMALLINT NOT NULL,
    ultimo      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_correlativo_asiento PRIMARY KEY (empresa_id, anio),
    CONSTRAINT ck_correlativo_asiento_ultimo CHECK (ultimo >= 0)
);

COMMENT ON TABLE correlativo_asiento IS 'Último número de asiento por empresa y año. Se incrementa con INSERT … ON CONFLICT DO UPDATE … RETURNING en la transacción del asiento (ADR-036). RLS forzado.';
COMMENT ON COLUMN correlativo_asiento.anio IS 'Año de la fecha contable del asiento; la numeración reinicia cada año.';
COMMENT ON COLUMN correlativo_asiento.ultimo IS 'Último número asignado; el bloqueo de la fila serializa la numeración sin huecos entre transacciones que terminan bien.';
COMMENT ON CONSTRAINT ck_correlativo_asiento_ultimo ON correlativo_asiento IS 'El correlativo nunca es negativo.';

ALTER TABLE correlativo_asiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE correlativo_asiento FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON correlativo_asiento TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON correlativo_asiento IS 'pilot_app solo ve y escribe correlativos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- El upsert necesita SELECT, INSERT y UPDATE de ultimo; nada más y sin DELETE (los números no se reutilizan).
GRANT SELECT, INSERT ON correlativo_asiento TO pilot_app;
GRANT UPDATE (ultimo) ON correlativo_asiento TO pilot_app;

-- 2. ASIENTO: cabecera de la partida; inmutable salvo el paso a REVERTIDO (ADR-019).
CREATE TABLE asiento (
    id                    UUID PRIMARY KEY,
    empresa_id            UUID NOT NULL REFERENCES empresa(id),
    anio                  SMALLINT NOT NULL,
    numero                BIGINT NOT NULL,
    fecha                 DATE NOT NULL,
    concepto              VARCHAR(500) NOT NULL,
    estado                VARCHAR(13) NOT NULL,  -- 13: 'CONTABILIZADO' no cabe en los 12 de CLAUDE.md §9.3
    origen_tipo           VARCHAR(12) NOT NULL,
    origen_id             UUID,
    modo_precio           VARCHAR(7),
    asiento_revertido_id  UUID,
    asiento_reversion_id  UUID,
    total_debe            NUMERIC(19,2) NOT NULL,
    total_haber           NUMERIC(19,2) NOT NULL,
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por            VARCHAR(64) NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_asiento_numero UNIQUE (empresa_id, anio, numero),
    -- Objetivo de las llaves foráneas compuestas de asiento_linea y de las dos referencias de reversión
    CONSTRAINT uq_asiento_empresa_id UNIQUE (empresa_id, id),
    CONSTRAINT fk_asiento_revertido FOREIGN KEY (empresa_id, asiento_revertido_id)
        REFERENCES asiento (empresa_id, id),
    CONSTRAINT fk_asiento_reversion FOREIGN KEY (empresa_id, asiento_reversion_id)
        REFERENCES asiento (empresa_id, id),
    CONSTRAINT ck_asiento_estado CHECK (estado IN ('CONTABILIZADO', 'REVERTIDO')),
    CONSTRAINT ck_asiento_origen_tipo CHECK (origen_tipo IN ('MANUAL', 'N8N', 'REVERSION')),
    CONSTRAINT ck_asiento_modo_precio CHECK (modo_precio IS NULL OR modo_precio IN ('CON_IVA', 'SIN_IVA')),
    CONSTRAINT ck_asiento_anio CHECK (anio = EXTRACT(YEAR FROM fecha)),
    CONSTRAINT ck_asiento_numero CHECK (numero >= 1),
    -- Partida doble a nivel de cabecera (CON-004, CON-005)
    CONSTRAINT ck_asiento_totales CHECK (total_debe = total_haber AND total_debe > 0),
    -- Una reversión siempre apunta al asiento que revierte, y solo ella lo hace (ADR-036, decisión 7)
    CONSTRAINT ck_asiento_reversion_coherente CHECK ((origen_tipo = 'REVERSION') = (asiento_revertido_id IS NOT NULL)),
    -- Un asiento está REVERTIDO si y solo si ya tiene su reversión enlazada
    CONSTRAINT ck_asiento_revertido_coherente CHECK ((estado = 'REVERTIDO') = (asiento_reversion_id IS NOT NULL)),
    -- Una reversión no se revierte (CON-009)
    CONSTRAINT ck_asiento_reversion_no_revertida CHECK (NOT (origen_tipo = 'REVERSION' AND estado = 'REVERTIDO'))
);

COMMENT ON TABLE asiento IS 'Cabecera de la partida contable. Inmutable: solo pasa de CONTABILIZADO a REVERTIDO (ADR-019). RLS forzado.';
COMMENT ON COLUMN asiento.anio IS 'Año de la fecha contable; con numero forma el número visible del asiento.';
COMMENT ON COLUMN asiento.numero IS 'Correlativo por empresa y año, asignado desde correlativo_asiento.';
COMMENT ON COLUMN asiento.fecha IS 'Fecha contable (DATE en hora de El Salvador), no futura (CON-007).';
COMMENT ON COLUMN asiento.estado IS 'CONTABILIZADO o REVERTIDO; el único cambio permitido tras guardar es CONTABILIZADO → REVERTIDO.';
COMMENT ON COLUMN asiento.origen_tipo IS 'MANUAL (Libro Diario), N8N (operación externa) o REVERSION (contra-asiento).';
COMMENT ON COLUMN asiento.origen_id IS 'operacion_externa.id cuando viene de n8n. Sin llave foránea: la tabla llega en F5.';
COMMENT ON COLUMN asiento.modo_precio IS 'Modo con que se separó el IVA (CON_IVA o SIN_IVA); nulo si ninguna línea llevó IVA.';
COMMENT ON COLUMN asiento.asiento_revertido_id IS 'En una reversión: el asiento que revierte (misma empresa, llave compuesta).';
COMMENT ON COLUMN asiento.asiento_reversion_id IS 'En un asiento revertido: la reversión que lo anula (misma empresa, llave compuesta).';
COMMENT ON COLUMN asiento.total_debe IS 'Σ Debe de las líneas guardadas; el trigger diferido verifica que coincida.';
COMMENT ON COLUMN asiento.total_haber IS 'Σ Haber de las líneas guardadas; el trigger diferido verifica que coincida.';
COMMENT ON COLUMN asiento.creado_por IS 'Valor de app.usuario_id; en asientos de n8n no es un usuario (como V11, ADR-036).';
COMMENT ON COLUMN asiento.version IS 'Se incrementa en la única actualización permitida (paso a REVERTIDO).';
COMMENT ON CONSTRAINT uq_asiento_numero ON asiento IS 'Sin números duplicados por empresa y año.';
COMMENT ON CONSTRAINT uq_asiento_empresa_id ON asiento IS 'Objetivo de las llaves foráneas compuestas (empresa_id, asiento_id): impide referenciar asientos de otra empresa (ADR-036).';
COMMENT ON CONSTRAINT ck_asiento_anio ON asiento IS 'El año de numeración es el de la fecha contable (ADR-036, decisión 7).';
COMMENT ON CONSTRAINT ck_asiento_totales ON asiento IS 'Partida doble en la cabecera: Debe = Haber y mayores que cero (CON-004, CON-005).';
COMMENT ON CONSTRAINT ck_asiento_reversion_coherente ON asiento IS 'origen_tipo = REVERSION si y solo si asiento_revertido_id no es nulo (ADR-036, decisión 7).';
COMMENT ON CONSTRAINT ck_asiento_revertido_coherente ON asiento IS 'estado = REVERTIDO si y solo si asiento_reversion_id no es nulo.';
COMMENT ON CONSTRAINT ck_asiento_reversion_no_revertida ON asiento IS 'Una reversión no se revierte (CON-009).';

-- Un asiento se revierte una sola vez (CON-008): a lo sumo una reversión apunta a cada asiento
CREATE UNIQUE INDEX uq_asiento_revertido_una_vez ON asiento (empresa_id, asiento_revertido_id)
    WHERE asiento_revertido_id IS NOT NULL;
COMMENT ON INDEX uq_asiento_revertido_una_vez IS 'Un asiento no puede tener dos reversiones (CON-008), aunque dos peticiones compitan.';

-- Una operación de n8n solo puede tener un asiento vigente (idempotencia contable, CLAUDE.md §12.6)
CREATE UNIQUE INDEX uq_asiento_operacion_vigente ON asiento (empresa_id, origen_id)
    WHERE origen_tipo = 'N8N' AND estado = 'CONTABILIZADO';
COMMENT ON INDEX uq_asiento_operacion_vigente IS 'Última defensa contra dos asientos vigentes de la misma operación de n8n; un asiento REVERTIDO deja libre la operación para reenviarla corregida.';

-- Libro Diario: filtro por rango de fechas de la empresa (el orden por año y número lo cubre uq_asiento_numero)
CREATE INDEX idx_asiento_fecha ON asiento (empresa_id, fecha);
COMMENT ON INDEX idx_asiento_fecha IS 'Consulta del Libro Diario y de los reportes por rango de fechas (CLAUDE.md §10.5).';

ALTER TABLE asiento ENABLE ROW LEVEL SECURITY;
ALTER TABLE asiento FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON asiento TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON asiento IS 'pilot_app solo ve y escribe asientos de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Inmutabilidad (ADR-019): sin DELETE y UPDATE solo de las columnas del paso a REVERTIDO
GRANT SELECT, INSERT ON asiento TO pilot_app;
GRANT UPDATE (estado, asiento_reversion_id, version) ON asiento TO pilot_app;

-- 3. ASIENTO_LINEA: cada línea es Debe o Haber, nunca ambos (CON-002).
CREATE TABLE asiento_linea (
    id             UUID PRIMARY KEY,
    empresa_id     UUID NOT NULL,
    asiento_id     UUID NOT NULL,
    numero_linea   SMALLINT NOT NULL,
    fecha          DATE NOT NULL,
    cuenta_id      UUID NOT NULL,
    descripcion    VARCHAR(300),
    debe           NUMERIC(19,2) NOT NULL DEFAULT 0,
    haber          NUMERIC(19,2) NOT NULL DEFAULT 0,
    origen_linea   VARCHAR(14) NOT NULL,
    linea_base_id  UUID,
    CONSTRAINT uq_asiento_linea_numero UNIQUE (asiento_id, numero_linea),
    -- Objetivo de la llave foránea de linea_base_id: garantiza que la línea base es del mismo asiento
    CONSTRAINT uq_asiento_linea_asiento_id UNIQUE (asiento_id, id),
    CONSTRAINT fk_asiento_linea_asiento FOREIGN KEY (empresa_id, asiento_id)
        REFERENCES asiento (empresa_id, id),
    CONSTRAINT fk_asiento_linea_cuenta FOREIGN KEY (empresa_id, cuenta_id)
        REFERENCES cuenta_contable (empresa_id, id),
    CONSTRAINT fk_asiento_linea_base FOREIGN KEY (asiento_id, linea_base_id)
        REFERENCES asiento_linea (asiento_id, id),
    CONSTRAINT ck_asiento_linea_debe CHECK (debe >= 0),
    CONSTRAINT ck_asiento_linea_haber CHECK (haber >= 0),
    -- Exactamente uno de los dos lados es mayor que cero (CON-002)
    CONSTRAINT ck_asiento_linea_un_lado CHECK ((debe = 0) <> (haber = 0)),
    CONSTRAINT ck_asiento_linea_origen CHECK (origen_linea IN ('USUARIO', 'IVA_CALCULADO', 'OPERACION'))
);

COMMENT ON TABLE asiento_linea IS 'Líneas de un asiento (Debe o Haber). Insert-only para pilot_app (ADR-019). RLS forzado.';
COMMENT ON COLUMN asiento_linea.numero_linea IS 'Posición de la línea dentro del asiento, desde 1.';
COMMENT ON COLUMN asiento_linea.fecha IS 'Copia de asiento.fecha para las consultas del Mayor; el trigger diferido verifica que coincida.';
COMMENT ON COLUMN asiento_linea.cuenta_id IS 'Cuenta de detalle de la misma empresa (llave compuesta).';
COMMENT ON COLUMN asiento_linea.origen_linea IS 'USUARIO (capturada), IVA_CALCULADO (expandida por "lleva IVA") u OPERACION (generada desde n8n).';
COMMENT ON COLUMN asiento_linea.linea_base_id IS 'En una línea de IVA: la línea que la originó, del mismo asiento (llave compuesta con asiento_id).';
COMMENT ON CONSTRAINT uq_asiento_linea_asiento_id ON asiento_linea IS 'Objetivo de fk_asiento_linea_base: la línea base pertenece al mismo asiento.';
COMMENT ON CONSTRAINT ck_asiento_linea_un_lado ON asiento_linea IS 'Solo Debe o solo Haber, nunca ambos ni ninguno (CON-002).';
COMMENT ON CONSTRAINT ck_asiento_linea_debe ON asiento_linea IS 'Montos no negativos (CON-003); los 2 decimales los fija NUMERIC(19,2).';
COMMENT ON CONSTRAINT ck_asiento_linea_haber ON asiento_linea IS 'Montos no negativos (CON-003); los 2 decimales los fija NUMERIC(19,2).';

-- Índice del Libro Mayor: movimientos de una cuenta en un rango de fechas
CREATE INDEX idx_linea_mayor ON asiento_linea (empresa_id, cuenta_id, fecha);
COMMENT ON INDEX idx_linea_mayor IS 'Movimientos de una cuenta en un rango de fechas: Libro Mayor, saldo del mes parcial y balanza.';

ALTER TABLE asiento_linea ENABLE ROW LEVEL SECURITY;
ALTER TABLE asiento_linea FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON asiento_linea TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON asiento_linea IS 'pilot_app solo ve y escribe líneas de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Las líneas nunca se modifican ni se borran (ADR-019): solo SELECT e INSERT
GRANT SELECT, INSERT ON asiento_linea TO pilot_app;

-- 4. TRIGGER DIFERIDO DE PARTIDA DOBLE (CLAUDE.md §9.3; ADR-036, decisión 7).
-- Una sola función para dos constraint triggers: uno sobre asiento_linea y otro sobre la cabecera, para que un
-- asiento sin líneas tampoco pase el COMMIT. Corre con los permisos de quien escribe (no es SECURITY DEFINER),
-- así que RLS también la limita a la empresa de la sesión.
CREATE FUNCTION validar_partida_doble() RETURNS trigger AS $$
DECLARE
    v_asiento    UUID;
    v_empresa    UUID;
    v_cabecera   asiento%ROWTYPE;
    v_debe       NUMERIC(19,2);
    v_haber      NUMERIC(19,2);
    v_lineas     INT;
    v_otra_fecha INT;
BEGIN
    -- 1. Identifica el asiento afectado según la tabla que disparó el trigger
    IF TG_TABLE_NAME = 'asiento' THEN
        v_asiento := NEW.id;
        v_empresa := NEW.empresa_id;
    ELSIF TG_OP = 'DELETE' THEN
        v_asiento := OLD.asiento_id;
        v_empresa := OLD.empresa_id;
    ELSE
        v_asiento := NEW.asiento_id;
        v_empresa := NEW.empresa_id;
    END IF;

    -- 2. Busca la cabecera por id y empresa de la fila que disparó el trigger (regla 1.1.3); si no existe o no es visible no hay nada que validar
    SELECT * INTO v_cabecera FROM asiento WHERE id = v_asiento AND empresa_id = v_empresa;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    -- 3. Suma ambos lados, cuenta las líneas y busca líneas con otra fecha
    SELECT COALESCE(SUM(debe), 0), COALESCE(SUM(haber), 0), COUNT(*), COUNT(*) FILTER (WHERE fecha <> v_cabecera.fecha)
      INTO v_debe, v_haber, v_lineas, v_otra_fecha
      FROM asiento_linea
     WHERE asiento_id = v_asiento AND empresa_id = v_cabecera.empresa_id;

    -- 4. Rechaza menos de dos líneas (CON-001; redundante con el CHECK de un solo lado, que impide que una línea sola cuadre, y se deja como defensa), descuadres (CON-005), totales de cabecera inconsistentes y fechas distintas
    IF v_lineas < 2 OR v_debe <> v_haber
       OR v_cabecera.total_debe <> v_debe OR v_cabecera.total_haber <> v_haber OR v_otra_fecha > 0 THEN
        RAISE EXCEPTION 'Asiento % inválido: diferencia %, líneas %, totales de cabecera % / %, sumas % / %, líneas con otra fecha %',
            v_asiento, v_debe - v_haber, v_lineas, v_cabecera.total_debe, v_cabecera.total_haber, v_debe, v_haber, v_otra_fecha;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION validar_partida_doble() IS 'Al COMMIT verifica del asiento afectado: al menos 2 líneas, Σ Debe = Σ Haber, totales de cabecera iguales a las sumas y la fecha de cada línea igual a la del asiento (última defensa de la partida doble, ADR-036). Todas sus consultas filtran por asiento y empresa (regla 1.1.3).';

-- Se evalúa al COMMIT, cuando ya se insertaron todas las líneas
CREATE CONSTRAINT TRIGGER trg_partida_doble
    AFTER INSERT OR UPDATE OR DELETE ON asiento_linea
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validar_partida_doble();
COMMENT ON TRIGGER trg_partida_doble ON asiento_linea IS 'Valida el asiento de cada línea insertada, modificada o borrada al COMMIT (CLAUDE.md §9.3).';

-- Solo al insertar la cabecera: un asiento sin líneas no llega a disparar el trigger de asiento_linea
CREATE CONSTRAINT TRIGGER trg_partida_doble_cabecera
    AFTER INSERT ON asiento
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION validar_partida_doble();
COMMENT ON TRIGGER trg_partida_doble_cabecera ON asiento IS 'Garantiza que un asiento sin líneas no pase el COMMIT (ADR-036, decisión 7).';

-- 5. TRANSICIONES DE ASIENTO (ADR-019): la única actualización válida es CONTABILIZADO → REVERTIDO.
CREATE FUNCTION validar_transicion_asiento() RETURNS trigger AS $$
BEGIN
    -- 1. Solo se permite pasar de CONTABILIZADO a REVERTIDO fijando la reversión (de nula a no nula) y subiendo version en 1
    IF OLD.estado = 'CONTABILIZADO' AND NEW.estado = 'REVERTIDO'
       AND OLD.asiento_reversion_id IS NULL AND NEW.asiento_reversion_id IS NOT NULL
       AND NEW.version = OLD.version + 1 THEN
        -- 2. La reversión enlazada debe ser realmente un contra-asiento de este asiento
        IF NOT EXISTS (SELECT 1 FROM asiento r
                        WHERE r.id = NEW.asiento_reversion_id AND r.empresa_id = OLD.empresa_id
                          AND r.asiento_revertido_id = OLD.id) THEN
            RAISE EXCEPTION 'Transición inválida del asiento %: % no es una reversión suya', OLD.id, NEW.asiento_reversion_id;
        END IF;
        RETURN NEW;
    END IF;

    -- 3. Cualquier otro cambio de estado, reversión o versión se rechaza
    RAISE EXCEPTION 'Transición inválida del asiento %: % → % (solo CONTABILIZADO → REVERTIDO con su reversión y version + 1)',
        OLD.id, OLD.estado, NEW.estado;
END;
$$ LANGUAGE plpgsql;
COMMENT ON FUNCTION validar_transicion_asiento() IS 'Permite únicamente CONTABILIZADO → REVERTIDO con asiento_reversion_id (de nulo a una reversión suya) y version + 1 (ADR-019).';

CREATE TRIGGER trg_asiento_transicion
    BEFORE UPDATE ON asiento
    FOR EACH ROW EXECUTE FUNCTION validar_transicion_asiento();
COMMENT ON TRIGGER trg_asiento_transicion ON asiento IS 'Rechaza toda actualización que no sea el paso a REVERTIDO (ADR-019).';
