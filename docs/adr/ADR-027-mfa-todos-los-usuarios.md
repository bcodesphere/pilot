# ADR-027 — MFA obligatorio para todos los usuarios

- **Estado:** Aceptada
- **Fecha:** 2026-09-24

## Contexto
La guía técnica pedía MFA para `admin_empresa` y `contador`. Esos roles son **por empresa** y viven en la base de Pilot (`empresa_usuario.rol`), así que Keycloak no los conoce al iniciar sesión. Además, todo usuario nuevo es `admin_empresa` de su propia empresa personal (ADR-029).

## Decisión
Keycloak exige MFA (TOTP) a **todos** los usuarios del realm `pilot`, configurado como acción requerida al primer inicio de sesión. Pilot no verifica claims de MFA (`amr`/`acr`) por rol.

## Alternativas consideradas
- **Flujo condicional en Keycloak según un atributo sincronizado desde Pilot:** requiere mantener dos fuentes de verdad de los roles.
- **Que Pilot exija `acr`/`amr` en las rutas de ciertos roles:** obliga a reautenticar al cambiar de empresa y complica el frontend.

## Consecuencias
- El realm versionado en `infra/keycloak/` configura TOTP obligatorio.
- En desarrollo y pruebas automatizadas se usan usuarios de prueba con TOTP de semilla conocida o un realm de pruebas con la acción desactivada, siempre documentado.
- Los flujos `navegador-pilot` y `restablecer-credenciales-pilot` exigen OTP sin condición (F1-02), para que borrar el OTP desde la consola de cuenta no desactive el MFA.
- **Recuperación:** si un usuario pierde su dispositivo TOTP, solo un administrador de Keycloak puede quitarle el OTP; al volver a iniciar sesión se le obliga a configurarlo de nuevo. En 1.0 no hay códigos de recuperación (decisión del usuario, 2026-09-24).
