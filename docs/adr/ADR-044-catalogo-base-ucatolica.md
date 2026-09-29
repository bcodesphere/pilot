# ADR-044 — Catálogo base: Manual de Aplicación de Cuentas Comercial (Universidad Católica de El Salvador)

- **Estado:** Aceptada
- **Fecha:** 2026-09-28

## Contexto

El catálogo base de Pilot (V10, ampliado por V15 para NIIF para PYMES, ADR-037) era un catálogo propio, sin respaldo en un documento externo reconocible por un contador salvadoreño. El usuario decidió el 2026-09-28 reemplazarlo por el catálogo del documento `entregables/MANUAL-DE-APLICACION-DE-CUENTAS-COMERCIAL .pdf` ("catálogo comercial", Universidad Católica de El Salvador), con sus propios códigos, para que el punto de partida de Pilot sea un catálogo reconocible y ya usado en la enseñanza contable local. Sigue siendo un borrador `[VERIFICAR]` con el contador (ADR-034): el reemplazo no resuelve esa validación pendiente, solo cambia la base sobre la que se hará.

## Decisión

1. **Fuente y alcance.** El catálogo nuevo sale de las páginas 4 a 18 del PDF (`pdftotext -layout`), clases 1 a 5, solo códigos de 1, 2, 4, 6 y 8 dígitos: Pilot no admite otras longitudes (CLAUDE.md §10.2) ni las clases 6 (cuenta de cierre) y 7 (cuentas de orden), fuera de alcance de 1.0. El catálogo resultante tiene **455 cuentas**, de las cuales **333 son de detalle** (sin hijas en la plantilla; no todas están en el nivel más profundo — ver decisión 4).
2. **Naturaleza.** La de la clase (1 deudora; 2, 3 y 5 acreedora; 4 deudora), salvo las cuentas que el PDF marca "(CR)" — y sus descendientes, siempre marcados también —, que son ACREEDORA aunque su clase sea deudora (23 cuentas, todas en la clase 1 salvo una: 410104, cuenta propia de Pilot). Es la misma regla que ya usaba el catálogo anterior (V10/V15), aplicada ahora a las marcas del PDF en vez de a una lista fija.
3. **Errata del PDF.** Los grupos "GASTOS DE VIÁTICOS, VIAJES Y DE REPRESENTACIÓN" (uno bajo gastos administrativos, otro bajo gastos de venta) tienen código de 5 dígitos (`41021` y `41031`), una longitud que Pilot no admite. Se cargan como `410210` y `410310` (6 dígitos): sus hijas ya traían el prefijo correcto (`41021001`…, `41031001`…, 8 dígitos), así que no cambian.
4. **Cuentas propias de Pilot, agregadas en un código libre de la misma estructura:** `110802` (Renta retenida a favor), `12010806` (Depreciación acumulada — equipo de cómputo, CR), `21080104` (IVA percibido por pagar), `2111`/`211101` (grupo PROVISIONES, nuevo, y su cuenta de litigios y contingencias), `410104` (Devoluciones y rebajas sobre compras, CR), `51010104`/`51010105` (Ventas exentas / no sujetas) y el grupo `44`/`4401`/`440101`/`44010101`/`44010102` (Impuesto sobre la renta), que se conserva igual que en V15 porque el Estado de Resultados lo presenta aparte de los demás costos y gastos (ADR-037). El PDF no tiene un grupo separado para el impuesto sobre la renta; sin él, el Estado de Resultados no podría aislarlo.
5. **Equivalencias.** La tabla completa (código anterior de Pilot → código del PDF, con las cuentas nuevas marcadas) está en `docs/contabilidad/catalogo-base.md`. Resumen por área:

   | Área | Cambia | Ejemplos |
   |---|---|---|
   | Efectivo y bancos | Los códigos de banco pasan de 1101010x a 110102/110103 (moneda nacional/extranjera) | 11010103 (Bancos) → 11010201 (Cuenta corriente) |
   | IVA | Pasa del grupo 1104/2102 al grupo 1109 (crédito) y 2108 (débito) del PDF | 11040101 (IVA crédito) → 110901; 21020101 (IVA débito) → 21080101 |
   | Activo fijo y depreciación | Se reordenan bajo 1201 (PPE) y 120108 (depreciación acumulada) | 12010101 (Mobiliario) → 12010301; 12020101 (Dep. acum. mobiliario) → 12010802 |
   | Pasivo y proveedores | Proveedores pasa a 2102; retenciones e ISR se reordenan bajo 2103/2104/2106 | 21010101 (Proveedores) → 21020101 (Proveedores nacionales) |
   | Costos y gastos | Se reordenan bajo 41 (antes 42) según el PDF; el grupo 44 (ISR) se conserva | 42020101 (Sueldos admin.) → 41020101 |
   | Ingresos | Ventas gravadas/exentas/no sujetas se reordenan bajo 5101 | 51010101 (Ventas gravadas) → 51010102 "Ventas a consumidor final" `[VERIFICAR]` — el PDF no distingue "gravadas" de "consumidor final" con la misma precisión fiscal que el catálogo anterior; el contador debe confirmar el mapeo |

