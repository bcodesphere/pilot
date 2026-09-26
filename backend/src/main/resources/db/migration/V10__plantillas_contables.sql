-- V10 — Plantillas globales de Contabilidad (CLAUDE.md §9.3, §10.2, §12.5; ADR-034, ADR-035).
-- Se copian a cada empresa al instalar la app (evento AplicacionInstalada, ADR-030). Son globales, sin RLS y
-- de solo lectura para pilot_app; un cambio de datos va en una migración nueva.

-- 1. PLANTILLA_CUENTA: catálogo base (docs/contabilidad/catalogo-base.md).
CREATE TABLE plantilla_cuenta (
    codigo      VARCHAR(8) PRIMARY KEY,
    nombre      VARCHAR(200) NOT NULL,
    naturaleza  VARCHAR(9) NOT NULL,
    -- Derivados del código: el nivel por su longitud (1, 2, 4, 6, 8 dígitos) y la clase por su primer dígito
    nivel       SMALLINT GENERATED ALWAYS AS (
                    CASE length(codigo) WHEN 1 THEN 1 WHEN 2 THEN 2 WHEN 4 THEN 3 WHEN 6 THEN 4 WHEN 8 THEN 5 END
                ) STORED,
    clase       SMALLINT GENERATED ALWAYS AS (substr(codigo, 1, 1)::smallint) STORED,
    CONSTRAINT ck_plantilla_cuenta_codigo CHECK (codigo ~ '^[1-5][0-9]*$'),
    CONSTRAINT ck_plantilla_cuenta_longitud CHECK (length(codigo) IN (1, 2, 4, 6, 8)),
    CONSTRAINT ck_plantilla_cuenta_naturaleza CHECK (naturaleza IN ('DEUDORA', 'ACREEDORA'))
);

COMMENT ON TABLE plantilla_cuenta IS 'Catálogo de cuentas base: borrador pendiente de validación por contador (ADR-034). Global, solo lectura para pilot_app.';
COMMENT ON COLUMN plantilla_cuenta.codigo IS 'Código de la cuenta; su primer dígito es la clase (1 a 5) y su longitud el nivel.';
COMMENT ON COLUMN plantilla_cuenta.naturaleza IS 'DEUDORA o ACREEDORA; por defecto según la clase, con excepciones marcadas en el catálogo base (p. ej. depreciación acumulada).';
COMMENT ON COLUMN plantilla_cuenta.nivel IS 'Derivado de la longitud: 1 clase, 2 grupo, 3 cuenta, 4 subcuenta, 5 detalle (CLAUDE.md §10.2).';
COMMENT ON COLUMN plantilla_cuenta.clase IS 'Derivado del primer dígito: 1 Activo, 2 Pasivo, 3 Capital, 4 Costos y Gastos, 5 Ingresos.';

GRANT SELECT ON plantilla_cuenta TO pilot_app;

INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('1', 'ACTIVO', 'DEUDORA'),
    ('11', 'ACTIVO CORRIENTE', 'DEUDORA'),
    ('1101', 'Efectivo y equivalentes', 'DEUDORA'),
    ('110101', 'Efectivo', 'DEUDORA'),
    ('11010101', 'Caja general', 'DEUDORA'),
    ('11010102', 'Caja chica', 'DEUDORA'),
    ('11010103', 'Bancos', 'DEUDORA'),
    ('1102', 'Cuentas por cobrar', 'DEUDORA'),
    ('110201', 'Cuentas por cobrar comerciales', 'DEUDORA'),
    ('11020101', 'Clientes', 'DEUDORA'),
    ('11020102', 'Cuentas por cobrar — emisores de tarjetas', 'DEUDORA'),
    ('1103', 'Inventarios', 'DEUDORA'),
    ('110301', 'Mercadería', 'DEUDORA'),
    ('11030101', 'Inventario de mercadería', 'DEUDORA'),
    ('1104', 'Impuestos por recuperar', 'DEUDORA'),
    ('110401', 'IVA', 'DEUDORA'),
    ('11040101', 'IVA crédito fiscal', 'DEUDORA'),
    ('11040102', 'IVA retenido a favor', 'DEUDORA'),
    ('11040103', 'IVA percibido a favor', 'DEUDORA'),
    ('11040104', 'IVA anticipo a cuenta (tarjetas)', 'DEUDORA'),
    ('110402', 'Impuesto sobre la renta', 'DEUDORA'),
    ('11040201', 'Pago a cuenta del impuesto sobre la renta', 'DEUDORA'),
    ('11040202', 'Renta retenida a favor', 'DEUDORA'),
    ('1105', 'Pagos anticipados', 'DEUDORA'),
    ('110501', 'Seguros', 'DEUDORA'),
    ('11050101', 'Seguros pagados por anticipado', 'DEUDORA'),
    ('12', 'ACTIVO NO CORRIENTE', 'DEUDORA'),
    ('1201', 'Propiedad, planta y equipo', 'DEUDORA'),
    ('120101', 'Bienes muebles', 'DEUDORA'),
    ('12010101', 'Mobiliario y equipo', 'DEUDORA'),
    ('12010102', 'Equipo de cómputo', 'DEUDORA'),
    ('12010103', 'Vehículos', 'DEUDORA'),
    ('1202', 'Depreciación acumulada', 'ACREEDORA'),
    ('120201', 'Depreciación acumulada de bienes muebles', 'ACREEDORA'),
    ('12020101', 'Depreciación acumulada — mobiliario y equipo', 'ACREEDORA'),
    ('12020102', 'Depreciación acumulada — equipo de cómputo', 'ACREEDORA'),
    ('12020103', 'Depreciación acumulada — vehículos', 'ACREEDORA'),
    ('2', 'PASIVO', 'ACREEDORA'),
    ('21', 'PASIVO CORRIENTE', 'ACREEDORA'),
    ('2101', 'Cuentas por pagar', 'ACREEDORA'),
    ('210101', 'Cuentas por pagar comerciales', 'ACREEDORA'),
    ('21010101', 'Proveedores', 'ACREEDORA'),
    ('2102', 'Impuestos por pagar', 'ACREEDORA'),
    ('210201', 'IVA', 'ACREEDORA'),
    ('21020101', 'IVA débito fiscal', 'ACREEDORA'),
    ('21020102', 'IVA por pagar', 'ACREEDORA'),
    ('21020103', 'IVA retenido por pagar', 'ACREEDORA'),
    ('21020104', 'IVA percibido por pagar', 'ACREEDORA'),
    ('210202', 'Impuesto sobre la renta', 'ACREEDORA'),
    ('21020201', 'Retenciones de renta por pagar', 'ACREEDORA'),
    ('21020202', 'Pago a cuenta por pagar', 'ACREEDORA'),
    ('21020203', 'Impuesto sobre la renta por pagar', 'ACREEDORA'),
    ('2103', 'Obligaciones laborales', 'ACREEDORA'),
    ('210301', 'Remuneraciones y aportes', 'ACREEDORA'),
    ('21030101', 'Sueldos por pagar', 'ACREEDORA'),
    ('21030102', 'ISSS por pagar', 'ACREEDORA'),
    ('21030103', 'AFP por pagar', 'ACREEDORA'),
    ('2104', 'Préstamos a corto plazo', 'ACREEDORA'),
    ('210401', 'Préstamos bancarios', 'ACREEDORA'),
    ('21040101', 'Préstamos bancarios a corto plazo', 'ACREEDORA'),
    ('22', 'PASIVO NO CORRIENTE', 'ACREEDORA'),
    ('2201', 'Préstamos a largo plazo', 'ACREEDORA'),
    ('220101', 'Préstamos bancarios', 'ACREEDORA'),
    ('22010101', 'Préstamos bancarios a largo plazo', 'ACREEDORA'),
    ('3', 'CAPITAL CONTABLE', 'ACREEDORA'),
    ('31', 'CAPITAL', 'ACREEDORA'),
    ('3101', 'Capital social', 'ACREEDORA'),
    ('310101', 'Capital social', 'ACREEDORA'),
    ('31010101', 'Capital social suscrito y pagado', 'ACREEDORA'),
    ('3102', 'Reserva legal', 'ACREEDORA'),
    ('310201', 'Reserva legal', 'ACREEDORA'),
    ('31020101', 'Reserva legal', 'ACREEDORA'),
    ('3103', 'Resultados acumulados', 'ACREEDORA'),
    ('310301', 'Resultados de ejercicios anteriores', 'ACREEDORA'),
    ('31030101', 'Utilidades de ejercicios anteriores', 'ACREEDORA'),
    ('31030102', 'Pérdidas de ejercicios anteriores', 'DEUDORA'),
    ('4', 'COSTOS Y GASTOS', 'DEUDORA'),
    ('41', 'COSTOS', 'DEUDORA'),
    ('4101', 'Costo de ventas', 'DEUDORA'),
    ('410101', 'Costo de ventas', 'DEUDORA'),
    ('41010101', 'Costo de ventas de mercadería', 'DEUDORA'),
    ('4102', 'Compras', 'DEUDORA'),
    ('410201', 'Compras', 'DEUDORA'),
    ('41020101', 'Compras de mercadería', 'DEUDORA'),
    ('41020102', 'Devoluciones y rebajas sobre compras', 'ACREEDORA'),
    ('42', 'GASTOS DE OPERACIÓN', 'DEUDORA'),
    ('4201', 'Gastos de venta', 'DEUDORA'),
    ('420101', 'Gastos de venta', 'DEUDORA'),
    ('42010101', 'Sueldos y salarios — ventas', 'DEUDORA'),
    ('42010102', 'Publicidad y propaganda', 'DEUDORA'),
    ('42010103', 'Comisiones por cobros con tarjeta', 'DEUDORA'),
    ('4202', 'Gastos de administración', 'DEUDORA'),
    ('420201', 'Gastos de administración', 'DEUDORA'),
    ('42020101', 'Sueldos y salarios — administración', 'DEUDORA'),
    ('42020102', 'Alquileres', 'DEUDORA'),
    ('42020103', 'Energía eléctrica, agua y teléfono', 'DEUDORA'),
    ('42020104', 'Papelería y útiles', 'DEUDORA'),
    ('42020105', 'Depreciación', 'DEUDORA'),
    ('42020106', 'Honorarios profesionales', 'DEUDORA'),
    ('43', 'GASTOS FINANCIEROS', 'DEUDORA'),
    ('4301', 'Gastos financieros', 'DEUDORA'),
    ('430101', 'Gastos financieros', 'DEUDORA'),
    ('43010101', 'Intereses bancarios', 'DEUDORA'),
    ('43010102', 'Comisiones bancarias', 'DEUDORA'),
    ('5', 'INGRESOS', 'ACREEDORA'),
    ('51', 'INGRESOS DE OPERACIÓN', 'ACREEDORA'),
    ('5101', 'Ventas', 'ACREEDORA'),
    ('510101', 'Ventas', 'ACREEDORA'),
    ('51010101', 'Ventas gravadas', 'ACREEDORA'),
    ('51010102', 'Ventas exentas', 'ACREEDORA'),
    ('51010103', 'Ventas no sujetas', 'ACREEDORA'),
    ('51010104', 'Devoluciones y rebajas sobre ventas', 'DEUDORA'),
    ('52', 'OTROS INGRESOS', 'ACREEDORA'),
    ('5201', 'Otros ingresos', 'ACREEDORA'),
    ('520101', 'Otros ingresos', 'ACREEDORA'),
    ('52010101', 'Ingresos financieros', 'ACREEDORA'),
    ('52010102', 'Otros ingresos', 'ACREEDORA');

