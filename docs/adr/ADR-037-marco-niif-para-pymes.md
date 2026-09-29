# ADR-037 — Marco contable NIIF para PYMES: terminología, estados de gestión y catálogo base ampliado

- **Estado:** Aceptada
- **Fecha:** 2026-09-27

## Contexto
Al cerrar F3 se revisó si la base contable de Pilot sigue las NIIF. La guía técnica no declaraba ningún marco contable: las NIIF para PYMES solo aparecían en el diseño diferido (`docs/diferido/`) y en una pregunta del formulario del contador (9.4). La revisión encontró:

1. La mecánica del núcleo es neutral al marco y compatible con NIIF para PYMES: partida doble, asientos inmutables con corrección por reversión, base de acumulación, ingresos sin el IVA cobrado por cuenta de terceros, moneda funcional USD, clasificación corriente/no corriente y cuentas complementarias (depreciación acumulada, devoluciones).
2. El catálogo por clases 1 a 5 es un plan de cuentas interno; NIIF no prescribe uno.
3. La terminología de los estados ("Balance General", "Capital Contable") no es la de NIIF para PYMES ("Estado de Situación Financiera", "Patrimonio").
4. Pilot 1.0 produce dos estados (situación financiera y resultados). Un juego completo según NIIF para PYMES incluye además el estado de cambios en el patrimonio, el estado de flujos de efectivo, las notas y la información comparativa; esos están en la guía técnica §20.3 (fuera de alcance).
5. El catálogo base no tiene cuentas para: gasto e impuesto diferido del impuesto sobre la renta, deterioro de cuentas por cobrar, beneficios a empleados (aguinaldo, vacaciones, indemnización), provisiones, inmuebles e intangibles.

## Decisión (del usuario, 2026-09-27)
1. **Marco contable:** Pilot adopta las **NIIF para PYMES** vigentes en El Salvador como marco de referencia de su contabilidad. La edición aplicable (el IASB publicó la 3.ª edición en 2025) y la resolución del Consejo de Vigilancia de la Profesión de Contaduría Pública y Auditoría (CVPCPA) que la adopta quedan `[VERIFICAR]` con el contador. La lógica contable sigue viviendo solo en el núcleo Java (ADR-006).
2. **Terminología NIIF** en la guía técnica, el contrato, las pantallas y las exportaciones de F4:
   - "Balance General" → **Estado de Situación Financiera** (ruta `GET /contabilidad/estados/situacion-financiera?fechaCorte`);
   - "Capital Contable" → **Patrimonio** (clase 3);
   - la fórmula de ADR-016 no cambia: Activo = Pasivo + Patrimonio + resultados no cerrados + utilidad del ejercicio.
3. **Estados de gestión en 1.0:** el Estado de Situación Financiera y el Estado de Resultados de Pilot 1.0 son **estados de gestión**. Pantallas y exportaciones llevan la leyenda: "Estado de gestión generado por Pilot; no constituye un juego completo de estados financieros conforme a NIIF para PYMES". El estado de cambios en el patrimonio, el flujo de efectivo, las notas y los comparativos siguen en §20.3.
4. **Estado de Resultados con el impuesto separado:** el grupo **44 (Impuesto sobre la renta)** se presenta aparte: Ingresos (5) − Costos y gastos (4, sin el grupo 44) = **Utilidad antes de impuesto**; − Impuesto sobre la renta (44) = **Utilidad (pérdida) del ejercicio**. El Estado de Situación Financiera usa la utilidad después de impuesto.
5. **Catálogo base ampliado ahora, como borrador:** una migración nueva agrega a `plantilla_cuenta` las cuentas de `docs/contabilidad/catalogo-base.md` marcadas "ADR-037" y renombra la clase 3 a "PATRIMONIO". Todo sigue `[VERIFICAR]` con el contador (ADR-034). Solo aplica a las empresas que instalen Contabilidad después de la migración; las ya instaladas no cambian (no se tocan datos por empresa).

## Alternativas consideradas
- **Esperar al contador para declarar el marco:** dejaría F4 sin criterio de presentación y obligaría a cambiar el contrato después.
- **Mantener "Balance General":** es habitual en El Salvador, pero no es la terminología de NIIF para PYMES; cambiarlo antes de escribir el contrato de F4 no cuesta nada.
- **Ampliar F4 con el estado de cambios en el patrimonio y el flujo de efectivo:** no cabe en el timebox de 1.0 y el flujo de efectivo necesita clasificar movimientos que hoy no se capturan.

## Consecuencias
- F4 escribe su contrato y pantallas con la terminología NIIF y la leyenda de estados de gestión.
- El catálogo base crece y el formulario del contador debe cubrir las cuentas nuevas.
- Las empresas de desarrollo ya instaladas conservan el catálogo anterior; para probar el ampliado se usa un usuario nuevo.
