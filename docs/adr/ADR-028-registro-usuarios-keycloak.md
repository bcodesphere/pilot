# ADR-028 — Registro de usuarios en Keycloak, sin DUI

- **Estado:** Aceptada (punto 6 modificado por ADR-032: agregar miembros es de Enterprise)
- **Fecha:** 2026-09-24

## Contexto
Todo el que usa Pilot debe ser un usuario registrado, con correo para recuperar la contraseña y, si lo acepta, para recibir publicidad. El DUI y el NIT quedan para la edición Enterprise (ADR-029).

## Decisión
1. **Captura:** autorregistro de Keycloak con perfil de usuario declarativo y un **tema visual de Pilot**. Campos: nombre, apellido, correo, teléfono y contraseña. **No se pide DUI.** La contraseña nunca pasa por Pilot.
2. **Teléfono:** atributo obligatorio, **solo números de El Salvador**: se guarda como `+503` seguido de 8 dígitos. No se admiten números extranjeros.
3. **Correo:** verificación obligatoria antes del primer inicio de sesión. La recuperación de contraseña es la de Keycloak. En desarrollo los correos van a Mailpit.
4. **Consentimiento de recomendaciones por correo:** casilla opcional en el formulario de registro, "Acepto recibir recomendaciones por correo", **desmarcada por defecto** y separada de la aceptación de términos. Pilot guarda la fecha de aceptación y la de retiro, y el usuario puede retirarlo o volver a darlo desde su perfil.
5. **Sincronización:** Keycloak es la fuente de nombre, correo y teléfono. Pilot los copia a `usuario` en cada inicio de sesión (alta en el primero). El consentimiento vive en Pilot desde el alta.
6. **Sin invitaciones pendientes:** una empresa solo puede agregar a usuarios **ya registrados**, buscándolos por correo exacto. No existe tabla de invitaciones. Si el correo no está registrado, el administrador recibe un error y el invitado debe registrarse primero.

## Alternativas consideradas
- **Formulario propio de Pilot con la Admin API de Keycloak:** Pilot recibiría contraseñas y guardaría credenciales de administrador de Keycloak.
- **Tabla `invitacion` con token por correo:** se descartó por decisión de producto: todos deben ser usuarios registrados.

## Consecuencias
- `usuario` agrega `telefono` (dato personal, enmascarado en logs), `recomendaciones_aceptadas_en` y `recomendaciones_retiradas_en`.
- F1-02 construye el realm, el perfil declarativo y el tema. F1-04 hace la sincronización al iniciar sesión.
- Enviar publicidad está fuera del alcance de 1.0: solo se guarda el consentimiento.
