# Keycloak — realm `pilot` (F1-02)

Identidad de Pilot: registro, verificación de correo, login OIDC con PKCE y MFA para todos (ADR-008, ADR-027, ADR-028).
Imagen fijada: `quay.io/keycloak/keycloak:26.3.5`. Solo desarrollo; producción queda para F6 (ver al final).

```text
infra/keycloak/
├── realms/pilot-realm.json     # Realm importado al arrancar (sin usuarios, sin secretos)
└── temas/pilot/                # Tema "pilot": login (padre keycloak.v2) y correo (padre keycloak)
```

## Cómo se importa el realm

El servicio `keycloak` de `infra/docker/compose.dev.yml` arranca con `start-dev --import-realm` y monta:

- `infra/keycloak/realms` → `/opt/keycloak/data/import` (solo lectura): los `.json` se importan al arrancar.
- `infra/keycloak/temas/pilot` → `/opt/keycloak/themes/pilot` (solo lectura): el tema, en carpeta aparte para que no se lea como un realm.

```bash
docker compose --env-file .env -f infra/docker/compose.dev.yml up -d keycloak mailpit
```

### Reimportar después de editar el realm

`--import-realm` usa la estrategia `IGNORE_EXISTING`: **no sobrescribe** un realm que ya existe. En desarrollo Keycloak usa su base H2 dentro
del contenedor (sin volumen), así que basta recrearlo, lo que descarta el realm y sus usuarios de prueba:

```bash
docker compose --env-file .env -f infra/docker/compose.dev.yml up -d --force-recreate keycloak mailpit
```

Los cambios del tema no necesitan reimportar (el modo `start-dev` no cachea temas), solo recargar la página.

## Correos (Mailpit)

Keycloak envía la verificación de correo y la recuperación de contraseña a Mailpit (`mailpit:1025`); se leen en <http://localhost:8025>.
Nada sale a Internet.

## Bloques del realm y por qué

| Bloque | Configuración | Motivo |
|---|---|---|
| Acceso | `registrationAllowed`, `registrationEmailAsUsername`, `loginWithEmailAllowed` = `true`; `duplicateEmailsAllowed`, `editUsernameAllowed` = `false`; `resetPasswordAllowed`, `verifyEmail`, `bruteForceProtected` = `true` | Autorregistro con el correo como usuario (ADR-028); correo único y verificado antes de entrar; recuperación de contraseña; bloqueo ante fuerza bruta (CLAUDE.md §14) |
| Idioma | `internationalizationEnabled`, `supportedLocales: ["es"]`, `defaultLocale: "es"` | Producto solo en español (`es-SV`) |
| Temas | `loginTheme` y `emailTheme` = `pilot` | Marca y textos de Pilot |
| Contraseñas | `length(12) and notUsername and notEmail` | Política confirmada por el usuario; la contraseña nunca pasa por Pilot |
| SMTP | `mailpit:1025`, sin autenticación ni TLS, remitente `no-reply@pilot.local` | **Solo desarrollo** |
| Política OTP | TOTP, HmacSHA1, 6 dígitos, 30 s, ventana 1 | Compatible con las aplicaciones autenticadoras comunes |
| Acciones requeridas | Lista estándar de Keycloak; `CONFIGURE_TOTP` habilitada y `defaultAction: true`; `delete_credential` habilitada | Todo usuario nuevo configura TOTP en su primer inicio de sesión. Al importar una lista de acciones, Keycloak no agrega las que faltan, por eso se declaran todas. Por sí sola no basta (solo cubre el primer login): ver «Flujos de autenticación» |
| Flujos de autenticación | `browserFlow = navegador-pilot`, `resetCredentialsFlow = restablecer-credenciales-pilot`, más todos los flujos incorporados de Keycloak | MFA que no se puede evitar (ADR-027): ver la sección siguiente |
| Perfil de usuario | Componente `declarative-user-profile` con `kc.user.profile.config` (JSON dentro de una cadena). Sin política de atributos no gestionados, así que solo existen los declarados | Ningún atributo llega sin declarar. **Sin DUI ni NIT** (ADR-028, ADR-029) |
| `email`, `firstName`, `lastName`, `telefono` | `required: {}` (obligatorios para usuario **y** administrador) | El backend guarda `telefono` como `NOT NULL`; así tampoco se crean usuarios incompletos por la API de administración |
| `telefono` | Patrón `^(\+503)?[0-9]{8}$`, error `telefonoInvalido` (mensaje en español), `inputType` `html5-tel` | Solo números de El Salvador; el backend lo normaliza a `+503XXXXXXXX` |
| `recomendaciones_correo` | `inputType: multiselect-checkboxes`, validador `options` con una única opción `"true"`, etiqueta vía `inputOptionLabels`; no obligatorio | Casilla opcional, desmarcada por defecto. Marcada → el atributo vale `["true"]`; desmarcada → no existe. Comprobado contra Keycloak 26.3.5 |
| Cliente `pilot-web` | Público (sin secreto), solo flujo estándar (sin implícito, `directAccessGrants` ni cuentas de servicio); `pkce.code.challenge.method = S256` | Aplicación de página única con PKCE (CLAUDE.md §5.2) |
| URIs | `redirectUris` y `post.logout.redirect.uris` = `http://localhost:5173/*`; `webOrigins` = `http://localhost:5173` | Servidor de Vite en desarrollo |
| Ámbitos del cliente | `basic`, `acr`, `profile`, `email`, `roles`, `web-origins` | `email`, `email_verified` y `name` salen de los ámbitos estándar `email` y `profile` |
| Mapeador `audiencia-pilot-api` | `oidc-audience-mapper`, audiencia `pilot-api`, solo token de acceso | La API validará `aud` |
| Mapeador `telefono` | atributo `telefono` → claim `telefono` (cadena), solo token de acceso | Sincronización del usuario en el login (F1-04) |
| Mapeador `recomendaciones_correo` | atributo → claim `recomendaciones_correo` (`boolean`), solo token de acceso | Consentimiento inicial (F1-04); sin claim si no lo aceptó |

