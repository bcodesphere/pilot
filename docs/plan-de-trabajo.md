# Plan de trabajo — Pilot 1.0 (Núcleo + Contabilidad)

| Campo | Valor |
|---|---|
| Alcance | Núcleo del ERP + app Contabilidad + webhook n8n de operaciones (ver `CLAUDE.md`, sección 2) |
| Fuera de alcance | Facturación electrónica DTE (en segundo plano, `docs/diferido/`) |
| Fecha del plan | 2026-09-23 |
| Supuesto de equipo | 1–2 desarrolladores a tiempo completo `[DECISIÓN]` confirmar |
| Estimación total | ≈ 12–14 semanas |

---

## 1. Decisiones confirmadas

| # | Pregunta | Decisión |
|---|---|---|
| 1 | Fórmula del Balance General | Activo = Pasivo + Capital + resultados no cerrados + utilidad del ejercicio (ADR-016) |
| 2 | Qué envía n8n | Operaciones de negocio con contrato propio de Pilot; primer tipo: **cierre de ingresos diarios** (ADR-017). DTE en segundo plano |
| 3 | Cuentas del asiento generado desde n8n | Reglas por tipo de operación × categoría (ingreso o cobro) × código, editables y precargadas (ADR-020) |
| 4 | Edición de asientos | Inmutables; se corrigen con asiento de reversión (ADR-019) |
| 5 | Modo de precio para n8n | Se usa el modo por defecto configurado en la app Contabilidad (ADR-015) |

---

## 2. Mapa de fases y dependencias

```text
F0 Fundaciones ──► F1 Núcleo ──► F2 Catálogo y configuración ──► F3 Libro Diario + Mayorización
                                                                      │
                                                   ┌──────────────────┴──────────────────┐
                                                   ▼                                     ▼
                                        F4 Reportes y estados              F5 Webhook n8n (cierre diario)
                                                   │                                     │
                                                   └──────────────────┬──────────────────┘
                                                                      ▼
                                                        F6 Endurecimiento y piloto
```

| Fase | Duración estimada | Depende de |
|---|---|---|
| F0 Fundaciones | 2 semanas | — |
| F1 Núcleo (usuarios, empresa, apps) | 2 semanas | F0 |
| F2 Catálogo de cuentas y configuración contable | 1.5 semanas | F1 |
| F3 Libro Diario, IVA manual y mayorización | 3 semanas | F2 |
| F4 Reportes y estados financieros | 2.5 semanas | F3 |
| F5 Webhook n8n: cierre de ingresos diarios | 2 semanas | F3 |
| F6 Endurecimiento y piloto con contador | 1.5 semanas | F4, F5 |

F4 y F5 pueden ejecutarse **en paralelo** si hay dos desarrolladores.

---

## 3. Fases detalladas

### F0 — Fundaciones (2 semanas)

**Tareas**
1. Proyecto base: `backend/` (Spring Boot 4.1 + Spring Modulith, Maven wrapper), `frontend/` (React 19 + Vite + TypeScript estricto, pnpm), `api-spec/` con Spectral.
2. `infra/docker/compose.dev.yml` con PostgreSQL 17, Keycloak 26, n8n y Mailpit.
3. Módulos `compartido`, `plataforma`, `contabilidad`, `integracion` con la prueba `ApplicationModules.verify()`.
4. Usuarios de base de datos `pilot_owner` (Flyway) y `pilot_app` (sin `BYPASSRLS`), y `TenantAwareTransactionManager` que fija `app.empresa_id` y `app.usuario_id`.
5. Infraestructura transversal: Problem Details (RFC 9457), tabla `idempotencia` y su filtro, tabla `auditoria` particionada, logs JSON con `traceId` y `empresaId`.
6. Contrato `api-spec/openapi/pilot-v1.yaml` base y generación de interfaces con `openapi-generator`; cliente Orval en el frontend.
7. CI en GitHub Actions: formato, lint, pruebas, gitleaks.

**Criterios de aceptación**
- `./mvnw verify` y `pnpm lint && pnpm test` pasan en CI.
- Una tabla de ejemplo con RLS: una prueba de integración demuestra que la empresa A no lee ni escribe datos de la empresa B.
- Una petición repetida con el mismo `Idempotency-Key` devuelve la respuesta original sin volver a ejecutar la operación.
- Un error de validación devuelve `application/problem+json` con `codigo` y lista `errores`.

---

### F1 — Núcleo del ERP (2 semanas)

**Tareas**
1. Realm `pilot` en Keycloak: autorregistro, verificación de correo (Mailpit), login OIDC con PKCE y MFA para `admin_empresa` y `contador`.
2. Tablas `usuario`, `empresa`, `empresa_usuario`, `aplicacion`, `empresa_aplicacion`, `api_key`.
3. Alta de usuario en Pilot la primera vez que inicia sesión (desde el `sub` del token).
4. Registro de empresa (`POST /empresas`): crea la empresa, asigna `admin_empresa`, activa Contabilidad y ejecuta la precarga de F2.
5. Selector de empresa activa (`X-Empresa-Id`) validado contra las membresías.
6. Gestión básica: editar la empresa, invitar usuarios, cambiar rol, desactivar membresía.
7. API keys: crear (secreto visible una sola vez), listar, revocar; hash Argon2id; alcances.
8. Frontend: shell con lanzador de apps que lee `GET /aplicaciones`.

