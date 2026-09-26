import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

/** Propiedades del diálogo. */
interface PropsDialogo {
  /** Título visible; nombra al diálogo (`aria-labelledby`). */
  titulo: string;
  /** Se invoca al pedir cerrarlo (Escape o clic en el fondo). */
  onCerrar: () => void;
  /** Si es false, Escape y el fondo no lo cierran (p. ej. mientras se guarda). */
  cerrable?: boolean;
  children: ReactNode;
}

/** Selector de los elementos que pueden recibir foco dentro del diálogo. */
const FOCALIZABLES =
  'a[href],button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

/**
 * Diálogo modal accesible: `role="dialog"` + `aria-modal`, foco inicial en el primer control,
 * foco atrapado con Tab, Escape para cerrar y devolución del foco al elemento que lo abrió.
 * Se monta solo mientras está abierto (el padre decide cuándo renderizarlo).
 * @param props ver {@link PropsDialogo}
 */
export function Dialogo({ titulo, onCerrar, cerrable = true, children }: PropsDialogo) {
  const raiz = useRef<HTMLDivElement>(null);
  const idTitulo = useId();
  // Referencias estables a las últimas props para no reinstalar el efecto en cada render
  const cierre = useRef({ onCerrar, cerrable });
  // Se actualiza en un efecto (no durante el render) para respetar las reglas de React
  useEffect(() => {
    cierre.current = { onCerrar, cerrable };
  });

  useEffect(() => {
    // 1. Recuerda quién tenía el foco para devolvérselo al cerrar
    const previo = document.activeElement as HTMLElement | null;
    // 2. Foco inicial: el primer control del diálogo (o el contenedor si no hay)
    (raiz.current?.querySelector<HTMLElement>(FOCALIZABLES) ?? raiz.current)?.focus();

    /** Escape cierra; Tab cicla dentro del diálogo. */
    const alPulsar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && cierre.current.cerrable) {
        e.stopPropagation();
        cierre.current.onCerrar();
      } else if (e.key === 'Tab' && raiz.current) {
        const items = Array.from(raiz.current.querySelectorAll<HTMLElement>(FOCALIZABLES));
        if (items.length === 0) return;
        const primero = items[0]!;
        const ultimo = items[items.length - 1]!;
        // 3. Atrapa el foco: de la última vuelve a la primera y viceversa
        if (e.shiftKey && document.activeElement === primero) {
          e.preventDefault();
          ultimo.focus();
        } else if (!e.shiftKey && document.activeElement === ultimo) {
          e.preventDefault();
          primero.focus();
        }
      }
    };
    document.addEventListener('keydown', alPulsar);
    return () => {
      document.removeEventListener('keydown', alPulsar);
      previo?.focus();
    };
  }, []);

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onMouseDown={(e) => {
        // El clic en el fondo (no en el contenido) cierra
        if (e.target === e.currentTarget && cerrable) onCerrar();
      }}
    >
      <div
        ref={raiz}
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        tabIndex={-1}
        className="w-full max-w-md space-y-4 rounded-lg bg-white p-6 shadow-lg"
      >
        <h2 id={idTitulo} className="text-lg font-semibold">
          {titulo}
        </h2>
        {children}
      </div>
    </div>,
    document.body,
  );
}
