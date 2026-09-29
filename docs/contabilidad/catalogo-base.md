# Catálogo de cuentas base — Pilot 1.0

> **Borrador para validación por contador** `[VERIFICAR]`. Desde el 2026-09-28 (ADR-044) el catálogo base es el del
> `entregables/MANUAL-DE-APLICACION-DE-CUENTAS-COMERCIAL .pdf` ("catálogo comercial", Universidad Católica de El
> Salvador), con sus propios códigos. Se carga en la tabla global `plantilla_cuenta` (migración
> `V19__catalogo_base_ucatolica.sql`) y se copia a cada empresa al instalar Contabilidad (CLAUDE.md 10.2). Cada
> empresa puede editarlo después. **Las empresas que ya instalaron Contabilidad antes de V19 conservan su catálogo
> anterior sin cambios** (ADR-044, decisión 8): este documento describe el catálogo de las empresas nuevas.

**Convenciones**

- Marco contable de referencia: NIIF para PYMES (ADR-037; edición y resolución del CVPCPA `[VERIFICAR]`). El catálogo
  es un plan de cuentas interno; NIIF no prescribe uno.
- Clases: 1 Activo, 2 Pasivo, 3 Patrimonio, 4 Costos y gastos de operación, 5 Ingresos. El grupo 44 (Impuesto sobre
  la renta) no está en el PDF; se conserva igual que en el catálogo anterior porque el Estado de Resultados lo
  presenta aparte (ADR-037, ADR-044).
- Niveles por longitud del código: clase (1), grupo (2), cuenta (4), subcuenta (6), detalle (8). Pilot no admite
  otras longitudes ni las clases 6 (cuenta de cierre) y 7 (cuentas de orden) del PDF: quedan fuera de alcance.
- Una cuenta acepta movimientos si no tiene hijas en la plantilla, sin importar su nivel: el PDF tiene hojas de
  nivel 4 (6 dígitos) sin hijas de 8 dígitos, como `110901` "Compras Locales" (cuenta de IVA crédito fiscal).
- Naturaleza: D = deudora, A = acreedora. Por defecto D en clases 1 y 4, y A en 2, 3 y 5; las 23 cuentas que el PDF
  marca "(CR)" —y sus descendientes, siempre marcados también— son **A** aunque su clase sea deudora (marcadas en
  **negrita**); la única excepción fuera de clase 1 es `410104`, cuenta propia de Pilot marcada "(CR)" (ADR-044).
- **Errata del PDF:** los grupos "GASTOS DE VIÁTICOS, VIAJES Y DE REPRESENTACIÓN" tenían código de 5 dígitos
  (`41021` y `41031`, una longitud que Pilot no admite); se cargaron como `410210` y `410310` (6 dígitos). Sus
  hijas (`41021001`…, `41031001`…) no cambiaron: ya traían el prefijo correcto.

## Totales por clase

| Clase | Cuentas | De detalle (sin hijas) |
|---|---|---|
| 1 — Activo | 121 | 86 |
| 2 — Pasivo | 80 | 57 |
| 3 — Patrimonio | 25 | 11 |
| 4 — Costos y gastos de operación | 196 | 158 |
| 5 — Ingresos | 33 | 21 |
| **Total** | **455** | **333** |

## Grupos (4 dígitos)

