# Guion de la demo de Pilot 1.0 (10–12 minutos)

Recorre la rúbrica en orden: 1) Libro Diario con partida doble, 2) mayorización en tiempo real, 3) estados financieros automáticos, 4) datos complementarios. Todo se hace en la app **Contabilidad** de Pilot, en <http://localhost:5173>.

> **Antes de empezar (2 minutos, fuera de la presentación).** Tener levantados compose, backend y frontend (ver `README.md`, sección 3), la sesión iniciada con un usuario ya registrado con MFA, la app Contabilidad instalada y la empresa **sin asientos** (para que las cifras coincidan con este guion). Fecha de trabajo: usar el día de la demo (no se permiten fechas futuras, `CON-007`).
>
> **Nota sobre códigos de cuenta.** Los nombres de cuenta de abajo son los del catálogo vigente. Si tras la migración del catálogo de la Universidad Católica (ADR-044) algún código cambió, en el buscador de cuentas escriba el nombre; lo que importa es la clase (1 activo, 2 pasivo, 3 patrimonio, 4 costos y gastos, 5 ingresos). Los códigos entre paréntesis son del catálogo base anterior y sirven de referencia.

## Cifras de la demo (para tener a mano)

| # | Hecho | Asiento (Debe / Haber) |
|---|---|---|
| 1 | Aporte de capital | Bancos 10,000.00 / Capital social 10,000.00 |
| 2 | Venta al contado con IVA (modo `CON_IVA`) | Caja general 1,130.00 / Ventas gravadas 1,130.00 marcada "lleva IVA" → se guarda Ventas 1,000.00 + IVA débito fiscal 130.00 |
| 3 | Compra de mercadería con IVA | Compras 565.00 marcada "lleva IVA" / Bancos 565.00 → se guarda Compras 500.00 + IVA crédito fiscal 65.00 |
| 4 | Gasto de alquiler sin IVA | Alquileres 200.00 / Caja general 200.00 |
| 5 | Asiento descuadrado (se bloquea) | Caja 100.00 / Ventas 90.00 |
| 6 | Reversión del gasto de alquiler | Se revierte el asiento 4 |

Saldos esperados **antes** de la reversión: Bancos 9,435.00 · Caja 930.00 · IVA crédito 65.00 · IVA débito 130.00 · Capital 10,000.00 · Ventas 1,000.00 · Compras 500.00 · Alquileres 200.00.

---

## Parte 1 — Libro Diario y partida doble (rúbrica 1) · ~4 min

**Dónde:** menú **Contabilidad → Libro Diario → Nuevo asiento**.

1. **Aporte de capital.** Fecha de hoy; concepto "Aporte inicial de capital"; línea 1: cuenta Bancos (11010103), Debe 10,000.00; línea 2: Capital social suscrito y pagado (31010101), Haber 10,000.00. Mostrar los **totales en vivo** y que el botón **Guardar** se habilita solo cuando Debe = Haber. Guardar; se ve el asiento N.º 1.
   - *Qué decir:* la partida doble se valida en tres lugares: formulario (frontend), servicio (backend) y un trigger de PostgreSQL al confirmar la transacción.
2. **Venta con IVA (`CON_IVA`).** Concepto "Venta al contado"; modo de precio `CON_IVA`; Caja general (11010101) Debe 1,130.00; Ventas gravadas (51010101) Haber 1,130.00 con la casilla **"lleva IVA"**. Mostrar la **vista previa**: Pilot separa Ventas 1,000.00 + IVA débito fiscal 130.00 (`IVA_CALCULADO`). Guardar.
   - *Qué decir:* el cálculo del IVA vive solo en el backend, con la tasa vigente de la tabla `tasa_impuesto` (no hay 13 % fijo en el código).
3. **Compra.** Concepto "Compra de mercadería"; Compras de mercadería (41020101) Debe 565.00 con "lleva IVA"; Bancos Haber 565.00. La vista previa muestra Compras 500.00 + IVA crédito fiscal 65.00. Guardar.
4. **Gasto.** Concepto "Pago de alquiler"; Alquileres (42020102) Debe 200.00; Caja general Haber 200.00, **sin** IVA. Guardar.
5. **Asiento descuadrado (se bloquea).** Nuevo asiento: Caja Debe 100.00; Ventas gravadas Haber 90.00. Mostrar el mensaje "Diferencia: 10.00" y el botón **Guardar deshabilitado**. Corregir el Haber a 100.00 y **descartar** el asiento sin guardarlo (así las cifras del guion no cambian).
   - *Qué decir:* si alguien llamara a la API directamente, el backend responde 422 `CON-005` con la diferencia; y si ambos fallaran, el trigger de la base aborta la transacción.
