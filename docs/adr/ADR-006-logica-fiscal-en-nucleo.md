# ADR-006 — Lógica fiscal y contable solo en el núcleo Java

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
El IVA y los asientos deben calcularse igual sin importar si el origen es la web, n8n u otra app.

## Decisión
El cálculo de IVA (`CalculadoraIva`), la armadura de asientos y la partida doble viven solo en el dominio Java. El frontend puede validar para mejorar la experiencia, pero toda vista previa con IVA la calcula el backend. n8n y las apps de origen envían hechos de negocio sin IVA calculado.

## Alternativas consideradas
- **Calcular en el frontend o en n8n:** duplica reglas y produce diferencias de centavos.

## Consecuencias
- El frontend usa `POST /contabilidad/asientos/vista-previa` cuando una línea lleva IVA.
- Las apps de origen no pueden imponer un IVA distinto al de Pilot.
