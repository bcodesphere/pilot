/**
 * Cliente mínimo de la API HTTP de Mailpit (`/api/v1`), con `fetch`. Keycloak envía a Mailpit el correo de
 * verificación del realm `pilot` (SMTP `mailpit:1025`), y el e2e lo lee de aquí como lo haría una persona.
 */

/** Dirección de Mailpit; sobrescribible con `E2E_MAILPIT_URL`. */
export const URL_MAILPIT = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025';

/** Correo recibido en Mailpit, con los campos que usa el e2e. */
export interface CorreoRecibido {
  /** Asunto del mensaje. */
  asunto: string;
  /** Destinatarios (direcciones). */
  para: string[];
  /** Cuerpo en texto plano. */
  texto: string;
}

/** Pausa asíncrona. */
const esperar = (ms: number) => new Promise((resolver) => setTimeout(resolver, ms));

/**
 * Espera a que llegue a Mailpit un correo para la dirección dada y lo devuelve completo.
 * @param destinatario dirección a la que se envió el correo
 * @param plazoMs tiempo máximo de espera
 * @returns el correo más reciente para esa dirección
 * @throws Error si no llega ninguno dentro del plazo
 */
export async function esperarCorreo(destinatario: string, plazoMs = 30_000): Promise<CorreoRecibido> {
  const limite = Date.now() + plazoMs;
  while (Date.now() < limite) {
    // 1. Búsqueda por destinatario (sintaxis de Mailpit: `to:<dirección>`)
    const busqueda = await fetch(
      `${URL_MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${destinatario}`)}`,
    );
    if (busqueda.ok) {
      const { messages } = (await busqueda.json()) as { messages: Array<{ ID: string }> };
      // 2. Con al menos un resultado, se lee el mensaje completo (la búsqueda solo trae un resumen)
      if (messages.length > 0) {
        const detalle = await fetch(`${URL_MAILPIT}/api/v1/message/${messages[0]!.ID}`);
        const mensaje = (await detalle.json()) as {
          Subject: string;
          To: Array<{ Address: string }>;
          Text: string;
        };
        return { asunto: mensaje.Subject, para: mensaje.To.map((t) => t.Address), texto: mensaje.Text };
      }
    }
    // 3. Aún no llega: se reintenta
    await esperar(500);
  }
  throw new Error(`No llegó ningún correo a ${destinatario} en ${plazoMs} ms`);
}

/**
 * Extrae el primer enlace `http(s)` de un correo de texto plano.
 * @param texto cuerpo del correo
 * @returns el enlace, sin saltos de línea finales
 * @throws Error si el cuerpo no trae ningún enlace
 */
export function primerEnlace(texto: string): string {
  const coincidencia = /https?:\/\/\S+/.exec(texto);
  if (!coincidencia) throw new Error('El correo no contiene ningún enlace');
  return coincidencia[0];
}
