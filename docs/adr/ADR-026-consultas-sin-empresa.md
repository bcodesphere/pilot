# ADR-026 — Consultas previas a conocer la empresa

- **Estado:** Aceptada
- **Fecha:** 2026-09-24

## Contexto
`TenantAwareTransactionManager` (F0-06) exige una empresa en el contexto para abrir cualquier transacción y así fijar `app.empresa_id` para Row-Level Security (ADR-002). En F1 hay operaciones que ocurren **antes** de conocer la empresa activa:
- buscar o dar de alta al usuario por el claim `sub` del token;
- listar las membresías del usuario (selector de empresa, `GET /me`) y validar `X-Empresa-Id`;
- encontrar una API key por su prefijo para derivar su empresa (webhook de n8n).

La guía técnica (§4.5) ya indicaba funciones `SECURITY DEFINER` mínimas, pero no cómo convivían con el gestor de transacciones.

## Decisión
1. **Modo explícito "sin empresa":** `ContextoEmpresa.ejecutarSinEmpresa(usuarioId, accion)`. El gestor abre la transacción **sin** fijar `app.empresa_id` (sí fija `app.usuario_id` cuando existe). Fuera de ese modo, una transacción sin empresa sigue fallando con `ContextoEmpresaAusenteException`.
2. **Falla del lado seguro:** en ese modo cualquier consulta a una tabla con RLS falla, porque `current_setting('app.empresa_id')` no existe y la conversión a `uuid` produce error. No se agrega `missing_ok` a las políticas.
3. **Lo único permitido en ese modo:**
   - leer y escribir tablas globales sin RLS (`usuario`, `auditoria_global`);
   - llamar a funciones `SECURITY DEFINER` con `SET search_path` fijo y `EXECUTE` revocado a `PUBLIC` y concedido solo a `pilot_app`, que devuelven solo los campos necesarios. Su dueño es el rol **`pilot_busqueda`** (`NOLOGIN NOSUPERUSER NOBYPASSRLS`), creado por migración, con `SELECT` solo sobre las tablas que consultan y una política RLS `FOR SELECT TO pilot_busqueda USING (true)` en cada una:
     - `membresias_de_usuario(usuario_id)` → empresa, nombre, rol y estado de las membresías activas;
     - `membresia_activa(usuario_id, empresa_id)` → rol o vacío;
     - `api_key_por_prefijo(prefijo)` → id, empresa, hash, alcances, expiración y revocación.
4. **Regla de ArchUnit:** solo las clases de `plataforma` autorizadas (resolución de identidad, validación de membresía y autenticación por API key) pueden llamar a `ejecutarSinEmpresa`. La lista se mantiene en la propia prueba.
5. Una vez validada la empresa, el resto de la petición corre con `ContextoEmpresa.ejecutarCon(...)` como hasta ahora.

## Por qué un rol propio y no `pilot_owner`
Las tablas tienen RLS **forzado**, que también se aplica a su dueño. Una función propiedad de `pilot_owner` no vería ninguna fila en producción, donde `pilot_owner` no será superusuario. En desarrollo sí lo es, así que el fallo quedaría oculto en las pruebas. Con `pilot_busqueda`, que no es dueño ni superusuario, las pruebas reproducen exactamente el comportamiento de producción, y el rol solo puede leer lo que las funciones necesitan.

## Alternativas consideradas
- **Funciones propiedad de `pilot_owner` con políticas `TO pilot_owner`:** funcionan, pero dan lectura total al dueño del esquema y las pruebas con superusuario no detectan errores de RLS.
- **`JdbcClient` fuera del gestor de transacciones:** es más simple, pero crea un segundo camino de acceso a datos que no pasa por el control del gestor y es más fácil de usar por error.
- **Políticas RLS con `current_setting(..., true)` y filas visibles sin empresa:** debilita el aislamiento de todas las tablas de negocio.
- **Rol de base de datos con `BYPASSRLS` para las búsquedas:** está prohibido por la regla 1.2.11.

## Consecuencias
- Las políticas `aislamiento_empresa` de las tablas que consultan las funciones se declaran `TO pilot_app`. Las políticas permisivas se combinan con OR y `current_setting('app.empresa_id')` sin el parámetro lanza error, así que si esa política también se aplicara a `pilot_busqueda`, las funciones fallarían.
- En producción `pilot_owner` necesita `CREATEROLE` para que la migración cree `pilot_busqueda`, o el rol se crea antes en el script de roles (F6).
- `ContextoEmpresa` y `TenantAwareTransactionManager` cambian en F1-04, con pruebas que demuestran que una tabla con RLS no es legible en modo sin empresa.
- Cada función `SECURITY DEFINER` nueva requiere su prueba de integración y una actualización de este ADR.
