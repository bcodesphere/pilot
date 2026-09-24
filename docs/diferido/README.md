# Diseños diferidos — NO implementar en Pilot 1.0

Esta carpeta conserva diseños que quedaron fuera del alcance de 1.0 para retomarlos después. **Claude no debe implementar nada de aquí** sin un ADR que lo incorpore al alcance (CLAUDE.md, regla 1.2.13).

| Archivo | Contenido | Estado |
|---|---|---|
| `vision-completa-con-dte.md` | Visión completa del producto: todos los módulos, facturación electrónica DTE 2.0 (emisión, contingencia, invalidación, retorno), contabilidad avanzada, eventos, webhooks salientes, n8n y roadmap original | Referencia |
| `mvp-con-webhook-dte.md` | Diseño intermedio del MVP en que n8n enviaba DTE (`DTE_MH` y `SIMPLIFICADO`) con reglas por tipo DTE × dirección, retenciones, percepciones y renta | Referencia para cuando vuelva la facturación electrónica |

Al retomar la facturación electrónica:

1. Crear un ADR que la incorpore al alcance y defina la versión objetivo.
2. Reutilizar el tipo de operación de n8n (ADR-017) agregando operaciones DTE, en lugar de un endpoint separado.
3. Revisar todos los `[VERIFICAR]` contra la normativa DTE vigente en ese momento.
