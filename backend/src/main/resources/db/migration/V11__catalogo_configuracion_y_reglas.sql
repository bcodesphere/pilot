-- V11 — Catálogo de cuentas, configuración contable y reglas de contabilización por empresa
-- (CLAUDE.md §9.3, §10.2, §11.3, ADR-020, ADR-035).
-- Las políticas aislamiento_empresa se declaran TO pilot_app (ADR-026) y sin missing_ok: sin app.empresa_id falla.
-- Las referencias entre estas tablas son llaves foráneas COMPUESTAS (empresa_id, cuenta_id): una fila nunca apunta a
-- la cuenta de otra empresa, aunque RLS fallara (ADR-035, decisión 6). Los controles de integridad referencial de
-- PostgreSQL no pasan por RLS, por eso esta defensa es necesaria.

-- 1. CUENTA_CONTABLE: catálogo por empresa; la clase es el primer dígito del código.
CREATE TABLE cuenta_contable (
    id                  UUID PRIMARY KEY,
    empresa_id          UUID NOT NULL REFERENCES empresa(id),
    codigo              VARCHAR(8) NOT NULL,
    nombre              VARCHAR(200) NOT NULL,
    clase               SMALLINT GENERATED ALWAYS AS (substr(codigo, 1, 1)::smallint) STORED,
    nivel               SMALLINT NOT NULL,
    cuenta_padre_id     UUID,
    naturaleza          VARCHAR(9) NOT NULL,
    acepta_movimientos  BOOLEAN NOT NULL,
    activa              BOOLEAN NOT NULL DEFAULT true,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por          VARCHAR(64) NOT NULL,
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por     VARCHAR(64),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_cuenta_contable_codigo UNIQUE (empresa_id, codigo),
    -- Objetivo de las llaves foráneas compuestas de las tres tablas de este archivo
    CONSTRAINT uq_cuenta_contable_empresa_id UNIQUE (empresa_id, id),
    CONSTRAINT fk_cuenta_contable_padre FOREIGN KEY (empresa_id, cuenta_padre_id)
        REFERENCES cuenta_contable (empresa_id, id),
    -- Solo clases 1 a 5 (CON-010); la clase 6 es del cierre anual, fuera de alcance
    CONSTRAINT ck_cuenta_contable_codigo CHECK (codigo ~ '^[1-5][0-9]*$'),
    -- Longitud válida (CON-015): clase 1, grupo 2, cuenta 4, subcuenta 6, detalle 8
    CONSTRAINT ck_cuenta_contable_longitud CHECK (length(codigo) IN (1, 2, 4, 6, 8)),
    -- El nivel es coherente con la longitud del código (CLAUDE.md §10.2)
    CONSTRAINT ck_cuenta_contable_nivel CHECK (
        nivel = CASE length(codigo) WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 4 THEN 3 WHEN 6 THEN 4 WHEN 8 THEN 5 END),
    CONSTRAINT ck_cuenta_contable_naturaleza CHECK (naturaleza IN ('DEUDORA', 'ACREEDORA'))
);

COMMENT ON TABLE cuenta_contable IS 'Catálogo de cuentas por empresa, copiado de plantilla_cuenta al instalar Contabilidad y editable. RLS forzado.';
COMMENT ON COLUMN cuenta_contable.codigo IS 'Código de 1, 2, 4, 6 u 8 dígitos; el de la cuenta padre es prefijo del de la hija. Único por empresa.';
COMMENT ON COLUMN cuenta_contable.clase IS 'Derivada del primer dígito: 1 Activo, 2 Pasivo, 3 Capital, 4 Costos y Gastos, 5 Ingresos.';
COMMENT ON COLUMN cuenta_contable.nivel IS '1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5 detalle; se valida contra la longitud del código.';
COMMENT ON COLUMN cuenta_contable.cuenta_padre_id IS 'Cuenta de nivel superior, de la misma empresa (llave compuesta); nula solo en las clases.';
COMMENT ON COLUMN cuenta_contable.naturaleza IS 'DEUDORA (aumenta con el Debe) o ACREEDORA (aumenta con el Haber); editable para cuentas complementarias.';
COMMENT ON COLUMN cuenta_contable.acepta_movimientos IS 'true solo en cuentas de detalle sin hijas; las demás no aceptan asientos (CON-006).';
COMMENT ON COLUMN cuenta_contable.activa IS 'false = no acepta movimientos nuevos (CON-006); no se puede desactivar con saldo (CON-012).';
COMMENT ON COLUMN cuenta_contable.creado_por IS 'Usuario (app.usuario_id) que la creó.';
COMMENT ON COLUMN cuenta_contable.actualizado_en IS 'Última modificación (UTC).';
COMMENT ON COLUMN cuenta_contable.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';
COMMENT ON COLUMN cuenta_contable.version IS 'Versión para concurrencia optimista (If-Match/ETag).';
COMMENT ON CONSTRAINT uq_cuenta_contable_empresa_id ON cuenta_contable IS 'Objetivo de las llaves foráneas compuestas (empresa_id, cuenta_id): impide referenciar cuentas de otra empresa (ADR-035).';

