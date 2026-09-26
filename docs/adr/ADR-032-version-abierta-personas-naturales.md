# ADR-032 — Versión abierta orientada a personas naturales: sin datos empresariales ni miembros

- **Estado:** Aceptada (modifica ADR-028 punto 6 y ADR-029 puntos 4 y 5)
- **Fecha:** 2026-09-25

## Contexto
ADR-031 define que el plan Gratuito es para quienes operan sin registro fiscal. El usuario de la versión abierta es una **persona natural**: la empresa `PERSONAL` (ADR-029) es su espacio de trabajo, no una persona jurídica. Hasta ahora 1.0 incluía editar datos de empresa (nombre comercial, NIT, NRC) y agregar a otros usuarios registrados como miembros (ADR-028).

## Decisión
1. **Sin datos empresariales en la versión abierta.** La empresa `PERSONAL` solo tiene un **nombre** editable, que al crearla es el nombre del usuario. Nombre comercial, NIT y NRC no se capturan ni se exponen. Las columnas siguen en la base, nulas, para la edición Enterprise; no se agregan restricciones.
2. **Configuración por app.** No hay configuración a nivel de empresa. Cada app guarda la suya; por ejemplo, Contabilidad guarda el modo de precio, las cuentas de IVA y las reglas en `configuracion_contable` y `regla_contabilizacion`.
3. **Miembros solo en Enterprise.** Agregar a otros usuarios registrados, cambiar su rol o desactivarlos pasa a la edición Enterprise. En 1.0 cada persona trabaja sola en su espacio y es `admin_empresa` de él.
4. **Se conserva el mecanismo multiempresa:** `empresa_usuario`, los roles, `X-Empresa-Id`, la validación de membresía y RLS. Así Enterprise no requiere rediseño.

## Alternativas consideradas
- **Mantener miembros en la versión abierta:** permitiría, por ejemplo, que el contador del usuario entre a su espacio. Se descartó por decisión de producto: la colaboración es un valor de Enterprise.
- **Quitar también la edición del nombre:** el nombre del espacio sería siempre el del usuario. Se prefirió dejarlo editable.

## Consecuencias
- Salen del contrato de 1.0 `GET/POST /empresas/{id}/usuarios` y `PATCH /empresas/{id}/usuarios/{usuarioId}`, con sus esquemas. `PATCH /empresas/{id}` solo acepta `nombre`.
- Los códigos `PLT-012`, `PLT-013` y `PLT-014` quedan reservados para Enterprise y no se usan en 1.0.
- En 1.0 un usuario tiene una sola membresía, así que el selector de empresa del frontend solo se muestra si hay más de una.
- El criterio de F1 "un usuario con dos empresas cambia de empresa" se prueba con datos de prueba y con el aislamiento entre usuarios, no desde la interfaz.
- Los roles `contador` y `auditor` siguen en el modelo; en 1.0 solo se asignan en pruebas.
