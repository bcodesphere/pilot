-- V16 — Operaciones guiadas: tabla operacion, origen_tipo OPERACION y reglas de los diez tipos guiados
-- (CLAUDE.md §9.3, §10.6, §12.5; ADR-018, ADR-019, ADR-020, ADR-034, ADR-035, ADR-041, ADR-042).
-- Sigue el patrón de V11 y V13: políticas aislamiento_empresa declaradas TO pilot_app (ADR-026, sin
-- missing_ok: sin app.empresa_id falla), y la referencia a asiento es una llave foránea COMPUESTA
-- (empresa_id, asiento_id): una fila de operacion nunca apunta al asiento de otra empresa aunque RLS fallara
-- (ADR-035, decisión 6).

-- 1. OPERACION: hecho de negocio registrado por el motor único ContabilizarOperacion (ADR-041, spec F4.5 §5.6).
CREATE TABLE operacion (
    id           UUID PRIMARY KEY,
    empresa_id   UUID NOT NULL REFERENCES empresa(id),
    tipo         VARCHAR(30) NOT NULL,
    fecha        DATE NOT NULL,
    descripcion  VARCHAR(300),
    datos        JSONB NOT NULL,
    resumen      JSONB NOT NULL,
    total        NUMERIC(19,2) NOT NULL CHECK (total > 0),
    asiento_id   UUID NOT NULL,
    estado       VARCHAR(13) NOT NULL DEFAULT 'CONTABILIZADA',  -- 13: 'CONTABILIZADA' no cabe en 12 (como asiento.estado, V13)
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    creado_por   VARCHAR(64) NOT NULL,
    version      BIGINT NOT NULL DEFAULT 0,
    -- Objetivo de la llave foránea compuesta de activo_fijo.operacion_id (V18), como uq_asiento_empresa_id en V13
    CONSTRAINT uq_operacion_empresa_id UNIQUE (empresa_id, id),
    -- El asiento generado es de la misma empresa (llave compuesta, como asiento_linea en V13)
    CONSTRAINT fk_operacion_asiento FOREIGN KEY (empresa_id, asiento_id) REFERENCES asiento (empresa_id, id),
    CONSTRAINT ck_operacion_estado CHECK (estado IN ('CONTABILIZADA', 'REVERTIDA')),
    -- Los diez tipos guiados de ADR-041 §5.2; CIERRE_INGRESOS_DIARIO no genera fila aquí (sigue en operacion_externa, F5)
    CONSTRAINT ck_operacion_tipo CHECK (tipo IN ('VENTA','COMPRA_GASTO','COBRO_CLIENTE','PAGO_PROVEEDOR','APORTE_CAPITAL',
        'PRESTAMO_RECIBIDO','PAGO_CUOTA','TRASLADO_FONDOS','COMPRA_ACTIVO_FIJO','DEPRECIACION_MENSUAL'))
);

COMMENT ON TABLE operacion IS 'Hecho de negocio registrado desde una pantalla guiada (ADR-041); guarda la entrada, el resumen calculado y el asiento resultante. Inmutable salvo el paso a REVERTIDA (ADR-019). RLS forzado.';
COMMENT ON COLUMN operacion.tipo IS 'Uno de los diez tipos guiados del motor ContabilizarOperacion (ADR-041 §5.2).';
COMMENT ON COLUMN operacion.fecha IS 'Fecha contable de la operación (DATE en hora de El Salvador, como asiento.fecha).';
COMMENT ON COLUMN operacion.datos IS 'Entrada de negocio ya validada (el cuerpo tipado del formulario), tal como la recibió el motor.';
COMMENT ON COLUMN operacion.resumen IS 'Resumen calculado por el motor: base, IVA, exento, no sujeto y total (ResumenOperacion del contrato).';
COMMENT ON COLUMN operacion.asiento_id IS 'Asiento generado por el motor en la misma transacción (ADR-018); nunca nulo.';
COMMENT ON COLUMN operacion.estado IS 'CONTABILIZADA o REVERTIDA; pasa a REVERTIDA cuando se revierte su asiento (evento AsientoRevertido, ADR-036).';
COMMENT ON COLUMN operacion.creado_por IS 'Valor de app.usuario_id que registró la operación.';
COMMENT ON COLUMN operacion.version IS 'Versión para concurrencia optimista (paso a REVERTIDA).';
COMMENT ON CONSTRAINT uq_operacion_empresa_id ON operacion IS 'Objetivo de la llave foránea compuesta de activo_fijo.operacion_id (V18): impide referenciar una operación de otra empresa (ADR-035).';

