# Catálogo de cuentas base — Pilot 1.0

> **Borrador para validación por contador** `[VERIFICAR]`. Se carga en la tabla global `plantilla_cuenta` y se copia a cada empresa al registrarla (CLAUDE.md 10.2). Cada empresa puede editarlo después.

**Convenciones**

- Clases: 1 Activo, 2 Pasivo, 3 Capital Contable, 4 Costos y Gastos, 5 Ingresos.
- Niveles por longitud del código: clase (1), grupo (2), cuenta (4), subcuenta (6), detalle (8).
- Solo las cuentas de detalle (8 dígitos, sin hijas) aceptan movimientos.
- Naturaleza: D = deudora, A = acreedora. Por defecto D en clases 1 y 4, y A en 2, 3 y 5; las excepciones se marcan en **negrita**.
- La columna "Uso" indica qué configuración la referencia por defecto.

| Código | Nombre | Nat. | Uso por defecto |
|---|---|---|---|
| 1 | ACTIVO | D | |
| 11 | ACTIVO CORRIENTE | D | |
| 1101 | Efectivo y equivalentes | D | |
| 110101 | Efectivo | D | |
| 11010101 | Caja general | D | Regla `EFECTIVO` |
| 11010102 | Caja chica | D | |
| 11010103 | Bancos | D | Reglas `TRANSFERENCIA`, `CHEQUE` |
| 1102 | Cuentas por cobrar | D | |
| 110201 | Cuentas por cobrar comerciales | D | |
| 11020101 | Clientes | D | Regla `CREDITO` |
| 11020102 | Cuentas por cobrar — emisores de tarjetas | D | Regla `TARJETA` |
| 1103 | Inventarios | D | |
| 110301 | Mercadería | D | |
| 11030101 | Inventario de mercadería | D | |
| 1104 | Impuestos por recuperar | D | |
| 110401 | IVA | D | |
| 11040101 | IVA crédito fiscal | D | `cuenta_iva_credito_id` |
| 11040102 | IVA retenido a favor | D | |
| 11040103 | IVA percibido a favor | D | |
| 11040104 | IVA anticipo a cuenta (tarjetas) | D | |
| 110402 | Impuesto sobre la renta | D | |
| 11040201 | Pago a cuenta del impuesto sobre la renta | D | |
| 11040202 | Renta retenida a favor | D | |
| 1105 | Pagos anticipados | D | |
| 110501 | Seguros | D | |
| 11050101 | Seguros pagados por anticipado | D | |
| 12 | ACTIVO NO CORRIENTE | D | |
| 1201 | Propiedad, planta y equipo | D | |
| 120101 | Bienes muebles | D | |
| 12010101 | Mobiliario y equipo | D | |
| 12010102 | Equipo de cómputo | D | |
| 12010103 | Vehículos | D | |
| 1202 | Depreciación acumulada | **A** | |
| 120201 | Depreciación acumulada de bienes muebles | **A** | |
| 12020101 | Depreciación acumulada — mobiliario y equipo | **A** | |
| 12020102 | Depreciación acumulada — equipo de cómputo | **A** | |
| 12020103 | Depreciación acumulada — vehículos | **A** | |
| 2 | PASIVO | A | |
| 21 | PASIVO CORRIENTE | A | |
| 2101 | Cuentas por pagar | A | |
| 210101 | Cuentas por pagar comerciales | A | |
| 21010101 | Proveedores | A | |
| 2102 | Impuestos por pagar | A | |
| 210201 | IVA | A | |
| 21020101 | IVA débito fiscal | A | `cuenta_iva_debito_id` |
| 21020102 | IVA por pagar | A | |
| 21020103 | IVA retenido por pagar | A | |
| 21020104 | IVA percibido por pagar | A | |
| 210202 | Impuesto sobre la renta | A | |
| 21020201 | Retenciones de renta por pagar | A | |
| 21020202 | Pago a cuenta por pagar | A | |
| 21020203 | Impuesto sobre la renta por pagar | A | |
| 2103 | Obligaciones laborales | A | |
| 210301 | Remuneraciones y aportes | A | |
| 21030101 | Sueldos por pagar | A | |
| 21030102 | ISSS por pagar | A | |
| 21030103 | AFP por pagar | A | |
| 2104 | Préstamos a corto plazo | A | |
| 210401 | Préstamos bancarios | A | |
| 21040101 | Préstamos bancarios a corto plazo | A | |
| 22 | PASIVO NO CORRIENTE | A | |
| 2201 | Préstamos a largo plazo | A | |
| 220101 | Préstamos bancarios | A | |
| 22010101 | Préstamos bancarios a largo plazo | A | |
| 3 | CAPITAL CONTABLE | A | |
| 31 | CAPITAL | A | |
| 3101 | Capital social | A | |
| 310101 | Capital social | A | |
| 31010101 | Capital social suscrito y pagado | A | |
| 3102 | Reserva legal | A | |
| 310201 | Reserva legal | A | |
| 31020101 | Reserva legal | A | |
| 3103 | Resultados acumulados | A | |
| 310301 | Resultados de ejercicios anteriores | A | |
| 31030101 | Utilidades de ejercicios anteriores | A | |
| 31030102 | Pérdidas de ejercicios anteriores | **D** | |
| 4 | COSTOS Y GASTOS | D | |
| 41 | COSTOS | D | |
| 4101 | Costo de ventas | D | |
| 410101 | Costo de ventas | D | |
| 41010101 | Costo de ventas de mercadería | D | |
| 4102 | Compras | D | |
| 410201 | Compras | D | |
| 41020101 | Compras de mercadería | D | |
| 41020102 | Devoluciones y rebajas sobre compras | **A** | |
| 42 | GASTOS DE OPERACIÓN | D | |
| 4201 | Gastos de venta | D | |
| 420101 | Gastos de venta | D | |
| 42010101 | Sueldos y salarios — ventas | D | |
| 42010102 | Publicidad y propaganda | D | |
| 42010103 | Comisiones por cobros con tarjeta | D | |
| 4202 | Gastos de administración | D | |
| 420201 | Gastos de administración | D | |
| 42020101 | Sueldos y salarios — administración | D | |
| 42020102 | Alquileres | D | |
| 42020103 | Energía eléctrica, agua y teléfono | D | |
| 42020104 | Papelería y útiles | D | |
| 42020105 | Depreciación | D | |
| 42020106 | Honorarios profesionales | D | |
| 43 | GASTOS FINANCIEROS | D | |
| 4301 | Gastos financieros | D | |
| 430101 | Gastos financieros | D | |
| 43010101 | Intereses bancarios | D | |
| 43010102 | Comisiones bancarias | D | |
| 5 | INGRESOS | A | |
| 51 | INGRESOS DE OPERACIÓN | A | |
| 5101 | Ventas | A | |
| 510101 | Ventas | A | |
| 51010101 | Ventas gravadas | A | Regla `VENTAS_GRAVADAS` |
| 51010102 | Ventas exentas | A | Regla `VENTAS_EXENTAS` |
| 51010103 | Ventas no sujetas | A | Regla `VENTAS_NO_SUJETAS` |
| 51010104 | Devoluciones y rebajas sobre ventas | **D** | |
| 52 | OTROS INGRESOS | A | |
| 5201 | Otros ingresos | A | |
| 520101 | Otros ingresos | A | |
| 52010101 | Ingresos financieros | A | |
| 52010102 | Otros ingresos | A | |

## Preguntas para el contador

1. ¿La empresa lleva inventario perpetuo (costo de ventas por venta) o periódico (cuenta de compras y ajuste al cierre)? El catálogo incluye ambas; se desactivarán las que no se usen.
2. ¿Las cuentas por cobrar a emisores de tarjetas deben separarse por banco?
3. ¿Qué cuentas adicionales considera imprescindibles para una PYME comercial en su primer mes?
4. ¿La numeración de 8 dígitos para cuentas de detalle es adecuada o prefiere otra estructura?