## Flujos de autenticación (ADR-027)

**Problema del flujo estándar.** En `browser`, el segundo factor está dentro de «Browser - Conditional 2FA»: una condición «user configured» y el OTP como
`ALTERNATIVE`. Si el usuario no tiene OTP la condición falla y el flujo lo deja entrar solo con contraseña. Como `delete_credential` está habilitada, un
usuario podría borrar su OTP desde la consola de cuenta y volver a entrar sin segundo factor. `CONFIGURE_TOTP` como acción por defecto solo protege el primer
inicio de sesión.

| Flujo propio | Asignado como | Diferencia con el estándar |
|---|---|---|
| `navegador-pilot` | `browserFlow` | Copia de `browser` (cookie, redirector de proveedor de identidad, organización) cuyo subflujo de formularios, `navegador-pilot forms`, tiene **Username Password Form `REQUIRED` + OTP Form `REQUIRED`**, sin el subflujo condicional ni «Condition - user configured» |
| `restablecer-credenciales-pilot` | `resetCredentialsFlow` | Copia de `reset credentials` con **OTP Form `REQUIRED`** entre el correo de restablecimiento y «Reset Password», en lugar de «Reset - Conditional OTP» |

Efecto: un usuario sin TOTP es obligado a configurarlo antes de terminar el inicio de sesión (el OTP Form `REQUIRED` le añade `CONFIGURE_TOTP`), y quien lo
tiene debe darlo en cada login. Borrar el OTP solo obliga a configurarlo otra vez; restablecer la contraseña tampoco inicia sesión sin segundo factor.
`CONFIGURE_TOTP` y `delete_credential` siguen como estaban.

Notas:
- Al declarar `authenticationFlows`, Keycloak deja de crear los flujos por defecto, así que el realm incluye **todos** los flujos incorporados de 26.3.5
  (exportados con `partial-export`, sin ids) y sus `authenticatorConfig`, además de los dos propios y de los enlaces `registrationFlow`, `directGrantFlow`, etc.
  Al actualizar Keycloak hay que regenerarlos y compararlos.
- El subflujo `Organization` (heredado) contiene una «Condition - user configured», pero no es un segundo factor: las organizaciones están desactivadas.
- La cookie de sesión SSO (`Cookie`, `ALTERNATIVE`) evita repetir el OTP dentro de una sesión ya autenticada con él.

## Tema `pilot`

- **Login** (`temas/pilot/login`): padre `keycloak.v2` (PatternFly 5). `theme.properties` repite `css/styles.css` del padre y agrega `css/pilot.css`
  (colores y nombre "Pilot"). `messages/messages_es.properties` define las etiquetas del perfil y el error del teléfono.
- **Correo** (`temas/pilot/email`): padre `keycloak`; `messages_es.properties` reescribe la verificación de correo y la recuperación de contraseña.
- Sin recursos externos (CDN): todo se sirve desde el propio Keycloak.

## Pendiente para producción (F6)

- SMTP real (hoy Mailpit), dominio y TLS delante de Keycloak, y `start` en lugar de `start-dev`.
- Base de datos propia de Keycloak (hoy H2 en el contenedor, sin persistencia) y su respaldo.
- URIs de redirección y `webOrigins` del dominio real; retirar las de `localhost`.
- Revisar la política de bloqueo por fuerza bruta y la vigencia de sesiones.
- Flujo de pruebas con usuarios que tengan TOTP de semilla conocida (ADR-027): no se incluyen usuarios en este realm.