**Criterios de aceptación**
- Un usuario nuevo se registra, verifica su correo, inicia sesión, crea su empresa y ve la app Contabilidad en el lanzador.
- Un usuario con dos empresas cambia de empresa y solo ve los datos de la empresa activa.
- Un `X-Empresa-Id` sin membresía devuelve 403 `PLT-003`.
- Una API key revocada o vencida devuelve 401; sin el alcance requerido, 403.
- Agregar una fila en `aplicacion` no requiere cambios en el shell.

---

### F2 — Catálogo de cuentas y configuración contable (1.5 semanas)

**Tareas**
1. Tabla global `plantilla_cuenta` con el catálogo de `docs/contabilidad/catalogo-base.md`, cargada por migración.
2. Tabla `cuenta_contable` y copia de la plantilla al registrar la empresa.
3. CRUD del catálogo con sus validaciones (`CON-010` a `CON-012`).
4. Tabla global `tasa_impuesto` con IVA 13 % vigente.
5. `configuracion_contable` (modo de precio, cuentas de IVA débito y crédito), precargada.
6. `regla_contabilizacion` precargada para `CIERRE_INGRESOS_DIARIO` (CLAUDE.md 12.5).
7. Frontend: árbol del catálogo con búsqueda, formulario de cuenta, pantalla de configuración y editor de reglas.

**Criterios de aceptación**
- Una empresa nueva tiene catálogo, configuración y reglas precargados sin intervención manual.
- No se puede crear una cuenta con código `6…`, con un padre que no sea prefijo de su código, ni un código duplicado.
- No se puede desactivar una cuenta con saldo, ni cambiar el código de una cuenta con movimientos.
- Cambiar el modo de precio queda en la auditoría con valor anterior y nuevo.
- Un contador valida el catálogo base y las reglas por defecto (acta en `docs/contabilidad/`).

---

### F3 — Libro Diario, IVA manual y mayorización (3 semanas)

**Tareas**
1. Tablas `asiento`, `asiento_linea`, `correlativo_asiento`, `saldo_cuenta_mensual`; trigger de partida doble diferido y permisos de solo inserción.
2. Dominio `Asiento` y `LineaAsiento` con las validaciones de CLAUDE.md 10.1.
3. `CalculadoraIva` y expansión de líneas "lleva IVA" (`CON_IVA` / `SIN_IVA`).
4. Caso de uso `RegistrarAsientoManual`: expandir, validar, numerar, guardar y mayorizar en **una sola transacción**.
5. Mayorización con upsert en `saldo_cuenta_mensual`, ordenado por `cuenta_id`.
6. Caso de uso `RevertirAsiento`.
7. Endpoints de asientos, vista previa y reversión.
8. Frontend: formulario del Libro Diario con líneas dinámicas, buscador de cuentas, casilla "lleva IVA", selector de modo, totales en vivo con `decimal.js` y botón Guardar bloqueado mientras no cuadre.
9. Pruebas: casos dorados de IVA, concurrencia de numeración y mayorización, propiedad "saldo = suma de líneas".

**Criterios de aceptación**
- **Backend:** asientos descuadrados, con menos de 2 líneas, con totales en cero, con líneas con Debe y Haber o ninguno, o con montos negativos se rechazan con 422 `CON-001…CON-005` y no se guarda nada.
- **Frontend:** Guardar permanece deshabilitado y se muestra la diferencia mientras no cuadre; el backend rechaza igual si se evita el frontend.
- **Mayorización:** los saldos cambian en la misma transacción del asiento; si el guardado falla, ningún saldo cambia.
- **IVA `CON_IVA`:** Haber "Ventas" 113.00 con IVA → Ventas 100.00 + IVA débito fiscal 13.00.
- **IVA `SIN_IVA`:** Debe "Compras" 100.00 con IVA → Compras 100.00 + IVA crédito fiscal 13.00.
- **Reversión:** invierte Debe y Haber, marca el original `REVERTIDO` y deja los saldos como estaban; no se puede revertir dos veces.
- **Numeración:** 50 asientos concurrentes reciben números consecutivos sin duplicados.
- Un `UPDATE` o `DELETE` sobre `asiento_linea` con `pilot_app` falla por permisos.

---

### F4 — Reportes y estados financieros (2.5 semanas)

**Tareas**
1. Consultas de lectura con `JdbcClient`: saldo a una fecha y movimientos de un rango.
2. Libro Diario, Libro Mayor / auxiliar con saldo acumulado, Balanza de Comprobación.
3. Balance General con utilidad del ejercicio, resultados no cerrados y alerta de diferencia.
4. Estado de Resultados por rango de fechas.
5. Resumen de IVA mensual con desglose manual / n8n.
6. Exportación PDF (Thymeleaf + OpenHTMLtoPDF), XLSX (Apache POI) y CSV.
7. Frontend: pantallas de reportes con filtros de período, jerarquía expandible y exportación.

