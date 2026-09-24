# ADR-008 — Keycloak como proveedor de identidad

- **Estado:** Aceptada
- **Fecha:** 2026-09-23

## Contexto
Se necesita registro, inicio de sesión, verificación de correo y MFA sin desarrollar un sistema de identidad propio.

## Decisión
Keycloak 26.x con realm `pilot` versionado en `infra/keycloak/`: autorregistro, verificación de correo, MFA obligatorio para `admin_empresa` y `contador`. La web usa OIDC con PKCE (`oidc-client-ts`, tokens en memoria). Pilot crea su registro de `usuario` la primera vez que recibe el `sub` del token.

## Alternativas consideradas
- **Autenticación propia:** alto riesgo de seguridad y mantenimiento.
- **Servicio gestionado (Auth0, Cognito):** costo y dependencia de terceros.

## Consecuencias
- Keycloak y Mailpit forman parte del entorno de desarrollo.
- Las integraciones usan API keys de Pilot, no cuentas de Keycloak.