-- Filtro por cuenta padre: árbol del catálogo y validación de hijas (CON-011, CON-015)
CREATE INDEX idx_cuenta_contable_padre ON cuenta_contable (empresa_id, cuenta_padre_id);
COMMENT ON INDEX idx_cuenta_contable_padre IS 'Búsqueda de las hijas de una cuenta (árbol del catálogo y validación de padre).';

ALTER TABLE cuenta_contable ENABLE ROW LEVEL SECURITY;
ALTER TABLE cuenta_contable FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON cuenta_contable TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON cuenta_contable IS 'pilot_app solo ve y escribe cuentas de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Crea y edita el catálogo; sin UPDATE de id, empresa_id, clase (generada) ni creado_*, y sin DELETE (se desactiva).
GRANT SELECT, INSERT ON cuenta_contable TO pilot_app;
GRANT UPDATE (codigo, nombre, naturaleza, nivel, cuenta_padre_id, acepta_movimientos, activa, actualizado_en, actualizado_por, version)
    ON cuenta_contable TO pilot_app;

-- 2. CONFIGURACION_CONTABLE: una fila por empresa (CLAUDE.md §11.3).
CREATE TABLE configuracion_contable (
    empresa_id             UUID PRIMARY KEY REFERENCES empresa(id),
    modo_precio_defecto    VARCHAR(7) NOT NULL DEFAULT 'CON_IVA',
    cuenta_iva_debito_id   UUID NOT NULL,
    cuenta_iva_credito_id  UUID NOT NULL,
    actualizado_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por        VARCHAR(64),
    version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_configuracion_contable_modo CHECK (modo_precio_defecto IN ('CON_IVA', 'SIN_IVA')),
    CONSTRAINT fk_configuracion_contable_iva_debito FOREIGN KEY (empresa_id, cuenta_iva_debito_id)
        REFERENCES cuenta_contable (empresa_id, id),
    CONSTRAINT fk_configuracion_contable_iva_credito FOREIGN KEY (empresa_id, cuenta_iva_credito_id)
        REFERENCES cuenta_contable (empresa_id, id)
);

COMMENT ON TABLE configuracion_contable IS 'Configuración contable de la empresa (una fila): modo de precio y cuentas de IVA. RLS forzado.';
COMMENT ON COLUMN configuracion_contable.modo_precio_defecto IS 'CON_IVA o SIN_IVA; valor inicial del formulario manual y modo de todas las operaciones de n8n (ADR-015).';
COMMENT ON COLUMN configuracion_contable.cuenta_iva_debito_id IS 'IVA débito fiscal (pasivo); cuenta de la misma empresa (llave compuesta).';
COMMENT ON COLUMN configuracion_contable.cuenta_iva_credito_id IS 'IVA crédito fiscal (activo); cuenta de la misma empresa (llave compuesta).';
COMMENT ON COLUMN configuracion_contable.actualizado_en IS 'Última modificación (UTC).';
COMMENT ON COLUMN configuracion_contable.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';
COMMENT ON COLUMN configuracion_contable.version IS 'Versión para concurrencia optimista (If-Match/ETag).';

ALTER TABLE configuracion_contable ENABLE ROW LEVEL SECURITY;
ALTER TABLE configuracion_contable FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON configuracion_contable TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON configuracion_contable IS 'pilot_app solo ve y escribe la configuración de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Se crea en la precarga y se edita; sin UPDATE de empresa_id y sin DELETE.
GRANT SELECT, INSERT ON configuracion_contable TO pilot_app;
GRANT UPDATE (modo_precio_defecto, cuenta_iva_debito_id, cuenta_iva_credito_id, actualizado_en, actualizado_por, version)
    ON configuracion_contable TO pilot_app;

