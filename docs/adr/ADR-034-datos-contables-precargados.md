# ADR-034 — Datos contables precargados en 1.0: catálogo base en borrador y fecha técnica del IVA

- **Estado:** Aceptada
- **Fecha:** 2026-09-26

## Contexto
F2 carga por migración dos datos globales que la guía técnica marca como pendientes de confirmar:
- el catálogo base de `docs/contabilidad/catalogo-base.md`, un borrador `[VERIFICAR]` que aún no valida un contador;
- la tasa de IVA del 13 % en `tasa_impuesto`, que exige una fecha `vigente_desde` y cuya fecha legal de inicio no está confirmada.

Esperar la validación bloquearía F2 y, con ella, F3 a F5 dentro del timebox de Contabilidad 1.0.

## Decisión
1. **El catálogo base se carga en `plantilla_cuenta` tal como está en el borrador.** Los comentarios de la migración dicen que es un borrador pendiente de validación. Cada empresa recibe una copia al instalar Contabilidad y puede editarla.
2. Si el contador cambia el catálogo, el cambio va en una **migración nueva** sobre `plantilla_cuenta`: afecta solo a las instalaciones futuras. Las empresas ya instaladas se corrigen editando su catálogo. El acta del contador sigue siendo un criterio de aceptación de F2 (plan de trabajo), pendiente.
3. **El IVA del 13 % se carga con `vigente_desde = 2000-01-01` y `vigente_hasta` nulo.** Es una **fecha técnica**, anterior a cualquier asiento posible, no la fecha de la reforma legal. El `COMMENT ON` lo dice y la fecha exacta queda `[VERIFICAR]` con el contador, por si alguna vez se necesita.
4. La tasa (0.1300) no se discute: es la de la guía técnica §11. Solo la fecha de inicio es técnica.

## Alternativas consideradas
- **Esperar al contador:** bloquea F2 a F5 y rompe el timebox.
- **Poner una fecha legal sin fuente:** viola la regla de no inventar datos fiscales (regla 1.2.12).

## Consecuencias
- Un cambio futuro de la tasa se registra cerrando `vigente_hasta` de la fila actual y agregando otra por migración; el `EXCLUDE` impide traslapes.
- La validación del catálogo por el contador sigue abierta en §19 de la guía técnica.
