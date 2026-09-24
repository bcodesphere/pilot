# ADR-025 — Tabla `auditoria_global` para entidades sin empresa

- **Estado:** Aceptada
- **Fecha:** 2026-09-24

## Contexto
`auditoria` exige `empresa_id NOT NULL` y está protegida con Row-Level Security forzado por empresa (ADR-002). Algunas entidades no pertenecen a ninguna empresa, como `usuario` (tabla global) y, en el futuro, el catálogo de `aplicacion`. La regla 1.1.11 exige auditar toda mutación de datos de negocio, así que esas mutaciones también necesitan dónde registrarse.

## Decisión
Se crea una tabla separada **`auditoria_global`** (en la migración de F1 que crea `usuario`), con las mismas columnas que `auditoria` menos `empresa_id`:
- Sin política RLS por empresa: no pertenece a ninguna.
- `pilot_app` solo tiene `INSERT`, sin `SELECT`, `UPDATE` ni `DELETE`. La leen operadores de la plataforma directamente en la base de datos. En 1.0 ningún endpoint la expone.
- Sin particiones: el volumen esperado es bajo. Se particionará con un ADR nuevo si crece.
- Retención de 10 años, igual que `auditoria`.

La aplicación elige la tabla según la entidad: si la entidad tiene `empresa_id` usa `auditoria`; si es global usa `auditoria_global`.

## Alternativas consideradas
- **`empresa_id` nulo en `auditoria`, con una política que admita las filas nulas cuando no hay empresa en sesión:** debilita el aislamiento de la tabla más sensible, porque una sesión sin empresa podría leer o escribir filas globales, y complica la política RLS.
- **No auditar entidades globales:** incumple la regla 1.1.11.

## Consecuencias
- `RegistroAuditoria` (F0-06) debe permitir el destino global, o se agrega un puerto hermano en F1.
- Leer `auditoria_global` desde la aplicación, si algún día hace falta, requiere un ADR con un rol y un endpoint propios.