-- Listado por empresa y fecha, más reciente primero (pantalla de operaciones, ADR-041)
CREATE INDEX idx_operacion_listado ON operacion (empresa_id, fecha DESC, id DESC);
COMMENT ON INDEX idx_operacion_listado IS 'Consulta paginada de operaciones por empresa, ordenada por fecha descendente (GET /contabilidad/operaciones).';

ALTER TABLE operacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE operacion FORCE ROW LEVEL SECURITY;

CREATE POLICY aislamiento_empresa ON operacion TO pilot_app
    USING (empresa_id = current_setting('app.empresa_id')::uuid)
    WITH CHECK (empresa_id = current_setting('app.empresa_id')::uuid);
COMMENT ON POLICY aislamiento_empresa ON operacion IS 'pilot_app solo ve y escribe operaciones de la empresa fijada en app.empresa_id; sin ese parámetro falla (intencional).';

-- Inmutable como asiento (ADR-019): sin DELETE y UPDATE solo del paso a REVERTIDA
GRANT SELECT, INSERT ON operacion TO pilot_app;
GRANT UPDATE (estado, version) ON operacion TO pilot_app;

-- 2. El asiento admite el origen OPERACION (origen_id = operacion.id); el nombre real del CHECK es
--    ck_asiento_origen_tipo (V13). asiento_linea.origen_linea ya admite 'OPERACION' desde V13 (ck_asiento_linea_origen).
ALTER TABLE asiento DROP CONSTRAINT ck_asiento_origen_tipo;
ALTER TABLE asiento ADD CONSTRAINT ck_asiento_origen_tipo CHECK (origen_tipo IN ('MANUAL','N8N','REVERSION','OPERACION'));
COMMENT ON CONSTRAINT ck_asiento_origen_tipo ON asiento IS 'MANUAL (Libro Diario), N8N (operación externa), REVERSION (contra-asiento) u OPERACION (operación guiada, ADR-041).';

-- 3. REGLAS: tipos y categorías nuevos, y grupo permitido (prefijo_permitido, ADR-042). Nombres reales de los
--    CHECK verificados en V10 (ck_plantilla_regla_categoria) y V11 (ck_regla_contabilizacion_tipo,
--    ck_regla_contabilizacion_categoria); tipo_operacion de la plantilla no tenía CHECK en V10, se agrega ahora.
--    categoria era VARCHAR(10) (le alcanzaba a INGRESO y COBRO); CONTRAPARTIDA (13) y DEPRECIACION (12) no caben.
ALTER TABLE plantilla_regla_contabilizacion ALTER COLUMN categoria TYPE VARCHAR(13);
ALTER TABLE regla_contabilizacion ALTER COLUMN categoria TYPE VARCHAR(13);
ALTER TABLE plantilla_regla_contabilizacion ADD CONSTRAINT ck_plantilla_regla_tipo CHECK (tipo_operacion IN
    ('CIERRE_INGRESOS_DIARIO','VENTA','COMPRA_GASTO','COBRO_CLIENTE','PAGO_PROVEEDOR','APORTE_CAPITAL',
     'PRESTAMO_RECIBIDO','PAGO_CUOTA','COMPRA_ACTIVO_FIJO','DEPRECIACION_MENSUAL'));
ALTER TABLE plantilla_regla_contabilizacion DROP CONSTRAINT ck_plantilla_regla_categoria;
ALTER TABLE plantilla_regla_contabilizacion ADD CONSTRAINT ck_plantilla_regla_categoria
    CHECK (categoria IN ('INGRESO','COBRO','PAGO','GASTO','CONTRAPARTIDA','ACTIVO','DEPRECIACION'));