| Código | Nombre |
|---|---|
| 1101 | Efectivo y equivalentes de efectivo |
| 1102 | Cuentas y documentos por cobrar |
| 1103 | Estimación para cuentas incobrables |
| 1104 | Inversiones a corto plazo |
| 1105 | Inventarios |
| 1106 | Accionistas |
| 1107 | Gastos pagados por anticipado |
| 1108 | Pago a cuenta - ISR |
| 1109 | Crédito fiscal – IVA |
| 1201 | Propiedades, planta y equipo |
| 1202 | Propiedades, planta y equipo – en arrendamiento financiero |
| 1203 | Propiedades de inversión |
| 1204 | Intangibles |
| 1205 | Cuentas por cobrar a largo plazo |
| 1206 | Inversiones permanentes |
| 1207 | Depósitos en garantía |
| 1208 | Impuesto sobre la renta diferido - activo |
| 1209 | Otras cuentas deudoras |
| 2101 | Préstamos a corto plazo y sobregiros |
| 2102 | Cuentas comerciales por pagar |
| 2103 | Acreedores varios |
| 2104 | Retenciones por pagar |
| 2105 | Beneficios a empleados por pagar |
| 2106 | Impuesto sobre la renta por pagar |
| 2107 | Obligaciones por arrendamiento financiero |
| 2108 | IVA - débito fiscal |
| 2109 | Cuentas por pagar compañías relacionadas y accionistas |
| 2110 | Dividendos por pagar |
| 2111 | Provisiones (cuenta propia de Pilot, ADR-044) |
| 2201 | Préstamos por pagar a largo plazo |
| 2202 | Obligaciones por arrendamiento financiero |
| 2203 | Beneficios por pagar a empleados – largo plazo |
| 2204 | Impuesto sobre la renta diferido - pasivo |
| 3101 | Capital social |
| 3102 | Reserva legal |
| 3103 | Superávit por revaluaciones |
| 3201 | Utilidades de ejercicios anteriores |
| 3202 | Utilidad del presente ejercicio |
| 3203 | Déficit de ejercicios anteriores |
| 3204 | Déficit del presente ejercicio |
| 4101 | Costo de ventas |
| 4102 | Gastos administrativos |
| 4103 | Gastos de venta |
| 4201 | Gastos financieros |
| 4202 | Pérdida en venta o retiro de activos fijos |
| 4203 | Gastos por deterioro en el valor de activos |
| 4204 | Pérdidas por siniestros |
| 4205 | Gastos de ejercicios anteriores |
| 4206 | Otros gastos |
| 4401 | Impuesto sobre la renta (grupo 44, conservado de ADR-037) |
| 5101 | Ingresos operacionales |
| 5201 | Productos financieros |
| 5202 | Ganancia en venta de activos fijos |
| 5203 | Indemnizaciones por siniestros |
| 5204 | Otros productos |

El detalle completo de las 455 cuentas (6 y 8 dígitos) está en `plantilla_cuenta` y se consulta con
`SELECT codigo, nombre, naturaleza FROM plantilla_cuenta ORDER BY codigo` como dueño de la base.

## Configuración y reglas fijas (ADR-042, ADR-044)

| Parámetro | Cuenta | Nombre |
|---|---|---|
| IVA débito fiscal (`cuenta_iva_debito_id`) | `21080101` | IVA - débito fiscal - facturas de consumidor final `[VERIFICAR]`: el PDF separa la venta con Comprobante de Crédito Fiscal en `21080102`, no usada aquí |
| IVA crédito fiscal (`cuenta_iva_credito_id`) | `110901` | Compras Locales |

Las 64 reglas de contabilización (9 del cierre de n8n + 55 de las diez operaciones guiadas, ADR-041) tienen su
cuenta por defecto y su `prefijo_permitido` recalculados para este catálogo; la lista completa se consulta con
`SELECT tipo_operacion, categoria, codigo, cuenta_codigo, prefijo_permitido, activa FROM plantilla_regla_contabilizacion ORDER BY tipo_operacion, categoria, codigo`.

## Cuentas propias de Pilot (no están en el PDF)

Agregadas en un código libre de la misma estructura, con la naturaleza indicada (ADR-044):

| Código | Nombre | Nat. | Motivo |
|---|---|---|---|
| 110802 | Renta retenida a favor | D | Pago a cuenta de ISR, hermana de `11080101`/`11080102` |
| 12010806 | Depreciación acumulada de equipo de cómputo | **A** | El PDF solo detalla el equipo de cómputo a 10 dígitos (fuera de alcance) |
| 21080104 | IVA percibido por pagar | A | Hermana de `21080101`-`21080103` |
| 2111 / 211101 | Provisiones / Provisiones por litigios y contingencias | A | Grupo nuevo, el PDF no tiene provisiones por contingencias en pasivo corriente |
| 410104 | Devoluciones y rebajas sobre compras | **A** | Contra-costo; el PDF no la incluye |
| 44 / 4401 / 440101 / 44010101 / 44010102 | Impuesto sobre la renta (grupo completo) | D | Se conserva igual que en V15; el Estado de Resultados lo presenta aparte (ADR-037) |
| 51010104 | Ventas exentas | A | El PDF solo distingue contribuyente/consumidor final/exportación |
| 51010105 | Ventas no sujetas | A | Ídem |

## Tabla de equivalencias (código anterior de Pilot → código del PDF)

Para quien migre datos o reportes hechos a mano contra el catálogo anterior (V10/V15). No aplica a empresas que ya
instalaron Contabilidad: ellas conservan el catálogo anterior íntegro.