-- 2. PLANTILLA_REGLA_CONTABILIZACION: reglas por defecto de CIERRE_INGRESOS_DIARIO (CLAUDE.md §12.5, ADR-020, ADR-035).
CREATE TABLE plantilla_regla_contabilizacion (
    tipo_operacion  VARCHAR(40) NOT NULL,
    categoria       VARCHAR(10) NOT NULL,
    codigo          VARCHAR(40) NOT NULL,
    cuenta_codigo   VARCHAR(8) REFERENCES plantilla_cuenta(codigo),
    activa          BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (tipo_operacion, categoria, codigo),
    CONSTRAINT ck_plantilla_regla_categoria CHECK (categoria IN ('INGRESO', 'COBRO')),
    -- Una regla activa siempre tiene cuenta; solo una inactiva puede carecer de ella (ADR-035)
    CONSTRAINT ck_plantilla_regla_cuenta CHECK (NOT activa OR cuenta_codigo IS NOT NULL)
);

COMMENT ON TABLE plantilla_regla_contabilizacion IS 'Reglas de contabilización por defecto, con el código de la cuenta (no su id). Borrador pendiente de validación por contador (ADR-034).';
COMMENT ON COLUMN plantilla_regla_contabilizacion.categoria IS 'INGRESO (concepto de ingreso) o COBRO (forma de pago).';
COMMENT ON COLUMN plantilla_regla_contabilizacion.cuenta_codigo IS 'Cuenta de detalle por defecto; nula solo en la regla inactiva COBRO/OTRO, que el contador configura y activa (ADR-035).';
COMMENT ON COLUMN plantilla_regla_contabilizacion.activa IS 'false solo para COBRO/OTRO, que no tiene cuenta por defecto.';