**Criterios de aceptación**
- Con el conjunto de datos dorado, cada reporte coincide al centavo con el resultado validado por contador.
- Balanza: total saldos deudores = total saldos acreedores.
- El Balance General cuadra (ADR-016); ante un descuadre forzado en la base de pruebas aparece la alerta con la diferencia exacta.
- Utilidad del Estado de Resultados = utilidad mostrada en el Balance para el mismo período.
- Los reportes reflejan un asiento inmediatamente después de guardarlo.
- PDF, XLSX y CSV tienen los mismos totales que la pantalla.
- Un reporte anual de 10,000 asientos responde en menos de 2 segundos (p95).

---

### F5 — Webhook n8n: cierre de ingresos diarios (2 semanas)

**Tareas**
1. Contrato en OpenAPI (`POST /integraciones/n8n/operaciones`) y esquema `api-spec/esquemas/operaciones/cierre-ingresos-diario/v1.json`.
2. Autenticación por API key con alcance `integracion:operaciones` y límite de peticiones.
3. Conversión del cuerpo a `OperacionContable` y caso de uso `ContabilizarOperacion` en `contabilidad` (CLAUDE.md 12.5).
4. Idempotencia doble (`Idempotency-Key` + `sistemaOrigen`/`idExterno`) y asiento único por operación.
5. Tablas `operacion_externa` e `intento_operacion_externa`; bitácora en el frontend.
6. Plantilla `integraciones/plantillas-n8n/cierre-ingresos-diario.json` (webhook de la app web → Pilot, reintentos y Error Trigger).
7. Runbook `docs/runbooks/correccion-cierre.md`.

**Criterios de aceptación**
- Con modo `CON_IVA`, el cierre de ejemplo (gravadas 1,130.00, exentas 50.00, efectivo 780.00, tarjeta 400.00) genera: Caja 780.00 + CxC tarjetas 400.00 / Ventas gravadas 1,000.00 + IVA débito 130.00 + Ventas exentas 50.00.
- El mismo cierre con modo `SIN_IVA` se rechaza con 422 `INT-006` y `diferencia` 146.90.
- Mismo `Idempotency-Key` y mismo cuerpo → 200 con la respuesta original y `Idempotency-Replayed: true`; misma clave con otro cuerpo → 422 `INT-005`; otra clave con el mismo `idExterno` vigente → 409 `INT-004`. Nunca hay dos asientos.
- Una forma de pago sin regla → 422 `CON-020`, nada se guarda y el intento aparece en la bitácora.
- Tras revertir el asiento de un cierre, el reenvío corregido con el mismo `idExterno` se acepta.
- Desde la app web de prueba → n8n → Pilot, el asiento aparece en el Libro Diario y en los reportes.

---

### F6 — Endurecimiento y piloto (1.5 semanas)

**Tareas**
1. Pruebas de aislamiento entre empresas por API y por SQL con `pilot_app`.
2. Pruebas de carga con k6: 20 asientos/s y 20 operaciones n8n/s por instancia.
3. Escaneos de seguridad (OWASP ZAP base, Trivy, CodeQL o Semgrep).
4. Pruebas end-to-end con Playwright: registro → empresa → asiento → reportes; cierre por n8n → asiento → reportes.
5. Panel de observabilidad y alertas de CLAUDE.md 16.4.
6. Validación con contador de un mes completo de una empresa piloto; acta en `docs/contabilidad/`.
7. Runbooks: restauración de respaldo y rotación de API keys.

**Criterios de aceptación**
- Todas las pruebas de CLAUDE.md sección 15 pasan en CI.
- Sin hallazgos de severidad alta abiertos.
- Un contador firma la validación del mes piloto (asientos, balanza, estados y resumen de IVA).

---

## 4. Orden de implementación dentro de cada funcionalidad

1. Contrato en `api-spec/` → lint con Spectral.
2. Migración Flyway nueva.
3. Pruebas de dominio (casos dorados) en rojo.
4. Dominio → caso de uso → adaptadores (JPA, REST).
5. Pruebas de integración con Testcontainers.
6. Cliente Orval regenerado → pantalla del frontend → pruebas de Vitest.
7. Comentarios completos, `./mvnw verify` y `pnpm lint && pnpm test`.
8. Actualización de `CLAUDE.md` y ADR si cambió alguna decisión.

---

## 5. Riesgos del plan

| Riesgo | Mitigación |
|---|---|
| Modo de precio de la empresa distinto al de la app de origen | Rechazo con `INT-006` y la diferencia exacta; documentado en la plantilla n8n y el runbook |
| Catálogo base o reglas por defecto no aceptados por el contador | Validación con contador al cerrar F2 |
| Interbloqueos en la mayorización concurrente | Upsert ordenado por `cuenta_id` y prueba de concurrencia en F3 |
| Reportes lentos con muchos movimientos | Saldos mensuales precalculados, índice `(empresa_id, cuenta_id, fecha)` y prueba de rendimiento en F4 |
| Presión por reincorporar la facturación electrónica | Queda en `docs/diferido/`; su regreso requiere un ADR y una nueva versión del plan |