-- 3. REGLA_CONTABILIZACION: cuenta para cada código de una operación de n8n (ADR-020, ADR-035).
CREATE TABLE regla_contabilizacion (
    id               UUID PRIMARY KEY,
    empresa_id       UUID NOT NULL REFERENCES empresa(id),
    tipo_operacion   VARCHAR(40) NOT NULL,
    categoria        VARCHAR(10) NOT NULL,
    codigo           VARCHAR(40) NOT NULL,
    cuenta_id        UUID,
    activa           BOOLEAN NOT NULL DEFAULT true,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por       VARCHAR(64) NOT NULL,
    actualizado_en   TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_por  VARCHAR(64),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_regla_contabilizacion UNIQUE (empresa_id, tipo_operacion, categoria, codigo),
    CONSTRAINT fk_regla_contabilizacion_cuenta FOREIGN KEY (empresa_id, cuenta_id)
        REFERENCES cuenta_contable (empresa_id, id),
    -- En 1.0 solo existe este tipo de operación (CLAUDE.md §12.1); otro requiere ADR y una migración nueva
    CONSTRAINT ck_regla_contabilizacion_tipo CHECK (tipo_operacion IN ('CIERRE_INGRESOS_DIARIO')),
    CONSTRAINT ck_regla_contabilizacion_categoria CHECK (categoria IN ('INGRESO', 'COBRO')),
    -- Una regla activa siempre tiene cuenta; solo una inactiva puede carecer de ella (ADR-035)
    CONSTRAINT ck_regla_contabilizacion_cuenta CHECK (NOT activa OR cuenta_id IS NOT NULL)
);

COMMENT ON TABLE regla_contabilizacion IS 'Reglas que dicen qué cuenta usar por tipo de operación, categoría y código en las operaciones de n8n. RLS forzado.';
COMMENT ON COLUMN regla_contabilizacion.tipo_operacion IS 'Tipo de operación externa; en 1.0 solo CIERRE_INGRESOS_DIARIO.';
COMMENT ON COLUMN regla_contabilizacion.categoria IS 'INGRESO (concepto de ingreso) o COBRO (forma de pago).';
COMMENT ON COLUMN regla_contabilizacion.codigo IS 'Concepto (VENTAS_GRAVADAS…) o forma de pago (EFECTIVO…), en mayúsculas con guion bajo.';
COMMENT ON COLUMN regla_contabilizacion.cuenta_id IS 'Cuenta de detalle de la misma empresa (llave compuesta); nula solo si la regla está inactiva (ADR-035).';
COMMENT ON COLUMN regla_contabilizacion.activa IS 'Una operación que use una regla inactiva o ausente se rechaza con CON-020.';
COMMENT ON COLUMN regla_contabilizacion.creado_por IS 'Usuario (app.usuario_id) que la creó.';
COMMENT ON COLUMN regla_contabilizacion.actualizado_en IS 'Última modificación (UTC).';
COMMENT ON COLUMN regla_contabilizacion.actualizado_por IS 'Usuario (app.usuario_id) que hizo la última modificación.';
COMMENT ON COLUMN regla_contabilizacion.version IS 'Versión para concurrencia optimista (If-Match/ETag).';

-- Consulta por cuenta: CON-016 (cuenta en uso por una regla activa). La búsqueda por código ya la cubre el UNIQUE.
CREATE INDEX idx_regla_contabilizacion_cuenta ON regla_contabilizacion (empresa_id, cuenta_id);
COMMENT ON INDEX idx_regla_contabilizacion_cuenta IS 'Detecta las reglas que usan una cuenta (CON-016).';

ALTER TABLE regla_contabilizacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE regla_contabilizacion FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON regla_contabilizacion TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON regla_contabilizacion IS 'pilot_app solo ve y escribe reglas de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Se editan la cuenta y el estado; las claves de la regla (tipo, categoría, código) no cambian y no hay DELETE.
GRANT SELECT, INSERT ON regla_contabilizacion TO pilot_app;
GRANT UPDATE (cuenta_id, activa, actualizado_en, actualizado_por, version) ON regla_contabilizacion TO pilot_app;