ALTER TABLE plantilla_regla_contabilizacion ADD COLUMN prefijo_permitido VARCHAR(8);
COMMENT ON CONSTRAINT ck_plantilla_regla_tipo ON plantilla_regla_contabilizacion IS 'CIERRE_INGRESOS_DIARIO (n8n) más los diez tipos guiados de ADR-041; otro tipo exige un ADR y una migración nueva.';
COMMENT ON COLUMN plantilla_regla_contabilizacion.prefijo_permitido IS 'Prefijo de código que debe tener la cuenta de esta regla (p. ej. "1101"); base de CON-022 (ADR-042).';

ALTER TABLE regla_contabilizacion DROP CONSTRAINT ck_regla_contabilizacion_tipo;
ALTER TABLE regla_contabilizacion ADD CONSTRAINT ck_regla_contabilizacion_tipo CHECK (tipo_operacion IN
    ('CIERRE_INGRESOS_DIARIO','VENTA','COMPRA_GASTO','COBRO_CLIENTE','PAGO_PROVEEDOR','APORTE_CAPITAL',
     'PRESTAMO_RECIBIDO','PAGO_CUOTA','COMPRA_ACTIVO_FIJO','DEPRECIACION_MENSUAL'));
ALTER TABLE regla_contabilizacion DROP CONSTRAINT ck_regla_contabilizacion_categoria;
ALTER TABLE regla_contabilizacion ADD CONSTRAINT ck_regla_contabilizacion_categoria
    CHECK (categoria IN ('INGRESO','COBRO','PAGO','GASTO','CONTRAPARTIDA','ACTIVO','DEPRECIACION'));
ALTER TABLE regla_contabilizacion ADD COLUMN prefijo_permitido VARCHAR(8);
COMMENT ON COLUMN regla_contabilizacion.prefijo_permitido IS 'Prefijo de código que debe tener la cuenta de esta regla; asignarle una cuenta fuera del grupo responde 422 CON-022 (ADR-042). Se completa más abajo antes de quedar NOT NULL.';

-- 4. Prefijo de las reglas del cierre ya existentes (CIERRE_INGRESOS_DIARIO), en la plantilla.
--    Borrador [VERIFICAR] con contador (ADR-034), como el resto de reglas por defecto.
UPDATE plantilla_regla_contabilizacion SET prefijo_permitido = CASE
    WHEN categoria = 'INGRESO' THEN '5101'
    WHEN codigo IN ('EFECTIVO','TRANSFERENCIA','CHEQUE') THEN '1101'
    WHEN codigo IN ('TARJETA','CREDITO') THEN '1102'
    ELSE '11' END
 WHERE tipo_operacion = 'CIERRE_INGRESOS_DIARIO';