6. **Configuración e IVA fijos (ADR-042).** IVA débito fiscal `21080101` ("IVA - débito fiscal - facturas de consumidor final"), IVA crédito fiscal `110901` ("Compras Locales"). Modo de precio por defecto sin cambios (`CON_IVA`).
7. **Reglas de contabilización.** Las mismas 64 reglas de ADR-041 (9 del cierre de n8n + 55 de los diez tipos guiados), con la cuenta por defecto y el `prefijo_permitido` recalculados para el catálogo nuevo. El prefijo de cada regla es, por defecto, el grupo de 4 dígitos de su propia cuenta; la depreciación acumulada usa el grupo más específico de 6 dígitos `120108` (para no aceptar cualquier cuenta de Propiedad, Planta y Equipo); las reglas sin cuenta por defecto (`OTRO_GASTO`, `OTRO` del cierre) mantienen un prefijo amplio (`4` y `11`).
8. **Empresas existentes no cambian.** Esta migración (V19) solo reemplaza las tablas de plantilla (`plantilla_cuenta`, `plantilla_configuracion_contable`, `plantilla_regla_contabilizacion`), que usa la precarga al instalar Contabilidad (ADR-030). Las empresas que ya instalaron Contabilidad antes de V19 conservan su catálogo, su configuración y sus reglas —coherentes entre sí— sin ningún cambio (regla 1.2.9: nunca se tocan datos de una empresa ya instalada).

## Alternativas consideradas

- **Ampliar el catálogo propio en vez de reemplazarlo:** se descartó porque el objetivo explícito del usuario era partir de un catálogo reconocible externamente, no seguir extendiendo uno propio.
- **Incluir las subcuentas de 10 y 11 dígitos del PDF** (bancos, proveedores y clientes individuales, cuentas de orden): se descartó porque Pilot 1.0 no admite esas longitudes ni lleva auxiliares de terceros (CLAUDE.md §20.2); quedan fuera de alcance, como antes.
- **Traducir "Ventas a contribuyentes/consumidor final" a "gravadas/exentas" sin marcarlo:** se descartó; queda `[VERIFICAR]` explícito porque la equivalencia fiscal no es exacta y el contador debe confirmarla antes de usarse en producción.

## Consecuencias

- Nueva migración `V19__catalogo_base_ucatolica.sql`; ninguna migración anterior se edita (regla 1.2.9).
- Toda prueba que dependía de códigos o nombres del catálogo anterior (117+36 cuentas, códigos de IVA `21020101`/`11040101`, etc.) necesita actualizarse a los códigos y nombres del catálogo nuevo; el detalle de qué archivos y qué pruebas quedaron pendientes está en el reporte de entrega de la tarea CAT (`.orquestacion/`).
- `docs/contabilidad/catalogo-base.md` pasa a documentar el catálogo del PDF y su tabla de equivalencias completa, en vez del catálogo propio de V10/V15.
- El PDF y su reordenamiento pueden no reflejar exactamente el marco NIIF para PYMES de ADR-037 (p. ej. no distingue depreciación de PPE en uso vs. en arrendamiento financiero con la misma profundidad); queda como parte del borrador `[VERIFICAR]` con el contador.
- La cuenta 21080101 ("facturas de consumidor final") como IVA débito fiscal único puede no ser exacta para ventas con Comprobante de Crédito Fiscal (el PDF separa esa situación en 21080102, no usada aquí); anotado como `[VERIFICAR]`.
