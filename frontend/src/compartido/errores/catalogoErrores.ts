import { formatearMoneda } from '@/compartido/dinero';

/**
 * Catálogo único de mensajes de error (ADR-043, spec F4.5 §7.6): traduce cada código de negocio de
 * la API (`PLT-xxx`, `CON-xxx`, `INT-xxx`, CLAUDE.md §8.4, §10 y §12.4/12.7) a un mensaje en español
 * y, cuando aplica, a la acción que resuelve la situación. Ningún texto visible cita el código crudo,
 * un documento interno ni un booleano sin traducir (criterio de UX obligatorio, AGENTS.md §3.7).
 */

/** Acción sugerida junto al mensaje (p. ej. un enlace a la pantalla que corrige la causa). */
export interface AccionError {
  /** Texto del botón o enlace, en imperativo ("Configurar", "Ir a Apps"). */
  etiqueta: string;
  /** Ruta del shell a la que lleva la acción. */
  href: string;
}

/** Resultado de traducir un error: el mensaje siempre está; la acción es opcional. */
export interface MensajeError {
  mensaje: string;
  accion?: AccionError;
}

/** Forma mínima de un error de la API que necesita el catálogo (subconjunto de `ProblemDetails`). */
export interface ProblemaError {
  codigo: string;
  detail?: string;
  diferencia?: string;
}

/** Mensaje cuando el código no está en el catálogo (nunca debería ocurrir con la API real). */
const MENSAJE_GENERICO = 'No pudimos completar la operación. Inténtalo de nuevo.';

/**
 * Convierte un código en mayúsculas con guiones bajos (p. ej. `OTRO_GASTO`) en una frase legible
 * ("Otro gasto"): minúsculas, espacios y la primera letra en mayúscula.
 * @param codigo código de negocio (concepto, forma de pago, destino de gasto…)
 */