-- 5. Reglas de las operaciones guiadas (spec F4.5 §5.3 a §5.5): tipo, categoría, código, cuenta por defecto y
--    grupo permitido. Borrador [VERIFICAR] con contador (ADR-034): cuentas por defecto y prefijos sujetos a
--    validación; mientras tanto se cargan tal como aparecen en el plan de F4.5 (tarea B1).
--    VENTA: 3 reglas de INGRESO + 4 de COBRO = 7 (valor que afirma MigracionesOperacionesIT).
INSERT INTO plantilla_regla_contabilizacion (tipo_operacion, categoria, codigo, cuenta_codigo, activa, prefijo_permitido) VALUES
 ('VENTA','INGRESO','GRAVADO','51010101',true,'5101'), ('VENTA','INGRESO','EXENTO','51010102',true,'5101'),
 ('VENTA','INGRESO','NO_SUJETO','51010103',true,'5101'),
 ('VENTA','COBRO','EFECTIVO','11010101',true,'1101'), ('VENTA','COBRO','BANCO','11010103',true,'1101'),
 ('VENTA','COBRO','TARJETA','11020102',true,'1102'), ('VENTA','COBRO','CREDITO','11020101',true,'1102'),
 ('COMPRA_GASTO','GASTO','MERCADERIA','41020101',true,'4102'), ('COMPRA_GASTO','GASTO','ALQUILER','42020102',true,'4202'),
 ('COMPRA_GASTO','GASTO','SERVICIOS_BASICOS','42020103',true,'4202'), ('COMPRA_GASTO','GASTO','PAPELERIA','42020104',true,'4202'),
 ('COMPRA_GASTO','GASTO','SUELDOS_ADMINISTRACION','42020101',true,'4202'), ('COMPRA_GASTO','GASTO','SUELDOS_VENTAS','42010101',true,'4201'),
 ('COMPRA_GASTO','GASTO','HONORARIOS','42020106',true,'4202'), ('COMPRA_GASTO','GASTO','PUBLICIDAD','42010102',true,'4201'),
 ('COMPRA_GASTO','GASTO','COMISIONES_BANCARIAS','43010102',true,'4301'),
 -- OTRO_GASTO nace inactiva y sin cuenta, como COBRO/OTRO del cierre (ADR-035): la configura el contador
 ('COMPRA_GASTO','GASTO','OTRO_GASTO',NULL,false,'42'),
 ('COMPRA_GASTO','PAGO','EFECTIVO','11010101',true,'1101'), ('COMPRA_GASTO','PAGO','BANCO','11010103',true,'1101'),
 ('COMPRA_GASTO','PAGO','CREDITO','21010101',true,'2101'),
 ('COBRO_CLIENTE','CONTRAPARTIDA','CLIENTES','11020101',true,'1102'), ('COBRO_CLIENTE','CONTRAPARTIDA','TARJETAS','11020102',true,'1102'),
 ('COBRO_CLIENTE','CONTRAPARTIDA','COMISION_TARJETA','42010103',true,'4201'), ('COBRO_CLIENTE','CONTRAPARTIDA','ANTICIPO_IVA','11040104',true,'1104'),
 ('COBRO_CLIENTE','COBRO','EFECTIVO','11010101',true,'1101'), ('COBRO_CLIENTE','COBRO','BANCO','11010103',true,'1101'),
 ('PAGO_PROVEEDOR','CONTRAPARTIDA','PROVEEDORES','21010101',true,'2101'),
 ('PAGO_PROVEEDOR','PAGO','EFECTIVO','11010101',true,'1101'), ('PAGO_PROVEEDOR','PAGO','BANCO','11010103',true,'1101'),
 ('APORTE_CAPITAL','CONTRAPARTIDA','CAPITAL','31010101',true,'3101'),
 ('APORTE_CAPITAL','COBRO','EFECTIVO','11010101',true,'1101'), ('APORTE_CAPITAL','COBRO','BANCO','11010103',true,'1101'),
 ('PRESTAMO_RECIBIDO','CONTRAPARTIDA','CORTO','21040101',true,'2104'), ('PRESTAMO_RECIBIDO','CONTRAPARTIDA','LARGO','22010101',true,'2201'),
 ('PRESTAMO_RECIBIDO','COBRO','EFECTIVO','11010101',true,'1101'), ('PRESTAMO_RECIBIDO','COBRO','BANCO','11010103',true,'1101'),
 ('PAGO_CUOTA','CONTRAPARTIDA','CORTO','21040101',true,'2104'), ('PAGO_CUOTA','CONTRAPARTIDA','LARGO','22010101',true,'2201'),
 ('PAGO_CUOTA','CONTRAPARTIDA','INTERESES','43010101',true,'4301'), ('PAGO_CUOTA','CONTRAPARTIDA','COMISION','43010102',true,'4301'),
 ('PAGO_CUOTA','PAGO','EFECTIVO','11010101',true,'1101'), ('PAGO_CUOTA','PAGO','BANCO','11010103',true,'1101'),
 ('COMPRA_ACTIVO_FIJO','ACTIVO','MOBILIARIO_EQUIPO','12010101',true,'1201'), ('COMPRA_ACTIVO_FIJO','ACTIVO','EQUIPO_COMPUTO','12010102',true,'1201'),
 ('COMPRA_ACTIVO_FIJO','ACTIVO','VEHICULO','12010103',true,'1201'), ('COMPRA_ACTIVO_FIJO','ACTIVO','EDIFICIO','12010202',true,'1201'),
 ('COMPRA_ACTIVO_FIJO','ACTIVO','TERRENO','12010201',true,'1201'),
 ('COMPRA_ACTIVO_FIJO','PAGO','EFECTIVO','11010101',true,'1101'), ('COMPRA_ACTIVO_FIJO','PAGO','BANCO','11010103',true,'1101'),
 ('COMPRA_ACTIVO_FIJO','PAGO','CREDITO','21010101',true,'2101'),
 ('DEPRECIACION_MENSUAL','DEPRECIACION','MOBILIARIO_EQUIPO','12020101',true,'1202'), ('DEPRECIACION_MENSUAL','DEPRECIACION','EQUIPO_COMPUTO','12020102',true,'1202'),
 ('DEPRECIACION_MENSUAL','DEPRECIACION','VEHICULO','12020103',true,'1202'), ('DEPRECIACION_MENSUAL','DEPRECIACION','EDIFICIO','12020201',true,'1202'),
 ('DEPRECIACION_MENSUAL','GASTO','DEPRECIACION','42020105',true,'4202');