GRANT SELECT ON plantilla_regla_contabilizacion TO pilot_app;

INSERT INTO plantilla_regla_contabilizacion (tipo_operacion, categoria, codigo, cuenta_codigo, activa) VALUES
    ('CIERRE_INGRESOS_DIARIO', 'INGRESO', 'VENTAS_GRAVADAS', '51010101', true),
    ('CIERRE_INGRESOS_DIARIO', 'INGRESO', 'VENTAS_EXENTAS', '51010102', true),
    ('CIERRE_INGRESOS_DIARIO', 'INGRESO', 'VENTAS_NO_SUJETAS', '51010103', true),
    ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'EFECTIVO', '11010101', true),
    ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'TARJETA', '11020102', true),
    ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'TRANSFERENCIA', '11010103', true),
    ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'CHEQUE', '11010103', true),
    ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'CREDITO', '11020101', true),
    ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'OTRO', NULL, false);

-- 3. PLANTILLA_CONFIGURACION_CONTABLE: una sola fila (CLAUDE.md §11.3, ADR-035).
CREATE TABLE plantilla_configuracion_contable (
    -- Llave fija: junto con su CHECK garantiza que la tabla nunca tenga más de una fila
    id                         BOOLEAN PRIMARY KEY DEFAULT true,
    modo_precio_defecto        VARCHAR(7) NOT NULL,
    cuenta_iva_debito_codigo   VARCHAR(8) NOT NULL REFERENCES plantilla_cuenta(codigo),
    cuenta_iva_credito_codigo  VARCHAR(8) NOT NULL REFERENCES plantilla_cuenta(codigo),
    CONSTRAINT ck_plantilla_configuracion_unica CHECK (id),
    CONSTRAINT ck_plantilla_configuracion_modo CHECK (modo_precio_defecto IN ('CON_IVA', 'SIN_IVA'))
);

COMMENT ON TABLE plantilla_configuracion_contable IS 'Configuración contable por defecto: una sola fila. Borrador pendiente de validación por contador (ADR-034).';
COMMENT ON COLUMN plantilla_configuracion_contable.id IS 'Siempre true: con su CHECK y su PRIMARY KEY impide una segunda fila.';
COMMENT ON COLUMN plantilla_configuracion_contable.modo_precio_defecto IS 'CON_IVA (el monto incluye IVA) o SIN_IVA (el IVA se suma), CLAUDE.md §11.1.';
COMMENT ON COLUMN plantilla_configuracion_contable.cuenta_iva_debito_codigo IS 'IVA débito fiscal (pasivo): 21020101.';
COMMENT ON COLUMN plantilla_configuracion_contable.cuenta_iva_credito_codigo IS 'IVA crédito fiscal (activo): 11040101.';

GRANT SELECT ON plantilla_configuracion_contable TO pilot_app;

INSERT INTO plantilla_configuracion_contable (modo_precio_defecto, cuenta_iva_debito_codigo, cuenta_iva_credito_codigo)
VALUES ('CON_IVA', '21020101', '11040101');
