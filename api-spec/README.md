# api-spec — Contratos de Pilot

Fuente de verdad de la API (ADR-003). Se modifica **antes** que el código.

- `openapi/pilot-v1.yaml` — contrato REST (OpenAPI 3.1).
- `esquemas/operaciones/` — esquemas JSON de operaciones de n8n (F5).
- `.spectral.yaml` — reglas de lint del contrato.

## Validar

```bash
npx -y @stoplight/spectral-cli@6.16.3 lint api-spec/openapi/pilot-v1.yaml \
  --ruleset api-spec/.spectral.yaml --fail-severity=warn
```

## Reglas propias

Descripción obligatoria (operaciones, parámetros, propiedades), `operationId` en camelCase,
rutas en kebab-case, propiedades en camelCase, errores 4xx/5xx en `application/problem+json`
y ningún monto como `number` (los montos usan el esquema `Monto`, ADR-013).

## Versionado

- La versión mayor va en la ruta (`/api/v1`) y en `info.version` (`1.y.z`).
- Cambios compatibles (campos opcionales nuevos, endpoints nuevos): sube `y` o `z`.
- Cambios incompatibles (quitar o renombrar campos, cambiar tipos): nueva versión mayor (`/api/v2`).
- Los esquemas de operaciones de n8n se versionan aparte (`.../v1.json`, `v2.json`) y conviven.