| Código anterior | Nombre anterior | Código nuevo | Nombre nuevo (PDF) |
|---|---|---|---|
| 11010101 | Caja general | 11010101 | Caja General |
| 11010102 | Caja chica | 11010102 | Caja Chica |
| 11010103 | Bancos | 11010201 | CUENTA CORRIENTE |
| 11020101 | Clientes | 11020101 | CUENTAS POR COBRAR CLIENTES |
| 11020102 | Cuentas por cobrar — emisores de tarjetas | 11020201 | VENTA CON TARJETA DE CRÉDITO |
| 11020201 | Estimación para cuentas incobrables | 110301 | Estimación para cuentas incobrables |
| 11030101 | Inventario de mercadería | 110501 | Bodega sucursal 01 |
| 11040101 | IVA crédito fiscal | 110901 | Compras Locales |
| 11040102 | IVA retenido a favor | 110905 | Retenciones 1% |
| 11040103 | IVA percibido a favor | 110903 | Percepciones 1% |
| 11040104 | IVA anticipo a cuenta (tarjetas) | 110904 | IVA pagado por anticipado 2% |
| 11040201 | Pago a cuenta del impuesto sobre la renta | 11080101 | Pago a Cuenta del periodo |
| 11040202 | Renta retenida a favor | 110802 | Renta retenida a favor (nueva de Pilot) |
| 11050101 | Seguros pagados por anticipado | 110701 | Seguros pagados por anticipado |
| 12010101 | Mobiliario y equipo | 12010301 | Mobiliario y equipo de Oficina |
| 12010102 | Equipo de cómputo | 12010302 | Equipo de cómputo |
| 12010103 | Vehículos | 120106 | Equipo de transporte |
| 12010201 | Terrenos | 120101 | Terrenos |
| 12010202 | Edificios | 120102 | Edificios |
| 12020101 | Depreciación acumulada — mobiliario y equipo | 12010802 | Depreciación acumulada de mobiliario y equipo |
| 12020102 | Depreciación acumulada — equipo de cómputo | 12010806 | Depreciación acumulada de equipo de cómputo (nueva de Pilot) |
| 12020103 | Depreciación acumulada — vehículos | 12010804 | Depreciación acumulada de equipo de transporte |
| 12020201 | Depreciación acumulada — edificios | 12010801 | Depreciación acumulada de Edificios |
| 12030101 | Programas y licencias informáticas | 120405 | Programas y sistemas |
| 12040101 | Amortización acumulada — programas y licencias | 12040605 | Amortización acumulada de programas y sistemas |
| 12050101 | Activo por impuesto sobre la renta diferido | 120802 | Activo por Impuesto S/ Renta Diferido |
| 21010101 | Proveedores | 21020101 | PROVEEDORES NACIONALES |
| 21020101 | IVA débito fiscal | 21080101 | IVA - débito fiscal - facturas de consumidor final |
| 21020102 | IVA por pagar | 210303 | IVA por pagar |
| 21020103 | IVA retenido por pagar | 21080103 | IVA - retenido por pagar |
| 21020104 | IVA percibido por pagar | 21080104 | IVA percibido por pagar (nueva de Pilot) |
| 21020201 | Retenciones de renta por pagar | 21040301 | Retencion con subordinación laboral |
| 21020202 | Pago a cuenta por pagar | 210304 | Pago a cuenta |
| 21020203 | Impuesto sobre la renta por pagar | 210601 | Impuesto sobre la Renta Anual |
| 21030101 | Sueldos por pagar | 210501 | Sueldos por pagar |
| 21030102 | ISSS por pagar | 210301 | Cuota patronal ISSS |
| 21030103 | AFP por pagar | 210302 | Cuota patronal AFP |
| 21030104 | Aguinaldo por pagar | 210505 | Aguinaldos por pagar |
| 21030105 | Vacaciones por pagar | 210504 | Vacaciones por pagar |
| 21040101 | Préstamos bancarios a corto plazo | 210102 | Préstamos bancarios (porción a corto plazo) |
| 21050101 | Provisiones por litigios y contingencias | 211101 | Provisiones por litigios y contingencias (nueva de Pilot, bajo 2111) |
| 22010101 | Préstamos bancarios a largo plazo | 220101 | Préstamos bancarios (Porción a largo plazo) |
| 22020101 | Provisión para indemnizaciones laborales | 220301 | Indemnizaciones por pagar |
| 22030101 | Pasivo por impuesto sobre la renta diferido | 220401 | Pasivo por Impuesto sobre la Renta diferido |
| 31010101 | Capital social suscrito y pagado | 31010101 | Capital Social Mínimo pagado |
| 31020101 | Reserva legal | 3102 | RESERVA LEGAL |
| 31030101 | Utilidades de ejercicios anteriores | 32010101 | Utilidad año anterior |
| 31030102 | Pérdidas de ejercicios anteriores | 32030101 | Ejercicio pasado |
| 41010101 | Costo de ventas de mercadería | 41010101 | Compra de mercadería adquirida para línea de venta 01 |
| 41020101 | Compras de mercadería | 41010101 | Compra de mercadería adquirida para línea de venta 01 |
| 41020102 | Devoluciones y rebajas sobre compras | 410104 | Devoluciones y rebajas sobre compras (nueva de Pilot, CR) |
| 42010101 | Sueldos y salarios — ventas | 41030101 | Salarios |
| 42010102 | Publicidad y propaganda | 41030306 | Publicidad y promoción |
| 42010103 | Comisiones por cobros con tarjeta | 420102 | Comisiones |
| 42020101 | Sueldos y salarios — administración | 41020101 | Salarios |
| 42020102 | Alquileres | 41021009 | Alquileres |
| 42020103 | Energía eléctrica, agua y teléfono | 41020302 | Servicio de energía eléctrica |
| 42020104 | Papelería y útiles | 41021010 | Papelería y útiles |
| 42020105 | Depreciación | 41020505 | Depreciación de Mobiliario y Equipo de Oficina |
| 42020106 | Honorarios profesionales | 41020405 | Otros honorarios |
| 42020107 | Deterioro de cuentas por cobrar | 420301 | Gastos por deterioro en el valor de activos |
| 42020108 | Amortización de intangibles | 41020601 | Amortización de activos intangibles |
| 42020109 | Aguinaldos y vacaciones | 41020103 | Aguinaldos |
| 42020110 | Indemnizaciones laborales | 41020106 | Indemnizaciones |
| 43010101 | Intereses bancarios | 420101 | Intereses sobre préstamos |
| 43010102 | Comisiones bancarias | 420102 | Comisiones |
| 51010101 | Ventas gravadas | 51010102 | Ventas a consumidor final `[VERIFICAR]` |
| 51010102 | Ventas exentas | 51010104 | Ventas exentas (nueva de Pilot) |
| 51010103 | Ventas no sujetas | 51010105 | Ventas no sujetas (nueva de Pilot) |
| 51010104 | Devoluciones y rebajas sobre ventas | 51010402 | Devoluciones sobre ventas |
| 52010101 | Ingresos financieros | 520101 | Intereses bancarios |
| 52010102 | Otros ingresos | 520403 | Otros productos |

