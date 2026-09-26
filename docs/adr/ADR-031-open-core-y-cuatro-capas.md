# ADR-031 — Modelo Open-Core / Freemium y solución estructurada en cuatro capas

- **Estado:** Aceptada
- **Fecha:** 2026-09-25

## Contexto
Hasta ahora la documentación describía el alcance de 1.0 (núcleo + Contabilidad) y dejaba en la guía técnica §20 las funciones futuras, entre ellas la facturación electrónica (DTE) y la edición Enterprise (ADR-029, ADR-030). Faltaba una visión de negocio explícita del **sistema final** que explique por qué existen dos ediciones y cómo debe organizarse la solución completa.

## Decisión

### 1. Mercado y modelo de negocio
- **Mercado objetivo:** El Salvador.
- **Modelo:** ERP **Open-Core / Freemium**.

| Plan | Público | Incluye |
|---|---|---|
| **Gratuito (Comunitario)** | Micro y pequeñas empresas que operan **sin registro fiscal** | Núcleo del ERP y apps de edición `COMUNITARIA` (en 1.0: Contabilidad) |
| **Enterprise** | Empresas con registro fiscal que necesitan cumplir con el Ministerio de Hacienda | Suscripción de **bajo costo** con la integración oficial de **Facturación Electrónica (DTE)** con el MH, empresa jurídica con NIT (ADR-029) y apps `ENTERPRISE` (ADR-030) |

`[DECISIÓN]` precio, periodicidad y límites de la suscripción Enterprise.

### 2. Cuatro capas obligatorias de la solución integral
Toda la solución final se estructura en estos cuatro pilares. Cada funcionalidad nueva debe declarar a qué capa pertenece.

| Capa | Propósito | Qué existe en 1.0 | Qué falta (guía técnica §20) |
|---|---|---|---|
| **I. Licenciamiento** | Gestión de los planes Free/Enterprise: qué puede usar cada empresa | `aplicacion.edicion` (`COMUNITARIA`/`ENTERPRISE`), apps Enterprise visibles y bloqueadas con `PLT-011`, empresa `PERSONAL` vs `JURIDICA` (ADR-029, ADR-030) | Planes, suscripción, cobro, upgrade de empresa personal a jurídica, activación de apps Enterprise |
| **II. Arquitectura de software** | Diseño robusto, escalable y mantenible | Monolito modular hexagonal, contract-first, multiempresa con RLS forzado, asientos inmutables, idempotencia, auditoría (ADR-001 a ADR-030) | Eventos salientes, perfil `worker`, almacenamiento S3 y demás componentes diferidos |
| **III. Acreditación tributaria DTE** | Cumplimiento técnico de la normativa del MH para DTE | Ningún DTE (diseño conservado en `docs/diferido/`). Base preparada: lógica fiscal solo en el núcleo Java (ADR-006) y tasa de IVA con vigencia en `tasa_impuesto` | Emisión, firma, transmisión, contingencia, invalidación y recepción de DTE; certificación ante el MH (ADR-007 diferido) |
| **IV. Blindaje legal y comercial** | Términos de servicio, protección de datos personales y garantías | Consentimiento de recomendaciones por correo con fechas de aceptación y retiro (ADR-028), datos personales enmascarados en logs, auditoría insert-only, aislamiento por empresa | Términos de servicio y política de privacidad publicados y aceptados con versión, acuerdo de nivel de servicio y garantías de la suscripción, procedimiento de derechos sobre datos personales |

`[VERIFICAR]` con asesoría legal: normativa salvadoreña de protección de datos personales y de comercio electrónico aplicable a los términos y a la política de privacidad. No se redacta texto legal sin esa revisión.

### 3. Relación con Pilot 1.0
- Este ADR **no amplía el alcance de 1.0** (guía técnica §2). Las capas I, III y IV se completan en versiones posteriores, cada una con su propio ADR (regla 1.2.13).
- 1.0 corresponde al **plan Gratuito**: núcleo + Contabilidad.
- **NIT y NRC en la versión abierta (modifica ADR-029, punto 4):** no se capturan ni se editan. `PATCH /empresas/{id}` solo acepta nombre y nombre comercial, y `Empresa` no los expone. No se agrega ninguna restricción: las columnas `empresa.nit` y `empresa.nrc` siguen existiendo y nulas para la edición Enterprise.

## Alternativas consideradas
- **Producto 100 % de pago:** excluye a las microempresas sin registro fiscal, que son el mercado de entrada.
- **Todo gratuito, incluido DTE:** la acreditación ante el MH y su mantenimiento normativo tienen un costo recurrente que la suscripción debe cubrir.
- **DTE como módulo independiente de pago por documento:** se pospone; la suscripción de bajo costo es más simple de explicar y de cobrar.

## Consecuencias
- La guía técnica §2.0 resume la visión, §20.2.1 agrega DTE a la edición Enterprise y el README la presenta.
- Las tareas futuras se clasifican por capa; la capa IV necesita asesoría legal antes de implementarse.
- El precio, la periodicidad y los límites de Enterprise quedan como decisión abierta en la guía técnica §19.
