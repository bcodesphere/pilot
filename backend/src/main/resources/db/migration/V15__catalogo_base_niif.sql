-- V15 — Catálogo base ampliado para NIIF para PYMES (ADR-037, decisión 5).
-- ADR-037 encontró que el catálogo base (V10) carecía de cuentas para deterioro de cuentas por cobrar,
-- inmuebles e intangibles (y su depreciación/amortización), impuesto sobre la renta diferido, beneficios a
-- empleados (aguinaldo, vacaciones, indemnización) y provisiones, y que la clase 3 se llamaba "CAPITAL CONTABLE"
-- en vez de "PATRIMONIO" (terminología NIIF para PYMES). Esta migración agrega esas cuentas a `plantilla_cuenta`
-- y renombra la clase 3, tal como quedaron marcadas "ADR-037" en docs/contabilidad/catalogo-base.md.
--
-- Sigue siendo un BORRADOR `[VERIFICAR]` con el contador (ADR-034): solo afecta a las empresas que instalen
-- Contabilidad después de esta migración (evento AplicacionInstalada, ADR-030); las ya instaladas conservan su
-- catálogo (ADR-037, decisión 5) y no se tocan aquí (nada de UPDATE/INSERT sobre cuenta_contable).

-- 1. ACTIVO CORRIENTE (11): estimación por deterioro de cuentas por cobrar, hermana de "Cuentas por cobrar
--    comerciales" (1102). Naturaleza ACREEDORA: es una cuenta complementaria que resta al activo (excepción a la
--    naturaleza deudora por defecto de la clase 1, como la depreciación acumulada).
INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('110202', 'Estimación por deterioro de cuentas por cobrar', 'ACREEDORA'),
    ('11020201', 'Estimación para cuentas incobrables', 'ACREEDORA');

-- 2. ACTIVO NO CORRIENTE (12): bienes inmuebles y su depreciación acumulada, activos intangibles y su
--    amortización acumulada, y el activo por impuesto sobre la renta diferido. Las cuentas de depreciación y
--    amortización acumulada son ACREEDORA (excepción a la clase 1), igual que las ya existentes de bienes
--    muebles (1202) en V10.
INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('120102', 'Bienes inmuebles', 'DEUDORA'),
    ('12010201', 'Terrenos', 'DEUDORA'),
    ('12010202', 'Edificios', 'DEUDORA'),
    ('120202', 'Depreciación acumulada de bienes inmuebles', 'ACREEDORA'),
    ('12020201', 'Depreciación acumulada — edificios', 'ACREEDORA'),
    ('1203', 'Activos intangibles', 'DEUDORA'),
    ('120301', 'Programas y licencias', 'DEUDORA'),
    ('12030101', 'Programas y licencias informáticas', 'DEUDORA'),
    ('1204', 'Amortización acumulada', 'ACREEDORA'),
    ('120401', 'Amortización acumulada de intangibles', 'ACREEDORA'),
    ('12040101', 'Amortización acumulada — programas y licencias', 'ACREEDORA'),
    ('1205', 'Activo por impuesto diferido', 'DEUDORA'),
    ('120501', 'Impuesto sobre la renta diferido', 'DEUDORA'),
    ('12050101', 'Activo por impuesto sobre la renta diferido', 'DEUDORA');

-- 3. PASIVO CORRIENTE (21): beneficios a empleados de corto plazo (aguinaldo, vacaciones), hermanas de las
--    obligaciones laborales ya existentes (2103), y provisiones por litigios y contingencias (2105, grupo nuevo).
INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('21030104', 'Aguinaldo por pagar', 'ACREEDORA'),
    ('21030105', 'Vacaciones por pagar', 'ACREEDORA'),
    ('2105', 'Provisiones', 'ACREEDORA'),
    ('210501', 'Provisiones', 'ACREEDORA'),
    ('21050101', 'Provisiones por litigios y contingencias', 'ACREEDORA');

-- 4. PASIVO NO CORRIENTE (22): beneficios a empleados a largo plazo (indemnizaciones) y el pasivo por impuesto
--    sobre la renta diferido, ambos grupos nuevos junto a los préstamos a largo plazo ya existentes (2201).
INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('2202', 'Beneficios a empleados a largo plazo', 'ACREEDORA'),
    ('220201', 'Indemnizaciones', 'ACREEDORA'),
    ('22020101', 'Provisión para indemnizaciones laborales', 'ACREEDORA'),
    ('2203', 'Pasivo por impuesto diferido', 'ACREEDORA'),
    ('220301', 'Impuesto sobre la renta diferido', 'ACREEDORA'),
    ('22030101', 'Pasivo por impuesto sobre la renta diferido', 'ACREEDORA');

-- 5. Renombre de la clase 3, de "CAPITAL CONTABLE" a "PATRIMONIO" (ADR-037, decisión 2: terminología NIIF para
--    PYMES). No cambia su código ni su naturaleza; los grupos y cuentas debajo (31 CAPITAL, etc.) no se tocan.
UPDATE plantilla_cuenta SET nombre = 'PATRIMONIO' WHERE codigo = '3';

-- 6. GASTOS DE ADMINISTRACIÓN (4202): deterioro de cuentas por cobrar, amortización de intangibles y las
--    contrapartidas de gasto de los beneficios a empleados agregados en los bloques 3 y 4.
INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('42020107', 'Deterioro de cuentas por cobrar', 'DEUDORA'),
    ('42020108', 'Amortización de intangibles', 'DEUDORA'),
    ('42020109', 'Aguinaldos y vacaciones', 'DEUDORA'),
    ('42020110', 'Indemnizaciones laborales', 'DEUDORA');

-- 7. Grupo 44, IMPUESTO SOBRE LA RENTA: se presenta aparte en el Estado de Resultados, después de la utilidad
--    antes de impuesto (CLAUDE.md §10.4, ADR-037 decisión 4). Incluye el gasto corriente y el diferido.
INSERT INTO plantilla_cuenta (codigo, nombre, naturaleza) VALUES
    ('44', 'IMPUESTO SOBRE LA RENTA', 'DEUDORA'),
    ('4401', 'Impuesto sobre la renta', 'DEUDORA'),
    ('440101', 'Impuesto sobre la renta', 'DEUDORA'),
    ('44010101', 'Gasto por impuesto sobre la renta corriente', 'DEUDORA'),
    ('44010102', 'Gasto (ingreso) por impuesto sobre la renta diferido', 'DEUDORA');

-- 8. El comentario de la columna "clase" de V10 todavía decía "3 Capital"; se corrige a "Patrimonio" para que
--    siga describiendo el dato real (bloque 5).
COMMENT ON COLUMN plantilla_cuenta.clase IS
    'Derivado del primer dígito: 1 Activo, 2 Pasivo, 3 Patrimonio, 4 Costos y Gastos (grupo 44 aparte), 5 Ingresos.';