-- Toda regla precargada, del cierre o guiada, ya tiene su prefijo: la columna queda obligatoria desde aquí
ALTER TABLE plantilla_regla_contabilizacion ALTER COLUMN prefijo_permitido SET NOT NULL;

-- 6. Completar las empresas que ya instalaron Contabilidad antes de esta migración (ADR-042, spec F4.5 "Foco de
--    revisión" 3). RLS forzado se aplica incluso al dueño y las políticas son TO pilot_app (ADR-026), así que el
--    dueño no ve filas sin levantar FORCE. Se levanta solo durante estas sentencias, dentro de la transacción de
--    Flyway, y se restablece a continuación; si algo falla, todo se revierte.
ALTER TABLE regla_contabilizacion NO FORCE ROW LEVEL SECURITY;
ALTER TABLE cuenta_contable NO FORCE ROW LEVEL SECURITY;

-- 6a. Prefijo de las reglas del cierre ya copiadas a esas empresas
UPDATE regla_contabilizacion r SET prefijo_permitido = p.prefijo_permitido
  FROM plantilla_regla_contabilizacion p
 WHERE p.tipo_operacion = r.tipo_operacion AND p.categoria = r.categoria AND p.codigo = r.codigo;

-- 6b. Reglas de los tipos guiados, nuevas para esas empresas. gen_random_uuid() genera UUID v4: se acepta SOLO en
--     esta migración de datos (ADR-010 aplica a los id que genera la aplicación, no a esta siembra masiva). Si la
--     cuenta por defecto no existe en esa empresa (catálogo distinto al de la plantilla actual), la regla queda
--     inactiva y sin cuenta (p.activa AND c.id IS NOT NULL), que cumple el CHECK de más abajo; el tablero (B5) la
--     mostrará como pendiente.
INSERT INTO regla_contabilizacion (id, empresa_id, tipo_operacion, categoria, codigo, cuenta_id, activa, prefijo_permitido, creado_por)
SELECT gen_random_uuid(), ea.empresa_id, p.tipo_operacion, p.categoria, p.codigo, c.id, p.activa AND c.id IS NOT NULL, p.prefijo_permitido, 'sistema'
  FROM empresa_aplicacion ea
  JOIN plantilla_regla_contabilizacion p ON p.tipo_operacion <> 'CIERRE_INGRESOS_DIARIO'
  LEFT JOIN cuenta_contable c ON c.empresa_id = ea.empresa_id AND c.codigo = p.cuenta_codigo
 WHERE ea.aplicacion_codigo = 'contabilidad'
ON CONFLICT (empresa_id, tipo_operacion, categoria, codigo) DO NOTHING;

-- Toda regla de toda empresa con Contabilidad instalada ya tiene su prefijo: la columna queda obligatoria
ALTER TABLE regla_contabilizacion ALTER COLUMN prefijo_permitido SET NOT NULL;

ALTER TABLE regla_contabilizacion FORCE ROW LEVEL SECURITY;
ALTER TABLE cuenta_contable FORCE ROW LEVEL SECURITY;
