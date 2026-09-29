-- =====================================================================================================
-- data.sql — Catálogo de cuentas y datos globales de Pilot 1.0
--   plantilla_cuenta                    Catálogo de cuentas base (Universidad Católica; ADR-044)
--   plantilla_regla_contabilizacion     Reglas de contabilización precargadas
--   plantilla_configuracion_contable    Modo de precio y cuentas de IVA por defecto
--   tasa_impuesto                       IVA 13 % con vigencia
--   aplicacion                          Catálogo de apps del ERP
--   plantilla_vida_util                 Vidas útiles para depreciación (pendientes de confirmar con el contador)
-- El catálogo de cada empresa (cuenta_contable) NO se guarda aquí: se copia de plantilla_cuenta al instalar la
-- app Contabilidad desde la pantalla Apps (evento AplicacionInstalada, ADR-030).
-- =====================================================================================================
--
-- PostgreSQL database dump
--

\restrict XCm5C8AQUf4SF4bo0xov0awmJehUEhiU56dzOwumzsJYulU9OVeecSLZeoDZtmy

-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: aplicacion; Type: TABLE DATA; Schema: public; Owner: pilot_owner
--

INSERT INTO public.aplicacion VALUES ('contabilidad', 'Contabilidad', 'Catálogo de cuentas, Libro Diario, mayorización, estados financieros e IVA.', 'COMUNITARIA', 10, true);
INSERT INTO public.aplicacion VALUES ('ventas', 'Ventas', 'Cotizaciones, pedidos y documentos de venta.', 'ENTERPRISE', 20, true);
INSERT INTO public.aplicacion VALUES ('clientes', 'Clientes', 'Directorio y seguimiento de clientes.', 'ENTERPRISE', 30, true);
INSERT INTO public.aplicacion VALUES ('proveedores', 'Proveedores', 'Directorio de proveedores y compras.', 'ENTERPRISE', 40, true);
INSERT INTO public.aplicacion VALUES ('inventario', 'Inventario', 'Bodegas, existencias y kardex.', 'ENTERPRISE', 50, true);
INSERT INTO public.aplicacion VALUES ('marketing', 'Marketing', 'Campañas y comunicación con clientes.', 'ENTERPRISE', 60, true);


--
-- Data for Name: plantilla_cuenta; Type: TABLE DATA; Schema: public; Owner: pilot_owner
--