6. En la lista del **Libro Diario** mostrar los 4 asientos, con su número, fecha, concepto y origen `MANUAL`, y abrir uno para ver sus líneas.

## Parte 2 — Mayorización en tiempo real (rúbrica 2) · ~2 min

**Dónde:** **Contabilidad → Mayor**.

1. Elegir **Caja general**: movimientos con saldo acumulado línea a línea; saldo final **930.00 Deudor** (1,130.00 − 200.00).
2. Elegir **Bancos**: saldo **9,435.00 Deudor**. Elegir **Capital social**: **10,000.00 Acreedor**.
3. Volver a **Nuevo asiento** solo para mostrar que, al guardar cualquiera, el Mayor ya lo refleja sin ningún proceso de "cierre" ni "mayorización" manual.
   - *Qué decir:* la mayorización ocurre en la misma transacción que guarda el asiento (ADR-018): o se guardan asiento y saldos, o no se guarda nada. Un saldo contrario a la naturaleza de la cuenta se muestra con alerta.
4. Abrir **Reportes → Diagnóstico** y mostrar que la verificación de mayorización da diferencia **0** (saldos mensuales = suma de líneas).

## Parte 3 — Estados financieros automáticos (rúbrica 3) · ~3 min

**Dónde:** **Contabilidad → Reportes**.

1. **Balanza de Comprobación:** todas las cuentas con saldo inicial, movimientos y saldo final; totales Debe = Haber.
2. **Estado de Resultados** (rango del mes): Ingresos (clase 5) 1,000.00 − Costos y gastos (clase 4) 700.00 (Compras 500.00 + Alquileres 200.00) = **Utilidad 300.00**.
3. **Estado de Situación Financiera** (fecha de corte hoy): Activo (clase 1) **10,430.00** = Pasivo (clase 2) 130.00 + Patrimonio (clase 3) 10,000.00 + Utilidad del ejercicio 300.00. Mostrar la **comprobación** en verde.
   - *Qué decir:* la clasificación se hace solo por el **primer dígito del código**. Como aún no hay cierre contable, la utilidad del período se suma al patrimonio para que 1 = 2 + 3 cuadre (ADR-016). Son estados de gestión, con la leyenda correspondiente (ADR-037).

## Parte 4 — Datos complementarios y corrección por reversión (rúbrica 4) · ~2 min

1. **Resumen de IVA** (mes actual): IVA débito 130.00, IVA crédito 65.00, diferencia estimada **65.00** a pagar; con desglose manual / n8n.
2. **Reversión.** En el Libro Diario abrir el asiento del alquiler → **Revertir** → elegir la fecha (hoy) → confirmar. Pilot crea un asiento de reversión con Debe y Haber intercambiados y marca el original como `REVERTIDO`; los asientos **nunca se editan ni se borran** (ADR-019; hay permisos de base de datos que lo impiden).
3. Volver al **Estado de Resultados**: utilidad **500.00**, y al **Estado de Situación Financiera**: Activo **10,630.00** = 130.00 + 10,000.00 + 500.00. Caja vuelve a **1,130.00** en el Mayor.
4. **Exportación:** en cualquier reporte (por ejemplo el Estado de Situación Financiera) usar **Exportar → PDF / XLSX / CSV** y abrir uno; las cifras son idénticas a las de la pantalla.
5. **Cierre** (30 segundos): mencionar que el **webhook de n8n** recibe el cierre de ingresos diarios de otras apps y lo contabiliza con las mismas reglas (bitácora de operaciones), que cada empresa ve solo sus datos (Row-Level Security) y que el catálogo de cuentas está basado en el manual de la Universidad Católica (`entregables/README.md`, sección 7).

## Si algo falla durante la demo

| Síntoma | Qué hacer |
|---|---|
| Al abrir Contabilidad aparece "no instalada" | Ir a **Configuración → Apps** e instalar Contabilidad (precarga catálogo, reglas e IVA) |
| Guardar responde error de cuenta | La cuenta debe ser de **detalle** y estar activa; usar el buscador de cuentas |
| Las cifras no coinciden con el guion | La empresa ya tenía asientos previos: usar una cuenta de usuario nueva |
| Fecha rechazada | No se permiten fechas futuras (`CON-007`); usar la de hoy |
