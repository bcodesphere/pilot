# ADR-016 — Balance General con utilidad del período

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
El requisito original es `1 (Activo) = 2 (Pasivo) + 3 (Capital)`. Sin cierre contable (fuera de alcance), las cuentas 4 y 5 nunca se trasladan al capital, así que esa igualdad literal no se cumple aunque la contabilidad esté correcta.

## Decisión
Activo = Pasivo + Capital + resultados de ejercicios anteriores no cerrados + utilidad del ejercicio, donde utilidad = Ingresos (5) − Costos y gastos (4). El ejercicio es el año calendario `[VERIFICAR]`. Si no cuadra, se muestra una alerta con la diferencia exacta.

## Alternativas consideradas
- **Fórmula literal 1 = 2 + 3:** alerta permanente durante todo el período.

## Consecuencias
- Con la partida doble garantizada, la alerta solo aparece ante errores de datos y enlaza al diagnóstico de mayorización.
- Al implementar cierres anuales, los resultados anteriores se trasladarán al capital y esa línea tenderá a cero.