INSERT INTO public.plantilla_cuenta VALUES ('1', 'ACTIVO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2', 'PASIVO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3', 'PATRIMONIO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4', 'CUENTAS DE RESULTADO DEUDORAS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('5', 'CUENTAS DE RESULTADO ACREEDORAS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11', 'ACTIVO CORRIENTE', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12', 'ACTIVO NO CORRIENTE', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21', 'PASIVO CORRIENTE', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('22', 'PASIVO NO CORRIENTE', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('31', 'CAPITAL Y RESERVAS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('32', 'RESULTADOS POR APLICAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41', 'COSTOS Y GASTOS DE OPERACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('42', 'OTROS COSTOS Y GASTOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('44', 'IMPUESTO SOBRE LA RENTA', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51', 'INGRESOS POR VENTAS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('52', 'OTROS PRODUCTOS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1101', 'EFECTIVO Y EQUIVALENTES DE EFECTIVO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1102', 'CUENTAS Y DOCUMENTOS POR COBRAR', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1103', 'ESTIMACIÓN PARA CUENTAS INCOBRABLES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1104', 'INVERSIONES A CORTO PLAZO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1105', 'INVENTARIOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1106', 'ACCIONISTAS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1107', 'GASTOS PAGADOS POR ANTICIPADO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1108', 'PAGO A CUENTA - ISR', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1109', 'CREDITO FISCAL – IVA', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1201', 'PROPIEDADES, PLANTA Y EQUIPO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1202', 'PROPIEDADES, PLANTA Y EQUIPO – EN ARRENDAMIENTO FINANCIERO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1203', 'PROPIEDADES DE INVERSIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1204', 'INTANGIBLES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1205', 'CUENTAS POR COBRAR A LARGO PLAZO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1206', 'INVERSIONES PERMANENTES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1207', 'DEPÓSITOS EN GARANTÍA', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1208', 'IMPUESTO SOBRE LA RENTA DIFERIDO - ACTIVO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('1209', 'OTRAS CUENTAS DEUDORAS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2101', 'PRÉSTAMOS A CORTO PLAZO Y SOBREGIROS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2102', 'CUENTAS COMERCIALES POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2103', 'ACREEDORES VARIOS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2104', 'RETENCIONES POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2105', 'BENEFICIOS A EMPLEADOS POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2106', 'IMPUESTO SOBRE LA RENTA POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2107', 'OBLIGACIONES POR ARRENDAMIENTO FINANCIERO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2108', 'IVA - DÉBITO FISCAL', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2109', 'CUENTAS POR PAGAR COMPAÑIAS RELACIONADAS Y ACCIONISTAS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2110', 'DIVIDENDOS POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2111', 'PROVISIONES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2201', 'PRESTAMOS POR PAGAR A LARGO PLAZO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2202', 'OBLIGACIONES POR ARRENDAMIENTO FINANCIERO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2203', 'BENEFICIOS POR PAGAR A EMPLEADOS – LARGO PLAZO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('2204', 'IMPUESTO SOBRE LA RENTA DIFERIDO - PASIVO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3101', 'CAPITAL SOCIAL', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3102', 'RESERVA LEGAL', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3103', 'SUPERAVIT POR REVALUACIONES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3201', 'UTILIDADES DE EJERCICIOS ANTERIORES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3202', 'UTILIDAD DEL PRESENTE EJERCICIO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3203', 'DÉFICIT DE EJERCICIOS ANTERIORES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('3204', 'DÉFICIT DEL PRESENTE EJERCICIO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4101', 'COSTO DE VENTAS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4102', 'GASTOS ADMINISTRATIVOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4103', 'GASTOS DE VENTA', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4201', 'GASTOS FINANCIEROS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4202', 'PÉRDIDA EN VENTA O RETIRO DE ACTIVOS FIJOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4203', 'GASTOS POR DETERIORO EN EL VALOR DE ACTIVOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4204', 'PÉRDIDAS POR SINIESTROS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4205', 'GASTOS DE EJERCICIOS ANTERIORES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4206', 'OTROS GASTOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('4401', 'Impuesto sobre la renta', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('5101', 'INGRESOS OPERACIONALES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('5201', 'PRODUCTOS FINANCIEROS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('5202', 'GANANCIA EN VENTA DE ACTIVOS FIJOS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('5203', 'INDEMNIZACIONES POR SINIESTROS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('5204', 'OTROS PRODUCTOS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110101', 'CAJA', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110102', 'BANCOS MONEDA NACIONAL', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110103', 'BANCOS MONEDA EXTRANJERA', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110201', 'CUENTAS POR COBRAR CRÉDITOS OTORGADOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110202', 'OTRAS CUENTAS POR COBRAR', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110203', 'PRÉSTAMOS A FUNCIONARIOS Y EMPLEADOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110204', 'DOCUMENTOS POR COBRAR', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110301', 'Estimación para cuentas incobrables', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110401', 'INVERSIONES EN BOLSA DE VALORES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110501', 'Bodega sucursal 01', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110502', 'Bodega sucursal 02', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110503', 'Bodega sucursal 03', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110504', 'Mercadería en Tránsito', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110505', 'Estimación por obsolescencia de inventarios', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110601', 'ACCIONISTA 01', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110602', 'ACCIONISTA 02', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110603', 'ACCIONISTA 03', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110701', 'Seguros pagados por anticipado', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110702', 'Alquileres pagados por anticipado', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110703', 'Papelería y útiles', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110704', 'Uniformes y equipo', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110705', 'Contratos por servicios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110706', 'Otros gastos pagados por anticipado', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110801', 'Pago a cuenta ISR', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110802', 'Renta retenida a favor', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110901', 'Compras Locales', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110902', 'Importaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110903', 'Percepciones 1%', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110904', 'IVA pagado por anticipado 2%', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('110905', 'Retenciones 1%', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120101', 'Terrenos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120102', 'Edificios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120103', 'MOBILIARIO Y EQUIPO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120104', 'Herramientas y equipos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120105', 'Instalaciones y mejoras', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120106', 'Equipo de transporte', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120107', 'CONSTRUCCIONES EN PROCESO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120108', 'DEPRECIACIÓN ACUMULADA – PROPIEDADES, PLANTA Y EQUIPO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120201', 'Terrenos en arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120202', 'Edificios en arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120203', 'Mobiliario y equipo en arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120204', 'Instalaciones y mejoras en arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120205', 'Equipo de transporte en arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120206', 'Otros equipos en arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120207', 'DEPRECIACIÓN ACUMULADA DE PROPIEDADES, PLANTA Y EQUIPO EN ARRENDAMIENTO FINANCIERO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120301', 'Terrenos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120302', 'Edificios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120303', 'Otros', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120401', 'Derecho de llave', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120402', 'Patentes y marcas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120403', 'Franquicias', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120404', 'Licencias y concesiones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120405', 'Programas y sistemas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120406', 'Amortización acumulada de activos intangibles', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120501', 'Clientes / Largo plazo', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120502', 'Otras cuentas por cobrar a largo plazo', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120601', 'Acciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120602', 'Participaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120701', 'Fianzas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120702', 'Cheque certificados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120703', 'Depósitos por bienes tomados en arrendamiento', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120704', 'Otras garantías', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120801', 'Crédito ISR años anteriores', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120802', 'Activo por Impuesto S/ Renta Diferido', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('120901', 'Otras cuentas deudoras a largo plazo', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210101', 'Sobregiros bancarios', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210102', 'Préstamos bancarios (porción a corto plazo)', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210103', 'Préstamos personales (porción a corto plazo)', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210201', 'PROVEEDORES POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210202', 'DOCUMENTOS POR PAGAR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210301', 'Cuota patronal ISSS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210302', 'Cuota patronal AFP', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210303', 'IVA por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210304', 'Pago a cuenta', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210305', 'Impuestos municipales', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210306', 'Otros impuestos por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210307', 'Intereses por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210308', 'Honorarios por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210309', 'Alquileres por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210310', 'Servicio telefónico por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210311', 'Anticipos de clientes', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210312', 'Provisiones de caja chica', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210313', 'Provisiones de arrendamiento', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210314', 'Otras cuentas por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210401', 'Cotización ISSS / Salud', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210402', 'COTIZACIÓN A FONDOS DE PENSIONES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210403', 'RETENCIÓN DE IMPUESTO SOBRE LA RENTA', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210501', 'Sueldos por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210502', 'Comisiones por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210503', 'Horas extras por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210504', 'Vacaciones por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210505', 'Aguinaldos por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210506', 'Gratificaciones y bonificaciones por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210507', 'Indemnizaciones por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210508', 'Bonificaciones por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210509', 'Otros beneficios a empleados por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210601', 'Impuesto sobre la Renta Anual', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210602', 'Pago a cuenta', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210701', 'Arrendamientos por leasing (Porcion a corto plazo)', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210801', 'IVA - DÉBITO FISCAL', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210901', 'ACCIONISTA 01', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('210902', 'ACCIONISTA 02', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('211001', 'ACCIONISTA 01', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('211002', 'ACCIONISTA 02', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('211101', 'Provisiones por litigios y contingencias', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('220101', 'Préstamos bancarios (Porción a largo plazo)', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('220102', 'Préstamos personales (Porción a largo plazo)', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('220201', 'Arrendamientos por leasing (Porcion a largo plazo)', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('220301', 'Indemnizaciones por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('220302', 'Otras prestaciones por pagar a largo plazo', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('220401', 'Pasivo por Impuesto sobre la Renta diferido', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('310101', 'CAPITAL SOCIAL MÍNIMO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('310102', 'CAPITAL SOCIAL VARIABLE', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('320101', 'UTILIDADES DE EJERCICIOS ANTERIORES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('320201', 'UTILIDAD DEL PRESENTE EJERCICIO', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('320301', 'DÉFICIT DE EJERCICIOS ANTERIORES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('320401', 'Déficit del presente ejercicio', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410101', 'COSTO DE VENTA MERCADERÍA ADQUIRIDA PARA LA VENTA SUCURSAL 01', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410102', 'COSTO DE VENTA MERCADERÍA ADQUIRIDA PARA LA VENTA SUCURSAL 02', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410103', 'COSTO DE VENTA MERCADERÍA ADQUIRIDA PARA LA VENTA SUCURSAL 03', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410104', 'Devoluciones y rebajas sobre compras', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410201', 'GASTOS DE PERSONAL', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410202', 'GASTOS DE MANTENIMIENTO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410203', 'GASTOS POR SERVICIOS PÚBLICOS Y PRIVADOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410204', 'HONORARIOS PROFESIONALES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410205', 'GASTOS POR DEPRECIACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410206', 'GASTOS POR AMORTIZACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410207', 'GASTOS POR SEGUROS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410208', 'GASTOS POR IMPUESTOS, TASAS MUNICIPALES Y OTRAS CONTRIBUCIONES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410209', 'GASTOS A CLIENTES Y EMPLEADOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410210', 'GASTOS DE VIÁTICOS, VIAJES Y DE REPRESENTACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410301', 'GASTOS DE PERSONAL', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410302', 'GASTOS DE MANTENIMIENTO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410303', 'GASTOS POR SERVICIOS PÚBLICOS Y PRIVADOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410304', 'HONORARIOS PROFESIONALES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410305', 'GASTOS POR DEPRECIACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410306', 'GASTOS POR AMORTIZACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410307', 'GASTOS POR SEGUROS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410308', 'GASTOS POR IMPUESTOS, TASAS MUNICIPALES Y OTRAS CONTRIBUCIONES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410309', 'ATENCIÓN A EMPLEADOS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('410310', 'GASTOS DE VIÁTICOS, VIAJES Y DE REPRESENTACIÓN', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420101', 'Intereses sobre préstamos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420102', 'Comisiones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420103', 'Seguros', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420104', 'Impuesto a las Operaciones Financieras', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420201', 'Pérdida en venta o retiro de activos fijos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420301', 'Gastos por deterioro en el valor de activos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420401', 'Pérdidas por siniestros', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420501', 'Gastos de ejercicios anteriores', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('420601', 'Otros Gastos no Clasificados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('440101', 'Impuesto sobre la renta', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('510101', 'VENTAS LOCALES SALA DE VENTAS 01', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('510102', 'VENTAS LOCALES SALA DE VENTAS 02', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('510103', 'VENTAS LOCALES SALA DE VENTAS 03', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('510104', 'REBAJAS Y DEVOLUCIONES SOBRE VENTAS', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520101', 'Intereses bancarios', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520102', 'Intereses sobre inversiones exentas de impuestos', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520103', 'Otros Intereses', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520201', 'Ganancia en venta de activos fijos', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520301', 'Indemnizaciones por siniestros', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520401', 'Diferencia en cambio de moneda extranjera', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520402', 'Comisiones', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('520403', 'Otros productos', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010101', 'Caja General', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010102', 'Caja Chica', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010201', 'CUENTA CORRIENTE', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010202', 'CUENTA DE AHORRO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010203', 'DEPÓSITOS A PLAZO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010301', 'CUENTA CORRIENTE', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010302', 'CUENTAS DE AHORRO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11010303', 'DEPÓSITOS A PLAZO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11020101', 'CUENTAS POR COBRAR CLIENTES', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11020201', 'VENTA CON TARJETA DE CRÉDITO', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11020301', 'Empleado 1', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11020401', 'Prestamos con garantía personal', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11040101', 'Bolproes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11080101', 'Pago a Cuenta del periodo', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('11080102', 'Pago a Cuenta periodo anterior', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010301', 'Mobiliario y equipo de Oficina', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010302', 'Equipo de cómputo', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010701', 'Edificios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010702', 'Instalaciones y mejoras locativas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010801', 'Depreciación acumulada de Edificios', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010802', 'Depreciación acumulada de mobiliario y equipo', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010803', 'Depreciación acumulada de instalaciones y mejoras', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010804', 'Depreciación acumulada de equipo de transporte', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010805', 'Depreciación acumulada de revaluaciones', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12010806', 'Depreciación acumulada de equipo de cómputo', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12020701', 'Depreciación acumulada de edificios en arrendamiento Financiero', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12020702', 'Depreciación acumulada de mobiliario y equipo en arrendamiento Financiero', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12020703', 'Depreciación acumulada de instalaciones y mejoras en arrendamiento Financiero', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12020704', 'Depreciación acumulada de equipo de transporte en arrendamiento Financiero', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12020705', 'Depreciación acumulada de otros equipos en arrendamiento Financiero', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12040601', 'Amortización acumulada de derecho de llave', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12040602', 'Amortización acumulada de patentes y marcas', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12040603', 'Amortización acumulada de franquicias', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12040604', 'Amortización acumulada de licencias y concesiones', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('12040605', 'Amortización acumulada de programas y sistemas', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21020101', 'PROVEEDORES NACIONALES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21020102', 'OTROS PROVEEDORES', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21020103', 'PROVEEDORES DEL EXTERIOR', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21020201', 'Documentos por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040201', 'ISSS provisional', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040202', 'AFP Crecer', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040203', 'AFP Confía', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040204', 'IPSFA', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040205', 'INPEP', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040301', 'Retencion con subordinación laboral', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040302', 'Retencion 10% eventuales', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21040303', 'Retencion Pensionados', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21080101', 'IVA - débito fiscal - facturas de consumidor final', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21080102', 'IVA - débito fiscal - comprobante crédito fiscal', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21080103', 'IVA - retenido por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('21080104', 'IVA percibido por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('31010101', 'Capital Social Mínimo pagado', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('31010102', 'Capital Social Mínimo por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('31010201', 'Capital social variable pagado', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('31010202', 'Capital social variable por pagar', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('32010101', 'Utilidad año anterior', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('32020101', 'Utilidad del presente ejercicio', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('32030101', 'Ejercicio pasado', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('32030102', 'Ejercicio anterior', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('32040101', 'Déficit del presente ejercicio', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010101', 'Compra de mercadería adquirida para linea de venta 01', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010102', 'Compra de mercadería adquirida para linea de venta 02', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010103', 'Compra de mercadería adquirida para linea de venta 03', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010201', 'Compra de mercadería adquirida para linea de venta 01', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010202', 'Compra de mercadería adquirida para linea de venta 02', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010203', 'Compra de mercadería adquirida para linea de venta 03', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010301', 'Compra de mercadería adquirida para linea de venta 01', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010302', 'Compra de mercadería adquirida para linea de venta 02', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41010303', 'Compra de mercadería adquirida para linea de venta 03', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020101', 'Salarios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020102', 'Vacaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020103', 'Aguinaldos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020104', 'Bonificaciones y gratificaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020105', 'Horas extras', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020106', 'Indemnizaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020107', 'Viáticos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020108', 'Cuota patronal seguridad social ISSS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020109', 'Cuota patronal fondo de pensiones AFP', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020110', 'INSAFORP', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020111', 'Comisiones, premios e incentivos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020112', 'Gastos por equipos de proteccion contra COVID', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020113', 'Otros gastos del personal', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020201', 'Mantenimiento de edificaciones e instalaciones propias', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020202', 'Mantenimiento de Mobiliario Equipo De Oficina', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020203', 'Mantenimiento y Reparación De Vehículos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020204', 'Otros gastos por mantenimiento', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020301', 'Servicio de agua', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020302', 'Servicio de energía eléctrica', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020303', 'Servicio de Teléfono', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020304', 'Servicio de internet/cable', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020305', 'Servicio de vigilancia', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020306', 'Publicidad y promoción', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020307', 'Impresiones y Encuadernaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020308', 'Suscripciones periódicos y Revistas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020309', 'Servicios de limpieza y Ornamentación', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020310', 'Otros servicios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020401', 'Honorarios legales', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020402', 'Honorarios contables', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020403', 'Honorarios de auditoria', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020404', 'Honorarios por servicios administrativos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020405', 'Otros honorarios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020501', 'Depreciación de edificaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020502', 'Depreciación a instalaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020503', 'Depreciación de mejoras a propiedades arrendadas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020504', 'Depreciación de Maquinaria y Equipo Industrial', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020505', 'Depreciación de Mobiliario y Equipo de Oficina', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020506', 'Depreciación de Herramientas y Equipo Pequeño', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020507', 'Depreciación de Equipo de transporte', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020508', 'Depreciación de edificaciones bajo arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020509', 'Depreciación de Maquinaria y Equipo Industrial bajo a arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020510', 'Depreciación de Mobiliario y Equipo de Oficina bajo a arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020511', 'Depreciación de Equipo de transporte bajo a arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020601', 'Amortización de activos intangibles', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020602', 'Amortización Licencias de software', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020701', 'Seguro de vida y medico', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020702', 'Seguro de activos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020801', 'Impuestos y tasas municipales', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020802', 'Derechos y aranceles de registros de comercio', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020803', 'Impuestos y derechos de aduanas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020804', 'Otras contribuciones públicas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020901', 'Atención a visitas y funcionarios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020902', 'Atención a empleados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020903', 'Atención a Clientes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020904', 'Cursos de capacitación a empleados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020905', 'Otras atenciones a clientes y empleados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41020906', 'Promoción y difusion de medidas sanitaria por COVID', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021001', 'Gastos de transportes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021002', 'Gastos de Viajes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021003', 'Gastos de Alimentación', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021004', 'Gastos de representación', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021005', 'Gasto de Hospedaje', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021006', 'Gasto de boletos aéreos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021007', 'Combustibles y lubricantes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021008', 'FOVIAL', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021009', 'Alquileres', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021010', 'Papelería y útiles', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021011', 'Donaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41021012', 'Otros gastos varios administrativos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030101', 'Salarios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030102', 'Vacaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030103', 'Aguinaldos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030104', 'Bonificaciones y gratificaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030105', 'Horas extras', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030106', 'Indemnizaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030107', 'Viáticos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030108', 'Cuota patronal seguridad social ISSS', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030109', 'Cuota patronal fondo de pensiones AFP', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030110', 'INSAFORP', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030111', 'Comisiones, premios e incentivos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030112', 'Gastos por equipos de proteccion contra COVID', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030113', 'Otros gastos del personal', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030201', 'Mantenimiento de edificaciones e instalaciones propias', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030202', 'Mantenimiento de Mobiliario Equipo De Oficina', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030203', 'Mantenimiento y Reparación De Vehículos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030204', 'Otros gastos por mantenimiento', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030301', 'Servicio de agua', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030302', 'Servicio de energía eléctrica', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030303', 'Servicio de Teléfono', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030304', 'Servicio de internet/cable', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030305', 'Servicio de vigilancia', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030306', 'Publicidad y promoción', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030307', 'Impresiones y Encuadernaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030308', 'Suscripciones periódicos y Revistas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030309', 'Servicios de limpieza y Ornamentación', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030401', 'Honorarios legales', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030402', 'Honorarios contables', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030403', 'Honorarios de auditoria', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030404', 'Honorarios por servicios administrativos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030501', 'Depreciación de edificaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030502', 'Depreciación a instalaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030503', 'Depreciación de mejoras a propiedades arrendadas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030504', 'Depreciación de Maquinaria y Equipo Industrial', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030505', 'Depreciación de Mobiliario y Equipo de Oficina', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030506', 'Depreciación de Herramientas y Equipo Pequeño', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030507', 'Depreciación de Equipo de transporte', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030508', 'Depreciación de edificaciones bajo a arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030509', 'Depreciación de Maquinaria y Equipo Industrial bajo arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030510', 'Depreciación de Mobiliario y Equipo de Oficina bajo arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030511', 'Depreciación de Equipo de transporte bajo arrendamiento financiero', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030601', 'Amortización de activos intangibles', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030602', 'Amortización Licencias de software', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030701', 'Seguro de vida y medico', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030702', 'Seguro de activos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030801', 'Impuestos y tasas municipales', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030802', 'Derechos y aranceles de registros de comercio', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030803', 'Impuestos y derechos de aduanas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030804', 'Otras contribuciones públicas', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030805', 'Gastos a clientes y empleados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030806', 'Atención a visitas y funcionarios', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030901', 'Atención a empleados repatriados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030902', 'Atención a Clientes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030903', 'Cursos de capacitación a empleados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030904', 'Otras atenciones a clientes y empleados', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41030905', 'Promoción y difusión de medidas sanitaria por COVID', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031001', 'Gastos de transportes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031002', 'Gastos de Viajes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031003', 'Gastos de Alimentación', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031004', 'Gastos de representación', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031005', 'Gasto de Hospedaje', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031006', 'Gasto de boletos aéreos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031007', 'Combustibles y lubricantes', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031008', 'FOVIAL', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031009', 'Alquileres', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031010', 'Papelería y útiles', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031011', 'Donaciones', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('41031012', 'Otros gastos varios administrativos', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('44010101', 'Gasto por impuesto sobre la renta corriente', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('44010102', 'Gasto (ingreso) por impuesto sobre la renta diferido', 'DEUDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010101', 'Ventas a contribuyentes', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010102', 'Ventas a consumidor final', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010103', 'Ventas de exportación', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010104', 'Ventas exentas', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010105', 'Ventas no sujetas', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010201', 'Ventas a contribuyentes', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010202', 'Ventas a consumidor final', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010203', 'Ventas de exportación', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010301', 'Ventas a contribuyentes', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010302', 'Ventas a consumidor final', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010303', 'Ventas de exportación', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010401', 'Rebajas sobre ventas', 'ACREEDORA', DEFAULT, DEFAULT);
INSERT INTO public.plantilla_cuenta VALUES ('51010402', 'Devoluciones sobre ventas', 'ACREEDORA', DEFAULT, DEFAULT);


--
-- Data for Name: plantilla_configuracion_contable; Type: TABLE DATA; Schema: public; Owner: pilot_owner
--

INSERT INTO public.plantilla_configuracion_contable VALUES (true, 'CON_IVA', '21080101', '110901');


--
-- Data for Name: plantilla_regla_contabilizacion; Type: TABLE DATA; Schema: public; Owner: pilot_owner
--

INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'INGRESO', 'VENTAS_GRAVADAS', '51010102', true, '5101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'INGRESO', 'VENTAS_EXENTAS', '51010104', true, '5101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'INGRESO', 'VENTAS_NO_SUJETAS', '51010105', true, '5101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'TARJETA', '11020201', true, '1102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'TRANSFERENCIA', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'CHEQUE', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'CREDITO', '11020101', true, '1102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('CIERRE_INGRESOS_DIARIO', 'COBRO', 'OTRO', NULL, false, '11');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'INGRESO', 'GRAVADO', '51010102', true, '5101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'INGRESO', 'EXENTO', '51010104', true, '5101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'INGRESO', 'NO_SUJETO', '51010105', true, '5101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'COBRO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'COBRO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'COBRO', 'TARJETA', '11020201', true, '1102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('VENTA', 'COBRO', 'CREDITO', '11020101', true, '1102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'MERCADERIA', '41010101', true, '4101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'ALQUILER', '41021009', true, '4102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'SERVICIOS_BASICOS', '41020302', true, '4102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'PAPELERIA', '41021010', true, '4102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'SUELDOS_ADMINISTRACION', '41020101', true, '4102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'SUELDOS_VENTAS', '41030101', true, '4103');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'HONORARIOS', '41020405', true, '4102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'PUBLICIDAD', '41030306', true, '4103');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'COMISIONES_BANCARIAS', '420102', true, '4201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'GASTO', 'OTRO_GASTO', NULL, false, '4');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'PAGO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'PAGO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_GASTO', 'PAGO', 'CREDITO', '21020101', true, '2102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COBRO_CLIENTE', 'CONTRAPARTIDA', 'CLIENTES', '11020101', true, '1102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COBRO_CLIENTE', 'CONTRAPARTIDA', 'TARJETAS', '11020201', true, '1102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COBRO_CLIENTE', 'CONTRAPARTIDA', 'COMISION_TARJETA', '420102', true, '4201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COBRO_CLIENTE', 'CONTRAPARTIDA', 'ANTICIPO_IVA', '110904', true, '1109');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COBRO_CLIENTE', 'COBRO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COBRO_CLIENTE', 'COBRO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_PROVEEDOR', 'CONTRAPARTIDA', 'PROVEEDORES', '21020101', true, '2102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_PROVEEDOR', 'PAGO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_PROVEEDOR', 'PAGO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('APORTE_CAPITAL', 'CONTRAPARTIDA', 'CAPITAL', '31010101', true, '3101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('APORTE_CAPITAL', 'COBRO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('APORTE_CAPITAL', 'COBRO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PRESTAMO_RECIBIDO', 'CONTRAPARTIDA', 'CORTO', '210102', true, '2101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PRESTAMO_RECIBIDO', 'CONTRAPARTIDA', 'LARGO', '220101', true, '2201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PRESTAMO_RECIBIDO', 'COBRO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PRESTAMO_RECIBIDO', 'COBRO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_CUOTA', 'CONTRAPARTIDA', 'CORTO', '210102', true, '2101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_CUOTA', 'CONTRAPARTIDA', 'LARGO', '220101', true, '2201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_CUOTA', 'CONTRAPARTIDA', 'INTERESES', '420101', true, '4201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_CUOTA', 'CONTRAPARTIDA', 'COMISION', '420102', true, '4201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_CUOTA', 'PAGO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('PAGO_CUOTA', 'PAGO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'ACTIVO', 'MOBILIARIO_EQUIPO', '12010301', true, '1201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'ACTIVO', 'EQUIPO_COMPUTO', '12010302', true, '1201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'ACTIVO', 'VEHICULO', '120106', true, '1201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'ACTIVO', 'EDIFICIO', '120102', true, '1201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'ACTIVO', 'TERRENO', '120101', true, '1201');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'PAGO', 'EFECTIVO', '11010101', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'PAGO', 'BANCO', '11010201', true, '1101');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('COMPRA_ACTIVO_FIJO', 'PAGO', 'CREDITO', '21020101', true, '2102');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('DEPRECIACION_MENSUAL', 'DEPRECIACION', 'MOBILIARIO_EQUIPO', '12010802', true, '120108');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('DEPRECIACION_MENSUAL', 'DEPRECIACION', 'EQUIPO_COMPUTO', '12010806', true, '120108');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('DEPRECIACION_MENSUAL', 'DEPRECIACION', 'VEHICULO', '12010804', true, '120108');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('DEPRECIACION_MENSUAL', 'DEPRECIACION', 'EDIFICIO', '12010801', true, '120108');
INSERT INTO public.plantilla_regla_contabilizacion VALUES ('DEPRECIACION_MENSUAL', 'GASTO', 'DEPRECIACION', '41020505', true, '4102');


--
-- Data for Name: plantilla_vida_util; Type: TABLE DATA; Schema: public; Owner: pilot_owner
--

INSERT INTO public.plantilla_vida_util VALUES ('MOBILIARIO_EQUIPO', 60, 0.0000, true, false);
INSERT INTO public.plantilla_vida_util VALUES ('EQUIPO_COMPUTO', 24, 0.0000, true, false);
INSERT INTO public.plantilla_vida_util VALUES ('VEHICULO', 48, 0.0000, true, false);
INSERT INTO public.plantilla_vida_util VALUES ('EDIFICIO', 240, 0.0000, true, false);


--
-- Data for Name: tasa_impuesto; Type: TABLE DATA; Schema: public; Owner: pilot_owner
--

INSERT INTO public.tasa_impuesto VALUES ('0192f1a4-7c3e-7b21-9d4e-5a6b7c8d9e01', 'IVA', 0.1300, '2000-01-01', NULL);


--
-- PostgreSQL database dump complete
--

\unrestrict XCm5C8AQUf4SF4bo0xov0awmJehUEhiU56dzOwumzsJYulU9OVeecSLZeoDZtmy