## Preguntas para el contador

1. `51010101` "Ventas gravadas" pasó a `51010102` "Ventas a consumidor final": el PDF no distingue "gravadas" de
   "consumidor final" con la misma precisión fiscal que el catálogo anterior. ¿Es correcto usar esa cuenta como
   destino por defecto de las ventas gravadas, o hace falta otra?
2. `21080101` "IVA - débito fiscal - facturas de consumidor final" queda como la única cuenta de IVA débito fiscal
   configurada; el PDF tiene una cuenta separada (`21080102`) para ventas con Comprobante de Crédito Fiscal. ¿Se
   necesita distinguir ambas en Pilot 1.0 o basta con una?
3. ¿La empresa lleva inventario perpetuo (costo de ventas por venta) o periódico (cuenta de compras y ajuste al
   cierre)? El catálogo incluye ambos enfoques en distintos grupos (4101 vs. 1105); se desactivarán los que no se
   usen.
4. ¿Las cuentas por cobrar a emisores de tarjetas (`11020201`) deben separarse por procesador (Credomatic,
   Serfinsa…), como sugiere el PDF a 10 dígitos, o basta un solo nivel de detalle?
5. Cuentas propias de Pilot agregadas fuera del PDF (110802, 12010806, 21080104, 2111/211101, 410104, grupo 44,
   51010104, 51010105): ¿son correctas su ubicación, nombre y naturaleza?
6. ¿Qué edición de las NIIF para PYMES y qué resolución del CVPCPA aplican a los estados de 2026?