function etiquetaLegible(codigo: string): string {
  const texto = codigo.toLowerCase().replaceAll('_', ' ');
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** Magnitud de una diferencia con signo (`MontoConSigno`), sin el signo, para mostrarla como "por $X". */
function magnitud(diferencia: string): string {
  return formatearMoneda(diferencia.startsWith('-') ? diferencia.slice(1) : diferencia);
}

/** Entrada del catálogo: un mensaje fijo, o una función que lo arma con los datos de la ocurrencia. */
type Entrada = MensajeError | ((problema: ProblemaError) => MensajeError);

/** Acción de configurar las reglas de contabilización (CON-020: falta una cuenta configurada). */
const ACCION_CONFIGURAR_REGLAS: AccionError = {
  etiqueta: 'Configurar',
  href: '/configuracion/contabilidad#reglas',
};

/**
 * Catálogo por código. Cubre `PLT-` (CLAUDE.md §8.4), `CON-` (§10.1, §10.2) e `INT-` (§12.4, §12.7).
 * Las entradas dinámicas usan `detail` o `diferencia`, que la API nunca llena con datos internos.
 */
const CATALOGO: Record<string, Entrada> = {
  // ------------------------------------------------------------------ PLT — plataforma (CLAUDE.md §8.4)
  'PLT-001': { mensaje: 'No pudimos leer la solicitud. Actualiza la página e inténtalo de nuevo.' },
  'PLT-002': { mensaje: 'Hay datos por corregir en el formulario.' },
  'PLT-003': { mensaje: 'No tienes acceso a esta empresa.' },
  'PLT-004': {
    mensaje: 'Esta aplicación no está instalada en tu espacio de trabajo.',
    accion: { etiqueta: 'Ir a Apps', href: '/configuracion/apps' },
  },
  'PLT-005': { mensaje: 'Ya se procesó una solicitud distinta con esta misma clave.' },
  'PLT-006': { mensaje: 'No pudimos completar la operación. Actualiza la página e inténtalo de nuevo.' },
  'PLT-007': { mensaje: 'Esta operación no está disponible.' },
  'PLT-008': { mensaje: 'Ya hay una solicitud igual en curso. Espera un momento e inténtalo de nuevo.' },
  'PLT-009': { mensaje: 'Tu sesión expiró o no es válida. Inicia sesión de nuevo.' },
  'PLT-010': { mensaje: 'No tienes permiso para hacer esto.' },
  'PLT-011': { mensaje: 'Esta app es de la edición Enterprise y no se puede instalar.' },
  'PLT-012': { mensaje: 'Ese correo no pertenece a una persona registrada en Pilot.' },
  'PLT-013': { mensaje: 'Esa persona ya es miembro de esta empresa.' },
  'PLT-014': { mensaje: 'La empresa debe conservar al menos un administrador activo.' },
  'PLT-015': { mensaje: 'Actualiza la página antes de guardar: falta información de la versión actual.' },
  'PLT-016': {
    mensaje:
      'Alguien más modificó esto mientras lo editabas. Actualiza la página para ver los cambios recientes.',
  },
  'PLT-017': { mensaje: 'No encontramos lo que buscabas.' },
  'PLT-500': { mensaje: 'Ocurrió un error inesperado. Inténtalo de nuevo más tarde.' },

  // ------------------------------------------------------------- CON — contabilidad (CLAUDE.md §10.1, §10.2)
  'CON-001': { mensaje: 'El asiento debe tener al menos dos líneas.' },
  'CON-002': { mensaje: 'Cada línea va solo al Debe o solo al Haber.' },
  'CON-003': { mensaje: 'Los montos no pueden ser negativos y admiten hasta 2 decimales.' },
  'CON-004': { mensaje: 'Los totales del asiento deben ser mayores que cero.' },
  'CON-005': (p) => ({
    mensaje: p.diferencia ? `El asiento no cuadra por ${magnitud(p.diferencia)}.` : 'El asiento no cuadra.',
  }),
  'CON-006': { mensaje: 'Esa cuenta no existe, está inactiva o no admite movimientos.' },
  'CON-007': { mensaje: 'La fecha no puede ser futura.' },
  'CON-008': { mensaje: 'Este asiento ya fue revertido.' },
  'CON-009': { mensaje: 'Una reversión no se puede volver a revertir.' },
  'CON-010': { mensaje: 'El código de cuenta debe iniciar con una clase del 1 al 5.' },
  'CON-011': {
    mensaje: 'Esta cuenta ya tiene movimientos: no se puede cambiar su código ni agregarle subcuentas.',
  },
  'CON-012': { mensaje: 'No se puede desactivar: la cuenta tiene saldo distinto de cero.' },
  'CON-013': { mensaje: '«Lleva IVA» no se puede marcar en una cuenta de IVA.' },
  'CON-014': { mensaje: 'Ya existe una cuenta con ese código.' },
  'CON-015': {
    mensaje: 'La longitud del código no es válida o no existe una cuenta padre activa con ese prefijo.',
  },
  'CON-016': { mensaje: 'Esta cuenta está en uso por la configuración contable o por una regla activa.' },
  'CON-017': { mensaje: 'No hay una tasa de IVA vigente para esa fecha.' },
  'CON-018': { mensaje: 'La fecha de la reversión no puede ser anterior a la del asiento original.' },
  'CON-020': (p) => {
    // El detalle trae "…para <CATEGORIA>/<CODIGO>" (p. ej. "GASTO/OTRO_GASTO"); se traduce el código a una frase
    const coincidencia = p.detail?.match(/para\s+[A-Z_]+\/([A-Z_]+)/);
    const etiqueta = coincidencia?.[1] ? etiquetaLegible(coincidencia[1]) : null;
    return {
      mensaje: etiqueta
        ? `Falta configurar la cuenta para «${etiqueta}».`
        : 'Falta configurar una cuenta para completar esta operación.',
      accion: ACCION_CONFIGURAR_REGLAS,
    };
  },
  'CON-021': {
    mensaje: 'Esta cuenta es del catálogo base: su código, nombre y naturaleza no se pueden cambiar.',
  },
  'CON-022': { mensaje: 'Esa cuenta no pertenece al grupo permitido para esta operación.' },
  'CON-023': {
    mensaje: 'La depreciación está bloqueada hasta que se confirmen las vidas útiles con tu contador.',
  },

  // ------------------------------------------------------------- INT — integración (CLAUDE.md §12.4, §12.7)
  'INT-001': { mensaje: 'La operación recibida no tiene el formato esperado.' },
  'INT-002': { mensaje: 'Este tipo de operación no está disponible.' },
  'INT-004': { mensaje: 'Este cierre ya se había registrado antes.' },
  'INT-005': { mensaje: 'Ya se procesó una solicitud distinta con esta misma clave.' },
  'INT-006': (p) => ({
    mensaje: p.diferencia
      ? `Los cobros no cuadran con los ingresos por ${magnitud(p.diferencia)}.`
      : 'Los cobros no cuadran con los ingresos.',
  }),
  'INT-008': { mensaje: 'No pudimos completar la operación. Actualiza la página e inténtalo de nuevo.' },
  'INT-009': { mensaje: 'Ya hay una solicitud igual en curso. Inténtalo de nuevo en unos segundos.' },
  'INT-010': { mensaje: 'La solicitud es demasiado grande.' },
};

/** Todos los códigos que el catálogo reconoce (para pruebas de cobertura y para el buscador de ayuda). */
export const CODIGOS_CONOCIDOS: readonly string[] = Object.keys(CATALOGO);

/**
 * Traduce un error de negocio de la API a un mensaje humano y, si aplica, a una acción.
 * @param problema código de negocio y, si la ocurrencia los trae, `detail` y `diferencia`
 * @returns el mensaje y la acción del catálogo; el genérico si el código no se reconoce
 */
export function mensajeDeError(problema: ProblemaError): MensajeError {
  const entrada = CATALOGO[problema.codigo];
  if (!entrada) return { mensaje: MENSAJE_GENERICO };
  return typeof entrada === 'function' ? entrada(problema) : entrada;
}
